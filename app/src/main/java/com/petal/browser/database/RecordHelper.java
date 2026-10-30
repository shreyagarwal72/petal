package com.petal.browser.database;

import android.content.Context;

/**
 * @deprecated Legacy FOSS Browser helper. Use {@link PetalDatabaseOpenHelper} instead.
 */
@Deprecated
class RecordHelper extends PetalDatabaseOpenHelper {

    RecordHelper(Context context) {
        super(context);
    }
}
