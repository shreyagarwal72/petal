package com.petal.browser.unit

import android.content.Context
import android.util.Log
import com.petal.browser.database.RecordAction
import java.util.Collections

/**
 * Manages session-scoped history isolation.
 * When "Clear on Exit" is enabled, tracks URLs and timestamps created during the current
 * active session, so on exit ONLY the history generated in this session is cleared while
 * keeping user logins, credentials, cookies, and prior permanent history completely safe.
 */
object PetalSessionHistoryManager {
    private const val TAG = "PetalSessionHistory"

    @Volatile
    private var sessionStartTime: Long = System.currentTimeMillis()

    private val sessionUrls: MutableSet<String> = Collections.synchronizedSet(HashSet<String>())

    @JvmStatic
    fun initSession() {
        sessionStartTime = System.currentTimeMillis()
        sessionUrls.clear()
        Log.d(TAG, "Initialized new browsing session at $sessionStartTime")
    }

    @JvmStatic
    fun recordSessionVisit(url: String?) {
        if (!url.isNullOrBlank() && !url.equals("about:blank", ignoreCase = true) && !url.startsWith("about:")) {
            sessionUrls.add(url.trim())
        }
    }

    @JvmStatic
    fun getSessionStartTime(): Long = sessionStartTime

    @JvmStatic
    fun getSessionUrls(): Set<String> = HashSet(sessionUrls)

    @JvmStatic
    fun clearSessionHistory(context: Context) {
        try {
            val action = RecordAction(context)
            action.open(true)
            action.deleteSessionHistory(sessionStartTime, sessionUrls)
            action.close()
            sessionUrls.clear()
            Log.i(TAG, "Successfully cleared session-isolated history records on exit")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing session history on exit", e)
        }
    }
}
