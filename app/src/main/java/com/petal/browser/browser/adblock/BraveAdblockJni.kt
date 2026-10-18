package com.petal.browser.browser.adblock

import android.util.Log

/**
 * BraveAdblockJni
 * Low-level JNI interface bridging Petal Browser directly to Brave's `adblock-rust` engine.
 * Supports zero-copy matching, cosmetic stylesheet retrieval, and graceful fallback.
 */
object BraveAdblockJni {

    private const val TAG = "BraveAdblockJni"
    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("adblock_petal")
            isNativeLoaded = true
            Log.d(TAG, "Successfully loaded native libadblock_petal.so")
        } catch (t: Throwable) {
            isNativeLoaded = false
            Log.w(TAG, "libadblock_petal.so not available, using high-speed fallback engine: ${t.message}")
        }
    }

    fun isAvailable(): Boolean = isNativeLoaded

    external fun nativeCreateEngine(rules: Array<String>): Long
    external fun nativeMatches(enginePtr: Long, url: String, host: String, requestType: String): Boolean
    external fun nativeCosmeticCss(enginePtr: Long, url: String): String?
    external fun nativeDestroyEngine(enginePtr: Long)
}
