package e621Syncer.threads;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.RFC4180Parser;
import com.opencsv.RFC4180ParserBuilder;
import com.opencsv.exceptions.CsvValidationException;

import e621Syncer.View;
import e621Syncer.db.DBCommand;
import e621Syncer.db.DBObject;
import e621Syncer.logic.Config;

public class DBSyncThread implements Runnable {
	public String strName = "DBSyncThread";
	public String strStatus = "created";

	public View oMain;

	public String[] aSystemKeys = { "DBSyncTimestamp", "DBSyncDumpDate", "DBSyncResume" };

	private String strWebDate = null;

	/**
	 * Create the DBSync Thread
	 * 
	 * @param m - View main class handle
	 */
	public DBSyncThread(View m) {
		System.out.println(strName + " init");
		oMain = m;
		System.out.println(strName + " init complete");
		strStatus = "initialized";
	}

	/**
	 * Main loop
	 */
	@Override
	public void run() {
		String strResume = getResume();
		switch (strResume) {
		case "download":
			checkDumpDate();
			download();
		case "unzip":
			unzip();
		case "importTags":
			importGeneral("importTags");
		case "importImplications":
			importGeneral("importImplications");
		case "importAliases":
			importGeneral("importAliases");
		case "importPosts":
			importGeneral("importPosts");
		case "importPools":
			importGeneral("importPools");
			setResume("NONE");
		}

		while (true) {
			if (checkTimeout()) {
				DBObject o = new DBObject();
				o.command = DBCommand.ACCESS_SYSTEM;
				o.type = DBCommand.UPDATE;
				o.strQuery1 = aSystemKeys[0];
				o.strQuery2 = System.currentTimeMillis() + "";
				putInQueue(o);
				if (checkDumpDate()) {
					download();
					unzip();
					importGeneral("importTags");
					importGeneral("importImplications");
					importGeneral("importAliases");
					importGeneral("importPosts");
					importGeneral("importPools");
					setResume("NONE");
				}
			}
			// Sleep for one minute
			try {
				strStatus = "Waiting for next update";
				Thread.sleep(1000 * 60);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Have we finished all ops before exiting the app last time?
	 * 
	 * @return String with the current resume status
	 */
	private String getResume() {
		DBObject o = new DBObject();
		o.command = DBCommand.ACCESS_SYSTEM;
		o.type = DBCommand.SELECT;
		o.strQuery1 = aSystemKeys[2];
		putInQueue(o);
		Config.waitForCommand(o, 0);

		if (!o.bNoResult) {
			return o.strResult1;
		}
		return "NONE";
	}

	/**
	 * Sets the resume status in the database
	 * 
	 * @param strData - String current resume status
	 */
	private void setResume(String strData) {
		DBObject o = new DBObject();
		o.command = DBCommand.ACCESS_SYSTEM;
		o.type = DBCommand.UPDATE;
		o.strQuery1 = aSystemKeys[2];
		o.strQuery2 = strData;
		putInQueue(o);
	}

	/**
	 * Checks if enough time has elapsed to check e621 for a new DB dump
	 * 
	 * @return boolean false when timeout not reached
	 */
	private boolean checkTimeout() {
		System.out.println(strName + " checkTimeout");
		DBObject o = new DBObject();
		o.command = DBCommand.ACCESS_SYSTEM;
		o.type = DBCommand.SELECT;
		o.strQuery1 = aSystemKeys[0];
		putInQueue(o);
		Config.waitForCommand(o, 0);

		if (o.bNoResult) {
			System.out.println(strName + " checkTimeout noResult -> true");
			return true;
		}

		long lDBTime = Long.parseLong(o.strResult1);
		if (System.currentTimeMillis() - lDBTime >= oMain.oConf.iSyncThreadTimeout) {
			System.out.println(strName + " checkTimeout -> true");
			return true;
		} else {
			System.out.println(strName + " checkTimeout -> false");
			return false;
		}
	}

	/**
	 * Check the date of the dump files that are available on e621
	 * 
	 * @return boolean true when there are newer files than have already been
	 *         imported
	 */
	private boolean checkDumpDate() {
		System.out.println(strName + " checkDumpDate");
		int iDBDate = 0;
		DBObject o = new DBObject();
		o.command = DBCommand.ACCESS_SYSTEM;
		o.type = DBCommand.SELECT;
		o.strQuery1 = aSystemKeys[1];
		putInQueue(o);
		Config.waitForCommand(o, 0);

		if (!o.bNoResult) {
			iDBDate = Integer.parseInt(o.strResult1);
			System.out.println(strName + " checkDumpDate DBDate=" + iDBDate);
		}

		ArrayList<String> result = null;
		try {
			result = downloadWebPage("https://e621.net/db_export/");
			System.out.println(strName + " checkDumpDate webdata length " + result.size());
		} catch (IOException e) {
			e.printStackTrace();
		}

		int iWebDate = 0;
		int iTemp = 0;
		if (result != null) {
			for (String strData : result) {
				if (strData.contains("csv.gz")) {
					String strDate = strData.substring(strData.indexOf("-") + 1, strData.indexOf("."));
					strDate = strDate.replaceAll("-", "");
					iTemp = Integer.parseInt(strDate);
					if (iTemp > iWebDate) {
						iWebDate = iTemp;
						strWebDate = strData.substring(strData.indexOf("-") + 1, strData.indexOf("."));
					}
				}
			}
		}

		if (iWebDate > iDBDate) {
			System.out.println(strName + " checkDumpDate -> true");
			o = new DBObject();
			o.command = DBCommand.ACCESS_SYSTEM;
			o.type = DBCommand.UPDATE;
			o.strQuery1 = aSystemKeys[1];
			o.strQuery2 = iWebDate + "";
			putInQueue(o);
			return true;
		}
		System.out.println(strName + " checkDumpDate -> false");
		return false;
	}

	/**
	 * Download all database dumps from e621
	 */
	private void download() {
		System.out.println(strName + " download");
		setResume("download");

		String[] filenames = { "pools", "posts", "tag_aliases", "tag_implications", "tags", "wiki_pages" };

		for (String strFilename : filenames) {
			System.out.println(strName + " download " + strFilename);
			File oFile = new File(oMain.oConf.strTempPath + "\\Temp\\" + strFilename + ".gz");
			if (oFile.exists()) {
				oFile.delete();
			}

			try {
				Files.copy(new URL("https://e621.net/db_export/" + strFilename + "-" + strWebDate + ".csv.gz")
						.openStream(), oFile.toPath());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Unpack the gzipped downloaded dump files
	 */
	private void unzip() {
		System.out.println(strName + " unzip");
		strStatus = "Unpacking databases";
		setResume("unzip");

		String[] filenames = { "pools", "posts", "tag_aliases", "tag_implications", "tags", "wiki_pages" };

		for (String strFilename : filenames) {
			System.out.println(strName + " unzip " + strFilename);
			File oFile = new File(oMain.oConf.strTempPath + "\\Temp\\" + strFilename + ".csv");
			if (oFile.exists()) {
				oFile.delete();
			}

			try (GZIPInputStream gis = new GZIPInputStream(
					new FileInputStream(oMain.oConf.strTempPath + "\\Temp\\" + strFilename + ".gz"));
					FileOutputStream fos = new FileOutputStream(oFile)) {

				byte[] buffer = new byte[1024];
				int len;
				while ((len = gis.read(buffer)) > 0) {
					fos.write(buffer, 0, len);
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		for (String strFilename : filenames) {
			try {
				Files.delete(Paths.get(oMain.oConf.strTempPath + "\\Temp\\" + strFilename + ".gz"));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Import the dump files into our database
	 * 
	 * @param strDB - String which table are we working on?
	 */
	private void importGeneral(String strDB) {
		System.out.println(strName + " importGeneral " + strDB);
		setResume(strDB);

		File oFile = null;
		String strHeader = null;
		String strDBName = null;
		switch (strDB) {
		case "importTags":
			oFile = new File(oMain.oConf.strTempPath + "\\Temp\\tags.csv");
			strHeader = "id,name,category,post_count";
			strDBName = "tags";
			break;
		case "importImplications":
			oFile = new File(oMain.oConf.strTempPath + "\\Temp\\tag_implications.csv");
			strHeader = "id,antecedent_name,consequent_name,created_at,status";
			strDBName = "tag_implications";
			break;
		case "importAliases":
			oFile = new File(oMain.oConf.strTempPath + "\\Temp\\tag_aliases.csv");
			strHeader = "id,antecedent_name,consequent_name,created_at,status";
			strDBName = "tag_aliases";
			break;
		case "importPosts":
			oFile = new File(oMain.oConf.strTempPath + "\\Temp\\posts.csv");
			strHeader = "id,uploader_id,created_at,md5,source,rating,image_width,image_height,tag_string,locked_tags,fav_count,file_ext,parent_id,change_seq,approver_id,file_size,comment_count,description,duration,updated_at,is_deleted,is_pending,is_flagged,score,up_score,down_score,is_rating_locked,is_status_locked,is_note_locked";
			strDBName = "posts";
			break;
		case "importPools":
			oFile = new File(oMain.oConf.strTempPath + "\\Temp\\pools.csv");
			strHeader = "id,name,created_at,updated_at,creator_id,description,is_active,category,post_ids";
			strDBName = "pools";
			break;
		}

		String text = "";
		try (BufferedReader brTest = new BufferedReader(new FileReader(oFile))) {
			text = brTest.readLine();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		if (!text.equals(strHeader)) {
			System.out.println(strName + " importGeneral " + strDB + " HEADER DIFFERENT. -> return");
			System.out.println(strName + " Expected: " + strHeader);
			System.out.println(strName + " Got: " + text);
			return;
		}

		int iLineCount = countEntries(oFile);
		int iCounter = 0;
		long lThrottleMessage = System.currentTimeMillis();

		RFC4180Parser rfc4180Parser = new RFC4180ParserBuilder().build();
		try (CSVReader reader = new CSVReaderBuilder(new FileReader(oFile)).withCSVParser(rfc4180Parser).build()) {
			String[] lineInArray;
			reader.readNext();
			while ((lineInArray = reader.readNext()) != null) {

				DBObject o = new DBObject();
				o.command = DBCommand.SYNC_INSERT;
				o.strQuery1 = strDBName;
				o.aStrQuery1 = lineInArray;

				while (oMain.oDB.aQueue.size() > 50) {
					try {
						Thread.sleep(1);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}

					if (System.currentTimeMillis() - lThrottleMessage > 1000) {
						lThrottleMessage = System.currentTimeMillis();
						System.out.println(strName + " importGeneral throttled, done " + iCounter + "/" + iLineCount);
						strStatus = "Importing " + strDB.replace("import", "") + " " + iCounter + "/" + iLineCount;
					}
				}

				putInQueue(o);
				iCounter++;
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (CsvValidationException e) {
			e.printStackTrace();
		}
		System.out.println(strName + " importGeneral" + strDB + " finished populating DB queue");
	}

	/**
	 * Subclass to handle downloading a webpage to array
	 * 
	 * @param url - String URL to download
	 * @return ArrayList<String> webpage data
	 * @throws IOException
	 */
	private ArrayList<String> downloadWebPage(String url) throws IOException {
		strStatus = "Downloading " + url.toString();
		ArrayList<String> result = new ArrayList<String>();
		String line;

		URLConnection urlConnection = new URL(url).openConnection();
		urlConnection.addRequestProperty("User-Agent", oMain.oConf.strUserAgent);
		urlConnection.setReadTimeout(5000);
		urlConnection.setConnectTimeout(5000);

		try (InputStream is = urlConnection.getInputStream();
				BufferedReader br = new BufferedReader(new InputStreamReader(is))) {

			while ((line = br.readLine()) != null) {
				result.add(line);
			}

		}

		return result;

	}

	/**
	 * Counts the entries of a given .csv database dump
	 * 
	 * @param oFile - File .csv to count
	 * @return int Linecount
	 */
	@SuppressWarnings("unused")
	private int countEntries(File oFile) {
		System.out.println(strName + " countEntries " + oFile.getName());

		int iResult = 0;
		RFC4180Parser rfc4180Parser = new RFC4180ParserBuilder().build();
		try (CSVReader reader = new CSVReaderBuilder(new FileReader(oFile)).withCSVParser(rfc4180Parser).build()) {
			String[] lineInArray;
			reader.readNext();
			while ((lineInArray = reader.readNext()) != null) {
				strStatus = "Counting database entries for " + oFile.getName() + ", currently " + iResult;
				iResult++;
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (CsvValidationException e) {
			e.printStackTrace();
		}
		System.out.println(strName + " countEntries " + oFile.getName() + " has " + iResult + " lines");
		System.gc();
		System.gc();
		return iResult;
	}

	private void putInQueue(DBObject o) {
		try {
			oMain.oDB.aQueue.put(o);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

}
