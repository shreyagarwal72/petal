package com.petal.browser.unit

import java.net.URLEncoder

/**
 * ArchiveBookEmbedManager constructs Internet Archive Open Library document & book embed URLs.
 */
object ArchiveBookEmbedManager {

    @JvmStatic
    fun getBookEmbedUrl(identifier: String?): String {

        if (identifier == null || identifier.trim().isEmpty()) return "";
        return try {
            "https://archive.org/embed/" + URLEncoder.encode(identifier.trim(), "UTF-8");
        
    }
}
