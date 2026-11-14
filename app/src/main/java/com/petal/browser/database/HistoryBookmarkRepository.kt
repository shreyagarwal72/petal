package com.petal.browser.database

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.preference.PreferenceManager
import java.util.Collections
import java.util.LinkedList

/**
 * Modern repository managing bookmarks, browsing history, and protected/standard domain lists.
 * Modernized from legacy Petal Browser database access object.
 */
open class HistoryBookmarkRepository(context: Context?) {

    companion object {
        const val HISTORY_ITEM = 0
        const val BOOKMARK_ITEM = 2
    }

    private val context: Context? = context?.applicationContext
    private val helper: PetalDatabaseOpenHelper = PetalDatabaseOpenHelper(context)
    private var database: SQLiteDatabase? = null

    fun open(rw: Boolean) {
        database = if (rw) helper.writableDatabase else helper.readableDatabase
    }

    fun close() {
        helper.close()
    }

    fun addBookmark(record: HistoryBookmarkItem?) {
        if (record == null ||
            record.title.trim().isEmpty() ||
            record.url.trim().isEmpty() ||
            record.time < 0L
        ) {
            return
        }
        val ctx = context ?: com.petal.browser.PetalApplication.instance
        if (ctx != null) {
            PetalPlacesProvider.addBookmark(ctx, record)
            try {
                com.petal.browser.account.progress.PetalProgressStore.bump(
                    ctx,
                    com.petal.browser.account.progress.PetalCounter.BOOKMARKS,
                    1L,
                    3L
                )
            } catch (_: Throwable) {}
        }
    }

    fun updateBookmark(oldUrl: String?, newTitle: String?, newUrl: String?): Boolean {
        if (oldUrl.isNullOrBlank() || newUrl.isNullOrBlank()) {
            return false
        }
        val ctx = context ?: com.petal.browser.PetalApplication.instance ?: return false
        return PetalPlacesProvider.updateBookmark(ctx, oldUrl, newTitle, newUrl)
    }

    fun setBookmarkReadingList(url: String?, isReadingList: Boolean): Boolean {
        if (url.isNullOrBlank()) {
            return false
        }
        val db = database ?: helper.writableDatabase
        val values = ContentValues().apply {
            put(HistoryBookmarkContract.COLUMN_IS_READING_LIST, if (isReadingList) 1 else 0)
        }
        val updated = db.update(
            HistoryBookmarkContract.TABLE_BOOKMARK,
            values,
            HistoryBookmarkContract.COLUMN_URL + "=?",
            arrayOf(url.trim())
        )
        return updated > 0
    }

    fun deleteBookmark(url: String?) {
        val ctx = context ?: com.petal.browser.PetalApplication.instance
        if (ctx != null && !url.isNullOrBlank()) {
            PetalPlacesProvider.deleteBookmark(ctx, url)
        }
    }

    fun listBookmark(context: Context, filter: Boolean, filterBy: Long): List<Record> {
        val list = PetalPlacesProvider.getAllBookmarks(context).toMutableList()
        val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val sortBy = sp.getString("sort_bookmark", "title") ?: "title"

        if (sortBy == "time") {
            list.sortWith(compareBy({ it.title }, { it.iconColor }))
        } else {
            list.sortBy { it.title?.lowercase() ?: "" }
        }
        if (sp.getBoolean("sort_bookmarkDomain", false)) {
            list.sortBy { it.domain }
        }
        Collections.reverse(list)
        return list
    }

    fun addHistory(record: HistoryBookmarkItem?) {
        if (record == null ||
            record.title.trim().isEmpty() ||
            record.url.trim().isEmpty() ||
            record.url.trim().equals("about:blank", ignoreCase = true) ||
            record.url.trim().startsWith("about:", ignoreCase = true) ||
            record.time < 0L
        ) {
            return
        }
        val ctx = context ?: com.petal.browser.PetalApplication.instance
        if (ctx != null) {
            PetalPlacesProvider.recordVisit(ctx, record.url, record.title)
        }
    }

    fun listHistory(context: Context): List<Record> {
        val list = PetalPlacesProvider.getHistory(context).toMutableList()
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        if (sp.getBoolean("sort_historyDomain", false)) {
            list.sortBy { it.domain }
        }
        return list
    }

    fun addDomain(domain: String?, table: String) {
        if (domain.isNullOrBlank()) return
        val db = database ?: helper.writableDatabase
        val values = ContentValues().apply {
            put(HistoryBookmarkContract.COLUMN_DOMAIN, domain.trim())
        }
        db.insert(table, null, values)
    }

