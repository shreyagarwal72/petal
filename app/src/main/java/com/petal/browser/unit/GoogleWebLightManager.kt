package com.petal.browser.unit

import java.net.URLEncoder

/**
 * GoogleWebLightManager constructs Google Web Light ultra-lightweight page loading URLs.
 */
object GoogleWebLightManager {

    @JvmStatic
    fun getWebLightUrl(targetUrl: String?): String {

        if (targetUrl.isNullOrBlank()) return ""
        return try {
            "https://googleweblight.com/i?u=" + URLEncoder.encode(targetUrl.trim(), "UTF-8");
        
    }
}
