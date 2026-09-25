package com.petal.browser.browser

import android.content.Context
import android.util.Log
import com.petal.browser.engine.gecko.PetalEngineStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.browser.session.storage.SessionStorage
import mozilla.components.browser.state.store.BrowserStore

/**
 * PetalSessionRestoreManager
 * ─────────────────────────────────────────────────────────────────────────
 * Atomic Session Storage persistence and restore system powered by Mozilla Android
 * Components [SessionStorage].
 *
 * Persists tab states, back/forward history stacks, and scroll states across
 * app kills and restarts while guaranteeing privacy (incognito tabs excluded).
 */
object PetalSessionRestoreManager {

    private const val TAG = "PetalSessionRestore"

    /**
     * Asynchronously snapshots and saves non-incognito tabs to disk.
     */
    fun save(store: BrowserStore, context: Context) {
        try {
            val storage = PetalEngineStore.getSessionStorage(context)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    storage.save(store.state)
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to persist tab session snapshot: ${t.message}")
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not access SessionStorage for save: ${t.message}")
        }
    }

    /**
     * Restores persisted tab session state into [BrowserStore].
     * Returns true if at least one tab session was restored.
     */
    suspend fun restore(store: BrowserStore, context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val storage = PetalEngineStore.getSessionStorage(context)
                val restoredState = storage.restore() ?: return@withContext false
                withContext(Dispatchers.Main) {
                    store.dispatch(
                        mozilla.components.browser.state.action.TabListAction.RestoreAction(
                            tabs = restoredState.tabs,
                            restoreLocation = mozilla.components.browser.state.action.TabListAction.RestoreAction.RestoreLocation.END
                        )
                    )
                }
                true
            } catch (t: Throwable) {
                Log.w(TAG, "Session restore encountered error: ${t.message}")
                false
            }
        }
    }
}
