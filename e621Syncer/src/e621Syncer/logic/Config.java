package e621Syncer.logic;

import java.awt.Dimension;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

import javax.swing.ImageIcon;

import e621Syncer.View;
import e621Syncer.db.DBObject;

public class Config {

	private File oConfigFile = new File("e621syncer.ini");

	public String strVersion = "0.5.6";
	public String strUserAgent;
	public String strTempPath = "";
	public String strArchivePath = "";
	public String strDBHostname = "localhost";
	public String strDBPort = "3306";
	public String strDBUsername = "e621sync";
	public String strDBPassword = "";
	public String strDBName = "e621sync";
	public int iNumWorkers = 1;
	public int iConverterThreads = 1;
	public int iDownloaderThreads = 4;
	public int iNumDBThreads = 16;
	public int iSyncThreadTimeout = 1000 * 60 * 60;

	public int iBPGQP = 25;
	public int iBPGSpeed = 9;
	public int iJPGQ = 95;
	public int iHEVCCQP = 25;

	public int iLogVerbosity = 5;
	public boolean bLogMessagesToConsole = false;
	public boolean bLogExceptionsToConsole = true;
	public boolean bResizeImageLoading = true;

	public View oMain;

	private String strAppID;

	/**
	 * Create the config object
	 * 
	 * @param v - View handle for the main class
	 */
	public Config(View v) {
		oMain = v;
		init();
	}

	/**
	 * Post-creation initalization
	 */
	private void init() {
		if (oConfigFile.exists()) {
			load();
		}

		oMain.textFieldDBHostname.setText(strDBHostname);
		oMain.textFieldDBPort.setText(strDBPort);
		oMain.textFieldDBName.setText(strDBName);
		oMain.textFieldDBUsername.setText(strDBUsername);
		oMain.passwordFieldDB.setText(strDBPassword);
		oMain.textFieldArchivePath.setText(strArchivePath);
		oMain.textFieldTempPath.setText(strTempPath);
		oMain.spinnerConverterThreads.setValue(iConverterThreads);
		oMain.chckbxLogExceptionsToConsole.setSelected(bLogExceptionsToConsole);
		oMain.chckbxLogMessagesToConsole.setSelected(bLogMessagesToConsole);
		oMain.chckbxResizeImage.setSelected(bResizeImageLoading);
		

		if (strAppID == null) {
			strAppID = generateRandomString(16);
			save();
		}

		strUserAgent = "e621syncer/" + strVersion + " (by Furball) AppID:" + strAppID;
	}

