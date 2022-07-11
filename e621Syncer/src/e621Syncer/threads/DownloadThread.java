package e621Syncer.threads;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

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

public class DownloadThread implements Runnable {
	public String strName = "DownloadThread";
	public String strStatus = "not initialized";

	public View oMain;

	private long lTimestampLimiter = System.currentTimeMillis();

	public boolean bRunning = false;
	public boolean bExited = true;

	int iLastID = 0;

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
		while (bRunning) {
			strStatus = "Waiting for download object";
			DBObject o = new DBObject();
			o.command = DBCommand.GET_DOWNLOAD_QUEUE;
			o.strQuery1 = iLastID + "";
			putInQueue(o);
			Config.waitForCommand(o, 1);
			if (!o.bNoResult) {
				iLastID = o.iResult2;
				download(o);
			} else {
				try {
					Thread.sleep(1000 * 5);
				} catch (InterruptedException e) {
					e.printStackTrace();
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
		System.out.println(strName + " download post " + o.iResult2);
		strStatus = "Downloading post " + o.iResult2;
		waitLimit();

		File oFile = new File(oMain.oConf.strTempPath + "\\Downloaded\\" + o.oResultPostObject1.strMD5 + "."
				+ o.oResultPostObject1.strExt);
		if (oFile.exists()) {
			oFile.delete();
		}

		try {
			saveFile(new URL("https://static1.e621.net/data/" + o.oResultPostObject1.strMD5.substring(0, 2) + "/"
					+ o.oResultPostObject1.strMD5.substring(2, 4) + "/" + o.oResultPostObject1.strMD5 + "."
					+ o.oResultPostObject1.strExt), oFile.getAbsolutePath(), oMain.oConf.strUserAgent);
			ack(o);
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * submethod that handles downloading
	 * 
	 * @param imgURL      - URL path to file
	 * @param imgSavePath - String absolute path to save to
	 * @param ua          - String user agent, loaded from config
	 * @return - boolean success?
	 */
	public static boolean saveFile(URL imgURL, String imgSavePath, String ua) {
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
		}

		httpGet.releaseConnection();

		return isSucceed;
	}

	/**
	 * Keep in line with the e621 API spec. Only 1 request per second.
	 */
	private void waitLimit() {
		while (System.currentTimeMillis() - lTimestampLimiter < 1000) {
			strStatus = "Waiting for " + (1000 - (System.currentTimeMillis() - lTimestampLimiter)) + "ms";
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		lTimestampLimiter = System.currentTimeMillis();
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

	private void putInQueue(DBObject o) {
		try {
			oMain.oDB.aQueue.putFirst(o);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
