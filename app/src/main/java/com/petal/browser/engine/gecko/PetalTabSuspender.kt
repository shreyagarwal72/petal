package com.petal.browser.engine.gecko

import android.content.ComponentCallbacks2
import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.concept.engine.mediasession.MediaSession

/**
 * PetalTabSuspender: store-driven Memory Saver.
 *
 * BrowserStore is the only source of truth. A background tab is suspended by dispatching
 * [EngineAction.SuspendEngineSessionAction]: Mozilla's EngineMiddleware saves the engine state
 * (history included) into the tab and closes the engine session. The tab keeps its title,
 * favicon, thumbnail and id; selecting it later makes the tab view controller re-create the
 * session and restore that state. Idle time comes from [TabSessionState.lastAccess]
 * (maintained by LastAccessMiddleware), so there is no private access map to leak.
 */
object PetalTabSuspender {
    private const val TAG = "PetalTabSuspender"

    const val PREF_MEMORY_SAVER_MODE = "sp_memory_saver_mode" // "aggressive", "balanced", "off"
    const val PREF_TAB_SUSPEND_TIMEOUT = "sp_tab_suspend_timeout" // "5", "15", "30", "never" (minutes)
    const val PREF_NEVER_SUSPEND_PINNED_MEDIA = "sp_never_suspend_pinned_media" // default true

    /** Tabs kept awake under memory pressure, besides the selected one. */
    private const val MIN_AWAKE_TABS_BALANCED = 3
    private const val MIN_AWAKE_TABS_AGGRESSIVE = 1

    /** Pure eligibility rule (unit-testable, no Android dependencies besides state classes). */
    @JvmStatic
    fun isEligible(
        tab: TabSessionState,
        selectedTabId: String?,
        isPinned: Boolean,
        protectPinnedAndMedia: Boolean
    ): Boolean {
        return isEligible(
            isSelected = tab.id == selectedTabId,
            hasEngineSession = tab.engineState.engineSession != null,
            hasActiveMedia = hasActiveMedia(tab),
            isPinned = isPinned,
            protectPinned = protectPinnedAndMedia
        )
    }

    /** Pure rule: selected, already-sleeping and media tabs are never suspended; pinned optionally. */
    @JvmStatic
    fun isEligible(
        isSelected: Boolean,
        hasEngineSession: Boolean,
        hasActiveMedia: Boolean,
        isPinned: Boolean,
        protectPinned: Boolean
    ): Boolean {
        if (isSelected) return false
        if (!hasEngineSession) return false // already sleeping or never started
        if (hasActiveMedia) return false // media tabs are never suspended
        if (protectPinned && isPinned) return false
        return true
    }

    @JvmStatic
    fun hasActiveMedia(tab: TabSessionState): Boolean =
        tab.mediaSessionState?.playbackState == MediaSession.PlaybackState.PLAYING

    @JvmStatic
    fun isSleeping(tab: TabSessionState): Boolean =
        tab.engineState.engineSession == null && tab.engineState.engineSessionState != null

    /** Idle threshold from settings, or null when suspension by time is disabled. */
    @JvmStatic
    fun timeoutMillis(context: Context): Long? {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        if ((sp.getString(PREF_MEMORY_SAVER_MODE, "balanced") ?: "balanced") == "off") return null
        val minutes = (sp.getString(PREF_TAB_SUSPEND_TIMEOUT, "15") ?: "15").toLongOrNull() ?: return null
        return minutes * 60_000L
    }

    private fun candidates(context: Context, protect: Boolean): List<TabSessionState> {
        val state = PetalEngineStore.getStore(context).state
        return state.tabs.filter {
            !PetalEngineStore.isEphemeralTabId(it.id) && isEligible(
                it, state.selectedTabId,
                com.petal.browser.browser.PetalTabMetadataStore.isPinned(context, it.id),
                protect
            )
        }
    }

    private fun protectPinnedAndMedia(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_NEVER_SUSPEND_PINNED_MEDIA, true)

    /** Time-based sweep: suspends eligible background tabs idle longer than the timeout. */
    @JvmStatic
    fun sweepIdleTabs(context: Context, now: Long = System.currentTimeMillis()): Int {
        val timeout = timeoutMillis(context) ?: return 0
        val store = PetalEngineStore.getStore(context)
        var count = 0
        for (tab in candidates(context, protectPinnedAndMedia(context))) {
            val last = tab.lastAccess
            if (last <= 0L || now - last < timeout) continue
            store.dispatch(EngineAction.SuspendEngineSessionAction(tab.id))
            count++
        }
        if (count > 0) Log.i(TAG, "Suspended $count idle background tab(s)")
        return count
    }

    /** Memory-pressure sweep: suspends least-recently-used eligible tabs beyond the awake budget. */
    @JvmStatic
    fun suspendUnderPressure(context: Context, level: Int): Int {
        val mode = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREF_MEMORY_SAVER_MODE, "balanced") ?: "balanced"
        if (mode == "off" || !isRealPressure(level)) return 0
        val keepAwake = if (mode == "aggressive") MIN_AWAKE_TABS_AGGRESSIVE else MIN_AWAKE_TABS_BALANCED
        val store = PetalEngineStore.getStore(context)
        val awake = store.state.tabs.count {
            it.engineState.engineSession != null && !PetalEngineStore.isEphemeralTabId(it.id)
        }
        val toSuspend = candidates(context, protectPinnedAndMedia(context))
            .sortedBy { it.lastAccess }
            .take((awake - keepAwake).coerceAtLeast(0))
        toSuspend.forEach { store.dispatch(EngineAction.SuspendEngineSessionAction(it.id)) }
        if (toSuspend.isNotEmpty()) Log.i(TAG, "Memory pressure level=$level: suspended ${toSuspend.size} tab(s)")
        return toSuspend.size
    }

    /** UI_HIDDEN (20) means "app went to background", not memory pressure. */
    @JvmStatic
    fun isRealPressure(level: Int): Boolean =
        level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW && level != ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
}
