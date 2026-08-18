package com.petal.browser.flags

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import android.webkit.WebSettings
import androidx.preference.PreferenceManager
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * Model representing an experimental Chrome Flag toggle.
 */
data class ChromeFlag(
    val key: String,
    val title: String,
    val description: String,
    val category: FlagCategory,
    val defaultValue: FlagState = FlagState.DEFAULT
)

enum class FlagState {
    DEFAULT,
    ENABLED,
    DISABLED
}

enum class FlagCategory(val label: String) {
    GRAPHICS("Graphics & Acceleration"),
    PERFORMANCE("Performance & Memory"),
    PRIVACY("Privacy & Security"),
    EXPERIMENTAL("Experimental Web APIs"),
    UI("UI & Rendering")
}

/**
 * Central manager for storing, querying, and applying Chrome Flags.
 */
object ChromeFlagsManager {
    private const val TAG = "ChromeFlagsManager"
    private const val PREF_PREFIX = "chrome_flag_"

    val ALL_FLAGS = listOf(
        // Graphics & Acceleration
        ChromeFlag(
            key = "enable-force-dark",
            title = "Auto Dark Mode for Web Contents",
            description = "Automatically renders all web content using modern Material algorithmic darkening.",
            category = FlagCategory.UI
        ),
        ChromeFlag(
            key = "enable-webgpu-developer-features",
            title = "Experimental WebGPU & WebGL2 Hardware Acceleration",
            description = "Enables high-performance WebGPU rendering pipeline and 3D graphics hardware acceleration.",
            category = FlagCategory.GRAPHICS
        ),
        ChromeFlag(
            key = "smooth-scrolling",
            title = "Hardware Accelerated Smooth Scrolling",
            description = "Applies sub-pixel touch physics and cubic-bezier interpolation for fluid web scrolling.",
            category = FlagCategory.GRAPHICS
        ),
        ChromeFlag(
            key = "enable-gpu-rasterization",
            title = "GPU Rasterization",
            description = "Uses GPU hardware to rasterize web content layers for high frame rate rendering.",
            category = FlagCategory.GRAPHICS
        ),
        ChromeFlag(
            key = "enable-zero-copy",
            title = "Zero-Copy Video & Canvas Rasterizer",
            description = "Writes rasterized tiles directly to GPU memory to reduce RAM memory copies.",
            category = FlagCategory.GRAPHICS
        ),

        // Performance & Memory
        ChromeFlag(
            key = "offscreen-pre-raster",
            title = "Offscreen Layer Pre-Rasterization",
            description = "Pre-rasters offscreen compositor tiles before scrolling for 120Hz smooth fling rendering.",
            category = FlagCategory.PERFORMANCE
        ),
        ChromeFlag(
            key = "media-background-playback",
            title = "Media Background Playback & Picture-in-Picture",
            description = "Allows YouTube and HTML5 video/audio playback when the tab or app is minimized.",
            category = FlagCategory.PERFORMANCE
        ),
        ChromeFlag(
            key = "quic-protocol",
            title = "Experimental HTTP/3 & QUIC Network Protocol",
            description = "Enables Google QUIC / HTTP/3 zero-RTT session handshake protocol for faster page loads.",
            category = FlagCategory.PERFORMANCE
        ),
        ChromeFlag(
            key = "enable-parallel-downloading",
            title = "Parallel Multi-Threaded Downloading",
            description = "Accelerates file download speeds by splitting files into multiple chunk requests.",
            category = FlagCategory.PERFORMANCE
        ),
        ChromeFlag(
            key = "back-forward-cache",
            title = "Instant Back/Forward Navigation Cache (bfcache)",
            description = "Caches previous web pages in memory for zero-delay instant back and forward navigation.",
            category = FlagCategory.PERFORMANCE
        ),
        ChromeFlag(
            key = "enable-dns-over-https",
            title = "DNS-over-HTTPS (DoH) Resolver",
            description = "Encrypts DNS domain name lookups via secure HTTPS queries.",
            category = FlagCategory.PERFORMANCE
        ),

        // Privacy & Security
        ChromeFlag(
            key = "safe-browsing-real-time",
            title = "Real-Time Google Safe Browsing Protection",
            description = "Checks URLs against Google Safe Browsing API in real-time to prevent phishing and malware.",
            category = FlagCategory.PRIVACY
        ),
        ChromeFlag(
            key = "fingerprint-defender",
            title = "Canvas & WebGL Anti-Fingerprinting Defender",
            description = "Injects anti-tracking noise into Canvas, WebGL, and AudioContext APIs.",
            category = FlagCategory.PRIVACY
        ),
        ChromeFlag(
            key = "enable-third-party-cookie-blocking",
            title = "Block Cross-Site Third-Party Tracking Cookies",
            description = "Blocks cross-domain tracking cookies to protect user privacy across sites.",
            category = FlagCategory.PRIVACY
        ),
        ChromeFlag(
            key = "https-first-mode",
            title = "HTTPS-First Upgrade Mode",
            description = "Upgrades all HTTP website requests to secure HTTPS and warns before unencrypted connections.",
            category = FlagCategory.PRIVACY
        ),

        // Experimental Web APIs
        ChromeFlag(
            key = "enable-javascript-harmony",
            title = "Experimental JavaScript Harmony & WebAssembly Features",
            description = "Enables bleeding-edge ECMAScript language extensions, SIMD, and WebAssembly thread pooling.",
            category = FlagCategory.EXPERIMENTAL
        ),
        ChromeFlag(
            key = "enable-web-bluetooth",
            title = "Web Bluetooth & Device Hardware API",
            description = "Allows websites to communicate securely with nearby Bluetooth Low Energy devices.",
            category = FlagCategory.EXPERIMENTAL
        ),
        ChromeFlag(
            key = "enable-web-usb",
            title = "WebUSB Hardware Peripheral API",
            description = "Allows web applications to connect to external USB hardware peripherals.",
            category = FlagCategory.EXPERIMENTAL
        ),
        ChromeFlag(
            key = "enable-web-share",
            title = "Web Share Target API Level 2",
            description = "Allows websites to send and receive shared text, links, and media files via native share sheet.",
            category = FlagCategory.EXPERIMENTAL
        ),

        // UI & Rendering
        ChromeFlag(
            key = "viewport-fit-cover",
            title = "Edge-to-Edge Notch Viewport Fitting",
            description = "Extends web viewports under status bar and display cutout notches seamlessly.",
            category = FlagCategory.UI
        ),
        ChromeFlag(
            key = "overlay-scrollbars",
            title = "Overlay & Minimalist Dynamic Scrollbars",
            description = "Replaces thick scrollbars with translucent floating overlay scrollbar pills.",
            category = FlagCategory.UI
        ),
        ChromeFlag(
            key = "enable-desktop-mode-zoom",
            title = "Automatic Desktop Mode Page Auto-Fit",
            description = "Automatically scales desktop website viewports to fit mobile screens perfectly.",
            category = FlagCategory.UI
        )
    )

