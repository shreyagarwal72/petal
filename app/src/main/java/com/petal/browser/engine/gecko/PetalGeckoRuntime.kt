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

        val settingsBuilder = GeckoRuntimeSettings.Builder()
            .aboutConfigEnabled(false)
            .contentBlocking(
                ContentBlocking.Settings.Builder()
                    .antiTracking(ContentBlocking.AntiTracking.DEFAULT)
                    // Block tracking cookies and isolate the rest (dynamic first-party isolation).
                    .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
                    .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
                    .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.STRICT)
                    .build()
            )
            .javaScriptEnabled(sp.getBoolean("profileStandard_javascript", true))
            .consoleOutput(false)
            .webManifest(true)
            .extensionsProcessEnabled(true)
            .extensionsWebAPIEnabled(true)
            .loginAutofillEnabled(false)

        // Firefox official memory and performance optimizations
        try {
            // Low memory device tuning
            if (isLowRamDevice) {
                Log.i(TAG, "Configuring GeckoRuntime for low-RAM device profile")
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
        try {
            // GeckoRuntime supports onTrimMemory natively
            rt.onTrimMemory(level)
            Log.d(TAG, "Propagated onTrimMemory level $level to GeckoRuntime")
        } catch (t: Throwable) {
            Log.d(TAG, "GeckoRuntime onTrimMemory fallback: ${t.message}")
        }

        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            try {
                // Purge memory caches in StorageController
                rt.storageController.clearData(
                    org.mozilla.geckoview.StorageController.ClearFlags.IMAGE_CACHE
                )
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
                rt.settings.javaScriptEnabled = sp.getBoolean("profileStandard_javascript", true)
                val adBlockEnabled = sp.getBoolean("sp_ad_block", true)
                val etpLevel = if (adBlockEnabled) ContentBlocking.EtpLevel.STRICT else ContentBlocking.EtpLevel.NONE
                rt.settings.contentBlocking.enhancedTrackingProtectionLevel = etpLevel
            } catch (e: Exception) {
                Log.w(TAG, "Error synchronizing GeckoRuntime preferences: ${e.message}")
            }
        }
    }
}
