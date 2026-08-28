package com.petal.browser.unit

import java.net.URLEncoder

/**
 * FaviconGrabberManager extracts favicon and touch icon arrays for site accent tinting.
 */
object FaviconGrabberManager {

    /**
     * Constructs FaviconGrabber API URL.
     * Endpoint: https://favicongrabber.com/api/site/{domain}
     */
    @JvmStatic
    fun getFaviconGrabberUrl(domain: String?): String {
        if (domain.isNullOrBlank()) return ""
        val cleanDomain = domain.replace("https://", "").replace("http://", "").split("/")[0]
        return try {
            "https://favicongrabber.com/api/site/" + URLEncoder.encode(cleanDomain, "UTF-8")
        } catch (e: Exception) {
            "https://favicongrabber.com/api/site/$cleanDomain"
        }
    }
}
