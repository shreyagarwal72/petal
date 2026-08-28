package com.petal.browser.database

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.preference.PreferenceManager
import com.petal.browser.unit.RecordUnit
import java.util.ArrayList
import java.util.Collections
import java.util.Comparator
import java.util.LinkedList

class RecordAction(context: Context?) {
    private val helper: RecordHelper = RecordHelper(context)
    private var database: SQLiteDatabase? = null

    fun open(rw: Boolean) {
        database = if (rw) helper.writableDatabase else helper.readableDatabase
    }

    fun close() {
        helper.close()
    }

    fun addBookmark(record: Record?) {
        if (record == null ||
            record.title == null ||
            record.title!!.trim().isEmpty() ||
            record.getURL() == null ||
            record.getURL()!!.trim().isEmpty() ||
            record.time < 0L
        ) {
            return
        }
        val values = ContentValues()
        values.put(RecordUnit.COLUMN_TITLE, record.title!!.trim())
        values.put(RecordUnit.COLUMN_URL, record.getURL()!!.trim())
        values.put(RecordUnit.COLUMN_TIME, record.iconColor)
        database?.insert(RecordUnit.TABLE_BOOKMARK, null, values)
    }

    fun listBookmark(context: Context, filter: Boolean, filterBy: Long): List<Record> {
        val list: MutableList<Record> = LinkedList()
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val sortBy = sp.getString("sort_bookmark", "title") ?: "title"
        val cursor: Cursor? = database?.query(
            RecordUnit.TABLE_BOOKMARK,
            arrayOf(
                RecordUnit.COLUMN_TITLE,
                RecordUnit.COLUMN_URL,
                RecordUnit.COLUMN_TIME
            ),
            null,
            null,
            null,
            null,
            "$sortBy COLLATE NOCASE;"
        )
        if (cursor != null) {
            cursor.moveToFirst()
            while (!cursor.isAfterLast) {
                val record = getRecord(cursor, BOOKMARK_ITEM)
                if (filter) {
                    if (record.iconColor == filterBy) {
                        list.add(record)
                    }
                } else {
                    list.add(record)
                }
                cursor.moveToNext()
            }
            cursor.close()
        }

        if (sortBy == "time") {
            // ignore desktop mode, JavaScript, and remote content when sorting colors
            list.sortWith(Comparator.comparing { obj: Record -> obj.title ?: "" })
            list.sortWith(Comparator.comparingLong { obj: Record -> obj.iconColor })
        }
        if (sp.getBoolean("sort_bookmarkDomain", false)) {
            list.sortWith(Comparator.comparing { obj: Record -> obj.getDomain() })
        }
        Collections.reverse(list)
        return list
    }

    fun addHistory(record: Record?) {
        if (record == null ||
            record.title == null ||
            record.title!!.trim().isEmpty() ||
            record.getURL() == null ||
            record.getURL()!!.trim().isEmpty() ||
            record.getURL()!!.trim().equals("about:blank", ignoreCase = true) ||
            record.getURL()!!.trim().startsWith("about:") ||
            record.time < 0L
        ) {
            return
        }

        val values = ContentValues()
        values.put(RecordUnit.COLUMN_TITLE, record.title!!.trim())
        values.put(RecordUnit.COLUMN_URL, record.getURL()!!.trim())
        values.put(RecordUnit.COLUMN_TIME, record.time)
        database?.insert(RecordUnit.TABLE_HISTORY, null, values)
    }

    fun listHistory(context: Context): List<Record> {
        val list: MutableList<Record> = ArrayList()
        val cursor: Cursor? = database?.query(
            RecordUnit.TABLE_HISTORY,
            arrayOf(
                RecordUnit.COLUMN_TITLE,
                RecordUnit.COLUMN_URL,
                RecordUnit.COLUMN_TIME
            ),
            null,
            null,
            null,
            null,
            RecordUnit.COLUMN_TIME + " COLLATE NOCASE;"
        )

        if (cursor != null) {
            cursor.moveToFirst()
            while (!cursor.isAfterLast) {
                list.add(getRecord(cursor, HISTORY_ITEM))
                cursor.moveToNext()
            }
            cursor.close()
        }
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        if (sp.getBoolean("sort_historyDomain", false)) {
            list.sortWith(Comparator.comparing { obj: Record -> obj.getDomain() })
        }
        return list
    }

