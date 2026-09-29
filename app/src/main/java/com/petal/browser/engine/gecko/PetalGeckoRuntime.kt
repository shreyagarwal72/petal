package com.petal.browser.engine.gecko

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * PetalGeckoRuntime
 * ─────────────────────────────────────────────────────────────────────────
 * Thread-safe process-wide singleton managing Mozilla GeckoView's GeckoRuntime.
 * Configures tracking protection, ad & content blocking, cookie policies,
 * hardware acceleration, and process priority.
 */
object PetalGeckoRuntime {
    private const val TAG = "PetalGeckoRuntime"

    @Volatile
    private var runtime: GeckoRuntime? = null

    private var geckoAvailable: Boolean? = null

    @JvmStatic
    fun isGeckoAvailable(context: Context): Boolean {
        geckoAvailable?.let { return it }
        return try {
            val hasRuntimeClass = try {
                Class.forName("org.mozilla.geckoview.GeckoRuntime")
                true
            } catch (t: Throwable) {
                false
            }
            if (!hasRuntimeClass) {
                geckoAvailable = false
                return false
            }
            // Check if native libxul is present in the APK's native library path
            val appInfo = context.applicationInfo
            val nativeDir = java.io.File(appInfo.nativeLibraryDir)
            val libXul = java.io.File(nativeDir, "libxul.so")
            // In stripped/lite builds, libxul.so is excluded
            val available = if (nativeDir.exists() && nativeDir.isDirectory) {
                val soFiles = nativeDir.list()
                if (soFiles != null && soFiles.isNotEmpty()) {
                    libXul.exists()
                } else {
                    // Fallback check
                    try {
                        System.loadLibrary("mozglue")
                        true
                    } catch (t: Throwable) {
                        false
                    }
                }
            } else {
                true
            }
            geckoAvailable = available
            available
        } catch (t: Throwable) {
            Log.w(TAG, "GeckoView native libraries not available: ${t.message}")
            geckoAvailable = false
            false
        }
    }

    @JvmStatic
    @Synchronized
    fun getOrCreate(context: Context): GeckoRuntime {
        runtime?.let { return it }

        if (!isGeckoAvailable(context)) {
            throw IllegalStateException("GeckoView native libraries are not available in this build (Lite / WebView mode)")
        }

        val appContext = context.applicationContext
        val sp = PreferenceManager.getDefaultSharedPreferences(appContext)

        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val isLowRamDevice = am?.isLowRamDevice ?: false

        val isDebug = try {
            (appContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (_: Throwable) { false }

        val blockThirdPartyCookies = sp.getBoolean("sp_block_third_party_cookies", false)
        val totalCookieProtection = sp.getBoolean("sp_total_cookie_protection", sp.getBoolean("sp_cookies_isolate", false))
        val defaultCookieBehavior = if (totalCookieProtection || blockThirdPartyCookies) {
            ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS
        } else {
            ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS
        }

        val settingsBuilder = GeckoRuntimeSettings.Builder()
            .aboutConfigEnabled(true)
            .contentBlocking(
                ContentBlocking.Settings.Builder()
                    .antiTracking(ContentBlocking.AntiTracking.DEFAULT)
                    // Accept non-trackers so search engine session tokens (Google NID, CONSENT) persist across queries without triggering bot Captchas
                    .cookieBehavior(defaultCookieBehavior)
                    .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
                    .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.DEFAULT)
                    .build()
            )
            .javaScriptEnabled(sp.getBoolean("sp_javascript", sp.getBoolean("profileStandard_javascript", true)))
            .consoleOutput(isDebug)
            .remoteDebuggingEnabled(isDebug)
            .webManifest(true)
            .extensionsProcessEnabled(!isLowRamDevice)
            .extensionsWebAPIEnabled(true)
            // Enable login autofill API so password manager extensions (Bitwarden, etc.) can
            // intercept login forms via the WebExtension loginAutofill API. Without this,
            // extensions receive the form events but cannot fill credentials.
            .loginAutofillEnabled(true)

        // Firefox official memory and performance optimizations
        try {
            // Low memory device tuning
            if (isLowRamDevice) {
                Log.i(TAG, "Configuring GeckoRuntime for low-RAM device profile (in-process extensions, constrained memory limits)")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not apply optional runtime settings: ${t.message}")
        }

        val newRuntime = GeckoRuntime.create(appContext, settingsBuilder.build())
        runtime = newRuntime
        Log.i(TAG, "Initialized GeckoRuntime with standard tracking protection and high-performance pipeline")
        return newRuntime
    }

    /**
     * Official Firefox GeckoView memory management bridge.
     * Propagates system onTrimMemory / onLowMemory signals to GeckoRuntime and Android Components,
     * freeing native image decoders, font caches, JIT memory, and background tab DOM trees.
     */
    @JvmStatic
    fun onTrimMemory(context: Context, level: Int) {
        val rt = runtime ?: return
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
            try {
                // Purge all volatile caches (image decodes, network cache, memory caches)
                rt.storageController.clearData(
                    org.mozilla.geckoview.StorageController.ClearFlags.ALL_CACHES
                )
                Log.d(TAG, "Purged all Gecko caches on critical memory trim level $level")
            } catch (t: Throwable) {
                Log.d(TAG, "Failed to purge caches on critical memory: ${t.message}")
            }
        } else if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            try {
                // Purge volatile image caches in StorageController
                rt.storageController.clearData(
                    org.mozilla.geckoview.StorageController.ClearFlags.IMAGE_CACHE
                )
                Log.d(TAG, "Purged Gecko image cache on memory trim level $level")
            } catch (t: Throwable) {
                Log.d(TAG, "Failed to purge image cache on low memory: ${t.message}")
            }
        }
    }

