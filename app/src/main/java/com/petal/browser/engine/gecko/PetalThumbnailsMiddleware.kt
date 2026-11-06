package com.petal.browser.engine.gecko

import com.petal.browser.unit.TabThumbnailCache
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store

/**
 * Keeps tab thumbnails exactly as long as their tab exists. Port of Mozilla's
 * `browser-thumbnails` ThumbnailsMiddleware: the BrowserStore decides when a tab is gone, so a
 * thumbnail is deleted when its tab is REMOVED from the store and at no other time. Destroying a
 * view (Activity recreation, memory-saver suspension, app exit) never touches thumbnails.
 */
class PetalThumbnailsMiddleware : Middleware<BrowserState, BrowserAction> {

    override fun invoke(
        store: Store<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction
    ) {
        // Runs BEFORE the reducer so the tab (and its private flag) is still in the state.
        when (action) {
            is TabListAction.RemoveTabAction -> forget(store.state, listOf(action.tabId))
            is TabListAction.RemoveTabsAction -> forget(store.state, action.tabIds)
            is TabListAction.RemoveAllNormalTabsAction ->
                forget(store.state, store.state.tabs.filterNot { it.content.private }.map { it.id })
            is TabListAction.RemoveAllPrivateTabsAction ->
                forget(store.state, store.state.tabs.filter { it.content.private }.map { it.id })
            is TabListAction.RemoveAllTabsAction -> TabThumbnailCache.evictAll()
            else -> Unit
        }
        next(action)
    }

    private fun forget(state: BrowserState, tabIds: List<String>) {
        for (id in tabIds) {
            val isPrivate = state.tabs.any { it.id == id && it.content.private }
            if (isPrivate) TabThumbnailCache.removePrivate(id) else TabThumbnailCache.remove(id)
        }
    }
}