    fun checkDomain(domain: String?, table: String): Boolean {
        if (domain.isNullOrBlank()) return false
        val db = database ?: helper.readableDatabase
        val cursor = db.query(
            table,
            arrayOf(HistoryBookmarkContract.COLUMN_DOMAIN),
            HistoryBookmarkContract.COLUMN_DOMAIN + "=?",
            arrayOf(domain.trim()),
            null,
            null,
            null
        )
        return cursor.use { it.moveToFirst() }
    }

    fun deleteDomain(domain: String?, table: String) {
        if (domain.isNullOrBlank()) return
        val db = database ?: helper.writableDatabase
        db.execSQL("DELETE FROM $table WHERE ${HistoryBookmarkContract.COLUMN_DOMAIN} = \"${domain.trim()}\"")
    }

    fun listDomains(table: String): List<String> {
        val list = ArrayList<String>()
        val db = database ?: helper.readableDatabase
        val cursor = db.query(
            table,
            arrayOf(HistoryBookmarkContract.COLUMN_DOMAIN),
            null,
            null,
            null,
            null,
            HistoryBookmarkContract.COLUMN_DOMAIN
        )
        cursor.use { c ->
            if (c.moveToFirst()) {
                while (!c.isAfterLast) {
                    list.add(c.getString(0))
                    c.moveToNext()
                }
            }
        }
        return list
    }

    fun checkBookmark(url: String?): Boolean {
        val ctx = context ?: com.petal.browser.PetalApplication.instance ?: return false
        return PetalPlacesProvider.checkBookmark(ctx, url)
    }

    fun addStartSite(record: HistoryBookmarkItem?) {
        if (record == null || record.title.trim().isEmpty() || record.url.trim().isEmpty()) return
        val db = database ?: helper.writableDatabase
        val values = ContentValues().apply {
            put(HistoryBookmarkContract.COLUMN_TITLE, record.title.trim())
            put(HistoryBookmarkContract.COLUMN_URL, record.url.trim())
            put(HistoryBookmarkContract.COLUMN_FILENAME, record.filename ?: "")
            put(HistoryBookmarkContract.COLUMN_ORDINAL, record.ordinal)
        }
        db.insert(HistoryBookmarkContract.TABLE_START, null, values)
    }

    fun listStartSites(): List<Record> {
        val list = ArrayList<Record>()
        val db = database ?: helper.readableDatabase
        val cursor = db.query(
            HistoryBookmarkContract.TABLE_START,
            arrayOf(
                HistoryBookmarkContract.COLUMN_TITLE,
                HistoryBookmarkContract.COLUMN_URL,
                HistoryBookmarkContract.COLUMN_FILENAME,
                HistoryBookmarkContract.COLUMN_ORDINAL
            ),
            null,
            null,
            null,
            null,
            HistoryBookmarkContract.COLUMN_ORDINAL + " ASC"
        )
        cursor?.use { c ->
            if (c.moveToFirst()) {
                do {
                    val record = Record().apply {
                        title = c.getString(0) ?: ""
                        url = c.getString(1) ?: ""
                        filename = c.getString(2)
                        ordinal = c.getInt(3)
                    }
                    list.add(record)
                } while (c.moveToNext())
            }
        }
        return list
    }

    fun checkStartSite(url: String?): Boolean = checkUrl(url, HistoryBookmarkContract.TABLE_START)

    fun checkUrl(url: String?, table: String): Boolean {
        if (url.isNullOrBlank()) return false
        val db = database ?: helper.readableDatabase
        val cursor = db.query(
            table,
            arrayOf(HistoryBookmarkContract.COLUMN_URL),
            HistoryBookmarkContract.COLUMN_URL + "=?",
            arrayOf(url.trim()),
            null,
            null,
            null
        )
        return cursor.use { it.moveToFirst() }
    }

    fun deleteURL(domain: String?, table: String) {
        if (domain.isNullOrBlank()) return
        val ctx = context ?: com.petal.browser.PetalApplication.instance
        if (table == HistoryBookmarkContract.TABLE_BOOKMARK && ctx != null) {
            PetalPlacesProvider.deleteBookmark(ctx, domain)
            return
        }
        if (table == HistoryBookmarkContract.TABLE_HISTORY && ctx != null) {
            PetalPlacesProvider.deleteHistory(ctx, domain)
            return
        }
        val db = database ?: helper.writableDatabase
        db.execSQL("DELETE FROM $table WHERE ${HistoryBookmarkContract.COLUMN_URL} = \"${domain.trim()}\"")
    }

