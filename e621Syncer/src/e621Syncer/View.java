package e621Syncer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import e621Syncer.db.Database;
import e621Syncer.logic.Config;
import e621Syncer.logic.TagObject;
import e621Syncer.threads.ConverterThread;
import e621Syncer.threads.DBSyncThread;
import e621Syncer.threads.DownloadThread;
import e621Syncer.threads.UIThread;
import net.miginfocom.swing.MigLayout;

public class View {

	public JFrame frmE;
	private JButton btnDownloader, btnConverter, btnGetNewest, btnBack, btnSaveSettings;

	public Database oDB;
	public DownloadThread oDown;
	public ConverterThread oConvert;
	public Config oConf;
	public DBSyncThread oDBS;
	public ViewerLogic oUILogic;

	public JTabbedPane tabbedPane;
	public JLabel lblDownladerStatus, lblSyncThread, lblConverterStatus, lblPostSize, lblPostExtension, lblScore,
			lblViewStatus, lblMode, lblPoolStatus, lblPostFilesize, lblPostID;
	public JTextArea textAreaDescription, textAreaPoolInfo;
	private JLabel lblNewLabel, lblNewLabel_1, lblNewLabel_2, lblNewLabel_3, lblNewLabel_4, lblNewLabel_5,
			lblNewLabel_6, lblNewLabel_7, lblNewLabel_8, lblNewLabel_9, lblNewLabel_11, lblNewLabel_10, lblNewLabel_12,
			lblNewLabel_13, lblNewLabel_14, lblNewLabel_15, lblNewLabel_16, lblNewLabel_17, lblNewLabel_18,
			lblNewLabel_19, lblNewLabel_20;
	public JPanel panelViewer, panelButtonBar, panelNorth, panelInfos, panelSidebar, panelMainWindow, panelPoolInfo,
			panelPoolButtons, panelPools;
	@SuppressWarnings("rawtypes")
	public JComboBox comboBoxSort, comboBoxQuerySize;
	public JTextField textFieldSearch, textFieldDBHostname, textFieldDBPort, textFieldDBName, textFieldDBUsername,
			textFieldTempPath, textFieldArchivePath;
	public JPasswordField passwordFieldDB;
	public JButton btnLeft, btnRight, btnNewButton, btnNewButton_1;

	public DefaultListModel<TagObject> modelTags = new DefaultListModel<>();
	public JList<TagObject> listSidebar;
	public DefaultListModel<String> modelPools = new DefaultListModel<>();
	public JList<String> listPools;

	private JScrollPane listSidebarScrollPane, listPoolsScrollPane;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					View window = new View();
					window.frmE.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 */
	public View() {
		initialize();

		init();
	}

