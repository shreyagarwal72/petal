package com.petal.browser.engine.gecko

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.petal.browser.browser.AlbumController
import com.petal.browser.browser.BrowserContainer
import com.petal.browser.browser.PlaceholderAlbumController
import com.petal.browser.view.PetalGeckoView
import mozilla.components.browser.state.action.EngineAction
import java.util.concurrent.TimeUnit

/**
 * PetalTabSuspender
 * ─────────────────────────────────────────────────────────────────────────
 * Official Firefox-style tab suspension and lifecycle hibernation system.
 *
 * Background tabs that have been idle for longer than the configured threshold
 * or background tabs culled during OS memory trimming have their heavyweight native
 * Gecko content process unlinked and closed.
 *
 * The tab is seamlessly transformed into a [PlaceholderAlbumController] preserving:
 * - URL (including intended/persistent URLs)
 * - Tab Title
 * - Favicon & Cached Preview Thumbnail
 * - Tab ID, Group ID, Group Title
 * - Incognito state
 *
 * When the user switches back to a suspended tab, [BrowserActivity.showAlbum]
 * transparently materializes a fresh GeckoSession and restores the exact tab view.
 */
object PetalTabSuspender {
    private const val TAG = "PetalTabSuspender"

    const val PREF_MEMORY_SAVER_MODE = "sp_memory_saver_mode" // "aggressive", "balanced", "off"
    const val PREF_TAB_SUSPEND_TIMEOUT = "sp_tab_suspend_timeout" // "5", "15", "30", "never" (minutes)

    /**
     * Determines whether a given [PetalGeckoView] tab is eligible for suspension.
     */
    fun canSuspendTab(tab: PetalGeckoView, isForeground: Boolean): Boolean {
        if (isForeground) return false
        // Never suspend if playing media/audio
        if (tab.isPlayingAudio() || tab.hasActiveMediaSession()) return false
        // Home tabs are lightweight about:blank anyway
        return true
    }

    /**
     * Suspends a specific [PetalGeckoView] tab at [index] in [BrowserContainer], replacing
     * it with a lightweight [PlaceholderAlbumController].
     *
     * @return true if suspended, false otherwise.
     */
    @JvmStatic
    fun suspendTab(context: Context, index: Int): Boolean {
        if (index < 0 || index >= BrowserContainer.size()) return false
        val controller = BrowserContainer.get(index)
        if (controller !is PetalGeckoView) return false

        val tabId = controller.getTabId()
        val url = if (controller.persistentUrl.isNotBlank()) controller.persistentUrl else controller.url
        val title = controller.title
        val favicon = controller.getFavicon()
        val groupId = controller.getTabGroupId()
        val groupTitle = controller.getTabGroupTitle()
        val isIncognito = controller.isIncognito()

        // Save preview thumbnail before tearing down native surface
        try {
            controller.updatePreviewCache(true)
        } catch (_: Throwable) {}

        // Notify BrowserStore of session suspension (triggers SuspendMiddleware/Unlink)
        try {
            PetalEngineStore.dispatch(EngineAction.SuspendEngineSessionAction(tabId))
        } catch (_: Throwable) {}

        // Safely deactivate and release native GeckoView session
        try {
            controller.deactivate()
            val session = controller.session
            if (session.isOpen) {
                controller.geckoView.releaseSession()
                session.close()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Error closing GeckoSession during suspension: ${t.message}")
        }

        // Replace with memory-friendly PlaceholderAlbumController
        val placeholder = PlaceholderAlbumController(
            context,
            title,
            url,
            favicon,
            tabId,
            groupId,
            groupTitle,
            isIncognito
        )
        BrowserContainer.replace(index, placeholder)
        Log.i(TAG, "Successfully suspended inactive tab '$title' ($tabId) -> native memory freed")
        return true
    }

    /**
     * Evaluates all open background tabs and suspends any tabs that exceed the inactivity threshold.
     */
    @JvmStatic
    fun checkAndSuspendInactiveTabs(context: Context, activeController: AlbumController?) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val saverMode = sp.getString(PREF_MEMORY_SAVER_MODE, "balanced") ?: "balanced"
        if (saverMode == "off") return

        val timeoutStr = sp.getString(PREF_TAB_SUSPEND_TIMEOUT, if (saverMode == "aggressive") "5" else "15") ?: "15"
        if (timeoutStr == "never") return

        val timeoutMinutes = timeoutStr.toLongOrNull() ?: 15L
        val timeoutMs = TimeUnit.MINUTES.toMillis(timeoutMinutes)
        val now = System.currentTimeMillis()

        for (i in 0 until BrowserContainer.size()) {
            val controller = BrowserContainer.get(i)
            if (controller === activeController) continue
            if (controller is PetalGeckoView) {
                if (!canSuspendTab(controller, false)) continue
                val lastAccess = com.petal.browser.compose.tabs.PetalInactiveTabManager.getTabLastAccess(context, controller.getTabId())
                if (lastAccess > 0L && (now - lastAccess) >= timeoutMs) {
                    suspendTab(context, i)
                }
            }
        }
    }

    /**
     * Suspends background tabs under memory pressure in sorted LRU order, preserving
     * at least [minActiveTabs] active tabs.
     */
    @JvmStatic
    fun trimBackgroundTabs(context: Context, activeController: AlbumController?, minActiveTabs: Int = 3) {
        val candidates = mutableListOf<Pair<Int, Long>>()

        for (i in 0 until BrowserContainer.size()) {
            val controller = BrowserContainer.get(i)
            if (controller === activeController) continue
            if (controller is PetalGeckoView) {
                if (!canSuspendTab(controller, false)) continue
                val lastAccess = com.petal.browser.compose.tabs.PetalInactiveTabManager.getTabLastAccess(context, controller.getTabId())
                candidates.add(Pair(i, lastAccess))
            }
        }

        // Sort ascending by last access (oldest accessed tabs first)
        candidates.sortBy { it.second }

        val toSuspendCount = (candidates.size - minActiveTabs).coerceAtLeast(0)
        for (k in 0 until toSuspendCount) {
            val index = candidates[k].first
            suspendTab(context, index)
        }
        Log.i(TAG, "Trimmed background tabs: suspended $toSuspendCount tabs (preserved $minActiveTabs)")
    }
}
