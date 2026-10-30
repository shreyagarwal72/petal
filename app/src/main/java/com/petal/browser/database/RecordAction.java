package com.petal.browser.database;

import android.content.Context;

/**
 * @deprecated Legacy FOSS Browser database access object. Use {@link HistoryBookmarkRepository} instead.
 */
@Deprecated
public class RecordAction extends HistoryBookmarkRepository {

    public RecordAction(Context context) {
        super(context);
    }
}
