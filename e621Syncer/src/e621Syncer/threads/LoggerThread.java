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
					logException(o);
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
	private void logException(LogObject o) {
		oMain.modelException.addElement(df.format(o.oDate));

		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		o.exception.printStackTrace(pw);
		String strTrace = sw.toString();
		oMain.modelException.addElement(strTrace);

		for (int i = oMain.modelException.getSize(); i > 100; i--) {
			oMain.modelException.remove(0);
		}
		oMain.listException.setSelectedIndex(oMain.modelException.getSize() - 1);
		oMain.listException.ensureIndexIsVisible(oMain.listException.getSelectedIndex());

		if (oMain.oConf.bLogExceptionsToConsole) {
			o.exception.printStackTrace();
		}
	}

	/**
	 * Log a message
	 * 
	 * @param o - LogObject
	 */
	private void logMessage(LogObject o) {
		if (o.iSeverity <= oMain.oConf.iLogVerbosity) {
			String strMessage = df.format(o.oDate) + " " + o.strMessage;
			oMain.modelLog.addElement(strMessage);
			for (int i = oMain.modelLog.getSize(); i > 100; i--) {
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
		Date now = Calendar.getInstance().getTime();
		o.oDate = now;
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
