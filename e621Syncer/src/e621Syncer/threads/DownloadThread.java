package e621Syncer.threads;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import e621Syncer.View;
import e621Syncer.db.DBCommand;
import e621Syncer.db.DBObject;
import e621Syncer.logic.Config;
import e621Syncer.logic.LogType;

public class DownloadThread implements Runnable {
	public String strName = "DownloadThread";
	public String strStatus = "not initialized";

	public View oMain;

	private long lTimestampLimiter = 0;

	public boolean bRunning = false;
	public boolean bExited = true;

	private List<Integer> aIDsDownloading = Collections.synchronizedList(new ArrayList<Integer>());
	private AtomicInteger iWorkers = new AtomicInteger();

	/**
	 * Create a new downloader thread
	 * 
	 * @param o - View main class handle
	 */
	public DownloadThread(View o) {
		oMain = o;
		strStatus = "initialized";
	}

	/**
	 * Main loop
	 */
	@Override
	public void run() {
		lTimestampLimiter = System.currentTimeMillis();
		while (bRunning) {
			waitLimit();
			DBObject o = new DBObject();

			synchronized (aIDsDownloading) {
				if (aIDsDownloading.size() > 0) {
					o.strQuery1 = "WHERE NOT post_id = " + aIDsDownloading.get(0);
					Iterator<Integer> i = aIDsDownloading.iterator();
					i.next();
					while (i.hasNext()) {
						o.strQuery1 = o.strQuery1 + " AND NOT post_id = " + i.next().intValue();
					}
				} else {
					o.strQuery1 = "";
				}
			}

			o.command = DBCommand.GET_DOWNLOAD_QUEUE;
			putInQueue(o);
			Config.waitForCommand(o, 1);
			if (!o.bNoResult) {
				aIDsDownloading.add(o.iResult2);
				download(o);
			} else {
				try {
					Thread.sleep(1000 * 5);
				} catch (InterruptedException e) {
					oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
				}
			}
		}
		strStatus = "exited";
		bExited = true;
	}

	/**
	 * Download the given object
	 * 
	 * @param o - DBObject
	 */
	private void download(DBObject o) {
		while (iWorkers.get() > oMain.oConf.iDownloaderThreads) {
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		Thread t = new Thread() {
			@Override
			public void run() {
				int i = iWorkers.incrementAndGet();

				oMain.oLog.log(strName + " " + i + " download post " + o.iResult2, null, 5, LogType.NORMAL);
				updateStatus();

				File oFile = new File(oMain.oConf.strTempPath + "\\Downloaded\\" + o.oResultPostObject1.strMD5 + "."
						+ o.oResultPostObject1.strExt);
				if (oFile.exists()) {
					oFile.delete();
				}

				try {
					saveFile(
							new URL("https://static1.e621.net/data/" + o.oResultPostObject1.strMD5.substring(0, 2) + "/"
									+ o.oResultPostObject1.strMD5.substring(2, 4) + "/" + o.oResultPostObject1.strMD5
									+ "." + o.oResultPostObject1.strExt),
							oFile.getAbsolutePath(), oMain.oConf.strUserAgent, oMain);
					ack(o);
				} catch (MalformedURLException e) {
					oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
				}

				synchronized (aIDsDownloading) {
					int iPos = -1;
					for (i = 0; i < aIDsDownloading.size(); i++) {
						if (aIDsDownloading.get(i) == o.iResult2) {
							iPos = i;
							break;
						}
					}
					if (iPos != -1) {
						aIDsDownloading.remove(iPos);
					} else {
						oMain.oLog.log(strName + " " + i + " Someone stole my aIDsDownloading !", null, 1,
								LogType.NORMAL);
					}
				}
				updateStatus();

				iWorkers.decrementAndGet();
			}
		};
		t.start();
	}

	/**
	 * submethod that handles downloading
	 * 
	 * @param imgURL      - URL path to file
	 * @param imgSavePath - String absolute path to save to
	 * @param ua          - String user agent, loaded from config
	 * @param oMain       - View main object handle
	 * @return - boolean success?
	 */
	public static boolean saveFile(URL imgURL, String imgSavePath, String ua, View oMain) {
		boolean isSucceed = true;

		CloseableHttpClient httpClient = HttpClients.createDefault();

		HttpGet httpGet = new HttpGet(imgURL.toString());
		httpGet.addHeader("User-Agent", ua);

		try {
			CloseableHttpResponse httpResponse = httpClient.execute(httpGet);
			HttpEntity imageEntity = httpResponse.getEntity();

			if (imageEntity != null) {
				FileUtils.copyInputStreamToFile(imageEntity.getContent(), new File(imgSavePath));
			}

		} catch (IOException e) {
			isSucceed = false;
			oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
		}

		httpGet.releaseConnection();

		return isSucceed;
	}

	/**
	 * Keep in line with the e621 API spec. Only 1 request per second.
	 */
	private void waitLimit() {
		long lTimePassed = System.currentTimeMillis() - lTimestampLimiter;
		while (lTimePassed < 1000) {
			try {
				Thread.sleep(1000 - lTimePassed - 10 > 0 ? 1000 - lTimePassed - 10 : 0);
			} catch (InterruptedException e) {
				oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
			}
			lTimePassed = System.currentTimeMillis() - lTimestampLimiter;
		}
		lTimestampLimiter += 1000;
	}

	/**
	 * Acknowledge the download to our DB
	 * 
	 * @param o - DBObject
	 */
	private void ack(DBObject o) {
		DBObject p = new DBObject();
		p.command = DBCommand.ACK_DOWNLOAD;
		p.strQuery1 = o.iResult1 + "";
		p.strQuery2 = o.iResult2 + "";
		putInQueue(p);
	}

	/**
	 * Updates the strStatus string of this class
	 */
	private synchronized void updateStatus() {
		strStatus = "Downloading " + aIDsDownloading.size() + " post" + (aIDsDownloading.size() == 1 ? " | " : "s | ");
		synchronized (aIDsDownloading) {
			Iterator<Integer> i = aIDsDownloading.iterator();
			while (i.hasNext()) {
				strStatus = strStatus + i.next();
				if (i.hasNext()) {
					strStatus = strStatus + ", ";
				}
			}
		}
	}

	private void putInQueue(DBObject o) {
		try {
			oMain.oDB.aQueue.putFirst(o);
		} catch (InterruptedException e) {
			oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
		}
	}
}
