package e621Syncer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.TrayIcon.MessageType;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

import e621Syncer.db.DBCommand;
import e621Syncer.db.DBObject;
import e621Syncer.logic.Config;
import e621Syncer.logic.LogType;
import e621Syncer.logic.PoolObject;
import e621Syncer.logic.PostObject;
import e621Syncer.logic.TagObject;
import e621Syncer.threads.PreloaderThread;

public class ViewerLogic {
	public String strName = "ViewerLogic";
	public String strStatus = "loaded";
	public String strStatusPools = "";

	public View oMain;

	public int iQueryOffset = 0;

	public AtomicBoolean bThreadBusy = new AtomicBoolean(false);

	public String strMode = "list";
	public String strQuerySQL = "";

	public JComponent oTrackedCenter = null;

	private ArrayList<TagObject> aPostTags = new ArrayList<TagObject>();
	public PreloaderThread oPreloaderLeft = new PreloaderThread(this, true);
	public PreloaderThread oPreloaderRight = new PreloaderThread(this, false);
	public boolean bTagSearch = false;
	public List<Integer> aTagSearchIDs;

	public LinkedBlockingDeque<PostObject> aQueueLeft = new LinkedBlockingDeque<PostObject>();
	public LinkedBlockingDeque<PostObject> aQueueRight = new LinkedBlockingDeque<PostObject>();
	public PostObject oCurrentPost;

	private boolean bPreloadingStatus = false;

	private ArrayList<PoolObject> aPools;

	/**
	 * Create ViewerLogic
	 * 
	 * @param oMain - handle for the main View class
	 */
	public ViewerLogic(View oMain) {
		this.oMain = oMain;
		init();
		strStatus = "initialized";
	}

	/**
	 * Post-Creatin initialization
	 */
	private void init() {
		Thread oThread = new Thread(oPreloaderLeft, "Preloader Thread Next");
		oThread.start();
		Thread oThread2 = new Thread(oPreloaderRight, "Preloader Thread Previous");
		oThread2.start();

		loadPools();
	}

	/**
	 * Tries to load all pools from the Pools database table and populates the Pools
	 * tab of the View class
	 */
	public void loadPools() {
		Thread t = new Thread() {
			@SuppressWarnings("unchecked")
			@Override
			public void run() {
				try {
					strStatusPools = "Load Pools";
					DBObject o = new DBObject();
					o.command = DBCommand.GET_POOLS;
					putInQueue(o);
					strStatusPools = "Waiting for DB";
					Config.waitForCommand(o, 1);
					aPools = (ArrayList<PoolObject>) o.oResult1;

					oMain.modelPools.removeAllElements();
					oMain.textAreaPoolInfo.setText("");
					int iCounter = 0;
					ArrayList<String> aTemp = new ArrayList<String>();
					for (PoolObject p : aPools) {
						strStatusPools = "Processed " + iCounter + "/" + aPools.size();
						aTemp.add(p.strName.replaceAll("_", " "));
						iCounter++;
					}
					oMain.modelPools.addAll(aTemp);
					strStatusPools = "Finished";
				} catch (Exception e) {
					strStatusPools = "Process crashed";
					oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
				}
			}
		};
		t.start();
	}

	/**
	 * Loads the "description" field of the selected pool into the information
	 * JLabel
	 */
	public void loadPoolInfo() {
		oMain.textAreaPoolInfo.setText(aPools.get(oMain.listPools.getSelectedIndex()).strDescription);
	}

	/**
	 * Tries to load the selected pool. This is done by parsing the PostIDs of the
	 * selected pool and populating the TagSearchID ArrayList with the IDs from the
	 * pool.
	 */
	public void loadSelectedPool() {
		stopPreloading();
		iQueryOffset = 0;
		if (strMode.equals("post")) {
			oMain.panelInfos.setVisible(false);
			oMain.panelSidebar.setVisible(false);
			if (oTrackedCenter != null) {
				oMain.panelViewer.remove(oTrackedCenter);
				oMain.panelViewer.add(oMain.panelMainWindow);
				oTrackedCenter = null;
			}
		}
		PoolObject p = aPools.get(oMain.listPools.getSelectedIndex());
		aTagSearchIDs = p.aPostIDs;
		strMode = "list";
		bTagSearch = true;
		loadItems(null);
		oMain.tabbedPane.setSelectedIndex(1);
		oMain.frmE.requestFocus();
	}

