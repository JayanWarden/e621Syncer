package e621Syncer.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import e621Syncer.View;
import e621Syncer.logic.LogType;
import e621Syncer.logic.PoolObject;
import e621Syncer.logic.PostObject;
import e621Syncer.logic.TagObject;

public class Database implements Runnable {
	public static String strName = "Database";

	private View oMain;

	private HikariConfig cfg;
	private HikariDataSource ds;

	private AtomicInteger iWorkers = new AtomicInteger();
	private ArrayList<Thread> aTrackedThreads = new ArrayList<Thread>();

	public LinkedBlockingDeque<DBObject> aQueue = new LinkedBlockingDeque<DBObject>();
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

		ds.setMaximumPoolSize(200);
		ds.setMinimumIdle(10);
		ds.setIdleTimeout(1000);
		oMain.oLog.log("HikariPool init size " + ds.getMaximumPoolSize(), null, 5, LogType.NORMAL);
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
				case SYNC_INSERT:
					syncInsert(o);
					break;
				case GET_DOWNLOAD_QUEUE:
					getDownloadQueue(o);
					break;
				case GET_POST:
					getPost(o);
					break;
				case GET_POST_FROM_ID_QUERY:
					getPostFromIDQuery(o);
					break;
				case ACK_DOWNLOAD:
					ackDownload(o);
					break;
				case GET_CONVERT_POST:
					getConvertPost(o);
					break;
				case ACK_CONVERT:
					ackConvert(o);
					break;
				case GET_NEWEST_POSTS:
					getNewestPosts(o);
					break;
				case REDOWNLOAD:
					redownload(o);
					break;
				case GET_POST_IDS_FROM_TAG_STRING:
					searchTags(o);
					break;
				case GET_POOLS:
					getPools(o);
					break;
				}
			}
			cleanThreads(false);
		}
		cleanThreads(true);
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
	 * Kill Signal received. Wait for all tracked DB Threads to exit
	 * 
	 * @param bWait - boolean wait for exit?
	 */
	private void cleanThreads(boolean bWait) {
		boolean loop = true;
		while (loop) {
			loop = false;
			ArrayList<Thread> temp = new ArrayList<>();
			for (Thread t : aTrackedThreads) {
				if (t.isAlive()) {
					temp.add(t);
				}
			}
			aTrackedThreads = temp;
			if (bWait) {
				oMain.oLog.log(strName + " kill received, waiting for " + aTrackedThreads.size() + " threads", null, 0,
						LogType.NORMAL);
				loop = true;
				if (aTrackedThreads.size() == 0) {
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

	/**
	 * Insert updates from the e621 database dump into our own database
	 * 
	 * @param o - DBObject
	 */
	private void syncInsert(DBObject o) {
		waitForThreads();
		if (o.strQuery1.equals("tags")) {
			syncInsertTags(o);
		} else if (o.strQuery1.equals("tag_implications") || o.strQuery1.equals("tag_aliases")) {
			syncInsertImplications(o);
		} else if (o.strQuery1.equals("posts")) {
			syncInsertPosts(o);
		} else if (o.strQuery1.equals("pools")) {
			syncInsertPools(o);
		}
	}

	/**
	 * Insert updates from the e621 database dump into our own database. Handles the
	 * "tags" database table.
	 * 
	 * @param o - DBObject
	 */
	private void syncInsertTags(DBObject o) {
		// id,name,category,post_count
		if (o.aStrQuery1.length == 4) {
			Thread t = new Thread() {
				@Override
				public void run() {
					iWorkers.incrementAndGet();
					try (Connection con = ds.getConnection();
							PreparedStatement ps = con.prepareStatement(
									"INSERT INTO tags (id, name, category, post_count) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE category=VALUES(category), post_count=VALUES(post_count)");) {

						ps.setString(1, o.aStrQuery1[0]);
						ps.setString(2, o.aStrQuery1[1]);
						ps.setString(3, o.aStrQuery1[2]);
						ps.setString(4, o.aStrQuery1[3]);
						ps.executeUpdate();
					} catch (Exception e) {
						oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
					}
					iWorkers.decrementAndGet();
					o.bFinished = true;
				}
			};
			t.start();
			aTrackedThreads.add(t);
		}
	}

	/**
	 * Insert updates from the e621 database dump into our own database. Handles the
	 * "tag_implications" database table.
	 * 
	 * @param o - DBObject
	 */
	private void syncInsertImplications(DBObject o) {
		// id,antecedent_name,consequent_name,created_at,status
		if (o.aStrQuery1.length == 5) {
			Thread t = new Thread() {
				@Override
				public void run() {
					iWorkers.incrementAndGet();
					try (Connection con = ds.getConnection();
							PreparedStatement ps = con.prepareStatement("INSERT INTO " + o.strQuery1
									+ " (id, antecedent_name, consequent_name, status) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE status=VALUES(status)");) {

						ps.setString(1, o.aStrQuery1[0]);
						ps.setString(2, o.aStrQuery1[1]);
						ps.setString(3, o.aStrQuery1[2]);
						ps.setString(4, o.aStrQuery1[4]);
						ps.executeUpdate();
					} catch (Exception e) {
						oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
					}
					iWorkers.decrementAndGet();
					o.bFinished = true;
				}
			};
			t.start();
			aTrackedThreads.add(t);
		}
	}

	/**
	 * Insert updates from the e621 database dump into our own database. Handles the
	 * "posts" database table.
	 * 
	 * @param o - DBObject
	 */
	private void syncInsertPosts(DBObject o) {
		// id,uploader_id,created_at,md5,source,rating,image_width,image_height,tag_string,locked_tags,fav_count,file_ext,
		// parent_id,change_seq,approver_id,file_size,comment_count,description,duration,updated_at,is_deleted,is_pending,
		// is_flagged,score,up_score,down_score,is_rating_locked,is_status_locked,is_note_locked
		if (o.aStrQuery1.length == 29) {
			Thread t = new Thread() {
				@Override
				public void run() {
					iWorkers.incrementAndGet();
					try (Connection con = ds.getConnection();
							PreparedStatement ps = con.prepareStatement("SELECT * FROM posts WHERE id = ?");) {
						ps.setString(1, o.aStrQuery1[0]);
						ResultSet rs = ps.executeQuery();
						if (rs.next()) {
							if (!rs.getString("tag_string").equals(o.aStrQuery1[8])) {
								deleteTagmap(o, con, oMain);
								createTagmap(o, con, oMain);
							}
							if (!rs.getString("source").equals(o.aStrQuery1[4])
									|| !rs.getString("score").equals(o.aStrQuery1[23])
									|| !rs.getString("parent_id").equals(o.aStrQuery1[12])
									|| !rs.getString("description").equals(o.aStrQuery1[17])) {

								try (PreparedStatement ps2 = con.prepareStatement(
										"UPDATE posts SET source = ?, score = ?, parent_id = ?, description = ? WHERE id = ?");) {
									ps2.setString(1, o.aStrQuery1[4]);
									ps2.setString(2, o.aStrQuery1[23]);
									ps2.setString(3, (o.aStrQuery1[12].equals("") ? "0" : o.aStrQuery1[12]));
									ps2.setString(4, o.aStrQuery1[17]);
									ps2.setString(5, o.aStrQuery1[0]);
									ps2.executeUpdate();
								}
							}
						} else {
							try (PreparedStatement ps2 = con.prepareStatement(
									"INSERT INTO posts (id, md5, source, score, image_width, image_height, tag_string, file_ext, parent_id, description, is_deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");) {
								ps2.setString(1, o.aStrQuery1[0]);
								ps2.setString(2, o.aStrQuery1[3]);
								ps2.setString(3, o.aStrQuery1[4]);
								ps2.setString(4, o.aStrQuery1[23]);
								ps2.setString(5, o.aStrQuery1[6]);
								ps2.setString(6, o.aStrQuery1[7]);
								ps2.setString(7, o.aStrQuery1[8]);
								ps2.setString(8, o.aStrQuery1[11]);
								ps2.setString(9, (o.aStrQuery1[12].equals("") ? "0" : o.aStrQuery1[12]));
								ps2.setString(10, o.aStrQuery1[17]);
								ps2.setString(11, o.aStrQuery1[20]);
								ps2.executeUpdate();
								deleteTagmap(o, con, oMain);
								createTagmap(o, con, oMain);
							}

							if (o.aStrQuery1[20].equals("f")) {
								try (PreparedStatement ps2 = con
										.prepareStatement("INSERT INTO download_queue (post_id) VALUES (?)");) {
									ps2.setString(1, o.aStrQuery1[0]);
									ps2.executeUpdate();
								}
							}
						}
					} catch (Exception e) {
						oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
					}
					iWorkers.decrementAndGet();
					o.bFinished = true;
				}
			};
			t.start();
			aTrackedThreads.add(t);
		} else {
			oMain.oLog.log(strName + " syncInsertPosts incorrect num arguments, " + o.aStrQuery1.length, null, 1,
					LogType.NORMAL);
		}
	}

	/**
	 * Create or update entries in the "tagmap" table.
	 * 
	 * @param o     - DBObject
	 * @param con   - Connection (HikariCP Connection object recycling)
	 * @param oMain - View handle for main object
	 */
	private static void createTagmap(DBObject o, Connection con, View oMain) {
		String strTags = o.aStrQuery1[8];
		String[] aTags = strTags.split("\\s+");
		try {
			for (String strTag : aTags) {
				int iTagID = -1;
				PreparedStatement ps = con.prepareStatement("SELECT id FROM tags WHERE name = ?");
				ps.setString(1, strTag);
				ResultSet rs = ps.executeQuery();
				if (rs.next()) {
					iTagID = rs.getInt("id");
				}

				if (iTagID != -1) {
					ps = con.prepareStatement("INSERT INTO tagmap (post_id, tag_id) VALUES (?, ?)");
					ps.setString(1, o.aStrQuery1[0]);
					ps.setString(2, iTagID + "");
					ps.executeUpdate();
				} else {
					oMain.oLog.log("Database createTagmap could not find tag " + strTag, null, 4, LogType.NORMAL);
				}
			}
		} catch (SQLException e) {
			oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
		}
	}

	/**
	 * Delete all objects from the tagmap that map to the given DBObject
	 * 
	 * @param o     - DBObject
	 * @param con   - Connection (HikariCP Connection object recycling)
	 * @param oMain - View handle for main object
	 */
	private static void deleteTagmap(DBObject o, Connection con, View oMain) {
		try (PreparedStatement ps = con.prepareStatement("DELETE FROM tagmap WHERE post_id = ?");) {
			ps.setString(1, o.aStrQuery1[0]);
			ps.executeUpdate();
		} catch (Exception e) {
			oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
		}
	}

	/**
	 * Insert updates from the e621 database dump into our own database. Handles the
	 * "pools" database table.
	 * 
	 * @param o - DBObject
	 */
	private void syncInsertPools(DBObject o) {
		// id,name,created_at,updated_at,creator_id,description,is_active,category,post_ids
		if (o.aStrQuery1.length == 9) {
			Thread t = new Thread() {
				@Override
				public void run() {
					iWorkers.incrementAndGet();
					SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
					long timestamp = 0;
					try {
						Date date = format.parse(o.aStrQuery1[3].substring(0, o.aStrQuery1[3].lastIndexOf('.')));
						timestamp = date.getTime();
					} catch (ParseException e1) {
						oMain.oLog.log(null, e1, 0, LogType.EXCEPTION);
					}
					try (Connection con = ds.getConnection();) {
						PreparedStatement ps = con.prepareStatement("SELECT * FROM pools WHERE id = ?");
						ps.setString(1, o.aStrQuery1[0]);
						ResultSet rs = ps.executeQuery();
						if (rs.next()) {
							if (!rs.getString("post_ids").equals(o.aStrQuery1[8])) {
								deletePoolmap(o, con, oMain);
								createPoolmap(o, con, oMain);
							}
							if (!rs.getString("updated_at").equals(o.aStrQuery1[3])
									|| !rs.getString("description").equals(o.aStrQuery1[5])) {
								try (PreparedStatement ps2 = con.prepareStatement(
										"UPDATE pools SET updated_at = ?, description = ? WHERE id = ?");) {
									ps2.setString(1, timestamp + "");
									ps2.setString(2, o.aStrQuery1[5]);
									ps2.setString(3, o.aStrQuery1[0]);
									ps2.executeUpdate();
								}
							}
						} else {
							ps = con.prepareStatement(
									"INSERT INTO pools (id, name, updated_at, description, post_ids) VALUES (?, ?, ?, ?, ?)");
							ps.setString(1, o.aStrQuery1[0]);
							ps.setString(2, o.aStrQuery1[1]);
							ps.setString(3, timestamp + "");
							ps.setString(4, o.aStrQuery1[5]);
							ps.setString(5, o.aStrQuery1[8]);
							ps.executeUpdate();
							createPoolmap(o, con, oMain);
						}
						ps.close();
					} catch (Exception e) {
						oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
					}
					iWorkers.decrementAndGet();
					o.bFinished = true;
				}
			};
			t.start();
			aTrackedThreads.add(t);
		} else {
			oMain.oLog.log(strName + " syncInsertPools incorrect num arguments, " + o.aStrQuery1.length, null, 1,
					LogType.NORMAL);
		}
	}

	/**
	 * Create or update entries in the "poolmap" table.
	 * 
	 * @param o     - DBObject
	 * @param con   - Connection (HikariCP Connection object recycling)
	 * @param oMain - View handle for main object
	 */
	private static void createPoolmap(DBObject o, Connection con, View oMain) {
		String strPosts = o.aStrQuery1[8];
		strPosts = strPosts.replace("{", "");
		strPosts = strPosts.replace("}", "");
		if (strPosts.length() >= 1) {
			String[] aPosts = strPosts.split(",");
			for (String strPost : aPosts) {
				int iPostID = Integer.parseInt(strPost);
				if (iPostID != -1) {
					try (PreparedStatement ps = con
							.prepareStatement("INSERT INTO poolmap (pool_id, post_id) VALUES (?, ?)");) {
						ps.setString(1, o.aStrQuery1[0]);
						ps.setString(2, iPostID + "");
						ps.executeUpdate();
					} catch (Exception e) {
						oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
					}
				} else {
					oMain.oLog.log("Database createPoolmap could not find post " + strPost, null, 1, LogType.NORMAL);
				}
			}
		}
	}

	/**
	 * Delete all objects from the poolmap that map to the given DBObject
	 * 
	 * @param o     - DBObject
	 * @param con   - Connection (HikariCP Connection object recycling)
	 * @param oMain - View handle for main thread
	 */
	private static void deletePoolmap(DBObject o, Connection con, View oMain) {
		try (PreparedStatement ps = con.prepareStatement("DELETE FROM poolmap WHERE pool_id = ?");) {
			ps.setString(1, o.aStrQuery1[0]);
			ps.executeUpdate();
		} catch (Exception e) {
			oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
		}
	}

	/**
	 * Tries to load the first entry in the download_queue database table
	 * 
	 * @param o - DBObject
	 */
	private void getDownloadQueue(DBObject o) {
		Thread t = new Thread() {
			@Override
			public void run() {
				iWorkers.incrementAndGet();

				try (Connection con = ds.getConnection();) {
					PreparedStatement ps = con
							.prepareStatement("SELECT * FROM download_queue " + o.strQuery1 + " LIMIT 1");
					ResultSet rs = ps.executeQuery();
					rs = ps.executeQuery();
					if (rs.next()) {
						o.iResult1 = rs.getInt(1);
						o.iResult2 = rs.getInt(2);

						DBObject temp = new DBObject();
						temp.strQuery1 = o.iResult2 + "";
						PostObject p = getPostStatic(temp, con, oMain);
						if (p != null) {
							o.oResultPostObject1 = p;
						} else {
							o.bNoResult = true;
						}
					} else {
						o.bNoResult = true;
					}
					ps.close();
				} catch (Exception e) {
					oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
				}

				iWorkers.decrementAndGet();
				o.bFinished = true;
			}
		};
		t.start();
		aTrackedThreads.add(t);
	}

	/**
	 * Tries to load the selected post and generates a PostObject with that info
	 * 
	 * @param o - DBObject
	 */
	private void getPost(DBObject o) {
		waitForThreads();
		Thread t = new Thread() {
			@Override
			public void run() {
				iWorkers.incrementAndGet();

				try (Connection con = ds.getConnection();) {
					PostObject p = getPostStatic(o, con, oMain);
					if (p != null) {
						o.oResultPostObject1 = p;
					} else {
						o.bNoResult = true;
					}
				} catch (Exception e) {
					oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
				}

				iWorkers.decrementAndGet();
				o.bFinished = true;
			}
		};
		t.start();
		aTrackedThreads.add(t);
	}

	/**
	 * Tries to generate a PostObject from the selected SQL Query. SQL Query must be
	 * SELECT id FROM posts WHERE...
	 * 
	 * @param o - DBObject o
	 */
	private void getPostFromIDQuery(DBObject o) {
		waitForThreads();
		Thread t = new Thread() {
			@Override
			public void run() {
				iWorkers.incrementAndGet();

				try (Connection con = ds.getConnection(); PreparedStatement ps = con.prepareStatement(o.strQuery1);) {
					ResultSet rs = ps.executeQuery();
					if (rs.next()) {
						o.strQuery1 = rs.getInt("id") + "";
						PostObject p = getPostStatic(o, con, oMain);
						if (p != null) {
							o.oResultPostObject1 = p;
						} else {
							o.bNoResult = true;
						}
					}
				} catch (Exception e) {
					o.bNoResult = true;
					oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
				}

				iWorkers.decrementAndGet();
				o.bFinished = true;
			}
		};
		t.start();
		aTrackedThreads.add(t);
	}

	/**
	 * Last step in the Post Loading workflow.
	 * 
	 * @param o     - DBObject
	 * @param con   - Connection (HikariDB connection recycling)
	 * @param oMain - View handle for main object
	 * @return PostObject
	 */
	private static PostObject getPostStatic(DBObject o, Connection con, View oMain) {
		try {
			PreparedStatement ps = con.prepareStatement("SELECT * FROM posts WHERE id = ?");
			ps.setString(1, o.strQuery1);
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {

				PostObject p = new PostObject();
				p.id = rs.getInt("id");
				p.strMD5 = rs.getString("md5");
				p.strSource = rs.getString("source");
				p.iScore = rs.getInt("score");
				p.iWidth = rs.getInt("image_width");
				p.iHeight = rs.getInt("image_height");
				p.strExt = rs.getString("file_ext");
				p.iParentID = rs.getInt("parent_id");
				p.strDescription = rs.getString("description");
				p.bDownloaded = rs.getBoolean("downloaded");

				if (p.bDownloaded) {
					int iRenameExt = rs.getInt("rename_ext");
					if (iRenameExt != 0) {
						PreparedStatement ps2 = con.prepareStatement("SELECT rename_ext FROM rename_ext WHERE id = ?");
						ps2.setString(1, iRenameExt + "");
						ResultSet rs2 = ps2.executeQuery();
						if (rs2.next()) {
							p.strExtConv = rs2.getString("rename_ext");
						} else {
							oMain.oLog.log(strName + " getPostStatic failed getting rename_ext for ID " + iRenameExt,
									null, 1, LogType.NORMAL);
							p.strExtConv = "FALSE";
						}
						ps2.close();
					} else {
						p.strExtConv = "FALSE";
					}
				} else {
					p.strExtConv = "FALSE";
				}

				String strTags = rs.getString("tag_string");
				String[] aTags = strTags.split("\\s+");
				p.aTags = parseTags(o, con, aTags, oMain);

				p.bThumb = rs.getBoolean("thumbnail");

				ps.close();
				return p;
			}
		} catch (Exception e) {
			oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
		}
		return null;
	}

	/**
	 * Parses all aTags and generates an array of TagObjects[]
	 * 
	 * @param o     - DBObject
	 * @param con   - Connection (HikariCP connection recycling)
	 * @param aTags - String[] with tags to parse
	 * @param oMain - View handle for main object
	 * @return TagObject[]
	 */
	public static TagObject[] parseTags(DBObject o, Connection con, String[] aTags, View oMain) {
		ArrayList<TagObject> aTagObjects = new ArrayList<TagObject>();
		for (String strTag : aTags) {
			try {
				PreparedStatement ps = con.prepareStatement("SELECT * FROM tags WHERE name = ?");
				ps.setString(1, strTag);
				ResultSet rs = ps.executeQuery();
				if (rs.next()) {
					TagObject t = new TagObject();
					t.iID = rs.getInt("id");
					t.strTag = strTag;
					t.iCategory = rs.getInt("category");
					t.iCount = rs.getInt("post_count");

					aTagObjects.add(t);
				}
			} catch (SQLException e) {
				oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
			}
		}
		TagObject[] aResult = new TagObject[aTagObjects.size()];
		for (int i = 0; i < aTagObjects.size(); i++) {
			aResult[i] = aTagObjects.get(i);
		}
		return aResult;
	}

	/**
	 * Deletes a post from the download_queue table and updates the status of the
	 * post in the posts table
	 * 
	 * @param o - DBObject
	 */
	private void ackDownload(DBObject o) {
		Thread t = new Thread() {
			@Override
			public void run() {
				iWorkers.incrementAndGet();

				try (Connection con = ds.getConnection();) {
					PreparedStatement ps = con.prepareStatement("DELETE FROM download_queue WHERE id = ?");
					ps.setString(1, o.strQuery1);
					ps.executeUpdate();

					ps = con.prepareStatement("UPDATE posts SET downloaded = TRUE WHERE id = ?");
					ps.setString(1, o.strQuery2);
					ps.executeUpdate();

					ps = con.prepareStatement("INSERT INTO convert_queue (post_id) VALUES (?)");
					ps.setString(1, o.strQuery2);
					ps.executeUpdate();

					ps.close();
				} catch (SQLException e) {
					oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
				}

				iWorkers.decrementAndGet();
				o.bFinished = true;
			}
		};
		t.start();
		aTrackedThreads.add(t);
	}

	/**
	 * Tries to retrieve the first entry of the convert_queue table
	 * 
	 * @param o - DBObject
	 */
	private void getConvertPost(DBObject o) {
		Thread t = new Thread() {
			@Override
			public void run() {
				iWorkers.incrementAndGet();

				try (Connection con = ds.getConnection();) {
					PreparedStatement ps = con.prepareStatement(
							"SELECT post_id FROM convert_queue WHERE NOT " + o.strQuery1 + " LIMIT 1 OFFSET ?");
					ps.setInt(1, o.iQuery1 * 2);
					ResultSet rs = ps.executeQuery();
					if (rs.next()) {
						o.strQuery1 = rs.getInt("post_id") + "";
						o.oResultPostObject1 = getPostStatic(o, con, oMain);
						if (o.oResultPostObject1 == null) {
							o.bNoResult = true;
						} else {
							o.bNoResult = false;
						}
					} else {
						o.bNoResult = true;
					}
					ps.close();
				} catch (SQLException e) {
					oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
				}

				iWorkers.decrementAndGet();
				o.bFinished = true;
			}
		};
		t.start();
		aTrackedThreads.add(t);
	}

	/**
	 * Deletes the post from the convert_queue and sets the status in the posts
	 * table
	 * 
	 * @param o - DBObject
	 */
	private void ackConvert(DBObject o) {
		Thread t = new Thread() {
			@Override
			public void run() {
				iWorkers.incrementAndGet();

				try (Connection con = ds.getConnection();) {
					PreparedStatement ps = con.prepareStatement("DELETE FROM convert_queue WHERE post_id = ?");
					ps.setString(1, o.oPostObjectQuery1.id + "");
					ps.executeUpdate();

					int iRenameExt = -1;
					ps = con.prepareStatement("SELECT id FROM rename_ext WHERE rename_ext = ?");
					ps.setString(1, o.oPostObjectQuery1.strExtConv);
					ResultSet rs = ps.executeQuery();
					if (rs.next()) {
						iRenameExt = rs.getInt("id");
					}

					if (iRenameExt == -1) {
						ps = con.prepareStatement("INSERT INTO rename_ext (rename_ext) VALUES (?)");
						ps.setString(1, o.oPostObjectQuery1.strExtConv);
						ps.executeUpdate();
						ps = con.prepareStatement("SELECT id FROM rename_ext WHERE rename_ext = ?");
						ps.setString(1, o.oPostObjectQuery1.strExtConv);
						rs = ps.executeQuery();
						if (rs.next()) {
							iRenameExt = rs.getInt("id");
						}
					}

					boolean bDeadlocked = true;
					int iRetries = 0;
					while (bDeadlocked && iRetries < 5) {
						bDeadlocked = false;
						iRetries++;
						try {
							ps = con.prepareStatement("UPDATE posts SET thumbnail = ?, rename_ext = ? WHERE id = ?");
							ps.setBoolean(1, o.oPostObjectQuery1.bThumb);
							ps.setString(2, iRenameExt + "");
							ps.setString(3, o.oPostObjectQuery1.id + "");
							ps.executeUpdate();
						} catch (Exception e) {
							bDeadlocked = true;
							if (iRetries == 5) {
								oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
							}
						}
					}

					ps.close();
				} catch (SQLException e) {
					oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
				}

				iWorkers.decrementAndGet();
				o.bFinished = true;
			}
		};
		t.start();
		aTrackedThreads.add(t);

	}

	/**
	 * Retrieves a list of newest posts in the DB, sorted by id descending (aka
	 * newest)
	 * 
	 * @param o - DBObject
	 */
	private void getNewestPosts(DBObject o) {
		Thread t = new Thread() {
			@Override
			public void run() {
				iWorkers.incrementAndGet();

				try (Connection con = ds.getConnection();
						PreparedStatement ps = con.prepareStatement(
								"SELECT id FROM posts WHERE NOT rename_ext = 0 ORDER BY id DESC LIMIT " + o.strQuery2
										+ " OFFSET " + o.strQuery1);) {
					ResultSet rs = ps.executeQuery();

					ArrayList<Integer> aResult = new ArrayList<Integer>();
					while (rs.next()) {
						aResult.add(rs.getInt("id"));
					}
					if (aResult.size() > 0) {
						o.oResult1 = aResult;
					} else {
						o.bNoResult = true;
					}
				} catch (SQLException e) {
					o.bNoResult = true;
					oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
				}

				iWorkers.decrementAndGet();
				o.bFinished = true;
			}
		};
		t.start();
		aTrackedThreads.add(t);
	}

	/**
	 * Sets status of the post to "redownload", adds it to the end of the
	 * download_queue and sets status in posts
	 * 
	 * @param o - DBObject
	 */
	private void redownload(DBObject o) {
		Thread t = new Thread() {
			@Override
			public void run() {
				iWorkers.incrementAndGet();

				try (Connection con = ds.getConnection();) {
					PreparedStatement ps = con.prepareStatement("SELECT id FROM download_queue WHERE post_id = ?");
					ps.setString(1, o.strQuery1);
					ResultSet rs = ps.executeQuery();
					if (!rs.next()) {
						ps = con.prepareStatement("INSERT INTO download_queue (post_id) VALUES (?)");
						ps.setString(1, o.strQuery1);
						ps.executeUpdate();
					}
					ps = con.prepareStatement("UPDATE posts SET downloaded = FALSE, rename_ext = 0 WHERE ID = ?");
					ps.setString(1, o.strQuery1);
					ps.executeUpdate();

					ps = con.prepareStatement("DELETE FROM convert_queue WHERE post_id = ?");
					ps.setString(1, o.strQuery1);
					ps.executeUpdate();

					ps.close();
				} catch (SQLException e) {
					oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
				}

				iWorkers.decrementAndGet();
				o.bFinished = true;
			}
		};
		t.start();
		aTrackedThreads.add(t);
	}

	/**
	 * Searches the DB for all IDs that correspond to the given tag. Repects the tag
	 * aliases
	 * 
	 * @param o - DBObject
	 */
	private void searchTags(DBObject o) {
		Thread t = new Thread() {
			@Override
			public void run() {
				iWorkers.incrementAndGet();

				try (Connection con = ds.getConnection();) {
					PreparedStatement ps = con.prepareStatement(
							"SELECT consequent_name FROM tag_aliases WHERE antecedent_name = ? LIMIT 1");
					ps.setString(1, o.strQuery1);
					ResultSet rs = ps.executeQuery();
					if (rs.next()) {
						o.strQuery1 = rs.getString("consequent_name");
					}

					ps = con.prepareStatement("SELECT id FROM tags WHERE name = ? LIMIT 1");
					ps.setString(1, o.strQuery1);
					rs = ps.executeQuery();
					if (rs.next()) {
						int iTag_ID = rs.getInt("id");
						ps = con.prepareStatement("SELECT * FROM tagmap WHERE tag_id = ?");
						ps.setString(1, iTag_ID + "");
						rs = ps.executeQuery();

						ArrayList<Integer> aResults = new ArrayList<Integer>();
						while (rs.next()) {
							aResults.add(rs.getInt("post_id"));
						}
						o.oResult1 = aResults;
						if (aResults.size() == 0) {
							o.bNoResult = true;
						}
					}

					ps.close();
				} catch (SQLException e) {
					oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
				}

				iWorkers.decrementAndGet();
				o.bFinished = true;
			}
		};
		t.start();
		aTrackedThreads.add(t);
	}

	/**
	 * Tries to populate the DBObject with all pools that are present in the DB
	 * 
	 * @param o - DBObject
	 */
	private void getPools(DBObject o) {
		Thread t = new Thread() {
			@Override
			public void run() {
				iWorkers.incrementAndGet();

				try (Connection con = ds.getConnection();) {
					PreparedStatement ps = con.prepareStatement("SELECT * FROM pools ORDER BY updated_at DESC");
					ResultSet rs = ps.executeQuery();

					ArrayList<PoolObject> p = new ArrayList<PoolObject>();
					while (rs.next()) {
						PoolObject temp = new PoolObject();
						temp.iID = rs.getInt("id");
						temp.strName = rs.getString("name");
						temp.lTimestamp = rs.getLong("updated_at");
						temp.strDescription = rs.getString("description");
						temp.aPostIDs = new ArrayList<Integer>();

						String strPosts = rs.getString("post_ids");
						strPosts = strPosts.replace("{", "");
						strPosts = strPosts.replace("}", "");
						String[] aPosts = strPosts.split(",");
						for (String str : aPosts) {
							if (str.length() != 0) {
								Integer i = Integer.parseInt(str);
								temp.aPostIDs.add(i);
							}
						}

						p.add(temp);
					}
					o.oResult1 = p;

					ps.close();
				} catch (SQLException e) {
					oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
				}

				iWorkers.decrementAndGet();
				o.bFinished = true;
			}
		};
		t.start();
		aTrackedThreads.add(t);
	}

	/**
	 * Wait for the execution of the currently tracked threads to exit, while thread
	 * count is above the thread limit
	 */
	private void waitForThreads() {
		while (iWorkers.get() >= oMain.oConf.iNumDBThreads) {
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				oMain.oLog.log(null, e, 0, LogType.EXCEPTION);
			}
		}
	}
}
