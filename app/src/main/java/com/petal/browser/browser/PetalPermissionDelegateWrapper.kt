package com.petal.browser.browser

import android.content.Context
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

/**
 * PetalPermissionDelegateWrapper
 * ──────────────────────────────────────────────────────────────────────────────
 * Thin re-bindable wrapper around [com.petal.browser.ui.components.PetalGeckoPermissionDelegate].
 *
 * Like [PetalPromptDelegateWrapper], GeckoEngineSession (android-components) does NOT install
 * a permissionDelegate — the slot is null until the app fills it. Petal takes sole ownership.
 *
 * The only correctness requirement is that [activitySupplier] always returns the CURRENT
 * Activity after a configuration change. [rebind] updates that lambda without touching
 * the GeckoSession, preventing stale-context leaks.
 *
 * Marked [PetalOwnedDelegate] so bindTab detects existing instances and calls [rebind]
 * instead of constructing a new delegate.
 */
internal class PetalPermissionDelegateWrapper(
    private val engine: GeckoSession.PermissionDelegate? = null,
    private val context: Context,
    @Volatile private var activitySupplier: () -> android.app.Activity?
) : GeckoSession.PermissionDelegate, PetalOwnedDelegate {

    /** Update the live-context supplier when the host Activity is recreated. */
    fun rebind(newActivitySupplier: () -> android.app.Activity?) {
        this.activitySupplier = newActivitySupplier
    }

    private fun inner() = com.petal.browser.ui.components.PetalGeckoPermissionDelegate(
        context, activitySupplier
    )

    override fun onAndroidPermissionsRequest(
        session: GeckoSession,
        permissions: Array<out String>?,
        callback: GeckoSession.PermissionDelegate.Callback
    ) {
        val act = activitySupplier()
        if (act != null) {
            inner().onAndroidPermissionsRequest(session, permissions, callback)
        } else if (engine != null) {
            engine.onAndroidPermissionsRequest(session, permissions, callback)
        } else {
            callback.reject()
        }
    }

    override fun onMediaPermissionRequest(
        session: GeckoSession,
        uri: String,
        video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
        audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
        callback: GeckoSession.PermissionDelegate.MediaCallback
    ) {
        val act = activitySupplier()
        if (act != null) {
            inner().onMediaPermissionRequest(session, uri, video, audio, callback)
        } else if (engine != null) {
            engine.onMediaPermissionRequest(session, uri, video, audio, callback)
        } else {
            callback.reject()
        }
    }

    override fun onContentPermissionRequest(
        session: GeckoSession,
        perm: GeckoSession.PermissionDelegate.ContentPermission
    ): GeckoResult<Int>? {
        val act = activitySupplier()
        if (act != null) {
            return inner().onContentPermissionRequest(session, perm)
        }
        return engine?.onContentPermissionRequest(session, perm)
            ?: GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
    }
}
