package e621Syncer.threads;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;

import org.apache.commons.io.FileUtils;

import e621Syncer.View;
import e621Syncer.db.DBCommand;
import e621Syncer.db.DBObject;
import e621Syncer.logic.Config;
import e621Syncer.logic.LogType;

public class ConverterThread implements Runnable {
	public String strName = "ConverterThread";
	public String strStatus = "not initialized";

	public View oMain;

	public boolean bRunning = false;
	public boolean bExited = true;

	public AtomicInteger iLastID = new AtomicInteger();
	private int iOffset = 0;

	/**
	 * Create this converter object
	 * 
	 * @param o - View handle for the main class
	 */
	public ConverterThread(View o, int i) {
		oMain = o;
		iOffset = i;
		strStatus = "initialized";
		strName = strName + " " + i;
	}

	/**
	 * Main loop
	 */
	@Override
	public void run() {
		while (bRunning) {
			strStatus = "Waiting for Convert Object";
			DBObject o = new DBObject();
			o.command = DBCommand.GET_CONVERT_POST;
			String strQuery = "post_id = " + iLastID.get();
			for (int i = 0; i < oMain.aConverters.size(); i++) {
				if (i != iOffset) {
					strQuery = strQuery + " AND NOT post_id = " + oMain.aConverters.get(i).iLastID.get();
				}
			}
			o.strQuery1 = strQuery;
			o.iQuery1 = iOffset;
			putInQueue(o);
			Config.waitForCommand(o, 0);
			if (!o.bNoResult) {
				if (o.oResultPostObject1.id != iLastID.get()) {
					iLastID.set(o.oResultPostObject1.id);
					convert(o);
				}
			} else {
				strStatus = "Sleeping";
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
	 * Start conversion on a new file
	 * 
	 * @param o - DBObject to convert
	 */
	private void convert(DBObject o) {
		oMain.oLog.log(strName + " convert " + o.oResultPostObject1.id, null, 5, LogType.NORMAL);
		boolean bSuccess = false;
		File oSource = new File(oMain.oConf.strTempPath + "\\Downloaded\\" + o.oResultPostObject1.strMD5 + "."
				+ o.oResultPostObject1.strExt);
		File oTarget = null;
		File oThumbnailSource = null;

		if (o.oResultPostObject1.strExt.equals("jpg") || o.oResultPostObject1.strExt.equals("png")) {
			oThumbnailSource = oSource;
			oTarget = new File(oMain.oConf.strTempPath + "\\Temp\\" + o.oResultPostObject1.strMD5 + ".bpg");
			bSuccess = convertImage(oSource, oTarget);
			o.oResultPostObject1.strExtConv = "bpg";
		} else if (o.oResultPostObject1.strExt.equals("swf")) {
			bSuccess = moveFile(oSource, false);
			o.oResultPostObject1.strExtConv = o.oResultPostObject1.strExt;
			ack(o);
			oSource.delete();
			return;
		} else if (o.oResultPostObject1.strExt.equals("webm") || o.oResultPostObject1.strExt.equals("mp4")) {
			oTarget = new File(oMain.oConf.strTempPath + "\\Temp\\" + o.oResultPostObject1.strMD5 + ".mp4");
			oThumbnailSource = new File(
					oMain.oConf.strTempPath + "\\Temp\\" + o.oResultPostObject1.strMD5 + ".big.jpg");
			bSuccess = convertVideo(oSource, oTarget, oThumbnailSource);
			o.oResultPostObject1.strExtConv = "mp4";
		} else if (o.oResultPostObject1.strExt.equals("gif")) {
			oThumbnailSource = new File(
					oMain.oConf.strTempPath + "\\Temp\\" + o.oResultPostObject1.strMD5 + ".extract.png");
			oTarget = new File(oMain.oConf.strTempPath + "\\Temp\\" + o.oResultPostObject1.strMD5 + ".gif");
			try {
				FileUtils.copyFile(oSource, oTarget);
			} catch (IOException e) {
				oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
			}
			bSuccess = extractFrame(oTarget, oThumbnailSource);
			o.oResultPostObject1.strExtConv = o.oResultPostObject1.strExt;
		} else {
			oMain.oLog.log(strName + " convert failed on extension check?", null, 4, LogType.NORMAL);
		}

		if (bSuccess) {
			bSuccess = createThumbnail(oThumbnailSource);
		} else {
			oMain.oLog.log(strName + " convert failed conversion", null, 4, LogType.NORMAL);
		}

		if (bSuccess) {
			o.oResultPostObject1.bThumb = true;
			bSuccess = moveFile(oTarget, true);
			if (bSuccess) {
				ack(o);
			} else {
				oMain.oLog.log(
						strName + " convert failed on final move, set redownload for post " + o.oResultPostObject1.id,
						null, 4, LogType.NORMAL);
				DBObject j = new DBObject();
				j.command = DBCommand.REDOWNLOAD;
				j.strQuery1 = o.oResultPostObject1.id + "";
				putInQueue(j);
			}
			if (oSource.exists()) {
				oSource.delete();
			}
		} else {
			oMain.oLog.log(strName + " convert failed on thumbnail creation, set redownload for post "
					+ o.oResultPostObject1.id, null, 4, LogType.NORMAL);
			DBObject j = new DBObject();
			j.command = DBCommand.REDOWNLOAD;
			j.strQuery1 = o.oResultPostObject1.id + "";
			putInQueue(j);
		}
		if (oTarget != null)
			if (oTarget.exists())
				oTarget.delete();
		if (oSource != null)
			if (oSource.exists())
				oSource.delete();
		if (oThumbnailSource != null)
			if (oThumbnailSource.exists())
				oThumbnailSource.delete();
	}

	/**
	 * Submethod that converts an image to bpg
	 * 
	 * @param oSource - File source file
	 * @param oTarget - File target file
	 * @return boolean success?
	 */
	private boolean convertImage(File oSource, File oTarget) {
		oMain.oLog.log(strName + " convertImage " + oSource.getName(), null, 5, LogType.NORMAL);
		strStatus = "Converting image";
		String[] command = new String[] {
				"\"lib\\bpgenc.exe" + '"' + " -m " + oMain.oConf.iBPGSpeed + " -q " + oMain.oConf.iBPGQP + " -premul "
						+ " -o \"" + oTarget.getAbsolutePath() + "\" " + " \"" + oSource.getAbsolutePath() + "\"" };

		Runtime rt = Runtime.getRuntime();
		try {
			Process proc = rt.exec(command);
			BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getErrorStream()));

			String s = null;
			while ((s = stdInput.readLine()) != null) {
				oMain.oLog.log(strName + " convertVideo " + s, null, 5, LogType.NORMAL);
			}
			stdInput.close();

			return true;
		} catch (IOException e) {
			oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
			return false;
		}
	}

	/**
	 * Moves a file from the temp directory to the archive directory
	 * 
	 * @param oFile  - File converted file to move
	 * @param bThumb - boolean has a thumbnail?
	 * @return boolean success?
	 */
	private boolean moveFile(File oFile, boolean bThumb) {
		oMain.oLog.log(strName + " moveFile", null, 4, LogType.NORMAL);
		strStatus = "Moving results";
		File oTarget = new File(oMain.oConf.strArchivePath + "\\" + oFile.getName().substring(0, 2) + "\\"
				+ oFile.getName().substring(2, 4) + "\\" + oFile.getName());
		oTarget.mkdirs();
		if (oTarget.exists()) {
			oTarget.delete();
		}

		File oThumb = new File(
				oFile.getAbsolutePath().substring(0, oFile.getAbsolutePath().lastIndexOf('.')) + "_thumb.jpg");
		File oThumbTarget = new File(oTarget.getParent(), oThumb.getName());
		if (oThumbTarget.exists()) {
			oThumbTarget.delete();
		}

		try {
			FileUtils.copyFile(oFile, oTarget);
			if (bThumb) {
				FileUtils.copyFile(oThumb, oThumbTarget);
			}
			oFile.delete();
			if (bThumb) {
				oThumb.delete();
			}
			return true;
		} catch (Exception e) {
			oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
			return false;
		}
	}

	/**
	 * Creates a thumbnail for the given source file
	 * 
	 * @param oFile - File source file
	 * @return boolean success?
	 */
	private boolean createThumbnail(File oFile) {
		oMain.oLog.log(strName + " createThumbnail", null, 5, LogType.NORMAL);
		strStatus = "Creating thumbnail";
		try {
			BufferedImage oOrig = ImageIO.read(oFile);
			Dimension imgSize = new Dimension(oOrig.getWidth(), oOrig.getHeight());
			Dimension thumbSize = new Dimension(128, 128);
			Dimension resized = Config.getScaledDimension(imgSize, thumbSize);
			int iTargetWidth = resized.width;
			int iTargetHeight = resized.height;

			if (iTargetHeight != 0 && iTargetWidth != 0) {
				Image tmp = oOrig.getScaledInstance(iTargetWidth, iTargetHeight, Image.SCALE_SMOOTH);
				BufferedImage oResized = new BufferedImage(iTargetWidth, iTargetHeight, BufferedImage.TYPE_INT_RGB);
				Graphics2D g2d = oResized.createGraphics();
				g2d.drawImage(tmp, 0, 0, null);
				g2d.dispose();

				File oThumb = new File(oMain.oConf.strTempPath + "\\Temp\\"
						+ oFile.getName().substring(0, oFile.getName().indexOf('.')) + "_thumb.png");
				ImageIO.write(oResized, "png", oThumb);
				File oFinished = new File(oMain.oConf.strTempPath + "\\Temp\\"
						+ oFile.getName().substring(0, oFile.getName().indexOf('.')) + "_thumb.jpg");

				String[] command = new String[] { "\"lib\\cjpeg-static.exe" + '"' + " -quality 60 -outfile \""
						+ oFinished.getAbsolutePath() + "\"  \"" + oThumb.getAbsolutePath() + "\"" };

				Runtime rt = Runtime.getRuntime();
				try {
					Process proc = rt.exec(command);
					BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getErrorStream()));

					String s = null;
					while ((s = stdInput.readLine()) != null) {
						oMain.oLog.log(strName + " createThumbnail " + s, null, 5, LogType.NORMAL);
					}
					stdInput.close();

					oThumb.delete();

					return true;
				} catch (IOException e) {
					oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
				}
			}
		} catch (Exception e) {
			oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
		}
		return false;
	}

	/**
	 * Converts a given source video to custom HEVC powered by ffmpeg/x265
	 * 
	 * @param oSource          - File source file
	 * @param oTarget          - File target file
	 * @param oThumbnailTarget - File thumbnail target file
	 * @return boolean success?
	 */
	private boolean convertVideo(File oSource, File oTarget, File oThumbnailTarget) {
		oMain.oLog.log(strName + " convertVideo " + oSource.getName(), null, 5, LogType.NORMAL);
		strStatus = "Converting video";
		String[] command = new String[] { "\"lib\\ffmpeg.exe" + '"' + " -i " + oSource.getAbsolutePath()
				+ " -c:v libx265 -vtag hvc1 -c:a copy -preset medium -x265-params \"wpp=1:tune=ssim:ctu=64:limit-refs=3:limit-modes=1:rc-lookahead=60:rd=5:ref=6:allow-non-conformance=1:rect=1:amp=1:aq-mode=3:b-intra=1:max-merge=5:weightb=1:analyze-src-pics=1:b-adapt=2:bframes=8:tu-intra-depth=4:tu-inter-depth=4:limit-tu=1:me=2:subme=2:ssim-rd=1:bframe-bias=100:crf="
				+ oMain.oConf.iHEVCCQP + "\"" + " " + oTarget.getAbsolutePath() + "\"" };

		Runtime rt = Runtime.getRuntime();
		try {
			Process proc = rt.exec(command);
			BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getErrorStream()));

			String s = null;
			while ((s = stdInput.readLine()) != null) {
				oMain.oLog.log(strName + " convertVideo " + s, null, 5, LogType.NORMAL);
			}
			stdInput.close();
		} catch (IOException e) {
			oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
			return false;
		}

		if (oTarget.exists()) {
			if (oTarget.length() > 0) {
				strStatus = "Creating video thumbnail";
				command = new String[] { "\"lib\\ffmpeg.exe" + '"' + " -i " + oSource.getAbsolutePath()
						+ " -vf \"select=eq(n\\,0)\" -q:v 1 -frames:v 1 " + oThumbnailTarget.getAbsolutePath() + "\"" };

				rt = Runtime.getRuntime();
				try {
					Process proc = rt.exec(command);
					BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getErrorStream()));

					String s = null;
					while ((s = stdInput.readLine()) != null) {
						oMain.oLog.log(strName + " convertVideo " + s, null, 5, LogType.NORMAL);
					}
					stdInput.close();

					return true;
				} catch (IOException e) {
					oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
					return false;
				}
			}
		}
		return false;
	}

	/**
	 * Tries to extract the first frame of a .gif file and save it as a png file
	 * 
	 * @param oSource - File source gif
	 * @param oTarget - File target png
	 * @return boolean success?
	 */
	private boolean extractFrame(File oSource, File oTarget) {
		try {
			ImageReader reader = ImageIO.getImageReadersBySuffix("gif").next();
			reader.setInput(ImageIO.createImageInputStream(new FileInputStream(oSource)), false);
			for (int i = 0; i < reader.getNumImages(true);) {
				BufferedImage image = reader.read(i);
				ImageIO.write(image, "PNG", oTarget);
				return true;
			}
		} catch (IOException e) {
			oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
		}
		return false;
	}

	/**
	 * Sends the ack signal to the DB
	 * 
	 * @param o - DBObject
	 */
	private void ack(DBObject o) {
		o.oPostObjectQuery1 = o.oResultPostObject1;
		o.command = DBCommand.ACK_CONVERT;
		putInQueue(o);
	}

	private void putInQueue(DBObject o) {
		try {
			oMain.oDB.aQueue.putFirst(o);
		} catch (InterruptedException e) {
			oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
		}
	}
}
