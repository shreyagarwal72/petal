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
    private val context: Context,
    @Volatile private var activitySupplier: () -> android.app.Activity?
) : GeckoSession.PermissionDelegate, PetalOwnedDelegate {

    /** Update the live-context supplier when the host Activity is recreated. */
    fun rebind(newActivitySupplier: () -> android.app.Activity?) {
        this.activitySupplier = newActivitySupplier
    }

    // Delegate to a fresh PetalGeckoPermissionDelegate on each call so the inner
    // object always reads the current activitySupplier. Construction is cheap
    // (no init work beyond reading SharedPreferences lazily).
    private fun inner() = com.petal.browser.ui.components.PetalGeckoPermissionDelegate(
        context, activitySupplier
    )

    override fun onAndroidPermissionsRequest(
        session: GeckoSession,
        permissions: Array<out String>?,
        callback: GeckoSession.PermissionDelegate.Callback
    ) = inner().onAndroidPermissionsRequest(session, permissions, callback)

    override fun onMediaPermissionRequest(
        session: GeckoSession,
        uri: String,
        video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
        audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
        callback: GeckoSession.PermissionDelegate.MediaCallback
    ) = inner().onMediaPermissionRequest(session, uri, video, audio, callback)

    override fun onContentPermissionRequest(
        session: GeckoSession,
        perm: GeckoSession.PermissionDelegate.ContentPermission
    ): GeckoResult<Int>? = inner().onContentPermissionRequest(session, perm)
}
