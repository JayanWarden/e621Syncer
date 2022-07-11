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
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;

import org.apache.commons.io.FileUtils;

import e621Syncer.View;
import e621Syncer.db.DBCommand;
import e621Syncer.db.DBObject;
import e621Syncer.logic.Config;

public class ConverterThread implements Runnable {
	public String strName = "ConverterThread";
	public String strStatus = "not initialized";

	public View oMain;

	public boolean bRunning = false;
	public boolean bExited = true;

	private int iLastID = 0;

	/**
	 * Create this converter object
	 * 
	 * @param o - View handle for the main class
	 */
	public ConverterThread(View o) {
		oMain = o;
		Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("JPEG");
		while (readers.hasNext()) {
			System.out.println("reader: " + readers.next());
		}
		strStatus = "initialized";
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
			o.strQuery1 = iLastID + "";
			putInQueue(o);
			Config.waitForCommand(o, 0);
			if (!o.bNoResult) {
				iLastID = o.oResultPostObject1.id;
				convert(o);
			} else {
				strStatus = "Sleeping";
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
	 * Start conversion on a new file
	 * 
	 * @param o - DBObject to convert
	 */
	private void convert(DBObject o) {
		System.out.println(strName + " convert " + o.oResultPostObject1.id);
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
				e.printStackTrace();
			}
			bSuccess = extractFrame(oTarget, oThumbnailSource);
			o.oResultPostObject1.strExtConv = o.oResultPostObject1.strExt;
		} else {
			System.out.println(strName + " convert failed on extension check?");
		}

		if (bSuccess) {
			bSuccess = createThumbnail(oThumbnailSource);
		} else {
			System.out.println(strName + " convert failed conversion");
		}

		if (bSuccess) {
			o.oResultPostObject1.bThumb = true;
			bSuccess = moveFile(oTarget, true);
			if (bSuccess) {
				ack(o);
			} else {
				System.out.println(
						strName + " convert failed on final move, set redownload for post " + o.oResultPostObject1.id);
				DBObject j = new DBObject();
				j.command = DBCommand.REDOWNLOAD;
				j.strQuery1 = o.oResultPostObject1.id + "";
				putInQueue(j);
			}
			if (oSource.exists()) {
				oSource.delete();
			}
		} else {
			System.out.println(strName + " convert failed on thumbnail creation, set redownload for post "
					+ o.oResultPostObject1.id);
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
		System.out.println(strName + " convertImage " + oSource.getName());
		strStatus = "Converting image";
		String[] command = new String[] { '"' + oMain.oConf.strTempPath + "\\lib\\bpgenc.exe" + '"' + " -m "
				+ oMain.oConf.iBPGSpeed + " -q " + oMain.oConf.iBPGQP + " -premul " + " -o \""
				+ oTarget.getAbsolutePath() + "\" " + " \"" + oSource.getAbsolutePath() + "\"" };

		Runtime rt = Runtime.getRuntime();
		try {
			Process proc = rt.exec(command);
			BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getErrorStream()));

			String s = null;
			while ((s = stdInput.readLine()) != null) {
				System.out.println(strName + " convertImage " + s);
			}
			stdInput.close();

			return true;
		} catch (IOException e) {
			e.printStackTrace();
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
		System.out.println(strName + " moveFile");
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
		} catch (IOException e) {
			e.printStackTrace();
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
		System.out.println(strName + " createThumbnail");
		strStatus = "Creating thumbnail";
		try {
			BufferedImage oOrig = ImageIO.read(oFile);
			Dimension imgSize = new Dimension(oOrig.getWidth(), oOrig.getHeight());
			Dimension thumbSize = new Dimension(128, 128);
			Dimension resized = getScaledDimension(imgSize, thumbSize);
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

				String[] command = new String[] {
						'"' + oMain.oConf.strTempPath + "\\lib\\cjpeg-static.exe" + '"' + " -quality 60 -outfile \""
								+ oFinished.getAbsolutePath() + "\"  \"" + oThumb.getAbsolutePath() + "\"" };

				Runtime rt = Runtime.getRuntime();
				try {
					Process proc = rt.exec(command);
					BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getErrorStream()));

					String s = null;
					while ((s = stdInput.readLine()) != null) {
						System.out.println(strName + " convertImage " + s);
					}
					stdInput.close();

					oThumb.delete();

					return true;
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
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
		System.out.println(strName + " convertVideo " + oSource.getName());
		strStatus = "Converting video";
		String[] command = new String[] { '"' + oMain.oConf.strTempPath + "\\lib\\ffmpeg.exe" + '"' + " -i "
				+ oSource.getAbsolutePath()
				+ " -c:v libx265 -vtag hvc1 -c:a copy -preset veryslow -x265-params \"wpp=1:pmode=1:pme=1:ref=8:allow-non-conformance=1:rect=1:b-intra=1:max-merge=5:weightb=1:analyze-src-pics=1:b-adapt=2:bframes=16:bframe-bias=100:crf="
				+ oMain.oConf.iHEVCCQP + "\"" + " " + oTarget.getAbsolutePath() + "\"" };

		Runtime rt = Runtime.getRuntime();
		try {
			Process proc = rt.exec(command);
			BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getErrorStream()));

			String s = null;
			while ((s = stdInput.readLine()) != null) {
				System.out.println(strName + " convertVideo " + s);
			}
			stdInput.close();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}

		if (oTarget.exists()) {
			if (oTarget.length() > 0) {
				strStatus = "Creating video thumbnail";
				command = new String[] { '"' + oMain.oConf.strTempPath + "\\lib\\ffmpeg.exe" + '"' + " -i "
						+ oSource.getAbsolutePath() + " -vf \"select=eq(n\\,0)\" -q:v 1 -frames:v 1 "
						+ oThumbnailTarget.getAbsolutePath() + "\"" };

				rt = Runtime.getRuntime();
				try {
					Process proc = rt.exec(command);
					BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getErrorStream()));

					String s = null;
					while ((s = stdInput.readLine()) != null) {
						System.out.println(strName + " convertVideo " + s);
					}
					stdInput.close();

					return true;
				} catch (IOException e) {
					e.printStackTrace();
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
			e.printStackTrace();
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

	/**
	 * Helper function to generate the correct dimension for the thumbnail crop
	 * 
	 * @param imgSize  - Dimension original image size
	 * @param boundary - Dimension bounding box for the maximum size
	 * @return Dimension new thumbnail size with correct aspect ratio
	 */
	public static Dimension getScaledDimension(Dimension imgSize, Dimension boundary) {

		int original_width = imgSize.width;
		int original_height = imgSize.height;
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

	private void putInQueue(DBObject o) {
		try {
			oMain.oDB.aQueue.putFirst(o);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}