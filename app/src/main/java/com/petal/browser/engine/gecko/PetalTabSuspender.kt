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
     * Determines whether a given tab is eligible for suspension.
     */
    fun canSuspendTab(tab: AlbumController, isForeground: Boolean): Boolean {
        if (isForeground) return false
        if (tab is PlaceholderAlbumController) return false
        // Never suspend if playing media/audio
        if (tab is PetalGeckoView) {
            if (tab.isPlayingAudio() || tab.hasActiveMediaSession()) return false
        } else if (tab is com.petal.browser.browser.PetalTabViewController) {
            val bridge = tab.getMediaBridge()
            if (bridge != null && bridge.hasActiveMedia()) return false
        }
        return true
    }

    /**
     * Suspends a specific tab at [index] in [BrowserContainer], replacing
     * it with a lightweight [PlaceholderAlbumController].
     *
     * @return true if suspended, false otherwise.
     */
    @JvmStatic
    fun suspendTab(context: Context, index: Int): Boolean {
        if (index < 0 || index >= BrowserContainer.size()) return false
        val controller = BrowserContainer.get(index)
        if (controller is PlaceholderAlbumController) return false

        val tabId: String
        val url: String
        val title: String
        val favicon: android.graphics.Bitmap?
        val groupId: String?
        val groupTitle: String?
        val isIncognito: Boolean

        when (controller) {
            is PetalGeckoView -> {
                tabId = controller.getTabId()
                url = if (controller.persistentUrl.isNotBlank()) controller.persistentUrl else controller.url
                title = controller.title
                favicon = controller.getFavicon()
                groupId = controller.getTabGroupId()
                groupTitle = controller.getTabGroupTitle()
                isIncognito = controller.isIncognito()

                try { controller.updatePreviewCache(true) } catch (_: Throwable) {}
                // Persist engine back/forward state before releasing the view (matches official Firefox session suspension)
                try {
                    controller.captureEngineState()?.let { state ->
                        if (!isIncognito && url.isNotBlank() && !com.petal.browser.unit.BrowserUnit.isHomePage(url)) {
                            com.petal.browser.unit.PetalTabStateStore.write(context, tabId, state)
                        }
                    }
                } catch (_: Throwable) {}

                try { PetalEngineStore.dispatch(EngineAction.SuspendEngineSessionAction(tabId)) } catch (_: Throwable) {}

                try {
                    controller.deactivate()
                    val session = controller.session
                    com.petal.browser.extensions.PetalExtensionManager.detachSession(session)
                    if (session.isOpen) {
                        controller.geckoView.releaseSession()
                        session.close()
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Error closing GeckoSession during suspension: ${t.message}")
                }
            }
            is com.petal.browser.browser.PetalTabViewController -> {
                tabId = controller.getTabId() ?: controller.hashCode().toString()
                url = controller.url
                title = controller.title
                favicon = controller.getFavicon()
                groupId = controller.getTabGroupId()
                groupTitle = controller.getTabGroupTitle()
                isIncognito = controller.isIncognito()

                try { controller.updatePreviewCache(true) } catch (_: Throwable) {}
                // Persist engine back/forward state before releasing the view
                try {
                    controller.captureEngineState()?.let { state ->
                        if (!isIncognito && url.isNotBlank() && !com.petal.browser.unit.BrowserUnit.isHomePage(url)) {
                            com.petal.browser.unit.PetalTabStateStore.write(context, tabId, state)
                        }
                    }
                } catch (_: Throwable) {}

                try { PetalEngineStore.dispatch(EngineAction.SuspendEngineSessionAction(tabId)) } catch (_: Throwable) {}

                try {
                    controller.deactivate()
                    com.petal.browser.extensions.PetalExtensionManager.detachSession(controller.getGeckoSession())
                    controller.destroy()
                } catch (t: Throwable) {
                    Log.w(TAG, "Error destroying PetalTabViewController during suspension: ${t.message}")
                }
            }
            else -> return false
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
     *
     * Collects eligible controllers first (snapshot), then suspends, to avoid modifying
     * BrowserContainer by index while iterating — BrowserContainer.replace() during iteration
     * can cause index drift or ConcurrentModificationException if an observer removes tabs.
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

        // Snapshot: collect (index, controller) pairs eligible for suspension before modifying.
        val toSuspend = mutableListOf<Pair<Int, AlbumController>>()
        for (i in 0 until BrowserContainer.size()) {
            val controller = BrowserContainer.get(i)
            if (controller === activeController) continue
            if (!canSuspendTab(controller, false)) continue
            val tabId = when (controller) {
                is PetalGeckoView -> controller.getTabId()
                is com.petal.browser.browser.PetalTabViewController -> controller.getTabId()
                else -> null
            } ?: continue
            val lastAccess = com.petal.browser.compose.tabs.PetalInactiveTabManager.getTabLastAccess(context, tabId)
            if (lastAccess > 0L && (now - lastAccess) >= timeoutMs) {
                toSuspend.add(Pair(i, controller))
            }
        }
        // Suspend in reverse index order so earlier indices stay valid as later ones are replaced.
        for ((index, _) in toSuspend.asReversed()) {
            suspendTab(context, index)
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
            if (!canSuspendTab(controller, false)) continue
            val tabId = when (controller) {
                is PetalGeckoView -> controller.getTabId()
                is com.petal.browser.browser.PetalTabViewController -> controller.getTabId()
                else -> null
            } ?: continue
            val lastAccess = com.petal.browser.compose.tabs.PetalInactiveTabManager.getTabLastAccess(context, tabId)
            candidates.add(Pair(i, lastAccess))
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
