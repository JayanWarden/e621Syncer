package e621Syncer.threads;

import e621Syncer.View;
import e621Syncer.logic.Config;

public class UIThread implements Runnable {
	public String strName = "UIThread";

	public View oMain;

	public boolean bRunning = true;
	public boolean bExited = false;

	public int iFrametimeMS = 100;
	public long lLastFrame, currentTime, gcf, lastTick;

	/**
	 * Create a new UI Updater thread
	 * 
	 * @param o - View main class handle
	 */
	public UIThread(View o) {
		oMain = o;
	}

	/**
	 * Main loop
	 */
	@Override
	public void run() {
		while (bRunning) {
			currentTime = System.currentTimeMillis();
			if (currentTime - lLastFrame > iFrametimeMS) {
				updateTitlebar();
				if (oMain.tabbedPane.getSelectedIndex() == 0) {
					updateDownloadThread();
					updateConverterThread();
					updateSyncThread();
				} else if (oMain.tabbedPane.getSelectedIndex() == 1) {
					updatePreloadStatus();
					updateViewStatus();
				} else if (oMain.tabbedPane.getSelectedIndex() == 2) {
					updatePoolsStatus();
				}

				lLastFrame = currentTime;
			} else {
				try {
					Thread.sleep(iFrametimeMS - (currentTime - lLastFrame));
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		bExited = true;
	}

	private void updateDownloadThread() {
		oMain.lblDownladerStatus.setText(oMain.oDown.strStatus);
	}

	private void updateConverterThread() {
		oMain.lblConverterStatus.setText(oMain.oConvert.strStatus);
	}

	private void updateSyncThread() {
		oMain.lblSyncThread.setText(oMain.oDBS.strStatus);
	}

	private void updatePreloadStatus() {
		String strLeft = "";
		for (int i = 0; i < oMain.oUILogic.aQueueLeft.size(); i++) {
			strLeft += "|";
		}
		strLeft += "<" + (oMain.oUILogic.oPreloaderLeft.bExecuting ? "*" : "");

		String strRight = (oMain.oUILogic.oPreloaderRight.bExecuting ? "*" : "") + ">";
		for (int i = 0; i < oMain.oUILogic.aQueueRight.size(); i++) {
			strRight += "|";
		}

		oMain.btnRight.setText(strRight);
		oMain.btnLeft.setText(strLeft);
	}

	private void updateViewStatus() {
		oMain.lblViewStatus.setText(oMain.oUILogic.strStatus + " offset " + oMain.oUILogic.iQueryOffset);
		oMain.lblMode.setText(oMain.oUILogic.strMode);
	}

	private void updatePoolsStatus() {
		oMain.lblPoolStatus.setText(oMain.oUILogic.strStatusPools);
	}

	private void updateTitlebar() {
		long totalMem = Runtime.getRuntime().totalMemory();
		long freeMem = Runtime.getRuntime().freeMemory();
		long lCurrentMem = totalMem - freeMem;

		if (lCurrentMem < lastTick) {
			gcf = lCurrentMem;
		}

		lastTick = lCurrentMem;

		oMain.frmE.setTitle("e621syncer v" + oMain.oConf.strVersion + " | GCF " + Config.convertBitrate(gcf) + " | "
				+ Config.convertBitrate(lCurrentMem) + "/" + Config.convertBitrate(totalMem));
	}

}
