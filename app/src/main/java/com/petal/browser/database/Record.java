package com.petal.browser.database;

/**
 * @deprecated Legacy FOSS Browser model class. Use {@link HistoryBookmarkItem} instead.
 */
@Deprecated
public class Record extends HistoryBookmarkItem {

    public Record() {
        super();
    }

    public Record(String title, String url, long time, long iconColor) {
        super(title, url, time, iconColor);
    }

    public Record(String title, String url, String filename, int ordinal) {
        super(title, url, filename, ordinal);
    }
}
