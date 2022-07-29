package e621Syncer.logic;

import java.awt.image.BufferedImage;

import javax.swing.ImageIcon;

public class PostObject {

	public int id;
	public String strMD5;
	public String strSource;
	public int iScore;
	public int iHeight;
	public int iWidth;
	public TagObject[] aTags;
	public String strExt;
	public String strExtConv;
	public int iParentID;
	public String strDescription;
	public boolean bDownloaded;
	public boolean bThumb;
	public long iFilesize;
	
	public boolean bImage = false;
	public boolean bResized = false;
	public boolean bResizeLock = false;
	public BufferedImage oImage;
	public ImageIcon oResized;
	public ImageIcon oGIF;

}
