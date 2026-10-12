package com.petal.browser.engine.gecko

import android.content.Context
import android.util.Log
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.selector.findTab
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.concept.engine.EngineSession
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store

/**
 * PetalSessionPrioritizationMiddleware
 * ─────────────────────────────────────────────────────────────────────────
 * Firefox Android Components inspired CPU & Process priority arbitration middleware.
 *
 * Implements Mozilla's SessionPrioritization protocol:
 * - Dynamically elevates the foreground active [EngineSession] to [EngineSession.SessionPriority.HIGH].
 * - Demotes background tabs to [EngineSession.SessionPriority.DEFAULT], allowing GeckoView's
 *   scheduler to throttle background JS execution timers and APZ compositor cycles.
 * - Restores high priority for tabs with active form inputs or user interactions.
 */
class PetalSessionPrioritizationMiddleware : Middleware<BrowserState, BrowserAction> {
    companion object {
        private const val TAG = "PetalSessionPriority"
    }

    private var previousHighestPriorityTabId: String = ""

    override fun invoke(
        store: Store<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction
    ) {
        when (action) {
            is EngineAction.UnlinkEngineSessionAction -> {
                val activeTab = store.state.findTab(action.tabId)
                try {
                    activeTab?.engineState?.engineSession?.updateSessionPriority(EngineSession.SessionPriority.DEFAULT)
                } catch (_: Throwable) {}
                if (previousHighestPriorityTabId == action.tabId) {
                    previousHighestPriorityTabId = ""
                }
            }
            is ContentAction.UpdateHasFormDataAction -> {
                if (action.adjustPriority) {
                    val tab = store.state.findTab(action.tabId)
                    val priority = if (action.containsFormData) {
                        EngineSession.SessionPriority.HIGH
                    } else {
                        EngineSession.SessionPriority.DEFAULT
                    }
                    try {
                        tab?.engineState?.engineSession?.updateSessionPriority(priority)
                    } catch (_: Throwable) {}
                }
            }
            else -> {}
        }

        next(action)

        when (action) {
            is TabListAction.SelectTabAction,
            is TabListAction.AddTabAction,
            is EngineAction.LinkEngineSessionAction -> {
                val state = store.state
                val selectedId = state.selectedTabId
                if (!selectedId.isNullOrEmpty() && selectedId != previousHighestPriorityTabId) {
                    updatePriority(state, selectedId)
                }
            }
            else -> {}
        }
    }

    private fun updatePriority(state: BrowserState, selectedTabId: String) {
        val currentTab = state.findTab(selectedTabId)
        val currentSession = currentTab?.engineState?.engineSession

        if (currentSession != null) {
            // Previous tab drops to DEFAULT priority
            if (previousHighestPriorityTabId.isNotEmpty() && previousHighestPriorityTabId != selectedTabId) {
                val previousTab = state.findTab(previousHighestPriorityTabId)
                try {
                    previousTab?.engineState?.engineSession?.updateSessionPriority(EngineSession.SessionPriority.DEFAULT)
                } catch (_: Throwable) {}
            }

            // Current tab elevated to HIGH priority for snappy UI responsiveness
            try {
                currentSession.updateSessionPriority(EngineSession.SessionPriority.HIGH)
                previousHighestPriorityTabId = selectedTabId
                Log.d(TAG, "Elevated tab $selectedTabId to HIGH CPU priority; background tabs set to DEFAULT")
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to update session priority: ${t.message}")
            }
        }
    }
}
