package com.petal.browser.database;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import com.petal.browser.unit.RecordUnit;

public class RecordAction {
    public static final int HISTORY_ITEM = 0;
    public static final int BOOKMARK_ITEM = 2;
    private final RecordHelper helper;
    private SQLiteDatabase database;

    public RecordAction(Context context) {
        this.helper = new RecordHelper(context);
    }
    public void open(boolean rw) {database = rw ? helper.getWritableDatabase() : helper.getReadableDatabase();}
    public void close() {
        helper.close();
    }

    public void addBookmark(Record record) {
        if (record == null
                || record.getTitle() == null
                || record.getTitle().trim().isEmpty()
                || record.getURL() == null
                || record.getURL().trim().isEmpty()
                || record.getTime() < 0L) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put(RecordUnit.COLUMN_TITLE, record.getTitle().trim());
        values.put(RecordUnit.COLUMN_URL, record.getURL().trim());
        values.put(RecordUnit.COLUMN_TIME, record.getIconColor());
        database.insert(RecordUnit.TABLE_BOOKMARK, null, values);
    }

    public List<Record> listBookmark(Context context, boolean filter, long filterBy) {

        List<Record> list = new LinkedList<>();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String sortBy = Objects.requireNonNull(sp.getString("sort_bookmark", "title"));
        Cursor cursor;
        cursor = database.query(
                RecordUnit.TABLE_BOOKMARK,
                new String[]{
                        RecordUnit.COLUMN_TITLE,
                        RecordUnit.COLUMN_URL,
                        RecordUnit.COLUMN_TIME
                },
                null,
                null,
                null,
                null,
                sortBy + " COLLATE NOCASE;"
        );
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            if (filter) {
                if ((getRecord(cursor, BOOKMARK_ITEM).getIconColor()) == filterBy) {
                    list.add(getRecord(cursor, BOOKMARK_ITEM));
                }
            } else {
                list.add(getRecord(cursor, BOOKMARK_ITEM));
            }
            cursor.moveToNext();
        }
        cursor.close();

