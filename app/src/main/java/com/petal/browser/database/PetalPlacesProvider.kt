package com.petal.browser.database

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mozilla.components.browser.storage.sync.PlacesBookmarksStorage
import mozilla.components.browser.storage.sync.PlacesHistoryStorage
import mozilla.components.concept.storage.BookmarkInfo
import mozilla.components.concept.storage.BookmarkNode
import mozilla.components.concept.storage.BookmarkNodeType
import mozilla.components.concept.storage.PageVisit
import mozilla.components.concept.storage.VisitType

/**
 * PetalPlacesProvider
 * ─────────────────────────────────────────────────────────────────────────
 * Single source of truth for browser History and Bookmarks, backed by official
 * Mozilla Android Components [PlacesHistoryStorage] and [PlacesBookmarksStorage].
 *
 * Implements a one-time idempotent migration from legacy SQLite tables ("Ninja4.db"),
 * keeping the Compose history & bookmarks UI, HTML exporter, and Firefox Sync in lockstep.
 */
object PetalPlacesProvider {
    private const val TAG = "PetalPlacesProvider"
    private const val PREF_MIGRATED = "sp_places_migrated_from_legacy_sqlite_v1"

    // Mozilla Places Canonical Root GUIDs
    private const val BOOKMARK_ROOT_GUID = "root________"
    private const val BOOKMARK_MOBILE_GUID = "mobile______"

    @Volatile
    private var historyStorage: PlacesHistoryStorage? = null

    @Volatile
    private var bookmarksStorage: PlacesBookmarksStorage? = null

    @Synchronized
    fun getHistoryStorage(context: Context): PlacesHistoryStorage {
        historyStorage?.let { return it }
        val appContext = context.applicationContext
        val storage = PlacesHistoryStorage(appContext)
        historyStorage = storage
        migrateIfNeeded(appContext, storage, getBookmarksStorage(context))
        return storage
    }

    @Synchronized
    fun getBookmarksStorage(context: Context): PlacesBookmarksStorage {
        bookmarksStorage?.let { return it }
        val appContext = context.applicationContext
        val storage = PlacesBookmarksStorage(appContext)
        bookmarksStorage = storage
        return storage
    }