	/**
	 * Lists the newest posts sorted by ID in the view window
	 */
	@SuppressWarnings("unchecked")
	public void listNewest() {
		if (!bThreadBusy.get()) {
			stopPreloading();
			iQueryOffset = 0;
			bTagSearch = false;
			oMain.panelInfos.setVisible(false);
			oMain.panelSidebar.setVisible(false);
			if (oTrackedCenter != null) {
				oMain.panelViewer.remove(oTrackedCenter);
				oMain.panelViewer.add(oMain.panelMainWindow);
				oTrackedCenter = null;
			}

			strMode = "list";
			DBObject o = new DBObject();
			o.command = DBCommand.GET_NEWEST_POSTS;
			o.strQuery1 = iQueryOffset + "";
			o.strQuery2 = getLimitSize();
			strQuerySQL = "";
			putInQueue(o);
			Config.waitForCommand(o, 0);
			if (!o.bNoResult) {
				ArrayList<Integer> aIDs = (ArrayList<Integer>) o.oResult1;
				loadItems(aIDs);
			}
		}
	}

	/**
	 * Search posts by keywords. Tries to get all Post IDs from the DB that
	 * correspond with the typed keywords
	 */
	public void search() {
		if (!bThreadBusy.get()) {
			Thread t = new Thread() {
				@SuppressWarnings("unchecked")
				@Override
				public void run() {
					bTagSearch = true;
					stopPreloading();
					iQueryOffset = 0;
					oMain.panelInfos.setVisible(false);
					oMain.panelSidebar.setVisible(false);
					if (oTrackedCenter != null) {
						oMain.panelViewer.remove(oTrackedCenter);
						oMain.panelViewer.add(oMain.panelMainWindow);
						oTrackedCenter = null;
					}
					strMode = "list";

					String strTags = oMain.textFieldSearch.getText();
					String[] aTags = strTags.split("\\s+");
					ArrayList<ArrayList<Integer>> aIDCollector = new ArrayList<ArrayList<Integer>>();
					for (String strTag : aTags) {
						strStatus = "Collecting results for " + strTag;
						DBObject o = new DBObject();
						o.command = DBCommand.GET_POST_IDS_FROM_TAG_STRING;
						o.strQuery1 = strTag;
						putInQueue(o);
						Config.waitForCommand(o, 0);
						if (!o.bNoResult && o.oResult1 != null) {
							aIDCollector.add((ArrayList<Integer>) o.oResult1);
						}
					}

					if (aIDCollector.size() > 0) {
						ArrayList<Integer> aResultIDs = aIDCollector.get(0);
						for (int i = 1; i < aIDCollector.size(); i++) {
							strStatus = "Filtering results, size " + aResultIDs.size();
							ArrayList<Integer> aTemp = new ArrayList<Integer>();
							ArrayList<Integer> aCompare = aIDCollector.get(i);
							for (int c : aCompare) {
								if (aResultIDs.contains(c)) {
									aTemp.add(c);
								}
							}
							aResultIDs = aTemp;
						}
						Collections.reverse(aResultIDs);
						aTagSearchIDs = Collections.synchronizedList(aResultIDs);
					}

					bThreadBusy.set(false);
					loadItems(null);
				}
			};
			t.start();
		}
	}

	/**
	 * Master Method for populating the View table with the preview thumbnails
	 * 
	 * @param aIDs - Just used when ClassicItemLoading, aka "List newest". List with
	 *             all Post IDs that are new.
	 */
	private void loadItems(ArrayList<Integer> aIDs) {
		Thread t = new Thread() {
			@Override
			public void run() {
				bThreadBusy.set(true);
				strMode = "list";
				oMain.panelMainWindow.removeAll();
				oMain.panelMainWindow.repaint();

				if (bTagSearch) {
					if (aTagSearchIDs != null) {
						loadItemsTagSearch();
					}
				} else {
					loadItemsClassic(aIDs);
				}
				oMain.frmE.repaint();
				bThreadBusy.set(false);
			}
		};
		t.start();
	}

