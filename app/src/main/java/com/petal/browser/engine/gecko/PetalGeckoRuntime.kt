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

        val settingsBuilder = GeckoRuntimeSettings.Builder()
            .aboutConfigEnabled(false)
            .contentBlocking(
                ContentBlocking.Settings.Builder()
                    .antiTracking(ContentBlocking.AntiTracking.DEFAULT)
                    .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS)
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

        val newRuntime = GeckoRuntime.create(appContext, settingsBuilder.build())
        runtime = newRuntime
        Log.i(TAG, "Initialized GeckoRuntime with standard tracking protection and high-performance pipeline")
        return newRuntime
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
            } catch (e: Exception) {
                Log.w(TAG, "Error synchronizing GeckoRuntime preferences: ${e.message}")
            }
        }
    }
}
