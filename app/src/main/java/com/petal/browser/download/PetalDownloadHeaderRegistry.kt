package com.petal.browser.download

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.petal.browser.PetalApplication
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * PetalDownloadHeaderRegistry
 * ─────────────────────────────────────────────────────────────────────────────
 * Header cache for download requests (Referer, Accept-Language, signed-URL headers, ...).
 *
 * Lifetime rules:
 * 1. An entry bound to a [downloadId] lives exactly as long as that download: it is removed on
 *    COMPLETED / CANCELLED / removal ([release]) and swept at startup by [retainDownloads].
 *    It is NOT subject to the TTL, so a download paused for days still resumes with its Referer.
 * 2. URL-only entries (the short window between registering and the id being known) keep a
 *    12 h sliding TTL.
 * 3. The fetch client only sees the request URL, so lookups by URL resolve to the entry that was
 *    registered last for that URL. Two concurrent downloads of one URL with different headers
 *    are therefore prevented upstream (the bridge refuses a duplicate active URL).
 * 4. Private downloads are memory-only and never touch disk.
 * 5. Secrets ([SECRET_HEADERS]) stay in memory; they are never written to SQLite. After a
 *    process restart they are gone, and a request that needs them fails with a clear 401/403
 *    instead of leaking credentials at rest.
 * 6. Disk writes run on a single background thread; the caller (possibly main) never blocks.
 */
object PetalDownloadHeaderRegistry {

    private const val TAG = "PetalHeaderRegistry"
    private const val TTL_MS = 12 * 60 * 60 * 1000L // 12 hours, URL-only entries

    @JvmField
    val SECRET_HEADERS: Set<String> = setOf("cookie", "authorization", "proxy-authorization")

    private class Entry(val headers: Map<String, String>, @Volatile var at: Long, val bound: Boolean)

    private val standardMemory = ConcurrentHashMap<String, Entry>()
    private val privateMemory = ConcurrentHashMap<String, Entry>()

