package e621Syncer.db;

import e621Syncer.logic.PostObject;

public class DBObject {
	public DBCommand type = null;
	public DBCommand command = null;

	public String strQuery1 = null;
	public String strQuery2 = null;
	public String strQuery3 = null;
	public String strQuery4 = null;
	public String[] aStrQuery1 = null;
	public int iQuery1 = 0;
	public PostObject oPostObjectQuery1 = null;

	public String strResult1 = null;
	public int iResult1 = 0;
	public int iResult2 = 0;
	public Object oResult1 = null;
	public PostObject oResultPostObject1 = null;

	public boolean bNoResult = false;
	public boolean bFinished = false;
}