    private fun migrateIfNeeded(
        appContext: Context,
        history: PlacesHistoryStorage,
        bookmarks: PlacesBookmarksStorage
    ) {
        val sp = PreferenceManager.getDefaultSharedPreferences(appContext)
        if (sp.getBoolean(PREF_MIGRATED, false)) return

        val helper = PetalDatabaseOpenHelper(appContext)
        val oldDb = try { helper.readableDatabase } catch (_: Throwable) { null }
        if (oldDb != null) {
            try {
                val db = oldDb
                // 1. Migrate Bookmarks from TABLE_BOOKMARK
                try {
                    val cursor = db.query(
                        HistoryBookmarkContract.TABLE_BOOKMARK,
                        arrayOf(
                            HistoryBookmarkContract.COLUMN_TITLE,
                            HistoryBookmarkContract.COLUMN_URL
                        ),
                        null, null, null, null, null
                    )
                    cursor?.use { c ->
                        val titleCol = c.getColumnIndex(HistoryBookmarkContract.COLUMN_TITLE)
                        val urlCol = c.getColumnIndex(HistoryBookmarkContract.COLUMN_URL)
                        while (c.moveToNext()) {
                            val title = if (titleCol >= 0) c.getString(titleCol) else ""
                            val url = if (urlCol >= 0) c.getString(urlCol) else ""
                            if (!url.isNullOrBlank() && url.startsWith("http")) {
                                runBlocking(Dispatchers.IO) {
                                    try {
                                        bookmarks.addItem(
                                            parentGuid = BOOKMARK_MOBILE_GUID,
                                            url = url.trim(),
                                            title = title.ifBlank { url }.trim(),
                                            position = null
                                        )
                                    } catch (_: Throwable) {}
                                }
                            }
                        }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Legacy bookmark table migration error: ${t.message}")
                }

                // 2. Migrate History from TABLE_HISTORY
                try {
                    val cursor = db.query(
                        HistoryBookmarkContract.TABLE_HISTORY,
                        arrayOf(
                            HistoryBookmarkContract.COLUMN_TITLE,
                            HistoryBookmarkContract.COLUMN_URL,
                            HistoryBookmarkContract.COLUMN_TIME
                        ),
                        null, null, null, null, null
                    )
                    cursor?.use { c ->
                        val urlCol = c.getColumnIndex(HistoryBookmarkContract.COLUMN_URL)
                        while (c.moveToNext()) {
                            val url = if (urlCol >= 0) c.getString(urlCol) else ""
                            if (!url.isNullOrBlank() && url.startsWith("http")) {
                                runBlocking(Dispatchers.IO) {
                                    try {
                                        history.recordVisit(
                                            url.trim(),
                                            PageVisit(VisitType.LINK)
                                        )
                                    } catch (_: Throwable) {}
                                }
                            }
                        }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Legacy history table migration error: ${t.message}")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Migration from legacy database failed: ${t.message}", t)
            }
        }
        sp.edit().putBoolean(PREF_MIGRATED, true).apply()
        Log.i(TAG, "Places migration from legacy SQLite completed.")
    }

    private fun normalizeUrl(url: String): String = url.trim()

    private fun findBookmarkNode(root: BookmarkNode?, targetUrl: String): BookmarkNode? {
        if (root == null) return null
        if (root.type == BookmarkNodeType.ITEM && root.url?.let(::normalizeUrl) == targetUrl) {
            return root
        }
        root.children?.forEach { child ->
            val found = findBookmarkNode(child, targetUrl)
            if (found != null) return found
        }
        return null
    }

    fun getAllBookmarks(context: Context): List<Record> {
        val bm = getBookmarksStorage(context)
        return runBlocking(Dispatchers.IO) {
            val root = bm.getTree(BOOKMARK_ROOT_GUID, true).getOrNull() ?: return@runBlocking emptyList<Record>()
            val result = mutableListOf<Record>()
            fun traverse(node: BookmarkNode) {
                val url = node.url
                if (node.type == BookmarkNodeType.ITEM && !url.isNullOrBlank()) {
                    val title = node.title?.ifBlank { url } ?: url
                    val timestamp = System.currentTimeMillis()
                    result.add(Record(title, url, timestamp, timestamp))
                }
                node.children?.forEach { traverse(it) }
            }
            traverse(root)
            result
        }
    }

    fun addBookmark(context: Context, record: HistoryBookmarkItem) {
        val url = record.url.trim()
        val title = record.title.ifBlank { url }.trim()
        if (url.isBlank() || !url.startsWith("http")) return

        val bm = getBookmarksStorage(context)
        runBlocking(Dispatchers.IO) {
            val root = bm.getTree(BOOKMARK_ROOT_GUID, true).getOrNull()
            val existing = findBookmarkNode(root, url)
            if (existing != null) {
                try {
                    bm.updateNode(
                        guid = existing.guid,
                        info = BookmarkInfo(title = title, url = url)
                    )
                } catch (_: Throwable) {}
            } else {
                try {
                    bm.addItem(BOOKMARK_MOBILE_GUID, url, title, null)
                } catch (_: Throwable) {}
            }
        }
    }

    fun updateBookmark(context: Context, oldUrl: String?, newTitle: String?, newUrl: String?): Boolean {
        if (oldUrl.isNullOrBlank() || newUrl.isNullOrBlank()) return false
        val bm = getBookmarksStorage(context)
        return runBlocking(Dispatchers.IO) {
            val root = bm.getTree(BOOKMARK_ROOT_GUID, true).getOrNull()
            val existing = findBookmarkNode(root, oldUrl.trim()) ?: return@runBlocking false
            try {
                bm.updateNode(
                    guid = existing.guid,
                    info = BookmarkInfo(
                        title = if (!newTitle.isNullOrBlank()) newTitle.trim() else newUrl.trim(),
                        url = newUrl.trim()
                    )
                )
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    fun deleteBookmark(context: Context, url: String?) {
        if (url.isNullOrBlank()) return
        val bm = getBookmarksStorage(context)
        runBlocking(Dispatchers.IO) {
            val root = bm.getTree(BOOKMARK_ROOT_GUID, true).getOrNull()
            val existing = findBookmarkNode(root, url.trim()) ?: return@runBlocking
            try {
                bm.deleteNode(existing.guid)
            } catch (_: Throwable) {}
        }
    }

    fun checkBookmark(context: Context, url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val bm = getBookmarksStorage(context)
        return runBlocking(Dispatchers.IO) {
            val root = bm.getTree(BOOKMARK_ROOT_GUID, true).getOrNull()
            findBookmarkNode(root, url.trim()) != null
        }
    }

    fun clearBookmarks(context: Context) {
        val bm = getBookmarksStorage(context)
        runBlocking(Dispatchers.IO) {
            val root = bm.getTree(BOOKMARK_ROOT_GUID, true).getOrNull() ?: return@runBlocking
            fun deleteRecursive(node: BookmarkNode) {
                if (node.type == BookmarkNodeType.ITEM) {
                    try { bm.deleteNode(node.guid) } catch (_: Throwable) {}
                }
                node.children?.forEach { deleteRecursive(it) }
            }
            deleteRecursive(root)
        }
    }

    fun getHistory(context: Context): List<Record> {
        val hs = getHistoryStorage(context)
        return runBlocking(Dispatchers.IO) {
            try {
                val visits = hs.getDetailedVisits(0L, Long.MAX_VALUE)
                visits.map { v ->
                    Record(
                        v.title?.ifBlank { v.url } ?: v.url,
                        v.url,
                        v.visitTime,
                        0L
                    )
                }.sortedByDescending { it.time }
            } catch (_: Throwable) {
                emptyList()
            }
        }
    }

    fun recordVisit(context: Context, url: String, title: String) {
        if (url.isBlank() || url.startsWith("about:") || url.startsWith("moz-extension://")) return
        val hs = getHistoryStorage(context)
        runBlocking(Dispatchers.IO) {
            try {
                hs.recordVisit(url.trim(), PageVisit(VisitType.LINK))
            } catch (_: Throwable) {}
        }
    }

    fun deleteHistory(context: Context, url: String) {
        if (url.isBlank()) return
        val hs = getHistoryStorage(context)
        runBlocking(Dispatchers.IO) {
            try {
                hs.deleteVisitsFor(url.trim())
            } catch (_: Throwable) {}
        }
    }

    fun clearHistory(context: Context) {
        val hs = getHistoryStorage(context)
        runBlocking(Dispatchers.IO) {
            try {
                hs.deleteEverything()
            } catch (_: Throwable) {}
        }
    }

    fun deleteHistoryByTitle(context: Context, title: String) {
        if (title.isBlank()) return
        val hs = getHistoryStorage(context)
        runBlocking(Dispatchers.IO) {
            try {
                val visits = hs.getDetailedVisits(0L, Long.MAX_VALUE)
                val matches = visits.filter { it.title.equals(title, ignoreCase = true) }
                matches.forEach { v -> hs.deleteVisitsFor(v.url) }
            } catch (_: Throwable) {}
        }
    }
}
