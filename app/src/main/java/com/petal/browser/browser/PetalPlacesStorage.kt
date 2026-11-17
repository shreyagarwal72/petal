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
     * Records title observation for an existing URL in local history.
     */
    fun recordTitleObservation(context: Context, url: String, title: String) {
        if (url.isBlank() || title.isBlank() || url.startsWith("about:") || url.startsWith("moz-extension://")) return
        PetalPlacesProvider.recordTitleObservation(context, url, title)
    }

    /**
     * Checks if a single URL has been visited.
     */
    fun hasVisited(context: Context, url: String): Boolean {
        return PetalPlacesProvider.hasVisited(context, url)
    }

    /**
     * Batch checks if the given URLs have been visited.
     */
    fun getVisited(context: Context, urls: List<String>): List<Boolean> {
        return PetalPlacesProvider.getVisited(context, urls)
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

    /**
     * Returns all saved bookmarks.
     */
    fun getAllBookmarks(context: Context): List<Record> {
        return PetalPlacesProvider.getAllBookmarks(context)
    }

    /**
     * Deletes a bookmark by URL.
     */
    fun deleteBookmark(context: Context, url: String) {
        PetalPlacesProvider.deleteBookmark(context, url)
    }

    /**
     * Checks if a bookmark exists for the given URL.
     */
    fun checkBookmark(context: Context, url: String): Boolean {
        return PetalPlacesProvider.checkBookmark(context, url)
    }

    /**
     * Returns the underlying PlacesHistoryStorage.
     */
    fun getHistoryStorage(context: Context): mozilla.components.browser.storage.sync.PlacesHistoryStorage {
        return PetalPlacesProvider.getHistoryStorage(context)
    }

    /**
     * Returns the underlying PlacesBookmarksStorage.
     */
    fun getBookmarksStorage(context: Context): mozilla.components.browser.storage.sync.PlacesBookmarksStorage {
        return PetalPlacesProvider.getBookmarksStorage(context)
    }
}
