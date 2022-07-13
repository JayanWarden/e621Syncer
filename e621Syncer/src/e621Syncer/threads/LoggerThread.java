package e621Syncer.threads;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;

import e621Syncer.View;
import e621Syncer.logic.LogObject;
import e621Syncer.logic.LogType;

public class LoggerThread implements Runnable {
	String strName = "LoggerThread";

	public View oMain;

	public LinkedBlockingQueue<LogObject> aMainQueue = new LinkedBlockingQueue<LogObject>();

	private String strDatePattern = "dd/MM/yyyy HH:mm:ss";
	private DateFormat df = new SimpleDateFormat(strDatePattern);

	public LoggerThread(View o) {
		oMain = o;
	}

	@Override
	public void run() {
		while (true) {
			try {
				LogObject o = aMainQueue.take();
				if (o.eLogType == LogType.EXCEPTION) {
					logException(o.exception);
				} else {
					logMessage(o);
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Logs an Exception
	 * 
	 * @param e - Exception
	 */
	private void logException(Exception e) {
		Date now = Calendar.getInstance().getTime();
		oMain.modelException.addElement(df.format(now));

		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		String strTrace = sw.toString();
		oMain.modelException.addElement(strTrace);

		while (oMain.modelException.getSize() > 100) {
			oMain.modelException.remove(0);
		}
		oMain.listException.setSelectedIndex(oMain.modelException.getSize() - 1);
		oMain.listException.ensureIndexIsVisible(oMain.listException.getSelectedIndex());

		if (oMain.oConf.bLogExceptionsToConsole) {
			e.printStackTrace();
		}
	}

	/**
	 * Log a message
	 * 
	 * @param o - LogObject
	 */
	private void logMessage(LogObject o) {
		if (o.iSeverity <= oMain.oConf.iLogVerbosity) {
			Date now = Calendar.getInstance().getTime();
			String strMessage = df.format(now) + " " + o.strMessage;
			oMain.modelLog.addElement(strMessage);
			while (oMain.modelLog.getSize() > 100) {
				oMain.modelLog.remove(0);
			}
			oMain.listLog.setSelectedIndex(oMain.modelLog.getSize() - 1);
			oMain.listLog.ensureIndexIsVisible(oMain.listLog.getSelectedIndex());

			if (oMain.oConf.bLogMessagesToConsole) {
				System.out.println(strMessage);
			}
		}
	}

	/**
	 * Creates a LogObject for insertion into the queue
	 * 
	 * @param strMessage - String Message. Can be null if Exception
	 * @param e          - Exception e. Can be null if Message
	 * @param iSeverity  - Int severity, from 0 - 5, 5 is verbose
	 * @param bException - boolean Exception?
	 * @return LogObject
	 */
	public void log(String strMessage, Exception e, int iSeverity, LogType eLogType) {
		LogObject o = new LogObject();
		o.eLogType = eLogType;
		if (o.eLogType == LogType.EXCEPTION) {
			o.exception = e;
		} else {
			o.iSeverity = iSeverity;
			o.strMessage = strMessage;
		}
		try {
			aMainQueue.put(o);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
	}
}