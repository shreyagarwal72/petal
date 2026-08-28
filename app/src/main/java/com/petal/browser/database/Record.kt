package com.petal.browser.database

import com.petal.browser.unit.HelperUnit

class Record {
    var iconColor: Long = 0L
    var title: String? = null
    private var url: String? = null
    var time: Long = 0L

    constructor() {
        this.title = null
        this.url = null
        this.time = 0L
        this.iconColor = 0L
    }

    constructor(title: String?, url: String?, time: Long, iconColor: Long) {
        this.title = title
        this.url = url
        this.time = time
        this.iconColor = iconColor
    }

    fun getURL(): String? {
        return url
    }

    fun setURL(url: String?) {
        this.url = url
    }

    fun getDomain(): String {
        return HelperUnit.domain(url)
    }
}
