package com.petal.browser.unit;

public class RecordUnit {

    /*

    Since Petal switched from whitelists to profiles to manage privacy settings,
    there is a lot of legacy code concerning the database.

    old whitelist -> new profile

    "JAVASCRIPT" whitelist -> Trusted websites
    "REMOTE" whitelist -> Standard websites
    "TABLE_PROTECTED" whitelist -> Protected websites

    */

    public static final String TABLE_START = "GRID";
    public static final String TABLE_BOOKMARK = "BOOKAMRK";
    public static final String TABLE_HISTORY = "HISTORY";
    public static final String TABLE_TRUSTED = "JAVASCRIPT";
    public static final String TABLE_PROTECTED = "COOKIE";
    public static final String TABLE_STANDARD = "REMOTE";

    public static final String COLUMN_TITLE = "TITLE";
    public static final String COLUMN_URL = "URL";
    public static final String COLUMN_TIME = "TIME";
    public static final String COLUMN_DOMAIN = "DOMAIN";
    public static final String COLUMN_FILENAME = "FILENAME";
    public static final String COLUMN_ORDINAL = "ORDINAL";

    public static final String CREATE_BOOKMARK = "CREATE TABLE "
            + TABLE_BOOKMARK
            + " ("
            + " " + COLUMN_TITLE + " text,"
            + " " + COLUMN_URL + " text,"
            + " " + COLUMN_TIME + " integer"
            + ")";

    public static final String CREATE_HISTORY = "CREATE TABLE "
            + TABLE_HISTORY
            + " ("
            + " " + COLUMN_TITLE + " text,"
            + " " + COLUMN_URL + " text,"
            + " " + COLUMN_TIME + " integer"
            + ")";

    public static final String CREATE_TRUSTED = "CREATE TABLE "
            + TABLE_TRUSTED
            + " ("
            + " " + COLUMN_DOMAIN + " text"
            + ")";

    public static final String CREATE_PROTECTED = "CREATE TABLE "
            + TABLE_PROTECTED
            + " ("
            + " " + COLUMN_DOMAIN + " text"
            + ")";

    public static final String CREATE_STANDARD = "CREATE TABLE "
            + TABLE_STANDARD
            + " ("
            + " " + COLUMN_DOMAIN + " text"
            + ")";

    public static final String TABLE_SESSION = "TAB_SESSION";
    public static final String COLUMN_DATA = "DATA";

    public static final String CREATE_SESSION = "CREATE TABLE IF NOT EXISTS "
            + TABLE_SESSION
            + " ("
            + " " + COLUMN_ORDINAL + " integer primary key,"
            + " " + COLUMN_DATA + " text"
            + ")";

    public static final String CREATE_START = "CREATE TABLE "
            + TABLE_START
            + " ("
            + " " + COLUMN_TITLE + " text,"
            + " " + COLUMN_URL + " text,"
            + " " + COLUMN_FILENAME + " text,"
            + " " + COLUMN_ORDINAL + " integer"
            + ")";
}