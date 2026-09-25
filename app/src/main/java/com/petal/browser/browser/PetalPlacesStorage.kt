package com.petal.browser.browser

import android.content.Context
import com.petal.browser.database.Record
import com.petal.browser.database.RecordAction

/**
 * PetalPlacesStorage
 * ─────────────────────────────────────────────────────────────────────────
 * Clean storage abstraction layer for browsing history and bookmarks.
 * Powered by high-speed SQLite [RecordAction] engine with zero overhead,
 * maintaining persistent access without requiring native Rust storage-sync dependencies.
 */
object PetalPlacesStorage {

    /**
     * Records a visited URL into local history.
     */
    fun recordVisit(context: Context, url: String, title: String) {
        if (url.isBlank() || url.startsWith("about:") || url.startsWith("moz-extension://")) return
        try {
            val action = RecordAction(context)
            action.open(true)
            val record = Record().apply {
                setTitle(title.ifBlank { url })
                setURL(url)
                setTime(System.currentTimeMillis())
            }
            action.addHistory(record)
            action.close()
        } catch (_: Throwable) {}
    }

    /**
     * Returns visited history records.
     */
    fun getVisited(context: Context): List<Record> {
        return try {
            val action = RecordAction(context)
            action.open(false)
            val list = action.listHistory(context)
            action.close()
            list ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * Clears all visited browsing history.
     */
    fun clearHistory(context: Context) {
        try {
            val action = RecordAction(context)
            action.open(true)
            action.clearHistory()
            action.close()
        } catch (_: Throwable) {}
    }
}
