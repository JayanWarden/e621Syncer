package e621Syncer.threads;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import e621Syncer.View;
import e621Syncer.db.DBCommand;
import e621Syncer.db.DBObject;
import e621Syncer.logic.Config;
import e621Syncer.logic.LogType;

public class DownloadManagerThread implements Runnable {
	public String strName = "DownloadManagerThread";
	public String strStatus = "not initialized";

	public View oMain;

	private long lTimestampLimiter = 0;

	public boolean bRunning = false;
	public boolean bExited = true;

	public List<Integer> aIDsDownloading = Collections.synchronizedList(new ArrayList<Integer>());
	private ArrayList<DownloaderThread> aWorkers = new ArrayList<DownloaderThread>();

	public LinkedBlockingQueue<DownloaderThread> aIdleWorkers = new LinkedBlockingQueue<DownloaderThread>();

	/**
	 * Create a new downloader thread
	 * 
	 * @param o - View main class handle
	 */
	public DownloadManagerThread(View o) {
		oMain = o;
		strStatus = "initialized";

		for (int i = 0; i < 5; i++) {
			DownloaderThread d = new DownloaderThread(this, i);
			Thread t = new Thread(d, "DownloaderThread " + i);
			t.start();
			aWorkers.add(d);
		}
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
				synchronized (aIDsDownloading) {
					aIDsDownloading.add(o.iResult2);
				}
				delegate(o);
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
	 * Delegate download job to subworker
	 * 
	 * @param o - DBObject
	 */
	private void delegate(DBObject o) {
		try {
			DownloaderThread t = aIdleWorkers.take();
			t.aWork.put(o);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
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
	 * Updates the strStatus string of this class
	 */
	public synchronized void updateStatus() {
		synchronized (aIDsDownloading) {
			strStatus = "Downloading " + aIDsDownloading.size() + " post" + (aIDsDownloading.size() == 1 ? " | " : "s | ");
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
