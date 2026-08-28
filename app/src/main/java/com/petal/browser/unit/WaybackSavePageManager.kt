package com.petal.browser.unit

import java.net.URLEncoder

/**
 * WaybackSavePageManager constructs Internet Archive Save Page URLs to archive active web pages.
 */
object WaybackSavePageManager {

    @JvmStatic
    fun getSavePageUrl(targetUrl: String?): String {

        if (targetUrl.isNullOrBlank()) return ""
        return try {
            "https://web.archive.org/save/" + URLEncoder.encode(targetUrl.trim(), "UTF-8");
        
    }
}
