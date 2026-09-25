package com.petal.browser.extensions

import android.util.Log
import mozilla.components.browser.engine.gecko.GeckoEngine

/**
 * PetalBuiltInExtensions
 * ─────────────────────────────────────────────────────────────────────────
 * Installs Mozilla's bundled WebCompat extension on GeckoEngine creation.
 * The webcompat extension is bundled inside the GeckoView/Android Components AAR
 * and automatically applies site-specific shims for mobile compatibility breakages.
 */
object PetalBuiltInExtensions {
    private const val TAG = "PetalBuiltInExtensions"
    private const val WEBCOMPAT_ID = "webcompat@mozilla.org"
    private const val WEBCOMPAT_URL = "resource://android/assets/extensions/webcompat/"

    @Volatile
    private var isInstalled = false

    @JvmStatic
    fun installAll(engine: GeckoEngine) {
        if (isInstalled) return
        try {
            engine.installBuiltInWebExtension(
                id = WEBCOMPAT_ID,
                url = WEBCOMPAT_URL,
                onSuccess = { ext ->
                    isInstalled = true
                    Log.i(TAG, "WebCompat extension loaded successfully: ${ext.id}")
                },
                onError = { e ->
                    Log.w(TAG, "WebCompat built-in install skipped or not present in AAR assets: $WEBCOMPAT_ID (${e.message})")
                }
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to invoke installBuiltInWebExtension: ${t.message}")
        }
    }
}
