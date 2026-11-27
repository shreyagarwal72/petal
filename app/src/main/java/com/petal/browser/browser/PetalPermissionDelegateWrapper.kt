package com.petal.browser.browser

import android.content.Context
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

/**
 * PetalPermissionDelegateWrapper
 * ──────────────────────────────────────────────────────────────────────────────
 * Thin re-bindable wrapper around [com.petal.browser.engine.gecko.PetalSitePermissionsBridge].
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
    @Volatile private var activitySupplier: () -> android.app.Activity?,
    @Volatile private var isPrivateProvider: () -> Boolean = { false }
) : GeckoSession.PermissionDelegate, PetalOwnedDelegate {

    /** Update the live-context supplier when the host Activity is recreated. */
    fun rebind(
        newActivitySupplier: () -> android.app.Activity?,
        newIsPrivateProvider: () -> Boolean = isPrivateProvider
    ) {
        this.activitySupplier = newActivitySupplier
        this.isPrivateProvider = newIsPrivateProvider
    }

    override fun onAndroidPermissionsRequest(
        session: GeckoSession,
        permissions: Array<out String>?,
        callback: GeckoSession.PermissionDelegate.Callback
    ) {
        val act = activitySupplier()
        if (act != null) {
            com.petal.browser.engine.gecko.PetalSitePermissionsBridge.handleAndroidPermissionsRequest(
                activitySupplier = activitySupplier,
                permissions = permissions,
                callback = callback
            )
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
        try {
            val what = when {
                video != null && video.isNotEmpty() && audio != null && audio.isNotEmpty() -> "Camera & microphone"
                video != null && video.isNotEmpty() -> "Camera"
                audio != null && audio.isNotEmpty() -> "Microphone"
                else -> null
            }
            if (what != null) {
                val host = try { android.net.Uri.parse(uri).host ?: uri } catch (_: Throwable) { uri }
                com.petal.browser.live.PetalLiveActionBus.postSimple(com.petal.browser.live.LiveEventKind.PERMISSION_IN_USE, "perm_media", "$what requested", host.take(60))
            }
        } catch (_: Throwable) {}
        if (act != null) {
            com.petal.browser.engine.gecko.PetalSitePermissionsBridge.handleMediaPermissionRequest(
                context = context,
                activitySupplier = activitySupplier,
                isPrivate = isPrivateProvider(),
                uri = uri,
                video = video,
                audio = audio,
                callback = callback
            )
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
            return com.petal.browser.engine.gecko.PetalSitePermissionsBridge.handleContentPermissionRequest(
                activitySupplier = activitySupplier,
                perm = perm,
                context = context,
                isPrivate = isPrivateProvider()
            )
        }
        return engine?.onContentPermissionRequest(session, perm)
            ?: GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
    }
}
