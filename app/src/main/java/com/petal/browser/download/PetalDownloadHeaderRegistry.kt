package com.petal.browser.download

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.petal.browser.PetalApplication
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * PetalDownloadHeaderRegistry
 * ─────────────────────────────────────────────────────────────────────────────
 * Authoritative header cache and persistence registry for download requests.
 *
 * Requirements addressed:
 * 1. Keyed by both [downloadId] and [url] to prevent collisions across multiple tabs
 *    or downloads targeting the same URL.
 * 2. Persisted to SQLite for standard downloads so paused/resumed downloads after
 *    process death retain their Referer, authorization, and signed-URL headers.
 * 3. Strict private-mode isolation: private downloads are cached strictly in-memory
 *    and NEVER touch disk storage.
 * 4. 12-hour sliding TTL from last access.
 */
object PetalDownloadHeaderRegistry {

    private const val TAG = "PetalHeaderRegistry"
    private const val TTL_MS = 12 * 60 * 60 * 1000L // 12 hours

    private class Entry(val headers: Map<String, String>, @Volatile var at: Long)

    // In-memory caches for fast retrieval
    private val standardMemory = ConcurrentHashMap<String, Entry>()
    private val privateMemory = ConcurrentHashMap<String, Entry>()

    private var dbHelper: HeaderDbHelper? = null

    private fun getDbHelper(): HeaderDbHelper? {
        if (dbHelper != null) return dbHelper
        val ctx = PetalApplication.instance?.applicationContext ?: return null
        synchronized(this) {
            if (dbHelper == null) {
                dbHelper = HeaderDbHelper(ctx)
            }
        }
        return dbHelper
    }

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
        val entry = Entry(headers, now)

        if (isPrivate) {
            // Strictly in-memory cache for private browsing; NEVER persist to disk
            if (url.isNotBlank()) privateMemory[url] = entry
            if (!downloadId.isNullOrBlank()) privateMemory[downloadId] = entry
            pruneCache(privateMemory, now)
            return
        }

        // Standard downloads: cache in memory and persist to SQLite
        if (url.isNotBlank()) standardMemory[url] = entry
        if (!downloadId.isNullOrBlank()) standardMemory[downloadId] = entry
        pruneCache(standardMemory, now)

        persistToDisk(url, downloadId, headers, now)
    }

    @JvmStatic
    @JvmOverloads
    fun headersFor(
        url: String,
        downloadId: String? = null,
        isPrivate: Boolean = false
    ): Map<String, String> {
        val now = System.currentTimeMillis()

        if (isPrivate) {
            // Look up in private memory only
            val entry = (!downloadId.isNullOrBlank() && privateMemory[downloadId] != null)
                .let { if (it) privateMemory[downloadId] else privateMemory[url] }
            if (entry != null) {
                entry.at = now
                return entry.headers
            }
            return emptyMap()
        }

        // Standard downloads: check memory first
        var entry = if (!downloadId.isNullOrBlank()) standardMemory[downloadId] else null
        if (entry == null && url.isNotBlank()) {
            entry = standardMemory[url]
        }

        if (entry != null) {
            entry.at = now
            return entry.headers
        }

        // Cache miss: check persistent SQLite database
        val diskHeaders = loadFromDisk(url, downloadId, now)
        if (diskHeaders.isNotEmpty()) {
            val loaded = Entry(diskHeaders, now)
            if (url.isNotBlank()) standardMemory[url] = loaded
            if (!downloadId.isNullOrBlank()) standardMemory[downloadId] = loaded
            return diskHeaders
        }

        return emptyMap()
    }

    @JvmStatic
    fun remove(url: String? = null, downloadId: String? = null) {
        if (!url.isNullOrBlank()) {
            standardMemory.remove(url)
            privateMemory.remove(url)
        }
        if (!downloadId.isNullOrBlank()) {
            standardMemory.remove(downloadId)
            privateMemory.remove(downloadId)
        }

        // Remove from persistent DB
        val helper = getDbHelper() ?: return
        try {
            val db = helper.writableDatabase
            val whereArgs = mutableListOf<String>()
            val whereClauses = mutableListOf<String>()
            if (!url.isNullOrBlank()) {
                whereClauses.add("url = ?")
                whereArgs.add(url)
            }
            if (!downloadId.isNullOrBlank()) {
                whereClauses.add("download_id = ?")
                whereArgs.add(downloadId)
            }
            if (whereClauses.isNotEmpty()) {
                db.delete("download_headers", whereClauses.joinToString(" OR "), whereArgs.toTypedArray())
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to remove headers from disk: ${e.message}")
        }
    }

    @JvmStatic
    fun clearPrivateHeaders() {
        privateMemory.clear()
    }

    private fun pruneCache(cache: ConcurrentHashMap<String, Entry>, now: Long) {
        cache.entries.removeAll { now - it.value.at > TTL_MS }
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

            // Prune expired entries from SQLite
            val threshold = timestamp - TTL_MS
            db.delete("download_headers", "updated_at < ?", arrayOf(threshold.toString()))
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to persist headers to disk: ${e.message}")
        }
    }

    private fun loadFromDisk(url: String, downloadId: String?, now: Long): Map<String, String> {
        val helper = getDbHelper() ?: return emptyMap()
        try {
            val db = helper.readableDatabase
            val threshold = now - TTL_MS
            val selection: String
            val selectionArgs: Array<String>

            if (!downloadId.isNullOrBlank() && url.isNotBlank()) {
                selection = "(download_id = ? OR url = ? OR key_id = ?) AND updated_at >= ?"
                selectionArgs = arrayOf(downloadId, url, downloadId, threshold.toString())
            } else if (!downloadId.isNullOrBlank()) {
                selection = "(download_id = ? OR key_id = ?) AND updated_at >= ?"
                selectionArgs = arrayOf(downloadId, downloadId, threshold.toString())
            } else if (url.isNotBlank()) {
                selection = "(url = ? OR key_id = ?) AND updated_at >= ?"
                selectionArgs = arrayOf(url, url, threshold.toString())
            } else {
                return emptyMap()
            }

            db.query(
                "download_headers",
                arrayOf("headers_json"),
                selection,
                selectionArgs,
                null,
                null,
                "updated_at DESC",
                "1"
            ).use { cursor ->
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
