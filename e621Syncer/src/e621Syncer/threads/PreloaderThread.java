package e621Syncer.threads;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.swing.ImageIcon;

import e621Syncer.View;
import e621Syncer.ViewerLogic;
import e621Syncer.db.DBCommand;
import e621Syncer.db.DBObject;
import e621Syncer.logic.Config;
import e621Syncer.logic.PostObject;

public class PreloaderThread implements Runnable {
	public static String strName = "PreloaderThread";

	public ViewerLogic oViewerLogic;

	private boolean bRightLoader;

	public boolean bRunning = false;
	public boolean bExecuting = false;
	public boolean bReachedEnd = false;
	public int iID;

	private String strSQL1 = "SELECT * FROM posts WHERE id ";
	private String strSQL2 = " AND NOT rename_ext = 0 GROUP BY id ORDER BY id ";
	private String strSQL3 = " LIMIT 1";

	/**
	 * Create a new Preloader Thread
	 * 
	 * @param o - ViewerLogic handle for the ViewerLogic class
	 * @param b - boolean true when we are the preloader for the "right" button
	 */
	public PreloaderThread(ViewerLogic o, boolean b) {
		oViewerLogic = o;
		bRightLoader = b;
	}

	/**
	 * Main loop
	 */
	@Override
	public void run() {
		while (true) {

			if (bRunning) {
				try {
					checkSize();
					preload();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			bExecuting = false;
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * If there are more than (5) Objects preloaded, remove one. Prevent memory
	 * overshoot and delete old preloaded posts
	 */
	private void checkSize() {
		if (bRightLoader) {
			if (oViewerLogic.aQueueLeft.size() > 5) {
				try {
					oViewerLogic.aQueueLeft.takeLast();
					PostObject o = oViewerLogic.aQueueLeft.takeLast();
					iID = o.id;
					oViewerLogic.aQueueLeft.putLast(o);
					bReachedEnd = false;
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		} else {
			if (oViewerLogic.aQueueRight.size() > 5) {
				try {
					oViewerLogic.aQueueRight.takeLast();
					PostObject o = oViewerLogic.aQueueRight.takeLast();
					iID = o.id;
					oViewerLogic.aQueueRight.putLast(o);
					bReachedEnd = false;
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Main preload method chooser
	 */
	private void preload() {
		boolean bContinue = false;
		if (bRightLoader) {
			if (oViewerLogic.aQueueLeft.size() < 5) {
				bContinue = true;
			}
		} else {
			if (oViewerLogic.aQueueRight.size() < 5) {
				bContinue = true;
			}
		}

		if (bContinue && !bReachedEnd) {
			if (oViewerLogic.bTagSearch) {
				tagPreload();
			} else {
				classicPreload();
			}
		}
	}

	/**
	 * Handle preloading of TagSearch mode
	 */
	private void tagPreload() {
		int iCurrentIndex = oViewerLogic.aTagSearchIDs.indexOf(iID);
		int iNextID;
		if (bRightLoader) {
			if (iCurrentIndex - 1 >= 0) {
				iNextID = oViewerLogic.aTagSearchIDs.get(iCurrentIndex - 1);
			} else {
				bReachedEnd = true;
				return;
			}
		} else {
			if (iCurrentIndex + 1 < oViewerLogic.aTagSearchIDs.size()) {
				iNextID = oViewerLogic.aTagSearchIDs.get(iCurrentIndex + 1);
			} else {
				bReachedEnd = true;
				return;
			}
		}

		DBObject o = new DBObject();
		o.command = DBCommand.GET_POST_FROM_ID_QUERY;
		o.strQuery1 = "SELECT id FROM posts WHERE id = " + iNextID;
		putInQueue(o);
		Config.waitForCommand(o, 0);
		if (!o.bNoResult && o.oResultPostObject1 != null) {
			if (!o.oResultPostObject1.strExtConv.equals("FALSE")) {
				loadFinal(o.oResultPostObject1);
			} else {
				iID = o.oResultPostObject1.id;
				Integer toRemove = iID;
				oViewerLogic.aTagSearchIDs.remove(toRemove);
			}
		} else {
			bReachedEnd = true;
		}
	}

	/**
	 * Handle preloading of "List Newest" mode
	 */
	private void classicPreload() {
		bExecuting = true;
		DBObject o = new DBObject();
		o.command = DBCommand.GET_POST_FROM_ID_QUERY;
		o.strQuery1 = strSQL1 + (bRightLoader ? "> " + iID : "< " + iID) + strSQL2 + (bRightLoader ? "ASC" : "DESC")
				+ strSQL3;
		putInQueue(o);
		Config.waitForCommand(o, 0);
		if (!o.bNoResult && o.oResultPostObject1 != null) {
			loadFinal(o.oResultPostObject1);
		} else {
			bReachedEnd = true;
		}
	}

	/**
	 * final preloading step
	 * 
	 * @param o - PostObject to preload
	 */
	private void loadFinal(PostObject o) {
		if (o.strExtConv.equals("bpg")) {
			loadBPG(o, oViewerLogic.oMain);
		} else if (o.strExtConv.equals("gif")) {
			loadGIF(o, oViewerLogic.oMain);
		}

		iID = o.id;

		if (bRightLoader) {
			try {
				oViewerLogic.aQueueLeft.put(o);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} else {
			try {
				oViewerLogic.aQueueRight.put(o);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Logic for loading a bpg file from disk into memory
	 * 
	 * @param o     - PostObject to load
	 * @param oMain - View Main Window handle
	 * @return boolean success?
	 */
	public static boolean loadBPG(PostObject o, View oMain) {
		try {
			File oTarget = new File(oMain.oConf.strTempPath + "\\Temp\\" + o.strMD5 + ".png");
			File oSource = new File(oMain.oConf.strArchivePath + "\\" + o.strMD5.substring(0, 2) + "\\"
					+ o.strMD5.substring(2, 4) + "\\" + o.strMD5 + ".bpg");
			o.iFilesize = oSource.length();

			String[] command = new String[] { "\"lib\\bpgdec.exe" + '"' + " -o \""
					+ oTarget.getAbsolutePath() + "\" " + " \"" + oSource.getAbsolutePath() + "\"" };

			Runtime rt = Runtime.getRuntime();
			try {
				Process proc = rt.exec(command);
				BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getErrorStream()));

				String s = null;
				while ((s = stdInput.readLine()) != null) {
					System.out.println(strName + " loadBPG " + s);
				}
				stdInput.close();
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}

			if (oTarget.exists()) {
				o.oImage = new ImageIcon(oTarget.getAbsolutePath());
			}

			oTarget.delete();
			o.bImage = true;
			return true;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	/**
	 * Logic for handling GIF loading
	 * 
	 * @param o     - PostObject to preload
	 * @param oMain - View main class handle
	 * @return boolean success?
	 */
	public static boolean loadGIF(PostObject o, View oMain) {
		try {
			File oSource = new File(oMain.oConf.strArchivePath + "\\" + o.strMD5.substring(0, 2) + "\\"
					+ o.strMD5.substring(2, 4) + "\\" + o.strMD5 + ".gif");
			o.iFilesize = oSource.length();
			if (oSource.exists()) {
				o.oImage = new ImageIcon(oSource.getAbsolutePath());
				o.bImage = true;
			}
			return true;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	private void putInQueue(DBObject o) {
		try {
			oViewerLogic.oMain.oDB.aQueue.putFirst(o);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
