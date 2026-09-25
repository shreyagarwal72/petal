package com.petal.browser.browser

import android.content.Context
import com.petal.browser.engine.gecko.PetalEngineStore
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.GeckoEngineView

/**
 * PetalTabManager
 * ─────────────────────────────────────────────────────────────────────────
 * Manages tabs using Mozilla Android Components [BrowserStore].
 *
 * Provides thread-safe actions for adding, removing, selecting, and querying
 * tab states from the unified BrowserStore.
 */
class PetalTabManager(
    private val context: Context,
    val store: BrowserStore = PetalEngineStore.getStore(context)
) {

    fun addTab(
        url: String,
        title: String = "",
        selectImmediately: Boolean = true,
        isPrivate: Boolean = false
    ): Pair<TabSessionState, EngineSession> {
        val tabId = "tab_${System.currentTimeMillis()}_${Math.abs(url.hashCode())}"
        return PetalEngineStore.createTabSession(
            context = context,
            tabId = tabId,
            url = url,
            title = title,
            isIncognito = isPrivate,
            select = selectImmediately
        )
    }

    fun removeTab(tabId: String) {
        PetalEngineStore.removeTab(context, tabId)
    }

    fun selectTab(tabId: String) {
        PetalEngineStore.selectTab(context, tabId)
    }

    fun allTabs(): List<TabSessionState> {
        return store.state.tabs
    }

    fun selectedTab(): TabSessionState? {
        return store.state.selectedTab
    }

    fun tabCount(): Int {
        return store.state.tabs.size
    }
}
