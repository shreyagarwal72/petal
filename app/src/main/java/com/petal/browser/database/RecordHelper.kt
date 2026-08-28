package com.petal.browser.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.petal.browser.unit.RecordUnit

internal class RecordHelper(context: Context?) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(RecordUnit.CREATE_HISTORY)
        database.execSQL(RecordUnit.CREATE_TRUSTED)
        database.execSQL(RecordUnit.CREATE_PROTECTED)
        database.execSQL(RecordUnit.CREATE_START)
        database.execSQL(RecordUnit.CREATE_BOOKMARK)
        database.execSQL(RecordUnit.CREATE_STANDARD)
        database.execSQL(RecordUnit.CREATE_SESSION)
    }

    // UPGRADE ATTENTION!!!
    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        when (oldVersion) {
            1 -> {
                database.execSQL(RecordUnit.CREATE_BOOKMARK)
                database.execSQL(RecordUnit.CREATE_STANDARD)
                database.execSQL(RecordUnit.CREATE_SESSION)
            }
            2 -> {
                database.execSQL(RecordUnit.CREATE_STANDARD)
                database.execSQL(RecordUnit.CREATE_SESSION)
            }
            3, 4 -> {
                database.execSQL(RecordUnit.CREATE_SESSION)
            }
        }
    }

    companion object {
        private const val DATABASE_NAME = "Ninja4.db"
        private const val DATABASE_VERSION = 5
    }
}
