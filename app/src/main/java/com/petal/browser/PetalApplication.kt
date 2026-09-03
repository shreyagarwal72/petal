package com.petal.browser

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.preference.PreferenceManager
import com.petal.browser.engine.ChromiumNativeEngineCore
import com.petal.browser.predictive.PetalPredictiveJunction
import com.petal.browser.unit.BrowserUnit
import com.petal.browser.unit.TabThumbnailCache
import com.petal.browser.widget.PetalSearchWidgetProvider

/**
 * Custom Application class for Petal Browser written in Kotlin.
 * Initializes ChromiumNativeEngineCore and predictive junctions during early app process launch.
 */
class PetalApplication : Application() {

    private var lastNightModeBits: Int = 0
    private var startedActivityCount: Int = 0

    private val wallpaperChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            PetalSearchWidgetProvider.updateAllWidgets(this@PetalApplication)
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(com.petal.browser.unit.HelperUnit.applyLanguage(base))
    }

    override fun onCreate() {
        super.onCreate()
        try {
            com.petal.browser.logger.PetalAppLogger.init(this)
            ChromiumNativeEngineCore.initialize(this)
            PetalPredictiveJunction.init(
                PreferenceManager.getDefaultSharedPreferences(this)
            )
            TabThumbnailCache.initDiskCache(this)
            Log.i(TAG, "Early engine initialization complete")
        } catch (e: Exception) {
            Log.e(TAG, "Failed early engine init", e)
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {
                startedActivityCount++
            }
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {
                startedActivityCount = maxOf(0, startedActivityCount - 1)
                if (startedActivityCount == 0 && activity.isFinishing) {
                    val sp = PreferenceManager.getDefaultSharedPreferences(this@PetalApplication)
                    if (sp.getBoolean("sp_clear_quit", false) || sp.getBoolean("sp_clear_on_exit", false)) {
                        BrowserUnit.clearOnExit(this@PetalApplication)
                    }
                }
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (activity.isFinishing) {
                    val sp = PreferenceManager.getDefaultSharedPreferences(this@PetalApplication)
                    if (sp.getBoolean("sp_clear_quit", false) || sp.getBoolean("sp_clear_on_exit", false)) {
                        BrowserUnit.clearOnExit(this@PetalApplication)
                    }
                }
            }
        })

        lastNightModeBits = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

        try {
            registerReceiver(wallpaperChangeReceiver, IntentFilter(Intent.ACTION_WALLPAPER_CHANGED))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register wallpaper change receiver", e)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val nightModeBits = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (nightModeBits != lastNightModeBits) {
            lastNightModeBits = nightModeBits
            try {
                PetalSearchWidgetProvider.updateAllWidgets(this)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh widgets after night mode change", e)
            }
        }
    }

    companion object {
        private const val TAG = "PetalApplication"
    }
}