    fun addDomain(domain: String?, table: String?) {
        if (domain == null || domain.trim().isEmpty() || table == null) {
            return
        }
        val values = ContentValues()
        values.put(RecordUnit.COLUMN_DOMAIN, domain.trim())
        database?.insert(table, null, values)
    }

    fun checkDomain(domain: String?, table: String?): Boolean {
        if (domain == null || domain.trim().isEmpty() || table == null) {
            return false
        }
        val cursor = database?.query(
            table,
            arrayOf(RecordUnit.COLUMN_DOMAIN),
            RecordUnit.COLUMN_DOMAIN + "=?",
            arrayOf(domain.trim()),
            null,
            null,
            null
        ) ?: return false
        val result = cursor.moveToFirst()
        cursor.close()
        return result
    }

    fun deleteDomain(domain: String?, table: String?) {
        if (domain == null || domain.trim().isEmpty() || table == null) {
            return
        }
        database?.execSQL("DELETE FROM $table WHERE ${RecordUnit.COLUMN_DOMAIN} = \"${domain.trim()}\"")
    }

    fun listDomains(table: String?): List<String> {
        val list: MutableList<String> = ArrayList()
        if (table == null) return list
        val cursor = database?.query(
            table,
            arrayOf(RecordUnit.COLUMN_DOMAIN),
            null,
            null,
            null,
            null,
            RecordUnit.COLUMN_DOMAIN
        ) ?: return list
        cursor.moveToFirst()
        while (!cursor.isAfterLast) {
            list.add(cursor.getString(0))
            cursor.moveToNext()
        }
        cursor.close()
        return list
    }

    fun checkBookmark(url: String?): Boolean {
        return checkUrl(url, RecordUnit.TABLE_BOOKMARK)
    }

    fun checkUrl(url: String?, table: String?): Boolean {
        if (url == null || url.trim().isEmpty() || table == null) {
            return false
        }
        val cursor = database?.query(
            table,
            arrayOf(RecordUnit.COLUMN_URL),
            RecordUnit.COLUMN_URL + "=?",
            arrayOf(url.trim()),
            null,
            null,
            null
        ) ?: return false
        val result = cursor.moveToFirst()
        cursor.close()
        return result
    }

    fun deleteURL(domain: String?, table: String?) {
        if (domain == null || domain.trim().isEmpty() || table == null) {
            return
        }
        database?.execSQL("DELETE FROM $table WHERE ${RecordUnit.COLUMN_URL} = \"${domain.trim()}\"")
    }

    fun clearTable(table: String?) {
        if (table == null) return
        database?.execSQL("DELETE FROM $table")
    }

    private fun getRecord(cursor: Cursor, type: Int): Record {
        val record = Record()
        record.title = cursor.getString(0)
        record.setURL(cursor.getString(1))
        record.time = cursor.getLong(2)

        if (type == BOOKMARK_ITEM) {
            record.iconColor = record.time
            record.time = 0 // time is no longer needed after extracting data
        }
        return record
    }

    fun listEntries(activity: Activity): List<Record> {
        val list: MutableList<Record> = ArrayList()
        val action = RecordAction(activity)
        action.open(false)
        list.addAll(action.listBookmark(activity, false, 0)) // move bookmarks to top of list
        list.addAll(action.listHistory(activity.applicationContext))
        action.close()
        return list
    }

    fun saveSessionStateJson(json: String?) {
        if (json == null) return
        val db = database ?: return
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM " + RecordUnit.TABLE_SESSION)
            val values = ContentValues()
            values.put(RecordUnit.COLUMN_ORDINAL, 1)
            values.put(RecordUnit.COLUMN_DATA, json)
            db.insert(RecordUnit.TABLE_SESSION, null, values)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getSessionStateJson(): String? {
        var cursor: Cursor? = null
        try {
            cursor = database?.query(
                RecordUnit.TABLE_SESSION,
                arrayOf(RecordUnit.COLUMN_DATA),
                RecordUnit.COLUMN_ORDINAL + "=?",
                arrayOf("1"),
                null,
                null,
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
        return null
    }

    fun clearSessionStateJson() {
        try {
            database?.execSQL("DELETE FROM " + RecordUnit.TABLE_SESSION)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        const val HISTORY_ITEM = 0
        const val BOOKMARK_ITEM = 2
    }
}
