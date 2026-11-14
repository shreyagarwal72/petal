package com.petal.browser.database

import com.petal.browser.unit.PetalAndroidUtils

/**
 * Modern data model representing a browser history entry or bookmark item.
 * Refactored from legacy Petal Browser Record POJO.
 */
open class HistoryBookmarkItem(
    var title: String = "",
    var url: String = "",
    var time: Long = 0L,
    var iconColor: Long = 0L,
    var filename: String? = null,
    var ordinal: Int = 0,
    var isReadingList: Boolean = false
) {
    constructor(title: String?, url: String?, time: Long, iconColor: Long) :
        this(title.orEmpty(), url.orEmpty(), time, iconColor, null, 0, false)

    constructor(title: String?, url: String?, filename: String?, ordinal: Int) :
        this(title.orEmpty(), url.orEmpty(), 0L, 0L, filename, ordinal, false)

    fun getURL(): String = url
    fun setURL(newUrl: String?) { url = newUrl.orEmpty() }

    val domain: String
        get() = PetalAndroidUtils.domain(url)
}
