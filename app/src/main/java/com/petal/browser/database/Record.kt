package com.petal.browser.database

/**
 * @deprecated Legacy FOSS Browser model class. Use [HistoryBookmarkItem] instead.
 */
@Deprecated("Legacy FOSS Browser model class. Use HistoryBookmarkItem instead.")
open class Record : HistoryBookmarkItem {
    constructor() : super()
    constructor(title: String?, url: String?, time: Long, iconColor: Long) : super(title, url, time, iconColor)
    constructor(title: String?, url: String?, filename: String?, ordinal: Int) : super(title, url, filename, ordinal)
}
