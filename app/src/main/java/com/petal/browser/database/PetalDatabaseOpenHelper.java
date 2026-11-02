package com.petal.browser.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Modern SQLiteOpenHelper managing Petal's local bookmarks, history, whitelist, and session databases.
 * Modernized from legacy FOSS Browser RecordHelper.
 */
public class PetalDatabaseOpenHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "Ninja4.db";
    public static final int DATABASE_VERSION = 6;

    public PetalDatabaseOpenHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL(HistoryBookmarkContract.CREATE_HISTORY);
        database.execSQL(HistoryBookmarkContract.CREATE_TRUSTED);
        database.execSQL(HistoryBookmarkContract.CREATE_PROTECTED);
        database.execSQL(HistoryBookmarkContract.CREATE_START);
        database.execSQL(HistoryBookmarkContract.CREATE_BOOKMARK);
        database.execSQL(HistoryBookmarkContract.CREATE_STANDARD);
        database.execSQL(HistoryBookmarkContract.CREATE_SESSION);
    }

    // UPGRADE ATTENTION!!!
    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        switch (oldVersion) {
            case 1:
                database.execSQL(HistoryBookmarkContract.CREATE_BOOKMARK);
                // CREATE_BOOKMARK above already includes IS_READING_LIST (current schema),
                // so a device upgrading from version 1 must NOT also run the case 5 ALTER
                // TABLE below - that would try to add a column that already exists and
                // crash. Jump straight past case 5 to case 2 for this path only.
                database.execSQL(HistoryBookmarkContract.CREATE_STANDARD);
                database.execSQL(HistoryBookmarkContract.CREATE_SESSION);
                break;
            case 2:
                database.execSQL(HistoryBookmarkContract.CREATE_STANDARD);
            case 3:
            case 4:
                database.execSQL(HistoryBookmarkContract.CREATE_SESSION);
            case 5:
                // Existing installs on version 5 have a BOOKMARK table without the
                // reading-list column - add it in place so saved bookmarks survive.
                try {
                    database.execSQL(HistoryBookmarkContract.ALTER_BOOKMARK_ADD_READING_LIST);
                } catch (Exception e) {
                    // Column already present (e.g. re-entrant upgrade) - safe to ignore.
                }
                // we want all updates, so no break statement here...
        }
    }
}
