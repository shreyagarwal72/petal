package com.petal.browser.unit

import java.net.URLEncoder

/**
 * HtmlToMarkdownManager constructs Markdown export endpoint URLs for web pages.
 */
object HtmlToMarkdownManager {

    /**
     * Constructs Statickit HTML-to-Markdown API export URL.
     * Endpoint: https://api.statickit.com/v1/markdown?url=
     */
    @JvmStatic
    fun getMarkdownExportUrl(targetUrl: String?): String {
        if (targetUrl.isNullOrBlank()) return ""
        val trimmed = targetUrl.trim()
        return try {
            "https://api.statickit.com/v1/markdown?url=" + URLEncoder.encode(trimmed, "UTF-8")
        } catch (e: Exception) {
            "https://api.statickit.com/v1/markdown?url=$trimmed"
        }
    }
}