	/**
	 * Tries to load all previews that matched the query for the previously looked
	 * up keyword. IDs that are not yet downloaded or have no file are excluded.
	 */
	private void loadItemsTagSearch() {
		int iChecked = 0;
		int iCurrentItems = 0;
		int iMaxItems = Integer.parseInt(getLimitSize());
		int iCounter = iQueryOffset;
		ArrayList<Integer> aToDelete = new ArrayList<Integer>();
		while (iCurrentItems < iMaxItems && iCounter < aTagSearchIDs.size()) {
			int iMaxStep = iMaxItems - iCurrentItems;

			ArrayList<DBObject> aDBOs = new ArrayList<DBObject>();
			for (int i = 0; i < iMaxStep && iCounter < aTagSearchIDs.size(); i++) {
				DBObject o = new DBObject();
				o.command = DBCommand.GET_POST;
				o.strQuery1 = aTagSearchIDs.get(iCounter) + "";
				putInQueue(o);
				aDBOs.add(o);
				iCounter++;
			}

			while (aDBOs.size() > 0) {
				DBObject o = aDBOs.get(0);
				if (o.bFinished) {
					aDBOs.remove(0);
					iChecked++;
					if (!o.oResultPostObject1.strExtConv.equals("FALSE")) {
						JLabel label = null;
						if (o.oResultPostObject1.bThumb) {
							ImageIcon oThumb = new ImageIcon(
									oMain.oConf.strArchivePath + "\\" + o.oResultPostObject1.strMD5.substring(0, 2)
											+ "\\" + o.oResultPostObject1.strMD5.substring(2, 4) + "\\"
											+ o.oResultPostObject1.strMD5 + "_thumb.jpg");
							label = new JLabel(oThumb);
						} else {
							label = new JLabel(o.oResultPostObject1.strExtConv);
						}

						if (o.oResultPostObject1.strExtConv.equals("gif")) {
							label.setBorder(BorderFactory.createLineBorder(Color.YELLOW, 5));
						} else if (o.oResultPostObject1.strExtConv.equals("mp4")) {
							label.setBorder(BorderFactory.createLineBorder(Color.BLUE, 5));
						}

						label.addMouseListener(new MouseAdapter() {
							public void mousePressed(MouseEvent e) {
								oMain.oLog.log("Clicked on: " + o.oResultPostObject1.id, null, 5, LogType.NORMAL);
								oMain.oUILogic.loadPost(o.oResultPostObject1);
							}
						});
						oMain.panelMainWindow.add(label);
						iCurrentItems++;
					} else {
						aToDelete.add(o.oResultPostObject1.id);
					}
					strStatus = "Loading item, checked " + iChecked;
				} else {
					try {
						Thread.sleep(0);
					} catch (InterruptedException e) {
						oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
					}
				}
			}
		}

		for (Integer i : aToDelete) {
			aTagSearchIDs.remove(i);
		}
		strStatus = "Done";
	}

	/**
	 * Load the given IDs as previews
	 * 
	 * @param aIDs - ArrayList<Integer> with the postIDs that should be loaded
	 */
	private void loadItemsClassic(ArrayList<Integer> aIDs) {
		ArrayList<DBObject> aDBOs = new ArrayList<DBObject>();
		for (int id : aIDs) {
			DBObject o = new DBObject();
			o.command = DBCommand.GET_POST;
			o.strQuery1 = id + "";
			putInQueue(o);
			aDBOs.add(o);
		}

		boolean bDone = false;
		while (!bDone) {
			bDone = true;
			for (DBObject o : aDBOs) {
				if (!o.bFinished) {
					bDone = false;
					break;
				}
			}
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
			}
		}

