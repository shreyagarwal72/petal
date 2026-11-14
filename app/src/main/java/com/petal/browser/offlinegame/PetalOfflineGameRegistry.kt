package com.petal.browser.offlinegame

import com.petal.browser.browser.PetalTabViewController
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Connects the engine's error-page interceptor to the tab view that renders the failing session.
 *
 * The interceptor is the one place that is guaranteed to run whenever Mozilla's error page is
 * about to be shown, so the offline game is triggered from there rather than from a wrapped
 * GeckoSession delegate. Both maps are weak, so sessions and views are never kept alive.
 */
object PetalOfflineGameRegistry {
    private val owners = WeakHashMap<Any, WeakReference<PetalTabViewController>>()

    @JvmStatic
    fun register(session: Any, owner: PetalTabViewController) {
        synchronized(owners) { owners[session] = WeakReference(owner) }
    }

    @JvmStatic
    fun notifyError(session: Any?, url: String?, errorName: String) {
        if (session == null) return
        val owner = synchronized(owners) { owners[session]?.get() }
        if (owner == null) {
            android.util.Log.d("PetalHop", "No tab view registered for session; $errorName ignored")
            return
        }
        owner.onEngineLoadError(url, errorName)
    }
}