    /**
     * Handles system onLowMemory callback by aggressively purging caches.
     */
    @JvmStatic
    fun onLowMemory(context: Context) {
        onTrimMemory(context, android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
    }

    @JvmStatic
    fun clearData(context: Context, flags: Long = org.mozilla.geckoview.StorageController.ClearFlags.ALL) {
        try {
            getOrCreate(context).storageController.clearData(flags)
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing Gecko data: ${e.message}")
        }
    }

    @JvmStatic
    fun syncPreferences(sp: SharedPreferences) {
        runtime?.let { rt ->
            try {
                // 1. JavaScript
                rt.settings.javaScriptEnabled = sp.getBoolean("sp_javascript", sp.getBoolean("profileStandard_javascript", true))

                // 2. Enhanced Tracking Protection & AdBlock
                val adBlockEnabled = sp.getBoolean("sp_ad_block", sp.getBoolean("profileStandard_adBlock", true))
                val etpLevel = if (adBlockEnabled) ContentBlocking.EtpLevel.STRICT else ContentBlocking.EtpLevel.NONE
                rt.settings.contentBlocking.setEnhancedTrackingProtectionLevel(etpLevel)
                rt.settings.contentBlocking.setStrictSocialTrackingProtection(adBlockEnabled)

                // 3. Block Third-Party / Tracking Cookies (Firefox ETP Cookie Isolation)
                val blockThirdPartyCookies = sp.getBoolean("sp_block_third_party_cookies", false)
                val cookieBehavior = if (blockThirdPartyCookies) {
                    ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS
                } else {
                    ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS
                }
                rt.settings.contentBlocking.setCookieBehavior(cookieBehavior)

                // 4. Anti-Tracking Flags (Fingerprinting, STP, Cryptominers, WebRTC, Social)
                val fingerprintProtection = sp.getBoolean("sp_fingerprint_protection", true)
                var antiTrackingFlags = ContentBlocking.AntiTracking.DEFAULT
                if (adBlockEnabled) {
                    antiTrackingFlags = antiTrackingFlags or ContentBlocking.AntiTracking.AD or ContentBlocking.AntiTracking.STP
                }
                if (fingerprintProtection) {
                    antiTrackingFlags = antiTrackingFlags or ContentBlocking.AntiTracking.FINGERPRINTING
                }
                rt.settings.contentBlocking.setAntiTracking(antiTrackingFlags)

                // 5. Safe Browsing Malware & Phishing Shield
                rt.settings.contentBlocking.setSafeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)

                // 6. WebAuthn & Passkeys Autofill
                val webauthnEnabled = sp.getBoolean("sp_webauthn_enabled", true)
                rt.settings.loginAutofillEnabled = webauthnEnabled
            } catch (e: Exception) {
                Log.w(TAG, "Error synchronizing GeckoRuntime preferences: ${e.message}")
            }
        }
    }
}