    private val diskExecutor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "petal-header-db").apply { isDaemon = true } }
    }

    private var dbHelper: HeaderDbHelper? = null

    private fun getDbHelper(): HeaderDbHelper? {
        dbHelper?.let { return it }
        val ctx = PetalApplication.instance?.applicationContext ?: return null
        synchronized(this) {
            if (dbHelper == null) dbHelper = HeaderDbHelper(ctx)
        }
        return dbHelper
    }

    /** Headers that are safe to write to disk. */
    @JvmStatic
    fun persistable(headers: Map<String, String>): Map<String, String> =
        headers.filterKeys { it.lowercase() !in SECRET_HEADERS }

    @JvmStatic
    @JvmOverloads
    fun put(
        url: String,
        headers: Map<String, String>,
        downloadId: String? = null,
        isPrivate: Boolean = false
    ) {
        if (url.isBlank() && downloadId.isNullOrBlank()) return
        if (headers.isEmpty()) return

        val now = System.currentTimeMillis()
        val bound = !downloadId.isNullOrBlank()
        val entry = Entry(headers, now, bound)
        val memory = if (isPrivate) privateMemory else standardMemory
        if (bound) memory[downloadId!!] = entry
        // The URL key is a lookup alias for the fetch client. Last writer wins.
        if (url.isNotBlank()) memory[url] = entry
        pruneCache(memory, now)

        if (isPrivate) return // strictly in-memory
        val safe = persistable(headers)
        if (safe.isEmpty()) return
        diskExecutor.execute { persistToDisk(url, downloadId, safe, now) }
    }

    @JvmStatic
    @JvmOverloads
    fun headersFor(
        url: String,
        downloadId: String? = null,
        isPrivate: Boolean = false
    ): Map<String, String> {
        val now = System.currentTimeMillis()
        val memory = if (isPrivate) privateMemory else standardMemory

        var entry = if (!downloadId.isNullOrBlank()) memory[downloadId] else null
        if (entry == null && url.isNotBlank()) entry = memory[url]
        if (entry != null) {
            entry.at = now
            return entry.headers
        }
        if (isPrivate) return emptyMap()

        // Cache miss (e.g. after process death): the persisted, secret-free copy.
        val disk = loadFromDisk(url, downloadId, now)
        if (disk.isNotEmpty()) {
            val loaded = Entry(disk, now, !downloadId.isNullOrBlank())
            if (url.isNotBlank()) standardMemory[url] = loaded
            if (!downloadId.isNullOrBlank()) standardMemory[downloadId] = loaded
        }
        return disk
    }

    /** Called when a download reaches a terminal state or is removed. */
    @JvmStatic
    fun release(downloadId: String?, url: String? = null) = remove(url = url, downloadId = downloadId)

    @JvmStatic
    fun remove(url: String? = null, downloadId: String? = null) {
        // Remove the url alias only if it still points at this download's entry.
        val boundEntry = if (!downloadId.isNullOrBlank()) {
            standardMemory[downloadId] ?: privateMemory[downloadId]
        } else null
        if (!downloadId.isNullOrBlank()) {
            standardMemory.remove(downloadId)
            privateMemory.remove(downloadId)
        }
        if (!url.isNullOrBlank()) {
            val stdAlias = standardMemory[url]
            if (boundEntry == null || stdAlias === boundEntry) standardMemory.remove(url)
            val privAlias = privateMemory[url]
            if (boundEntry == null || privAlias === boundEntry) privateMemory.remove(url)
        }
        diskExecutor.execute {
            val helper = getDbHelper() ?: return@execute
            try {
                val db = helper.writableDatabase
                val clauses = mutableListOf<String>()
                val args = mutableListOf<String>()
                if (!downloadId.isNullOrBlank()) {
                    clauses.add("download_id = ?"); args.add(downloadId)
                    clauses.add("key_id = ?"); args.add(downloadId)
                }
                if (!url.isNullOrBlank() && downloadId.isNullOrBlank()) {
                    clauses.add("url = ?"); args.add(url)
                }
                if (clauses.isNotEmpty()) {
                    db.delete("download_headers", clauses.joinToString(" OR "), args.toTypedArray())
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to remove headers from disk: ${e.message}")
            }
        }
    }

    /**
     * Startup sweep: drop every download-bound entry whose download no longer exists in the store,
     * and expired URL-only rows.
     */
    @JvmStatic
    fun retainDownloads(liveDownloadIds: Set<String>) {
        diskExecutor.execute {
            val helper = getDbHelper() ?: return@execute
            try {
                val db = helper.writableDatabase
                db.delete("download_headers", "updated_at < ? AND download_id = ''", arrayOf((System.currentTimeMillis() - TTL_MS).toString()))
                db.query("download_headers", arrayOf("download_id"), "download_id != ''", null, null, null, null).use { c ->
                    val stale = mutableListOf<String>()
                    while (c.moveToNext()) {
                        val id = c.getString(0)
                        if (id !in liveDownloadIds) stale.add(id)
                    }
                    stale.forEach { db.delete("download_headers", "download_id = ?", arrayOf(it)) }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "retainDownloads failed: ${e.message}")
            }
        }
    }

    @JvmStatic
    fun clearPrivateHeaders() {
        privateMemory.clear()
    }

    private fun pruneCache(cache: ConcurrentHashMap<String, Entry>, now: Long) {
        cache.entries.removeAll { !it.value.bound && now - it.value.at > TTL_MS }
    }

    private fun persistToDisk(
        url: String,
        downloadId: String?,
        headers: Map<String, String>,
        timestamp: Long
    ) {
        val helper = getDbHelper() ?: return
        try {
            val json = JSONObject()
            headers.forEach { (k, v) -> json.put(k, v) }
            val db = helper.writableDatabase
            val cv = ContentValues().apply {
                put("key_id", downloadId ?: url)
                put("download_id", downloadId ?: "")
                put("url", url)
                put("headers_json", json.toString())
                put("updated_at", timestamp)
            }
            db.insertWithOnConflict("download_headers", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            // Only URL-only rows expire by TTL; download-bound rows live until released.
            db.delete("download_headers", "updated_at < ? AND download_id = ''", arrayOf((timestamp - TTL_MS).toString()))
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to persist headers to disk: ${e.message}")
        }
    }

    private fun loadFromDisk(url: String, downloadId: String?, now: Long): Map<String, String> {
        val helper = getDbHelper() ?: return emptyMap()
        try {
            val db = helper.readableDatabase
            val threshold = (now - TTL_MS).toString()
            val selection: String
            val selectionArgs: Array<String>
            if (!downloadId.isNullOrBlank()) {
                selection = "download_id = ? OR key_id = ? OR (download_id = '' AND url = ? AND updated_at >= ?)"
                selectionArgs = arrayOf(downloadId, downloadId, url, threshold)
            } else if (url.isNotBlank()) {
                // Any row for this URL: bound rows (live download) or fresh URL-only rows.
                selection = "url = ? AND (download_id != '' OR updated_at >= ?)"
                selectionArgs = arrayOf(url, threshold)
            } else {
                return emptyMap()
            }
            db.query("download_headers", arrayOf("headers_json"), selection, selectionArgs, null, null, "updated_at DESC", "1").use { cursor ->
                if (cursor.moveToFirst()) {
                    val rawJson = cursor.getString(0)
                    if (!rawJson.isNullOrBlank()) {
                        val json = JSONObject(rawJson)
                        val map = LinkedHashMap<String, String>()
                        val keys = json.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            map[key] = json.optString(key, "")
                        }
                        return map
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to load headers from disk: ${e.message}")
        }
        return emptyMap()
    }

    private class HeaderDbHelper(context: Context) : SQLiteOpenHelper(context, "petal_download_headers.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS download_headers (
                    key_id TEXT PRIMARY KEY,
                    download_id TEXT,
                    url TEXT,
                    headers_json TEXT NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_headers_url ON download_headers(url)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_headers_download_id ON download_headers(download_id)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS download_headers")
            onCreate(db)
        }
    }
}
