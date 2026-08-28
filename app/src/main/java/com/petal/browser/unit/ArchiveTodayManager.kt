package com.petal.browser.unit

import java.net.URLEncoder

/**
 * ArchiveTodayManager constructs Archive.today anti-paywall snapshot URLs.
 */
object ArchiveTodayManager {

    @JvmStatic
    fun getArchiveTodayUrl(targetUrl: String?): String {

        if (targetUrl.isNullOrBlank()) return ""
        return try {
            "https://archive.is/newest/" + URLEncoder.encode(targetUrl.trim(), "UTF-8");
        
    }
}
