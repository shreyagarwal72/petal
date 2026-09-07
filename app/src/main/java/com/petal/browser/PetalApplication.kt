package com.petal.browser

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.os.Process
import android.util.Log
import androidx.preference.PreferenceManager
import com.petal.browser.engine.ChromiumNativeEngineCore
import com.petal.browser.predictive.PetalPredictiveJunction
import com.petal.browser.unit.BrowserUnit
import com.petal.browser.unit.TabThumbnailCache
import com.petal.browser.widget.PetalSearchWidgetProvider
import dagger.hilt.android.HiltAndroidApp

/**
 * Custom Application class for Petal Browser written in Kotlin.
 * Initializes ChromiumNativeEngineCore and predictive junctions during early app process launch.
 */
@HiltAndroidApp
class PetalApplication : Application() {

    private var lastNightModeBits: Int = 0
    private var startedActivityCount: Int = 0

    private val wallpaperChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            PetalSearchWidgetProvider.updateAllWidgets(this@PetalApplication)
        }
    }

    override fun attachBaseContext(base: Context) {
        try {
            com.petal.browser.logger.PetalAppLogger.init(base)
        } catch (_: Throwable) {}
        super.attachBaseContext(com.petal.browser.unit.HelperUnit.applyLanguage(base))
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            try {
                org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("")
            } catch (t: Throwable) {
                Log.w("PetalApplication", "Failed to add HiddenApi exemptions", t)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // GeckoView starts helper processes for GPU, content tabs and crash
        // handling. Android creates our Application in each of them too. A
        // GeckoRuntime must only ever be created by the browser process: doing
        // so in CrashHelper starts another Gecko instance there, after which
        // Gecko terminates the browser process a few seconds after launch.
        if (!isMainProcess()) {
            Log.i(TAG, "Skipping browser initialization in helper process")
            return
        }

        try {
            com.petal.browser.logger.PetalAppLogger.init(this)
            com.petal.browser.engine.gecko.PetalGeckoRuntime.getOrCreate(this)
            com.petal.browser.browser.PetalAdBlockEngine.ensureInitialized(this)
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
                    com.petal.browser.logger.PetalAppLogger.markCleanExit(this@PetalApplication)
                    val sp = PreferenceManager.getDefaultSharedPreferences(this@PetalApplication)
                    if (sp.getBoolean("sp_clear_quit", false) || sp.getBoolean("sp_clear_on_exit", false)) {
                        BrowserUnit.clearOnExit(this@PetalApplication)
                    }
                }
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (activity.isFinishing) {
                    com.petal.browser.logger.PetalAppLogger.markCleanExit(this@PetalApplication)
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

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (!isMainProcess()) return
        // Tab previews are disposable; release their decoded bitmaps before Android
        // kills Gecko's content process under background memory pressure.
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            TabThumbnailCache.clearMemory()
        }
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
            TabThumbnailCache.evictAll()
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

    private fun isMainProcess(): Boolean {
        val processName = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            activityManager?.runningAppProcesses
                ?.firstOrNull { it.pid == Process.myPid() }
                ?.processName
        }
        return processName == packageName
    }

    companion object {
        private const val TAG = "PetalApplication"
        @JvmStatic
        var instance: PetalApplication? = null
            private set
    }
}