	/**
	 * Tries to load the config file from disk. The config file is next to the
	 * executable .jar file, oConfigFile
	 */
	public void load() {
		try {
			FileReader fileReader = new FileReader(oConfigFile);
			BufferedReader bufferedReader = new BufferedReader(fileReader);
			String line = null;
			while ((line = bufferedReader.readLine()) != null) {
				parseLine(line);
			}
			bufferedReader.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Parse a single line from the config file.
	 * 
	 * @param strData - String single line from the config file
	 */
	private void parseLine(String strData) {
		try {
			String strValue = strData.substring(strData.indexOf(':') + 1);
			if (strData.startsWith("ID:")) {
				strAppID = strValue;
			} else if (strData.startsWith("TP:")) {
				strTempPath = strValue;
			} else if (strData.startsWith("AP:")) {
				strArchivePath = strValue;
			} else if (strData.startsWith("DBH:")) {
				strDBHostname = strValue;
			} else if (strData.startsWith("DBP:")) {
				strDBPort = strValue;
			} else if (strData.startsWith("DBU:")) {
				strDBUsername = strValue;
			} else if (strData.startsWith("DBPW:")) {
				strDBPassword = strValue;
			} else if (strData.startsWith("DBN:")) {
				strDBName = strValue;
			} else if (strData.startsWith("#W:")) {
				iNumWorkers = Integer.parseInt(strValue);
			} else if (strData.startsWith("DBW:")) {
				iNumDBThreads = Integer.parseInt(strValue);
			} else if (strData.startsWith("STX:")) {
				iSyncThreadTimeout = Integer.parseInt(strValue);
			} else if (strData.startsWith("BQ:")) {
				iBPGQP = Integer.parseInt(strValue);
			} else if (strData.startsWith("BS:")) {
				iBPGSpeed = Integer.parseInt(strValue);
			} else if (strData.startsWith("JQ:")) {
				iJPGQ = Integer.parseInt(strValue);
			} else if (strData.startsWith("X265CFR:")) {
				iHEVCCQP = Integer.parseInt(strValue);
			} else if (strData.startsWith("CT#:")) {
				iConverterThreads = Integer.parseInt(strValue);
			} else if (strData.startsWith("DT#:")) {
				iDownloaderThreads = Integer.parseInt(strValue);
			} else if (strData.startsWith("LGV:")) {
				iLogVerbosity = Integer.parseInt(strValue);
			} else if (strData.startsWith("LGE:")) {
				bLogExceptionsToConsole = strValue.equals("T");
			} else if (strData.startsWith("LGM:")) {
				bLogMessagesToConsole = strValue.equals("T");
			} else if(strData.startsWith("RSZ:")) {
				bResizeImageLoading = strValue.equals("T");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Write the current config to disk
	 */
	public void save() {
		strTempPath = oMain.textFieldTempPath.getText();
		strArchivePath = oMain.textFieldArchivePath.getText();
		strDBHostname = oMain.textFieldDBHostname.getText();
		strDBPort = oMain.textFieldDBPort.getText();
		strDBUsername = oMain.textFieldDBUsername.getText();
		strDBPassword = new String(oMain.passwordFieldDB.getPassword());
		strDBName = oMain.textFieldDBName.getText();
		iConverterThreads = (Integer) oMain.spinnerConverterThreads.getValue();
		bLogExceptionsToConsole = oMain.chckbxLogExceptionsToConsole.isSelected();
		bLogMessagesToConsole = oMain.chckbxLogMessagesToConsole.isSelected();
		bResizeImageLoading = oMain.chckbxResizeImage.isSelected();

		StringBuilder sb = new StringBuilder();
		sb.append("e621Syncer config file" + System.lineSeparator());
		sb.append("ID:" + strAppID + System.lineSeparator());
		sb.append("TP:" + strTempPath + System.lineSeparator());
		sb.append("AP:" + strArchivePath + System.lineSeparator());
		sb.append("DBH:" + strDBHostname + System.lineSeparator());
		sb.append("DBP:" + strDBPort + System.lineSeparator());
		sb.append("DBU:" + strDBUsername + System.lineSeparator());
		sb.append("DBPW:" + strDBPassword + System.lineSeparator());
		sb.append("DBN:" + strDBName + System.lineSeparator());
		sb.append("#W:" + iNumWorkers + System.lineSeparator());
		sb.append("DBW:" + iNumDBThreads + System.lineSeparator());
		sb.append("STX:" + iSyncThreadTimeout + System.lineSeparator());
		sb.append("BQ:" + iBPGQP + System.lineSeparator());
		sb.append("BS:" + iBPGSpeed + System.lineSeparator());
		sb.append("JQ:" + iJPGQ + System.lineSeparator());
		sb.append("X265CRF:" + iHEVCCQP + System.lineSeparator());
		sb.append("CT#:" + iConverterThreads + System.lineSeparator());
		sb.append("DT#:" + iDownloaderThreads + System.lineSeparator());
		sb.append("LGV:" + iLogVerbosity + System.lineSeparator());
		sb.append("LGE:" + (bLogExceptionsToConsole ? "T" : "F") + System.lineSeparator());
		sb.append("LGM:" + (bLogMessagesToConsole ? "T" : "F") + System.lineSeparator());
		sb.append("RSZ:" + (bResizeImageLoading ? "T" : "F") + System.lineSeparator());

		try {
			FileWriter fw = new FileWriter(oConfigFile);

			fw.write(sb.toString());
			fw.flush();
			fw.close();
			fw = null;
			sb = null;
			System.gc();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Static helper method, called by other classes. wait for the execution of the
	 * DBObject to finish
	 * 
	 * @param o     - DBObject to wait for
	 * @param sleep - Sleep timeout in milliseconds
	 */
	public static void waitForCommand(DBObject o, int sleep) {
		while (!o.bFinished) {
			try {
				Thread.sleep(sleep);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Convert bytecount to readable string
	 * 
	 * @param lBitrate - Size in bytes
	 * @return String readable filesize
	 */
	public static String convertBitrate(long lBitrate) {
		if (lBitrate < 10000)
			return lBitrate + "B";
		lBitrate /= 1000;
		if (lBitrate < 10000)
			return lBitrate + "KiB";
		lBitrate /= 1000;
		if (lBitrate < 10000)
			return lBitrate + "MiB";
		lBitrate /= 1000;
		if (lBitrate < 10000)
			return lBitrate + "GiB";
		lBitrate /= 1000;
		return lBitrate + "TiB";
	}

	/**
	 * Generates a random lowercase character string with length iLen
	 * 
	 * @param iLen - Length of the returned random String
	 * @return String - random data
	 */
	public static String generateRandomString(int iLen) {
		int leftLimit = 97; // letter 'a'
		int rightLimit = 122; // letter 'z'
		Random random = new Random();
		StringBuilder buffer = new StringBuilder(iLen);
		for (int i = 0; i < iLen; i++) {
			int randomLimitedInt = leftLimit + (int) (random.nextFloat() * (rightLimit - leftLimit + 1));
			buffer.append((char) randomLimitedInt);
		}
		return buffer.toString();
	}
	
	/**
	 * Resize a loaded image in the given PostObject to a maximum viewport size, retaining aspect ratio
	 * @param o - PostObject to resize
	 * @param dViewport - Dimension maximum viewport size
	 */
	public static void resizeImage(PostObject o, Dimension dViewport) {
		Dimension d = getScaledDimension(new Dimension(o.oImage.getWidth(), o.oImage.getHeight()),
				dViewport);
		o.oResized = new ImageIcon(o.oImage.getScaledInstance(d.width, d.height, java.awt.Image.SCALE_SMOOTH));
		o.bResized = true;
	}
	
	/**
	 * Helper function to generate new boundary constrained dimensions while preserving aspect ratio
	 * 
	 * @param source  - Dimension original image size
	 * @param boundary - Dimension bounding box for the maximum size
	 * @return Dimension new thumbnail size with correct aspect ratio
	 */
	public static Dimension getScaledDimension(Dimension source, Dimension boundary) {

		int original_width = source.width;
		int original_height = source.height;
		int bound_width = boundary.width;
		int bound_height = boundary.height;
		int new_width = original_width;
		int new_height = original_height;

		if (original_width > bound_width) {
			new_width = bound_width;
			new_height = (new_width * original_height) / original_width;
		}

		if (new_height > bound_height) {
			new_height = bound_height;
			new_width = (new_height * original_width) / original_height;
		}

		return new Dimension(new_width, new_height);
	}
}
