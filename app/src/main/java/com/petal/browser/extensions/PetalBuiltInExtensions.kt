package com.petal.browser.extensions

import android.util.Log
import mozilla.components.browser.engine.gecko.GeckoEngine
import mozilla.components.feature.webcompat.WebCompatFeature

/**
 * PetalBuiltInExtensions
 * ─────────────────────────────────────────────────────────────────────────
 * Installs Mozilla's bundled WebCompat extension on GeckoEngine creation.
 * The webcompat extension is bundled inside the GeckoView/Android Components AAR
 * and automatically applies site-specific shims for mobile compatibility breakages.
 */
object PetalBuiltInExtensions {
    private const val TAG = "PetalBuiltInExtensions"

    @Volatile
    private var isInstalled = false

    @JvmStatic
    fun installAll(engine: GeckoEngine) {
        if (isInstalled) {
            Log.d(TAG, "WebCompatFeature already installed; skipping duplicate call")
            return
        }
        isInstalled = true
        try {
            WebCompatFeature.install(engine)
            Log.i(TAG, "WebCompatFeature.install(engine) completed successfully for webcompat@mozilla.org")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to invoke WebCompatFeature.install(engine): ${t.message}", t)
        }
    }
}