	/**
	 * Initialize the contents of the frame.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void initialize() {
		frmE = new JFrame();
		frmE.setBackground(Color.DARK_GRAY);
		frmE.setTitle("e621Syncer");
		frmE.setBounds(100, 100, 1351, 840);
		frmE.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		JMenuBar menuBar = new JMenuBar();
		menuBar.setBackground(Color.GRAY);
		frmE.setJMenuBar(menuBar);

		JMenu mnNewMenu = new JMenu("File");
		menuBar.add(mnNewMenu);

		JMenuItem mntmNewMenuItem = new JMenuItem("Close");
		mntmNewMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				quit();
			}
		});
		mnNewMenu.add(mntmNewMenuItem);

		JMenuItem mntmNewMenuItem_1 = new JMenuItem("Soft-Close");
		mntmNewMenuItem_1.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				softClose();
			}
		});
		mnNewMenu.add(mntmNewMenuItem_1);

		tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		tabbedPane.setBackground(Color.GRAY);
		frmE.getContentPane().add(tabbedPane, BorderLayout.CENTER);

		JPanel panelSettings = new JPanel();
		panelSettings.setBackground(Color.GRAY);
		tabbedPane.addTab("Settings", null, panelSettings, null);
		panelSettings.setLayout(new MigLayout("", "[][][][]", "[][][][][][][][]"));

		btnDownloader = new JButton("Start Downloader");
		btnDownloader.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				toggleDownloadThread();
			}
		});
		panelSettings.add(btnDownloader, "cell 0 0");

		lblDownladerStatus = new JLabel("~~~~");
		panelSettings.add(lblDownladerStatus, "cell 1 0");

		btnConverter = new JButton("Start Converter");
		btnConverter.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				toggleConverterThread();
			}
		});

		lblNewLabel_19 = new JLabel("Temp Path:");
		panelSettings.add(lblNewLabel_19, "cell 2 0,alignx trailing");

		textFieldTempPath = new JTextField();
		panelSettings.add(textFieldTempPath, "cell 3 0,alignx left");
		textFieldTempPath.setColumns(32);
		panelSettings.add(btnConverter, "cell 0 1");

		lblConverterStatus = new JLabel("~~~~");
		panelSettings.add(lblConverterStatus, "cell 1 1");

		lblNewLabel_20 = new JLabel("Archive Path:");
		panelSettings.add(lblNewLabel_20, "cell 2 1,alignx trailing");

		textFieldArchivePath = new JTextField();
		panelSettings.add(textFieldArchivePath, "cell 3 1,alignx left");
		textFieldArchivePath.setColumns(32);

		lblNewLabel = new JLabel("WebSync Status:");
		panelSettings.add(lblNewLabel, "cell 0 2");

		lblSyncThread = new JLabel("~~~~");
		panelSettings.add(lblSyncThread, "cell 1 2");

		lblNewLabel_12 = new JLabel("Database Settings");
		panelSettings.add(lblNewLabel_12, "cell 0 3");

		lblNewLabel_13 = new JLabel("IP / Hostname:");
		panelSettings.add(lblNewLabel_13, "cell 0 4,alignx trailing");

		textFieldDBHostname = new JTextField();
		panelSettings.add(textFieldDBHostname, "flowx,cell 1 4,alignx left");
		textFieldDBHostname.setColumns(16);

		lblNewLabel_14 = new JLabel("Port:");
		panelSettings.add(lblNewLabel_14, "cell 2 4,alignx right");

		textFieldDBPort = new JTextField();
		panelSettings.add(textFieldDBPort, "cell 3 4,alignx left");
		textFieldDBPort.setColumns(5);

		lblNewLabel_16 = new JLabel("Database Name:");
		panelSettings.add(lblNewLabel_16, "cell 0 5,alignx right");

		textFieldDBName = new JTextField();
		panelSettings.add(textFieldDBName, "cell 1 5,alignx left");
		textFieldDBName.setColumns(16);

		lblNewLabel_15 = new JLabel("Username:");
		lblNewLabel_15.setHorizontalAlignment(SwingConstants.LEFT);
		panelSettings.add(lblNewLabel_15, "cell 0 6,alignx trailing");

		textFieldDBUsername = new JTextField();
		panelSettings.add(textFieldDBUsername, "flowx,cell 1 6,alignx left");
		textFieldDBUsername.setColumns(16);

		lblNewLabel_17 = new JLabel("Password:");
		panelSettings.add(lblNewLabel_17, "cell 2 6,alignx right");

		passwordFieldDB = new JPasswordField();
		passwordFieldDB.setColumns(16);
		panelSettings.add(passwordFieldDB, "cell 3 6,alignx left");

		btnSaveSettings = new JButton("Save Settings");
		btnSaveSettings.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				saveSettings();
			}
		});
		panelSettings.add(btnSaveSettings, "cell 0 7");

		lblNewLabel_18 = new JLabel("Saving settings requires a restart");
		panelSettings.add(lblNewLabel_18, "cell 1 7 2 1");

		panelViewer = new JPanel();
		panelViewer.setBackground(Color.GRAY);
		tabbedPane.addTab("Browse", null, panelViewer, null);
		panelViewer.setLayout(new BorderLayout(0, 0));

		panelNorth = new JPanel();
		panelNorth.setBackground(Color.GRAY);
		panelViewer.add(panelNorth, BorderLayout.NORTH);
		panelNorth.setLayout(new MigLayout("", "[][grow]", "[]"));

		lblNewLabel_1 = new JLabel("Search:");
		panelNorth.add(lblNewLabel_1, "cell 0 0,alignx trailing");

		textFieldSearch = new JTextField();
		textFieldSearch.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				search();
			}
		});
		panelNorth.add(textFieldSearch, "cell 1 0,growx");
		textFieldSearch.setColumns(10);

		listSidebarScrollPane = new JScrollPane();

		panelSidebar = new JPanel();
		panelViewer.add(panelSidebar, BorderLayout.WEST);
		panelSidebar.setLayout(new BorderLayout(0, 0));
		panelSidebar.setVisible(false);

		listSidebar = new JList<TagObject>(modelTags);
		listSidebar.setBackground(Color.GRAY);
		listSidebar.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				tagClicked();
			}
		});
		// panelSidebar.add(listSidebar);
		listSidebarScrollPane.setViewportView(listSidebar);
		panelSidebar.add(listSidebarScrollPane);

		panelButtonBar = new JPanel();
		panelButtonBar.setBackground(Color.GRAY);
		FlowLayout flowLayout = (FlowLayout) panelButtonBar.getLayout();
		flowLayout.setAlignment(FlowLayout.LEFT);
		panelViewer.add(panelButtonBar, BorderLayout.SOUTH);

		btnGetNewest = new JButton("Get Newest");
		btnGetNewest.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				listNewest();
			}
		});

		btnLeft = new JButton("<");
		btnLeft.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				previous();
			}
		});

		btnBack = new JButton("Back");
		btnBack.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				back();
			}
		});
		panelButtonBar.add(btnBack);
		panelButtonBar.add(btnLeft);

		btnRight = new JButton(">");
		btnRight.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				next();
			}
		});
		panelButtonBar.add(btnRight);
		btnGetNewest.setHorizontalAlignment(SwingConstants.LEFT);
		panelButtonBar.add(btnGetNewest);

		lblNewLabel_5 = new JLabel("Query sort:");
		panelButtonBar.add(lblNewLabel_5);

		comboBoxSort = new JComboBox();
		comboBoxSort.setModel(new DefaultComboBoxModel(new String[] { "Date", "Score" }));
		comboBoxSort.setSelectedIndex(0);
		panelButtonBar.add(comboBoxSort);

		lblNewLabel_6 = new JLabel("Query size:");
		panelButtonBar.add(lblNewLabel_6);

		comboBoxQuerySize = new JComboBox();
		comboBoxQuerySize.setModel(new DefaultComboBoxModel(new String[] { "50", "75", "100", "150" }));
		comboBoxQuerySize.setSelectedIndex(0);
		panelButtonBar.add(comboBoxQuerySize);

		lblNewLabel_9 = new JLabel("Status:");
		panelButtonBar.add(lblNewLabel_9);

		lblViewStatus = new JLabel("~~~~");
		panelButtonBar.add(lblViewStatus);

		lblNewLabel_7 = new JLabel("| Mode:");
		panelButtonBar.add(lblNewLabel_7);

		lblMode = new JLabel("~");
		panelButtonBar.add(lblMode);

		panelInfos = new JPanel();
		panelInfos.setBackground(Color.GRAY);
		panelViewer.add(panelInfos, BorderLayout.EAST);
		panelInfos.setLayout(new MigLayout("", "[grow][][][]", "[][][][grow]"));
		panelInfos.setVisible(false);

		lblNewLabel_2 = new JLabel("Size:");
		panelInfos.add(lblNewLabel_2, "cell 0 0");

		lblPostSize = new JLabel("~~~~");
		panelInfos.add(lblPostSize, "flowx,cell 1 0");

		lblNewLabel_10 = new JLabel("| ID:");
		panelInfos.add(lblNewLabel_10, "cell 2 0");

		lblPostID = new JLabel("~~~~");
		panelInfos.add(lblPostID, "cell 3 0");

		lblNewLabel_3 = new JLabel("Ext:");
		panelInfos.add(lblNewLabel_3, "cell 0 1");

		lblPostExtension = new JLabel("~~~~");
		panelInfos.add(lblPostExtension, "cell 1 1");

		lblNewLabel_11 = new JLabel("| Size:");
		panelInfos.add(lblNewLabel_11, "cell 2 1");

		lblPostFilesize = new JLabel("~~~~");
		panelInfos.add(lblPostFilesize, "cell 3 1");

		lblNewLabel_4 = new JLabel("Score:");
		panelInfos.add(lblNewLabel_4, "cell 0 2");

		lblScore = new JLabel("~~~~");
		panelInfos.add(lblScore, "cell 1 2");

		textAreaDescription = new JTextArea();
		textAreaDescription.setBackground(Color.LIGHT_GRAY);
		textAreaDescription.setTabSize(4);
		textAreaDescription.setLineWrap(true);
		panelInfos.add(textAreaDescription, "cell 0 3 4 1,grow");

		panelMainWindow = new JPanel();
		panelMainWindow.setBackground(Color.GRAY);
		panelViewer.add(panelMainWindow, BorderLayout.CENTER);

		panelPools = new JPanel();
		tabbedPane.addTab("Pools", null, panelPools, null);
		panelPools.setLayout(new BorderLayout(0, 0));

		panelPoolInfo = new JPanel();
		panelPools.add(panelPoolInfo, BorderLayout.EAST);
		panelPoolInfo.setLayout(new BorderLayout(0, 0));

		textAreaPoolInfo = new JTextArea();
		textAreaPoolInfo.setBackground(Color.GRAY);
		panelPoolInfo.add(textAreaPoolInfo, BorderLayout.CENTER);

		panelPoolButtons = new JPanel();
		panelPoolButtons.setBackground(Color.GRAY);
		panelPools.add(panelPoolButtons, BorderLayout.SOUTH);
		panelPoolButtons.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));

		btnNewButton = new JButton("Load Pool");
		btnNewButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				loadPool();
			}
		});
		panelPoolButtons.add(btnNewButton);

		btnNewButton_1 = new JButton("Refresh");
		btnNewButton_1.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				refreshPools();
			}
		});
		panelPoolButtons.add(btnNewButton_1);

		lblNewLabel_8 = new JLabel("Status:");
		panelPoolButtons.add(lblNewLabel_8);

		lblPoolStatus = new JLabel("~~~~");
		panelPoolButtons.add(lblPoolStatus);

		listPools = new JList<String>(modelPools);
		listPools.setBackground(Color.GRAY);
		listPools.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				loadPoolInfo();
			}
		});
		listPoolsScrollPane = new JScrollPane();
		listPoolsScrollPane.setViewportView(listPools);
		panelPools.add(listPoolsScrollPane, BorderLayout.CENTER);
	}

	/**
	 * Post-JFrame Creation initialization. Mostly starts threads and creates
	 * objects.
	 */
	private void init() {
		oConf = new Config(this);

		try {
			oDB = new Database(this);
			Thread oDBThread = new Thread(oDB, "Database Communication Thread");
			oDBThread.start();
		} catch (Exception e) {
			e.printStackTrace();
		}

		oDBS = new DBSyncThread(this);
		Thread oDBSThread = new Thread(oDBS, "e621 DB Sync Thread");
		oDBSThread.start();

		oDown = new DownloadThread(this);
		oConvert = new ConverterThread(this);

		oUILogic = new ViewerLogic(this);

		UIThread oUI = new UIThread(this);
		Thread oUIThread = new Thread(oUI, "UI Thread");
		oUIThread.start();

		listSidebar.setCellRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = -4147678356842096276L;

			@SuppressWarnings("rawtypes")
			@Override
			public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected,
					boolean cellHasFocus) {
				Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof TagObject) {
					TagObject tag = (TagObject) value;

					setText(tag.strTag + " (" + tag.iCount + ")");
					switch (tag.iCategory) {
					case 1:
						setBackground(Color.ORANGE.brighter());
						break;
					case 3:
						setBackground(Color.PINK.darker());
						break;
					case 4:
						setBackground(Color.green);
						break;
					case 5:
						setBackground(Color.pink);
						break;
					default:
						setBackground(Color.LIGHT_GRAY);
					}
					if (isSelected) {
						setBackground(getBackground().darker());
					}
				} else {
					setText("whodat?");
				}
				return c;
			}

		});

		try {
			GlobalScreen.registerNativeHook();
			GlobalScreen.addNativeKeyListener(new NativeKeyListener() {
				public void nativeKeyPressed(NativeKeyEvent e) {
					if (frmE.isActive()) {
						// System.out.println(e.getKeyCode() + " key pressed");
						if (tabbedPane.getSelectedIndex() == 1) {
							if (!textFieldSearch.hasFocus()) {
								switch (e.getKeyCode()) {
								case 32:
									next();
									break;
								case 30:
									previous();
									break;
								}
							} else {
								if (e.getKeyCode() == 1) {
									frmE.requestFocus();
								}
							}
						}
					}
				}
			});
		} catch (NativeHookException e1) {
			e1.printStackTrace();
		}
	}

	/**
	 * Starts/Stops the download thread
	 */
	private void toggleDownloadThread() {
		if (oDown.bRunning) {
			oDown.bRunning = false;
			btnDownloader.setText("Start Downloader");
		} else {
			if (oDown.bExited) {
				oDown.bRunning = true;
				oDown.bExited = false;
				Thread oThread = new Thread(oDown, "Download Thread");
				oThread.start();
			} else {
				oDown.bRunning = true;
			}
			btnDownloader.setText("Stop Downloader");
		}
	}

	/**
	 * Starts/Stops the converter thread
	 */
	private void toggleConverterThread() {
		if (oConvert.bRunning) {
			oConvert.bRunning = false;
			btnConverter.setText("Start Converter");
		} else {
			if (oConvert.bExited) {
				oConvert.bRunning = true;
				oConvert.bExited = false;
				Thread oThread = new Thread(oConvert, "Converter Thread");
				oThread.start();
			} else {
				oConvert.bRunning = true;
			}
			btnConverter.setText("Stop Converter");
		}
	}

	private void listNewest() {
		oUILogic.iQueryOffset = 0;
		oUILogic.listNewest();
	}

	private void previous() {
		oUILogic.previous();
	}

	private void next() {
		oUILogic.next();
	}

	private void back() {
		oUILogic.back();
	}

	private void search() {
		frmE.requestFocusInWindow();
		oUILogic.search();
	}

	private void loadPoolInfo() {
		oUILogic.loadPoolInfo();
	}

	private void loadPool() {
		oUILogic.loadSelectedPool();
	}

	private void refreshPools() {
		oUILogic.loadPools();
	}

	private void tagClicked() {
		textFieldSearch.setText(
				textFieldSearch.getText() + " " + modelTags.getElementAt(listSidebar.getSelectedIndex()).strTag);
	}

	private void saveSettings() {
		oConf.save();
	}

	public void quit() {
		System.exit(0);
	}

	/**
	 * File -> Soft-Close. Waits for DB Threads to exit before closing application.
	 * To avoid DB corruption.
	 */
	public void softClose() {
		oDB.bRunning = false;
		oDown.bRunning = false;
		oConvert.bRunning = false;
		while (!oDB.bExited || !oDown.bExited || !oConvert.bExited) {
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		quit();
	}

}
