package e621Syncer.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import e621Syncer.View;
import e621Syncer.logic.LogType;
import e621Syncer.threads.DatabaseThread;

public class Database implements Runnable {
	public static String strName = "Database";

	public View oMain;

	private HikariConfig cfg;
	private HikariDataSource ds;

	private ArrayList<DatabaseThread> aDBWorkers = new ArrayList<DatabaseThread>();

	public LinkedBlockingDeque<DBObject> aQueue = new LinkedBlockingDeque<DBObject>();
	public LinkedBlockingQueue<DatabaseThread> aFreeWorkers = new LinkedBlockingQueue<DatabaseThread>();
	public boolean bRunning = true;
	public boolean bExited = false;

	/**
	 * Create the main database class
	 * 
	 * @param m - View handle for the main view class
	 */
	public Database(View m) {
		oMain = m;

		cfg = new HikariConfig();

//		try {
//			Class.forName("com.mysql.jdbc.Driver");
//		} catch (ClassNotFoundException e) {
//			e.printStackTrace();
//		}
//		cfg.setDriverClassName("com.mysql.jdbc.Driver");
		cfg.setJdbcUrl("jdbc:mysql://" + oMain.oConf.strDBHostname + ":" + oMain.oConf.strDBPort + "/"
				+ oMain.oConf.strDBName + "?useSSL=false");
		cfg.setUsername(oMain.oConf.strDBUsername);
		cfg.setPassword(oMain.oConf.strDBPassword);
		cfg.addDataSourceProperty("cachePrepStmts", true);
		cfg.addDataSourceProperty("prepStmtCacheSize", 250);
		cfg.addDataSourceProperty("prepStmtCacheSqlLimit", 2048);
		cfg.addDataSourceProperty("databaseName", oMain.oConf.strDBName);
		ds = new HikariDataSource(cfg);

		ds.setMaximumPoolSize(oMain.oConf.iNumDBThreads * 2);
		ds.setMinimumIdle(oMain.oConf.iNumDBThreads);
		ds.setIdleTimeout(1000);
		oMain.oLog.log("HikariPool init size " + ds.getMaximumPoolSize(), null, 5, LogType.NORMAL);

		for (int i = 0; i < oMain.oConf.iNumDBThreads; i++) {
			DatabaseThread o = new DatabaseThread(this, ds, i);
			Thread t = new Thread(o, "DatabaseThread " + i);
			t.start();
			aDBWorkers.add(o);
		}
	}

	/**
	 * Main loop
	 */
	@SuppressWarnings("incomplete-switch")
	@Override
	public void run() {
		while (bRunning) {
			DBObject o = null;
			try {
				o = aQueue.take();
			} catch (InterruptedException e) {
				oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
			}
			if (o != null) {
				switch (o.command) {
				case ACCESS_SYSTEM:
					accessSystem(o);
					break;
				default:
					promoteToThreads(o);
				}
			}
		}
		cleanThreads();
		bExited = true;
	}

	/**
	 * Access the "system" database table. Used for state information.
	 * 
	 * @param o - DBObject
	 */
	private void accessSystem(DBObject o) {
		try (Connection con = ds.getConnection();
				Statement statement = con.createStatement();
				ResultSet resultSet = statement.executeQuery("SELECT * FROM system WHERE k = '" + o.strQuery1 + "'");) {
			if (resultSet.next()) {
				if (o.type == DBCommand.SELECT) {
					o.strResult1 = resultSet.getString("v");
				} else if (o.type == DBCommand.UPDATE) {
					statement.executeUpdate(
							"UPDATE system SET v = '" + o.strQuery2 + "' WHERE k = '" + o.strQuery1 + "'");
				}
			} else {
				if (o.type == DBCommand.SELECT) {
					o.bNoResult = true;
				} else if (o.type == DBCommand.UPDATE) {
					statement.executeUpdate(
							"INSERT INTO system (k, v) VALUES ('" + o.strQuery1 + "', '" + o.strQuery2 + "')");
				}
				return;
			}
		} catch (SQLException e) {
			oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
		}
		o.bFinished = true;
	}

	/**
	 * Tries to pass of a DBObject to a free worker thread
	 * 
	 * @param o
	 */
	private void promoteToThreads(DBObject o) {
		try {
			DatabaseThread t = aFreeWorkers.take();
			t.aQueue.add(o);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Kill Signal received. Wait for all tracked DB Threads to exit
	 * 
	 * @param bWait - boolean wait for exit?
	 */
	private void cleanThreads() {
		boolean loop = true;
		while (loop) {
			ArrayList<DatabaseThread> temp = new ArrayList<>();
			for (DatabaseThread t : aDBWorkers) {
				if (t.bExecuting) {
					temp.add(t);
				}
			}
			aDBWorkers = temp;

			oMain.oLog.log(strName + " kill received, waiting for " + aDBWorkers.size() + " threads", null, 0,
					LogType.NORMAL);
			if (aDBWorkers.size() == 0) {
				loop = false;
				continue;
			}
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
			}
		}
	}
}