    fun deleteHistoryByTitle(title: String?) {
        if (title.isNullOrBlank()) return
        val ctx = context ?: com.petal.browser.PetalApplication.instance
        if (ctx != null) {
            PetalPlacesProvider.deleteHistoryByTitle(ctx, title)
            return
        }
        val db = database ?: helper.writableDatabase
        db.delete(
            HistoryBookmarkContract.TABLE_HISTORY,
            "${HistoryBookmarkContract.COLUMN_TITLE} = ? OR ${HistoryBookmarkContract.COLUMN_URL} = ?",
            arrayOf(title.trim(), title.trim())
        )
    }

    fun clearTable(table: String) {
        val ctx = context ?: com.petal.browser.PetalApplication.instance
        if (table == HistoryBookmarkContract.TABLE_BOOKMARK && ctx != null) {
            PetalPlacesProvider.clearBookmarks(ctx)
            return
        }
        if (table == HistoryBookmarkContract.TABLE_HISTORY && ctx != null) {
            PetalPlacesProvider.clearHistory(ctx)
            return
        }
        val db = database ?: helper.writableDatabase
        db.execSQL("DELETE FROM $table")
    }

    private fun getRecord(cursor: Cursor, type: Int): Record {
        val record = Record().apply {
            title = cursor.getString(0) ?: ""
            url = cursor.getString(1) ?: ""
            time = cursor.getLong(2)
        }
        if (type == BOOKMARK_ITEM && cursor.columnCount > 3) {
            record.isReadingList = cursor.getInt(3) != 0
        }

        if (type == BOOKMARK_ITEM) {
            record.iconColor = record.time
            record.time = 0 // time is no longer needed after extracting data
        }
        return record
    }

    fun listEntries(activity: Activity): List<Record> {
        val list = ArrayList<Record>()
        val repo = HistoryBookmarkRepository(activity)
        repo.open(false)
        list.addAll(repo.listBookmark(activity, false, 0)) // move bookmarks to top of list
        list.addAll(repo.listHistory(activity.applicationContext))
        repo.close()
        return list
    }

    fun saveSessionStateJson(json: String?) {
        if (json == null) return
        val db = database ?: helper.writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM " + HistoryBookmarkContract.TABLE_SESSION)
            val values = ContentValues().apply {
                put(HistoryBookmarkContract.COLUMN_ORDINAL, 1)
                put(HistoryBookmarkContract.COLUMN_DATA, json)
            }
            db.insert(HistoryBookmarkContract.TABLE_SESSION, null, values)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    val sessionStateJson: String?
        get() {
            val db = database ?: helper.readableDatabase
        var cursor: Cursor? = null
        try {
            cursor = db.query(
                HistoryBookmarkContract.TABLE_SESSION,
                arrayOf(HistoryBookmarkContract.COLUMN_DATA),
                HistoryBookmarkContract.COLUMN_ORDINAL + "=?",
                arrayOf("1"),
                null,
                null,
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        } catch (e: Exception) {
            Log.w("HistoryBookmarkRepo", "getSessionStateJson error", e)
        } finally {
            cursor?.close()
        }
        return null
    }

    fun clearSessionStateJson() {
        try {
            val db = database ?: helper.writableDatabase
            db.execSQL("DELETE FROM " + HistoryBookmarkContract.TABLE_SESSION)
        } catch (e: Exception) {
            Log.w("HistoryBookmarkRepo", "clearSessionStateJson error", e)
        }
    }

    fun deleteSessionHistory(sessionStartTime: Long, sessionUrls: Set<String>?) {
        val db = database ?: helper.writableDatabase
        if (!db.isOpen) return
        try {
            db.beginTransaction()
            db.delete(
                HistoryBookmarkContract.TABLE_HISTORY,
                "${HistoryBookmarkContract.COLUMN_TIME} >= ?",
                arrayOf(sessionStartTime.toString())
            )
            if (!sessionUrls.isNullOrEmpty()) {
                for (url in sessionUrls) {
                    if (url.isNotBlank()) {
                        db.delete(
                            HistoryBookmarkContract.TABLE_HISTORY,
                            "${HistoryBookmarkContract.COLUMN_URL} = ?",
                            arrayOf(url.trim())
                        )
                    }
                }
            }
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e("HistoryBookmarkRepo", "Error deleting session history", e)
        } finally {
            try {
                db.endTransaction()
            } catch (_: Exception) {}
        }
    }

    fun optimizeDatabase() {
        val db = database ?: helper.writableDatabase
        if (!db.isOpen) return
        try {
            db.execSQL("PRAGMA cache_size = -4000;")
            val c = db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE);", null)
            c?.use { it.moveToFirst() }
            db.execSQL("VACUUM;")
        } catch (e: Exception) {
            Log.w("HistoryBookmarkRepo", "Database optimization skipped or failed: " + e.message)
        }
    }
}
