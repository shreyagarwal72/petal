package com.petal.browser.browser

import android.content.Context
import com.petal.browser.engine.gecko.PetalEngineStore
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.ContentState
import mozilla.components.browser.state.state.EngineState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.store.BrowserStore
import java.util.UUID

class PetalTabManager(context: Context) {

    // Hold the application context so a long-lived manager never leaks an Activity.
    private val context: Context = context.applicationContext

    private val store: BrowserStore = PetalEngineStore.getStore(this.context)

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
        // UUID: millis + url hash collided for same-URL tabs opened in the same millisecond,
        // and Math.abs(Int.MIN_VALUE) is negative.
        val tabId = "tab_${UUID.randomUUID()}"

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
        // Close the engine session explicitly; RemoveTabAction alone leaves the native
        // Gecko session alive when it was created outside the engine-session middleware.
        val engineSession = store.state.tabs.firstOrNull { it.id == tabId }?.engineState?.engineSession
        PetalEngineStore.removeTab(context, tabId)
        try { engineSession?.close() } catch (_: Throwable) {}
    }

    /**
     * Selects an active tab by its tab ID.
     */
    fun selectTab(tabId: String) {
        // Selecting an unknown ID would leave selectedTabId dangling.
        if (store.state.tabs.none { it.id == tabId }) return
        PetalEngineStore.selectTab(context, tabId)
    }

    /**
     * Returns the currently selected tab session state, if any.
     */
    fun getSelectedTab(): TabSessionState? {
        val state = store.state
        return state.tabs.firstOrNull { it.id == state.selectedTabId }
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