		for (DBObject o : aDBOs) {
			try {
				JLabel label = null;
				if (o.oResultPostObject1.bThumb) {
					ImageIcon oThumb = new ImageIcon(
							oMain.oConf.strArchivePath + "\\" + o.oResultPostObject1.strMD5.substring(0, 2) + "\\"
									+ o.oResultPostObject1.strMD5.substring(2, 4) + "\\" + o.oResultPostObject1.strMD5
									+ "_thumb.jpg");
					label = new JLabel(oThumb);
				} else {
					label = new JLabel(o.oResultPostObject1.strExtConv);
				}

				if (o.oResultPostObject1.strExtConv.equals("gif")) {
					label.setBorder(BorderFactory.createLineBorder(Color.YELLOW, 5));
				} else if (o.oResultPostObject1.strExtConv.equals("mp4")) {
					label.setBorder(BorderFactory.createLineBorder(Color.BLUE, 5));
				}

				label.addMouseListener(new MouseAdapter() {
					public void mousePressed(MouseEvent e) {
						oMain.oLog.log("Clicked on: " + o.oResultPostObject1.id, null, 5, LogType.NORMAL);
						oMain.oUILogic.loadPost(o.oResultPostObject1);
					}
				});

				oMain.panelMainWindow.add(label);
				oMain.panelMainWindow.repaint();
			} catch (Exception e) {
				oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
			}
		}
		oMain.frmE.repaint();
	}

	/**
	 * The logic the the "left" button. Calls different classes depending on which
	 * mode we are in right now.
	 */
	@SuppressWarnings("unchecked")
	public void previous() {
		if (!bThreadBusy.get()) {
			if (strMode.equals("list")) {
				if (bTagSearch) {
					iQueryOffset -= Integer.parseInt(getLimitSize());
					if (iQueryOffset < 0) {
						iQueryOffset = 0;
					}
					loadItems(null);
				} else {
					iQueryOffset -= Integer.parseInt(getLimitSize());
					if (iQueryOffset < 0) {
						iQueryOffset = 0;
					}
					DBObject o = new DBObject();
					o.command = DBCommand.GET_NEWEST_POSTS;
					o.strQuery1 = iQueryOffset + "";
					o.strQuery2 = getLimitSize();
					putInQueue(o);
					Config.waitForCommand(o, 0);
					if (!o.bNoResult) {
						ArrayList<Integer> aIDs = (ArrayList<Integer>) o.oResult1;
						loadItems(aIDs);
					}
				}
			} else if (strMode.equals("post")) {
				if (aQueueLeft.size() > 0) {
					try {
						aQueueRight.putFirst(oCurrentPost);
						loadPost(aQueueLeft.take());
					} catch (InterruptedException e) {
						e.printStackTrace();
					}

				}
			}
		}
	}

	/**
	 * The logic the the "right" button. Calls different classes depending on which
	 * mode we are in right now.
	 */
	@SuppressWarnings("unchecked")
	public void next() {
		if (!bThreadBusy.get()) {
			if (strMode.equals("list")) {
				if (bTagSearch) {
					iQueryOffset += Integer.parseInt(getLimitSize());
					loadItems(null);
				} else {
					iQueryOffset += Integer.parseInt(getLimitSize());
					DBObject o = new DBObject();
					o.command = DBCommand.GET_NEWEST_POSTS;
					o.strQuery1 = iQueryOffset + "";
					o.strQuery2 = getLimitSize();
					putInQueue(o);
					Config.waitForCommand(o, 0);
					if (!o.bNoResult) {
						ArrayList<Integer> aIDs = (ArrayList<Integer>) o.oResult1;
						loadItems(aIDs);
					}
				}
			} else if (strMode.equals("post")) {
				if (aQueueRight.size() > 0) {
					try {
						aQueueLeft.putFirst(oCurrentPost);
						loadPost(aQueueRight.take());
					} catch (InterruptedException e) {
						oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
					}
				}
			}
		}
	}

	/**
	 * The logic the the "Back" button. Calls different classes depending on which
	 * mode we are in right now.
	 */
	public void back() {
		if (!bThreadBusy.get()) {
			if (strMode.equals("post")) {
				if (bTagSearch) {
					oMain.panelInfos.setVisible(false);
					oMain.panelSidebar.setVisible(false);
					if (oTrackedCenter != null) {
						oMain.panelViewer.remove(oTrackedCenter);
						oMain.panelViewer.add(oMain.panelMainWindow);
						oTrackedCenter = null;
					}
					stopPreloading();
					loadItems(null);
				} else {
					listNewest();
				}
			}
		}
	}

	/**
	 * Unloads the JPanel for the previews and loads the selected Post
	 * 
	 * @param o - PostObject, the post we want to load
	 */
	public void loadPost(PostObject o) {
		if (!bThreadBusy.get()) {
			oCurrentPost = o;
			startPreloading();

			oMain.panelInfos.setVisible(true);
			oMain.panelSidebar.setVisible(true);
			oMain.listSidebar.setVisible(true);
			oMain.lblPostSize.setText(o.iWidth + "x" + o.iHeight);
			oMain.lblPostExtension.setText(o.strExtConv);
			oMain.lblScore.setText(o.iScore + "");
			oMain.textAreaDescription.setText(o.strDescription);
			oMain.lblPostID.setText(o.id + "");

			oMain.modelTags.removeAllElements();
			aPostTags.clear();
			ArrayList<TagObject> aArtists = new ArrayList<TagObject>();
			ArrayList<TagObject> aCopyrights = new ArrayList<TagObject>();
			ArrayList<TagObject> aCharacters = new ArrayList<TagObject>();
			ArrayList<TagObject> aDefinitions = new ArrayList<TagObject>();
			ArrayList<TagObject> aRest = new ArrayList<TagObject>();
			ArrayList<ArrayList<TagObject>> aCollection = new ArrayList<ArrayList<TagObject>>();
			aCollection.add(aArtists);
			aCollection.add(aCopyrights);
			aCollection.add(aCharacters);
			aCollection.add(aDefinitions);
			aCollection.add(aRest);
			for (TagObject t : o.aTags) {
				switch (t.iCategory) {
				case 1:
					aArtists.add(t);
					break;
				case 3:
					aCopyrights.add(t);
					break;
				case 4:
					aCharacters.add(t);
					break;
				case 5:
					aDefinitions.add(t);
					break;
				default:
					aRest.add(t);
				}
			}
			for (ArrayList<TagObject> list : aCollection) {
				for (TagObject t : list) {
					oMain.modelTags.addElement(t);
				}
			}

			strMode = "post";
			oMain.panelMainWindow.removeAll();
			if (o.strExtConv.equals("bpg")) {
				loadImage(o, false);
			} else if (o.strExtConv.equals("gif")) {
				loadImage(o, true);
			} else if (o.strExtConv.equals("swf")) {
				loadSWF(o);
			}
			oMain.lblPostFilesize.setText(Config.convertBitrate(o.iFilesize));
		}
	}

	/**
	 * Called by loadPost. If the selected post is an image, load the image and
	 * display it.
	 * 
	 * @param o    - PostObject to load
	 * @param bGif - boolean, is o a .gif? (we could parse that from the filename,
	 *             but meh.)
	 */
	private void loadImage(PostObject o, boolean bGif) {
		if (oTrackedCenter != null) {
			oMain.panelViewer.remove(oTrackedCenter);
			oTrackedCenter = null;
		}
		if (o.oImage == null) {
			if (bGif) {
				PreloaderThread.loadGIF(o, oMain);
			} else {
				PreloaderThread.loadBPG(o, oMain);
			}
		}

		JScrollPane pane = new JScrollPane();
		pane.getVerticalScrollBar().setUnitIncrement(16);
		pane.setBackground(Color.GRAY);
		JPanel panel = new JPanel();
		pane.setViewportView(panel);
		MouseAdapter ma = getScrollListener(panel);
		panel.addMouseListener(ma);
		panel.addMouseMotionListener(ma);
		panel.setBackground(Color.GRAY);

		if (o.oImage != null) {
			JLabel label = new JLabel(o.oImage);
			panel.add(label);
		} else {
			JLabel label = new JLabel("FAILED TO LOAD " + (bGif ? "GIF" : "BPG"));
			panel.add(label);
		}
		oMain.panelViewer.remove(oMain.panelMainWindow);
		oMain.panelViewer.add(pane, BorderLayout.CENTER);
		oTrackedCenter = pane;
		oMain.frmE.revalidate();
		oMain.frmE.repaint();
	}

	/**
	 * Deprecated. Loading .swf files is currently not possible.
	 * 
	 * @param o - PostObject to load
	 */
	private void loadSWF(PostObject o) {
		File oSource = new File(oMain.oConf.strArchivePath + "\\" + o.strMD5.substring(0, 2) + "\\"
				+ o.strMD5.substring(2, 4) + "\\" + o.strMD5 + ".swf_");
		oMain.oLog.log(strName + " loadSWF " + oSource.getAbsolutePath(), null, 5, LogType.NORMAL);
		if (oSource.exists()) {
			JLabel label = new JLabel("NO FLASH PLUGIN AVAILABLE");
			oMain.panelMainWindow.add(label);
		} else {
			JLabel label = new JLabel("FAILED TO LOAD SWF");
			oMain.panelMainWindow.add(label);
		}
		oMain.frmE.repaint();
	}

	/**
	 * Custom Scroll listener for the Display Panel. Makes it possible to scroll the
	 * displayed image by dragging the mouse with leftclick.
	 * 
	 * @param panel - JPanel to attach to. Must be the panel that the Image JLabel
	 *              gets attached to
	 * @return MouseAdapter custom scroll listener
	 */
	private MouseAdapter getScrollListener(JPanel panel) {
		MouseAdapter ma = new MouseAdapter() {

			private Point origin;

			@Override
			public void mousePressed(MouseEvent e) {
				origin = new Point(e.getPoint());
			}

			@Override
			public void mouseReleased(MouseEvent e) {
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				if (origin != null) {
					JViewport viewPort = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, panel);
					if (viewPort != null) {
						int deltaX = origin.x - e.getX();
						int deltaY = origin.y - e.getY();

						Rectangle view = viewPort.getViewRect();
						view.x += deltaX;
						view.y += deltaY;

						panel.scrollRectToVisible(view);
					}
				}
			}

		};

		return ma;
	}

	/**
	 * Helper Function to translate the Limit Size dropdown list to actual Strings
	 * (that are actually integers)
	 * 
	 * @return String limit size
	 */
	private String getLimitSize() {
		switch (oMain.comboBoxQuerySize.getSelectedIndex()) {
		case 0:
			return "50";
		case 1:
			return "75";
		case 2:
			return "100";
		case 3:
			return "150";
		}
		return "1";
	}

	/**
	 * Stops the two preloader threads. TODO: Fix this, when a thread is in the
	 * process of a long lookup action
	 */
	private void stopPreloading() {
		if (bPreloadingStatus) {
			oPreloaderLeft.bRunning = false;
			oPreloaderRight.bRunning = false;
			aQueueLeft.clear();
			aQueueRight.clear();

			bPreloadingStatus = false;
		}
	}

	/**
	 * Starts the preloader threads and feeds them the information about the current
	 * post
	 */
	private void startPreloading() {
		if (!bPreloadingStatus) {
			aQueueLeft.clear();
			aQueueRight.clear();
			oPreloaderLeft.iID = oCurrentPost.id;
			oPreloaderRight.iID = oCurrentPost.id;
			oPreloaderLeft.bRunning = true;
			oPreloaderRight.bRunning = true;
			oPreloaderRight.bReachedEnd = false;
			oPreloaderLeft.bReachedEnd = false;

			bPreloadingStatus = true;
		}
	}

	private void putInQueue(DBObject o) {
		try {
			oMain.oDB.aQueue.putFirst(o);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
