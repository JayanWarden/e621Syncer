package e621Syncer.threads;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import e621Syncer.View;
import e621Syncer.db.DBCommand;
import e621Syncer.db.DBObject;
import e621Syncer.logic.LogType;

public class DownloaderThread implements Runnable {
	public String strName = "DownloaderThread ";
	public int iIndex;

	public DownloadManagerThread oManager;

	public LinkedBlockingQueue<DBObject> aWork = new LinkedBlockingQueue<DBObject>();

	public DownloaderThread(DownloadManagerThread o, int i) {
		oManager = o;
		iIndex = i;
		strName += i;
	}

	@Override
	public void run() {
		while (true) {
			try {
				oManager.aIdleWorkers.put(this);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			try {
				DBObject o = aWork.take();
				download(o);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Download the given object
	 * 
	 * @param o - DBObject
	 */
	private void download(DBObject o) {
		oManager.oMain.oLog.log(strName + " download post " + o.iResult2, null, 5, LogType.NORMAL);
		oManager.updateStatus();

		File oFile = new File(oManager.oMain.oConf.strTempPath + "\\Downloaded\\" + o.oResultPostObject1.strMD5 + "."
				+ o.oResultPostObject1.strExt);
		if (oFile.exists()) {
			oFile.delete();
		}

		try {
			saveFile(
					new URL("https://static1.e621.net/data/" + o.oResultPostObject1.strMD5.substring(0, 2) + "/"
							+ o.oResultPostObject1.strMD5.substring(2, 4) + "/" + o.oResultPostObject1.strMD5 + "."
							+ o.oResultPostObject1.strExt),
					oFile.getAbsolutePath(), oManager.oMain.oConf.strUserAgent, oManager.oMain);
			ack(o);
		} catch (MalformedURLException e) {
			oManager.oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
		}

		synchronized (oManager.aIDsDownloading) {
			int iPos = -1;
			for (int i = 0; i < oManager.aIDsDownloading.size(); i++) {
				if (oManager.aIDsDownloading.get(i) == o.iResult2) {
					iPos = i;
					break;
				}
			}
			if (iPos != -1) {
				oManager.aIDsDownloading.remove(iPos);
			} else {
				oManager.oMain.oLog.log(strName + " Someone stole my aIDsDownloading !", null, 1, LogType.NORMAL);
			}
		}
		oManager.updateStatus();
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
		RequestConfig config = RequestConfig.custom().setConnectionRequestTimeout(1000 * 60)
				.setConnectTimeout(1000 * 60).setSocketTimeout(1000 * 60).build();
		CloseableHttpClient httpClient = HttpClients.createDefault();

		HttpGet httpGet = new HttpGet(imgURL.toString());
		httpGet.addHeader("User-Agent", ua);
		httpGet.setConfig(config);

		try {
			CloseableHttpResponse httpResponse = httpClient.execute(httpGet);
			HttpEntity imageEntity = httpResponse.getEntity();

			if (imageEntity != null) {
				FileUtils.copyInputStreamToFile(imageEntity.getContent(), new File(imgSavePath));
			}
			httpGet.releaseConnection();
			return true;
		} catch (IOException e) {
			oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
		}
		return false;
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
			oManager.oMain.oDB.aQueue.putFirst(o);
		} catch (InterruptedException e) {
			oManager.oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
		}
	}
}
