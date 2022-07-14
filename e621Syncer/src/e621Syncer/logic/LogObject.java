package e621Syncer.logic;

import java.util.Date;

public class LogObject {
	public int iSeverity = 0;
	public LogType eLogType;

	public Date oDate;
	public String strMessage;
	public Exception exception;
}
