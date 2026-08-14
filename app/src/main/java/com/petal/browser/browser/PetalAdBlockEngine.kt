package com.petal.browser.browser

import android.content.Context
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.util.regex.Pattern

object PetalAdBlockEngine {
    private var isEnabled = true

    // Built-in EasyList & Tracker blocking patterns
    private val adPatterns = listOf(
        Pattern.compile(".*(doubleclick\\.net|googlesyndication\\.com|adnxs\\.com|pubmatic\\.com).*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*(amazon-adsystem\\.com|adservice\\.google\\.com|criteo\\.com).*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*(rubiconproject\\.com|taboola\\.com|outbrain\\.com|scorecardresearch\\.com).*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*/(ads|adserver|banner|popunder|popup)/.*", Pattern.CASE_INSENSITIVE)
    )

    fun setAdBlockEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    fun isAdBlockEnabled(): Boolean = isEnabled

    fun shouldBlockUrl(url: String?): Boolean {
        if (!isEnabled || url.isNullOrBlank()) return false
        for (pattern in adPatterns) {
            if (pattern.matcher(url).matches()) {
                return true
            }
        }
        return false
    }

    fun createDummyResponse(): WebResourceResponse {
        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
    }
}
