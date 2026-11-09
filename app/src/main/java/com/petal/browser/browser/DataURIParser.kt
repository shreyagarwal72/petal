package com.petal.browser.browser

import android.util.Base64
import android.webkit.MimeTypeMap

class DataURIParser(url: String) {
    val filename: String
    val imagedata: ByteArray

    init {
        val data = url.substring(url.indexOf(",") + 1)
        val mimeType = url.substring(url.indexOf(":") + 1, url.indexOf(";"))
        val fileType = url.substring(url.indexOf(":") + 1, url.indexOf("/"))
        val suffix = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        filename = "$fileType.$suffix"
        imagedata = Base64.decode(data, Base64.DEFAULT)
    }
}
