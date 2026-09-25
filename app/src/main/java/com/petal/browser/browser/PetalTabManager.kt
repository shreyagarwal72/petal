package com.petal.browser.browser

import android.content.Context
import com.petal.browser.engine.gecko.PetalEngineStore
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.ContentState
import mozilla.components.browser.state.state.EngineState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.store.BrowserStore
class PetalTabManager(private val context: Context) {

    private val store: BrowserStore = PetalEngineStore.getStore(context)

    /**
     * Creates and adds a new tab to [BrowserStore].
     */
    fun addTab(
        url: String = "about:blank",
        title: String = "",
        isIncognito: Boolean = false,
        select: Boolean = true
    ): TabSessionState {
        val eng = PetalEngineStore.getEngine(context)
        val session = eng.createSession(private = isIncognito)
        val tabId = "tab_${System.currentTimeMillis()}_${Math.abs(url.hashCode())}"

        val tabState = TabSessionState(
            id = tabId,
            content = ContentState(
                url = url,
                private = isIncognito,
                title = title
            ),
            engineState = EngineState(
                engineSession = session
            )
        )

        store.dispatch(TabListAction.AddTabAction(tabState, select = select))
        if (url.isNotBlank() && !url.equals("about:blank", ignoreCase = true)) {
            session.loadUrl(url)
        }
        return tabState
    }

    /**
     * Removes an existing tab by its unique tab ID.
     */
    fun removeTab(tabId: String) {
        PetalEngineStore.removeTab(context, tabId)
    }

    /**
     * Selects an active tab by its tab ID.
     */
    fun selectTab(tabId: String) {
        PetalEngineStore.selectTab(context, tabId)
    }

    /**
     * Returns the currently selected tab session state, if any.
     */
    fun getSelectedTab(): TabSessionState? {
        return store.state.tabs.firstOrNull { it.id == store.state.selectedTabId }
    }

    /**
     * Returns the list of all currently active tabs.
     */
    fun getAllTabs(): List<TabSessionState> {
        return store.state.tabs
    }

    /**
     * Returns the list of non-incognito tabs.
     */
    fun getNormalTabs(): List<TabSessionState> {
        return store.state.tabs.filter { !it.content.private }
    }

    /**
     * Returns the list of private/incognito tabs.
     */
    fun getPrivateTabs(): List<TabSessionState> {
        return store.state.tabs.filter { it.content.private }
    }
}
