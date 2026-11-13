package com.petal.browser.download

import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers *why* the download pipeline refused or aborted a URL.
 *
 * Mozilla's AbstractFetchDownloadService only reports Status.FAILED in the BrowserStore, so the
 * reason would otherwise be lost. Petal's own clients (e.g. [PetalFallbackFetchClient]) record the
 * reason here, keyed by URL, and the Downloads screen reads it back for FAILED rows.
 */
object PetalDownloadFailureLog {
    private const val MAX_ENTRIES = 64
    private val reasons = ConcurrentHashMap<String, String>()

    @JvmStatic
    fun record(url: String, reason: String) {
        if (url.isBlank() || reason.isBlank()) return
        if (reasons.size >= MAX_ENTRIES) {
            reasons.keys.firstOrNull()?.let { reasons.remove(it) }
        }
        reasons[url] = reason
    }

    @JvmStatic
    fun reasonFor(url: String): String? = reasons[url]

    @JvmStatic
    fun clear(url: String) {
        reasons.remove(url)
    }
}
