package com.petal.browser.unit

import java.net.URLEncoder

/**
 * ArchivePhSearchManager constructs Archive.ph / Archive.today index search URLs.
 */
object ArchivePhSearchManager {

    @JvmStatic
    fun getSearchUrl(targetUrl: String?): String {

        if (targetUrl.isNullOrBlank()) return ""
        return try {
            "https://archive.ph/search/?q=" + URLEncoder.encode(targetUrl.trim(), "UTF-8");
        
    }
}
