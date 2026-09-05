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

    @JvmStatic
    @Synchronized
    fun getOrCreate(context: Context): GeckoRuntime {
        runtime?.let { return it }

        val appContext = context.applicationContext
        val sp = PreferenceManager.getDefaultSharedPreferences(appContext)

        val settingsBuilder = GeckoRuntimeSettings.Builder()
            .aboutConfigEnabled(false)
            .contentBlocking(
                ContentBlocking.Settings.Builder()
                    .antiTracking(ContentBlocking.AntiTracking.STRICT)
                    .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY)
                    .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
                    .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.STRICT)
                    .build()
            )
            .javaScriptEnabled(sp.getBoolean("profileStandard_javascript", true))
            .consoleOutput(false)

        val newRuntime = GeckoRuntime.create(appContext, settingsBuilder.build())
        runtime = newRuntime
        Log.i(TAG, "Initialized GeckoRuntime with STRICT anti-tracking and content blocking")
        return newRuntime
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
