package com.petal.browser.unit

object RecordUnit {

    /*

    Since Petal switched from whitelists to profiles to manage privacy settings,
    there is a lot of legacy code concerning the database.

    old whitelist -> new profile

    "JAVASCRIPT" whitelist -> Trusted websites
    "REMOTE" whitelist -> Standard websites
    "TABLE_PROTECTED" whitelist -> Protected websites

    */

    const val TABLE_START = "GRID"
    const val TABLE_BOOKMARK = "BOOKAMRK"
    const val TABLE_HISTORY = "HISTORY"
    const val TABLE_TRUSTED = "JAVASCRIPT"
    const val TABLE_PROTECTED = "COOKIE"
    const val TABLE_STANDARD = "REMOTE"

    const val COLUMN_TITLE = "TITLE"
    const val COLUMN_URL = "URL"
    const val COLUMN_TIME = "TIME"
    const val COLUMN_DOMAIN = "DOMAIN"
    const val COLUMN_FILENAME = "FILENAME"
    const val COLUMN_ORDINAL = "ORDINAL"

    const val CREATE_BOOKMARK = "CREATE TABLE " +
            TABLE_BOOKMARK +
            " (" +
            " " + COLUMN_TITLE + " text," +
            " " + COLUMN_URL + " text," +
            " " + COLUMN_TIME + " integer" +
            ")"

    const val CREATE_HISTORY = "CREATE TABLE " +
            TABLE_HISTORY +
            " (" +
            " " + COLUMN_TITLE + " text," +
            " " + COLUMN_URL + " text," +
            " " + COLUMN_TIME + " integer" +
            ")"

    const val CREATE_TRUSTED = "CREATE TABLE " +
            TABLE_TRUSTED +
            " (" +
            " " + COLUMN_DOMAIN + " text" +
            ")"

    const val CREATE_PROTECTED = "CREATE TABLE " +
            TABLE_PROTECTED +
            " (" +
            " " + COLUMN_DOMAIN + " text" +
            ")"

    const val CREATE_STANDARD = "CREATE TABLE " +
            TABLE_STANDARD +
            " (" +
            " " + COLUMN_DOMAIN + " text" +
            ")"

    const val TABLE_SESSION = "TAB_SESSION"
    const val COLUMN_DATA = "DATA"

    const val CREATE_SESSION = "CREATE TABLE IF NOT EXISTS " +
            TABLE_SESSION +
            " (" +
            " " + COLUMN_ORDINAL + " integer primary key," +
            " " + COLUMN_DATA + " text" +
            ")"

    const val CREATE_START = "CREATE TABLE " +
            TABLE_START +
            " (" +
            " " + COLUMN_TITLE + " text," +
            " " + COLUMN_URL + " text," +
            " " + COLUMN_FILENAME + " text," +
            " " + COLUMN_ORDINAL + " integer" +
            ")"
}
