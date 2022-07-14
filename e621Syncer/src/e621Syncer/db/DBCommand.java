package e621Syncer.db;

public enum DBCommand {

	/**
	 * Used for DBCommand.type
	 */
	SELECT,

	/**
	 * Used for DBCommand.type
	 */
	UPDATE,

	/**
	 * <pre>
	 * Access the system table.
	 * SELECT:
	 * strQuery1 = k
	 * strResult1 = v
	 * bNoResult = true, when no result for strQuery1
	 * 
	 * UPDATE:
	 * strQuery1 = k
	 * strQuery2 = v
	 * </pre>
	 */
	ACCESS_SYSTEM,

	/**
	 * <pre>
	 * Used by DBSyncThread to insert if not exist
	 * strQuery1 = Name of the table
	 * aStrQuery1 = Array with the CSV data.
	 * </pre>
	 */
	SYNC_INSERT,

	/**
	 * <pre>
	 * Tries to retrieve one element from the download queue
	 * strQuery1 = STRING PostIDs to exclude. Must have "WHERE NOT post_id = ID[ AND NOT post_id....]
	 * 
	 * iResult1 = ID of download_queue
	 * iResult2 = ID of post to download
	 * oResult1 = PostObject referencing this Element
	 * bNoResult = true, when no download queued
	 * </pre>
	 */
	GET_DOWNLOAD_QUEUE,

	/**
	 * <pre>
	 * Tries to retrieve a post object corresponding to ID
	 * strQuery1 = Post ID
	 * 
	 * oResultPostObject1 = PostObject
	 * bNoResult = true, when no post found
	 * </pre>
	 */
	GET_POST,

	/**
	 * <pre>
	 * Tries to retrieve a post by given query strQuery1 = SQL Query to contruct a
	 * PostObject from Needs to contain "SELECT id FROM posts"
	 * 
	 * oResultPostObject1 = PostObject bNoResult = true, when no post found
	 */
	GET_POST_FROM_ID_QUERY,

	/**
	 * <pre>
	 * removes an element from the download queue and sets downloaded = 1 in posts
	 * strQuery1 = ID of the element in download_queue
	 * strQuery2 = ID of post
	 * </pre>
	 */
	ACK_DOWNLOAD,

	/**
	 * <pre>
	 * Tries to retrieve a post that needs converting, in regards to
	 * config.aFileMask 
	 * strQuery1 = SQL String, contains "post_id = ID[ AND NOT post_id = ID ...]"
	 * iQuery1 = Offset of the SELECT statement
	 * 
	 * oResultPostObject1 = post that needs converting 
	 * bNoResult = true, when no post found
	 * </pre>
	 */
	GET_CONVERT_POST,

	/**
	 * <pre>
	 * ACK a converted post
	 * oPostObjectQuery1 = post object to update
	 * </pre>
	 */
	ACK_CONVERT,

	/**
	 * <pre>
	 * Get a list of up to strQuery2 IDs of the newest posts in the DB
	 * strQuery1 = start at this offset
	 * strQuery2 = LIMIT size
	 * 
	 * oResult1 = ArrayList<Integer> with all the posts.id of the posts
	 * bNoResult = true, when no posts found
	 * </pre>
	 */
	GET_NEWEST_POSTS,

	/**
	 * <pre>
	 * Get a list of up to strQuery2 IDs of the posts regarding to the seach query
	 * strQuery1 = start at this offset
	 * strQuery2 = LIMIT size
	 * strQuery3 = ORDER BY column name
	 * strQuery4 = search keywords
	 * 
	 * oResult1 = ArrayList<Integer> with all the posts.id of the posts
	 * bNoResult = true, when no posts found
	 * </pre>
	 */
	SEARCH_POSTS,

	/**
	 * <pre>
	 * Adds an item to the download list
	 * strQuery1 = ID of the post to add to downloads
	 * </pre>
	 */
	REDOWNLOAD,

	/**
	 * <pre>
	 * Tries to get all Post IDs corresponding to a tag string
	 * DOES NOT TRY rename_ext != 0 !!!!
	 * strQuery1 = Tag String
	 * 
	 * oResult1 = ArrayList<Integer> with all the posts.id of the query
	 * bNoResult = true, when no posts found
	 * </pre>
	 */
	GET_POST_IDS_FROM_TAG_STRING,

	/**
	 * <pre>
	 * Tries to load all pools in the database
	 * 
	 * oResult1 = ArrayList<PoolObject> with all pools present
	 * bNoResult = Database broken maybe? :D
	 * </pre>
	 */
	GET_POOLS
}