        if (sortBy.equals("time")) {
            //ignore desktop mode, JavaScript, and remote content when sorting colors
            list.sort(Comparator.comparing(Record::getTitle));
            list.sort(Comparator.comparingLong(Record::getIconColor));
        }
        if (sp.getBoolean("sort_bookmarkDomain", false)) {
            list.sort(Comparator.comparing(Record::getDomain));
        }
        Collections.reverse(list);
        return list;
    }

    public void addHistory(Record record) {
        if (record == null
                || record.getTitle() == null
                || record.getTitle().trim().isEmpty()
                || record.getURL() == null
                || record.getURL().trim().isEmpty()
                || record.getURL().trim().equalsIgnoreCase("about:blank")
                || record.getURL().trim().startsWith("about:")
                || record.getTime() < 0L) {
            return;
        }
        record.setTime(record.getTime());

        ContentValues values = new ContentValues();
        values.put(RecordUnit.COLUMN_TITLE, record.getTitle().trim());
        values.put(RecordUnit.COLUMN_URL, record.getURL().trim());
        values.put(RecordUnit.COLUMN_TIME, record.getTime());
        database.insert(RecordUnit.TABLE_HISTORY, null, values);
    }

    public List<Record> listHistory(Context context) {
        List<Record> list = new ArrayList<>();
        Cursor cursor;
        cursor = database.query(
                RecordUnit.TABLE_HISTORY,
                new String[]{
                        RecordUnit.COLUMN_TITLE,
                        RecordUnit.COLUMN_URL,
                        RecordUnit.COLUMN_TIME
                },
                null,
                null,
                null,
                null,
                RecordUnit.COLUMN_TIME + " COLLATE NOCASE;"
        );

        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            list.add(getRecord(cursor, HISTORY_ITEM));
            cursor.moveToNext();
        }
        cursor.close();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        if (sp.getBoolean("sort_historyDomain", false)) {
            list.sort(Comparator.comparing(Record::getDomain));
        }
        return list;
    }

    public void addDomain(String domain, String table) {
        if (domain == null || domain.trim().isEmpty()) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put(RecordUnit.COLUMN_DOMAIN, domain.trim());
        database.insert(table, null, values);
    }

    public boolean checkDomain(String domain, String table) {
        if (domain == null || domain.trim().isEmpty()) {
            return false;
        }
        Cursor cursor = database.query(
                table,
                new String[]{RecordUnit.COLUMN_DOMAIN},
                RecordUnit.COLUMN_DOMAIN + "=?",
                new String[]{domain.trim()},
                null,
                null,
                null
        );
        boolean result = cursor.moveToFirst();
        cursor.close();
        return result;
    }

    public void deleteDomain(String domain, String table) {
        if (domain == null || domain.trim().isEmpty()) {
            return;
        }
        database.execSQL("DELETE FROM " + table + " WHERE " + RecordUnit.COLUMN_DOMAIN + " = " + "\"" + domain.trim() + "\"");
    }

    public List<String> listDomains(String table) {
        List<String> list = new ArrayList<>();
        Cursor cursor = database.query(
                table,
                new String[]{RecordUnit.COLUMN_DOMAIN},
                null,
                null,
                null,
                null,
                RecordUnit.COLUMN_DOMAIN
        );
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            list.add(cursor.getString(0));
            cursor.moveToNext();
        }
        cursor.close();
        return list;
    }

    public boolean checkBookmark(String url) {
        return checkUrl(url, RecordUnit.TABLE_BOOKMARK);
    }

    public boolean checkUrl(String url, String table) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        Cursor cursor = database.query(
                table,
                new String[]{RecordUnit.COLUMN_URL},
                RecordUnit.COLUMN_URL + "=?",
                new String[]{url.trim()},
                null,
                null,
                null
        );
        boolean result = cursor.moveToFirst();
        cursor.close();

        return result;
    }

    public void deleteURL(String domain, String table) {
        if (domain == null || domain.trim().isEmpty()) {
            return;
        }
        database.execSQL("DELETE FROM " + table + " WHERE " + RecordUnit.COLUMN_URL + " = " + "\"" + domain.trim() + "\"");
    }

    public void clearTable(String table) {
        database.execSQL("DELETE FROM " + table);
    }

    private Record getRecord(Cursor cursor, int type) {
        Record record = new Record();
        record.setTitle(cursor.getString(0));
        record.setURL(cursor.getString(1));
        record.setTime(cursor.getLong(2));

        if (type == BOOKMARK_ITEM) {
            record.setIconColor(record.getTime());
            record.setTime(0);  //time is no longer needed after extracting data
        } else if (type == HISTORY_ITEM) {
            record.setTime(record.getTime());
        }
        return record;
    }

    public List<Record> listEntries(Activity activity) {
        List<Record> list = new ArrayList<>();
        RecordAction action = new RecordAction(activity);
        action.open(false);
        list.addAll(action.listBookmark(activity, false, 0)); //move bookmarks to top of list
        list.addAll(action.listHistory(activity.getApplicationContext()));
        action.close();
        return list;
    }

    public void saveSessionStateJson(String json) {
        if (json == null) return;
        database.beginTransaction();
        try {
            database.execSQL("DELETE FROM " + RecordUnit.TABLE_SESSION);
            ContentValues values = new ContentValues();
            values.put(RecordUnit.COLUMN_ORDINAL, 1);
            values.put(RecordUnit.COLUMN_DATA, json);
            database.insert(RecordUnit.TABLE_SESSION, null, values);
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    public String getSessionStateJson() {
        Cursor cursor = null;
        try {
            cursor = database.query(
                    RecordUnit.TABLE_SESSION,
                    new String[]{RecordUnit.COLUMN_DATA},
                    RecordUnit.COLUMN_ORDINAL + "=?",
                    new String[]{"1"},
                    null,
                    null,
                    null
            );
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    public void clearSessionStateJson() {
        try {
            database.execSQL("DELETE FROM " + RecordUnit.TABLE_SESSION);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}