    fun getFlagState(context: Context, key: String): FlagState {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val raw = sp.getString(PREF_PREFIX + key, FlagState.DEFAULT.name) ?: FlagState.DEFAULT.name
        return try {
            FlagState.valueOf(raw)
        } catch (e: Exception) {
            FlagState.DEFAULT
        }
    }

    fun setFlagState(context: Context, key: String, state: FlagState) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putString(PREF_PREFIX + key, state.name).apply()
    }

    fun resetAllFlags(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = sp.edit()
        ALL_FLAGS.forEach { flag ->
            editor.remove(PREF_PREFIX + flag.key)
        }
        editor.apply()
    }

    fun isFlagEnabled(context: Context, key: String): Boolean {
        val state = getFlagState(context, key)
        return when (state) {
            FlagState.ENABLED -> true
            FlagState.DISABLED -> false
            FlagState.DEFAULT -> false
        }
    }

    /**
     * Applies enabled Chrome Flags directly to a WebView's WebSettings instance.
     */
    @JvmStatic
    fun applyFlagsToWebSettings(context: Context, webSettings: WebSettings) {
        try {
            // Force Dark Flag
            val forceDarkState = getFlagState(context, "enable-force-dark")
            if (forceDarkState == FlagState.ENABLED) {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(webSettings, true)
                } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                    @Suppress("DEPRECATION")
                    WebSettingsCompat.setForceDark(webSettings, WebSettingsCompat.FORCE_DARK_ON)
                }
            } else if (forceDarkState == FlagState.DISABLED) {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(webSettings, false)
                } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                    @Suppress("DEPRECATION")
                    WebSettingsCompat.setForceDark(webSettings, WebSettingsCompat.FORCE_DARK_OFF)
                }
            }

            // Offscreen Pre-Raster Flag
            val offscreenState = getFlagState(context, "offscreen-pre-raster")
            if (offscreenState == FlagState.ENABLED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                webSettings.setOffscreenPreRaster(true)
            } else if (offscreenState == FlagState.DISABLED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                webSettings.setOffscreenPreRaster(false)
            }

            // Safe Browsing Flag
            val safeBrowsingState = getFlagState(context, "safe-browsing-real-time")
            if (safeBrowsingState == FlagState.ENABLED) {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
                    WebSettingsCompat.setSafeBrowsingEnabled(webSettings, true)
                }
            } else if (safeBrowsingState == FlagState.DISABLED) {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
                    WebSettingsCompat.setSafeBrowsingEnabled(webSettings, false)
                }
            }

            // Media Background Playback Flag
            val mediaState = getFlagState(context, "media-background-playback")
            if (mediaState == FlagState.ENABLED) {
                webSettings.mediaPlaybackRequiresUserGesture = false
            } else if (mediaState == FlagState.DISABLED) {
                webSettings.mediaPlaybackRequiresUserGesture = true
            }

            // JavaScript & WebAssembly Harmony Flag
            val jsState = getFlagState(context, "enable-javascript-harmony")
            if (jsState == FlagState.ENABLED) {
                webSettings.javaScriptEnabled = true
                webSettings.domStorageEnabled = true
                webSettings.databaseEnabled = true
            }

            Log.d(TAG, "Chrome Flags applied to WebSettings successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying Chrome Flags to WebSettings", e)
        }
    }

    /** Checks if a given query URL is a request to open Chrome Flags */
    @JvmStatic
    fun isFlagsUrl(url: String?): Boolean {
        if (url == null) return false
        val clean = url.trim().lowercase()
        return clean == "chrome://flags" || clean == "chrome://flags/" ||
               clean == "about:flags" || clean == "about:flags/" ||
               clean == "petal://flags" || clean == "petal://flags/"
    }
}
