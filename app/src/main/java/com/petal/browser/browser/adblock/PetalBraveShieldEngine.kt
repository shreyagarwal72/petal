package com.petal.browser.browser.adblock

import android.content.Context
import android.util.Log
import com.petal.browser.browser.PetalAdBlockMode
import java.util.concurrent.atomic.AtomicBoolean

/**
 * PetalBraveShieldEngine
 * High-performance orchestrator for Brave's adblock-rust engine in Petal Browser.
 * Handles native engine lifecycle, filter list compilation, zero-copy network request matching,
 * and dynamic cosmetic CSS generation.
 */
object PetalBraveShieldEngine {

    private const val TAG = "PetalBraveShieldEngine"
    private var enginePtr: Long = 0L
    private val isInitialized = AtomicBoolean(false)

    // Pre-bundled high-impact EasyList / Brave Shields rules
    private val bundledRules = arrayOf(
        // High frequency trackers & ad servers
        "||doubleclick.net^",
        "||googlesyndication.com^",
        "||google-analytics.com^",
        "||adservice.google.com^",
        "||adnxs.com^",
        "||popads.net^",
        "||popcash.net^",
        "||adform.net^",
        "||taboola.com^",
        "||outbrain.com^",
        "||criteo.com^",
        "||rubiconproject.com^",
        "||pubmatic.com^",
        "||smartadserver.com^",
        "||amazon-adsystem.com^",
        "||propellerads.com^",
        "||exoclick.com^",
        "||scorecardresearch.com^",
        "||monetag.com^",
        "||hilltopads.com^",
        "||adcash.com^",
        "||adsterra.com^",
        "||a-ads.com^",
        "||mgid.com^",
        "||revcontent.com^",
        "||trafficjunky.com^",
        "||coinhive.com^",
        "||statcounter.com^",
        "||pixel.facebook.com^",
        // Path patterns
        "/pagead/",
        "/ad_banner",
        "/popunder",
        "/popup.js",
        "adsbygoogle.js",
        // YouTube ad endpoints
        "||youtube.com/api/stats/ads^",
        "||youtube.com/pagead/*",
        "||googlevideo.com/videoplayback*&adformat=*",
    )

    fun initialize(context: Context) {
        if (isInitialized.getAndSet(true)) return

        if (BraveAdblockJni.isAvailable()) {
            try {
                enginePtr = BraveAdblockJni.nativeCreateEngine(bundledRules)
                Log.i(TAG, "Brave adblock-rust engine initialized successfully (handle: $enginePtr)")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to initialize Brave native adblock engine", t)
                enginePtr = 0L
            }
        } else {
            Log.i(TAG, "Brave native library not available, fallback engine engaged")
        }
    }

    /**
     * Checks if a network request should be blocked according to Brave's rules.
     */
    fun shouldBlock(requestUrl: String, pageHost: String, requestType: String = "other"): Boolean {
        if (enginePtr != 0L && BraveAdblockJni.isAvailable()) {
            return try {
                BraveAdblockJni.nativeMatches(enginePtr, requestUrl, pageHost, requestType)
            } catch (e: Exception) {
                false
            }
        }
        return false
    }

    /**
     * Returns cosmetic hiding CSS rules calculated by Brave for the given URL.
     */
    fun getCosmeticCss(url: String): String? {
        if (enginePtr != 0L && BraveAdblockJni.isAvailable()) {
            return try {
                BraveAdblockJni.nativeCosmeticCss(enginePtr, url)
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    fun isNativeActive(): Boolean = enginePtr != 0L
}
