package com.petal.browser.browser

import android.content.Context
import com.petal.browser.database.PetalPlacesProvider
import com.petal.browser.database.Record

/**
 * PetalPlacesStorage
 * ─────────────────────────────────────────────────────────────────────────
 * Clean storage abstraction layer for browsing history and bookmarks,
 * backed by Mozilla's Places engine via [PetalPlacesProvider].
 */
object PetalPlacesStorage {

    /**
     * Records a visited URL into local history.
     */
    fun recordVisit(context: Context, url: String, title: String) {
        if (url.isBlank() || url.startsWith("about:") || url.startsWith("moz-extension://")) return
        PetalPlacesProvider.recordVisit(context, url, title)
    }

    /**
     * Returns visited history records.
     */
    fun getVisited(context: Context): List<Record> {
        return PetalPlacesProvider.getHistory(context)
    }

    /**
     * Clears all visited browsing history.
     */
    fun clearHistory(context: Context) {
        PetalPlacesProvider.clearHistory(context)
    }
}
