package com.petal.browser.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Modern SQLiteOpenHelper managing Petal's local bookmarks, history, whitelist, and session databases.
 * Modernized from legacy Petal Browser RecordHelper.
 */
class PetalDatabaseOpenHelper(context: Context?) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "Ninja4.db"
        const val DATABASE_VERSION = 6
    }

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(HistoryBookmarkContract.CREATE_HISTORY)
        database.execSQL(HistoryBookmarkContract.CREATE_TRUSTED)
        database.execSQL(HistoryBookmarkContract.CREATE_PROTECTED)
        database.execSQL(HistoryBookmarkContract.CREATE_START)
        database.execSQL(HistoryBookmarkContract.CREATE_BOOKMARK)
        database.execSQL(HistoryBookmarkContract.CREATE_STANDARD)
        database.execSQL(HistoryBookmarkContract.CREATE_SESSION)
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 1) {
            database.execSQL(HistoryBookmarkContract.CREATE_BOOKMARK)
            database.execSQL(HistoryBookmarkContract.CREATE_STANDARD)
            database.execSQL(HistoryBookmarkContract.CREATE_SESSION)
            return
        }

        var v = oldVersion
        if (v == 2) {
            database.execSQL(HistoryBookmarkContract.CREATE_STANDARD)
            v = 3
        }
        if (v in 3..4) {
            database.execSQL(HistoryBookmarkContract.CREATE_SESSION)
            v = 5
        }
        if (v == 5) {
            try {
                database.execSQL(HistoryBookmarkContract.ALTER_BOOKMARK_ADD_READING_LIST)
            } catch (_: Exception) {
                // Column already present (e.g. re-entrant upgrade) - safe to ignore.
            }
        }
    }
}
