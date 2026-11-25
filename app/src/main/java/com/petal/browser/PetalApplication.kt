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
import com.petal.browser.predictive.PetalPredictiveJunction
import com.petal.browser.unit.PetalAndroidUtils
import com.petal.browser.unit.PetalBrowserUtils
import com.petal.browser.unit.TabThumbnailCache
import com.petal.browser.widget.PetalSearchWidgetProvider
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Custom Application class for Petal Browser written in Kotlin.
 * Initializes predictive junctions and Gecko runtime during early app process launch.
 */
@HiltAndroidApp
class PetalApplication : Application(), androidx.work.Configuration.Provider {

    override val workManagerConfiguration: androidx.work.Configuration
        get() = androidx.work.Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()

    private var lastNightModeBits: Int = 0
    private var startedActivityCount: Int = 0
    private var lastActivityRef: java.lang.ref.WeakReference<Activity>? = null

    private val wallpaperChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Not just a redraw: the cached wallpaper slice and glass tiles must be rebuilt from the new wallpaper.
            try { com.petal.browser.widget.liquidglass.PetalWallpaperBlurHelper.rebakeAllFromWallpaper(this@PetalApplication) } catch (_: Throwable) {}
        }
    }

    // Sliders fire many changes a second; coalesce them into one widget redraw.
    private val glassWidgetHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val glassWidgetRefresh = Runnable { PetalSearchWidgetProvider.updateAllWidgets(this@PetalApplication) }

    private val widgetThemePrefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "sp_theme_config" || key == "sp_force_dark_mode") {
            val sp = PreferenceManager.getDefaultSharedPreferences(this@PetalApplication)
            com.petal.browser.engine.gecko.PetalGeckoRuntime.syncTheme(sp)
        }
        if (key != null && key.startsWith("sp_liquid_glass_")) {
            // Master switch, widget switch, sheen, refraction, refraction width, alpha...: the widgets draw from these.
            glassWidgetHandler.removeCallbacks(glassWidgetRefresh)
            glassWidgetHandler.postDelayed(glassWidgetRefresh, 250L)
            return@OnSharedPreferenceChangeListener
        }
        if (key == null || com.petal.browser.ui.theme.PetalFontPrefs.isFontKey(key)) {
            com.petal.browser.widget.glance.PetalWidgetShapes.clear()
            PetalSearchWidgetProvider.updateAllWidgets(this@PetalApplication)
            return@OnSharedPreferenceChangeListener
        }
        when (key) {
            "sp_theme_config",
            "sp_palette_id",
            "useDynamicColor",
            "sp_amoled",
            "sp_color_style",
            "sp_expressive_colors",
            "sp_search_engine" -> {
                PetalSearchWidgetProvider.updateAllWidgets(this@PetalApplication)
            }
        }
    }

    override fun attachBaseContext(base: Context) {
        try {
            com.petal.browser.logger.PetalAppLogger.init(base)
        } catch (_: Throwable) {}
        super.attachBaseContext(PetalAndroidUtils.applyLanguage(base))
    }

    override fun onCreate() {
        super.onCreate()
        com.petal.browser.download.PetalDownloadResumer.install(this)
        instance = this
        // Initializes default user preferences and settings.
        com.petal.browser.account.progress.PetalProgressStore.init(this)

        // One-time migration: purge stale Google Lens / snap photo provider preference
        try {
            val petalPrefs = getSharedPreferences("petal_settings", Context.MODE_PRIVATE)
            if (petalPrefs.contains("sp_snap_photo_provider")) {
                petalPrefs.edit().remove("sp_snap_photo_provider").apply()
            }
            val defaultSp = PreferenceManager.getDefaultSharedPreferences(this)
            if (defaultSp.contains("sp_snap_photo_provider")) {
                defaultSp.edit().remove("sp_snap_photo_provider").apply()
            }
        } catch (_: Throwable) {}
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
            com.petal.browser.logger.PetalLiveLogEngine.init(this)
            com.petal.browser.logger.PetalMainThreadWatchdog.start()
            com.petal.browser.download.PetalNotificationChannelOptimizer.install()

            // Stage 4: Startup reflection self-check for GeckoSession extraction
            com.petal.browser.engine.gecko.PetalEngineBridge.selfCheckReflection(this)

            val runtime = com.petal.browser.engine.gecko.PetalGeckoRuntime.getOrCreate(this)
            // Stage 2: Call GeckoRuntime.warmUp() once on the main process after runtime creation
            runtime.warmUp()

            // GeckoView engine and SessionStorage construction require the UI thread. Defer
            // this until Application.onCreate returns so it does not delay activity startup.
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    com.petal.browser.engine.gecko.PetalEngineStore.getEngine(this@PetalApplication)
                    Log.i(TAG, "Initialized GeckoEngine at application start for WebCompat")
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to initialize GeckoEngine at application start: ${t.message}")
                }
                // Initialize Android Components session persistence alongside the
                // shared Gecko runtime. PetalEngineStore owns the BrowserStore and
                // AutoSave lifecycle; touching it here makes state persistence
                // available before the first tab is materialized, while retaining
                // the existing Petal tab/session UI as the source of truth.
                try {
                    com.petal.browser.engine.gecko.PetalEngineStore.getSessionStorage(this@PetalApplication)
                    // Fenix's three triggers (30 s foreground timer, background, tab/load changes).
                    // Writes stay blocked until the saved session has been restored into the store.
                    com.petal.browser.engine.gecko.PetalEngineStore.startAutoSave(this@PetalApplication)
                } catch (t: Throwable) {
                    Log.w(TAG, "BrowserStore session persistence unavailable", t)
                }
            }
            com.petal.browser.media.sniffer.PetalMediaGrabberInstaller.install(this)
            com.petal.browser.browser.PetalAdBlockEngine.ensureInitialized(this)
            PetalPredictiveJunction.init(
                PreferenceManager.getDefaultSharedPreferences(this)
            )
            TabThumbnailCache.initDiskCache(this)
            com.petal.browser.appleduo.AppleDuoManager.init(this)
            try {
                com.petal.browser.profile.PetalProfileManager.init(this)
                com.petal.browser.wallpaper.PetalWallpaperManager.init(this)
                com.petal.browser.collections.PetalCollectionManager.init(this)
                com.petal.browser.compose.downloads.PetalFetchDownloadBridge.ensureInitialized(this)
                com.petal.browser.download.queue.DownloadQueueManager.start(this)
                // Sweep ephemeral upload buffers & compact SQLite databases
                com.petal.browser.engine.gecko.PetalStorageManager.cleanEphemeralStorage(this)
                com.petal.browser.engine.gecko.PetalStorageManager.optimizeDatabases(this)
            } catch (t: Throwable) {
                Log.w(TAG, "Early profile/wallpaper/download init failed", t)
            }
            Log.i(TAG, "Early engine initialization complete")
        } catch (e: Exception) {
            Log.e(TAG, "Failed early engine init", e)
        }

        // If Liquid Glass widgets are enabled but the wallpaper copy is missing (fresh install, or the
        // file was cleared), extract the current system wallpaper now so placed widgets can render
        // their glass on first compose, without requiring the user to open the aligner first.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                com.petal.browser.widget.liquidglass.PetalWallpaperBlurHelper.rebakeAllFromWallpaper(this@PetalApplication)
            } catch (t: Throwable) {
                Log.w(TAG, "Liquid glass cold-start rebake skipped", t)
            }
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {
                startedActivityCount++
                lastActivityRef = java.lang.ref.WeakReference(activity)
            }
            override fun onActivityResumed(activity: Activity) {
                com.petal.browser.engine.gecko.PetalGeckoRuntime.setForegroundActivity(activity)
            }
            override fun onActivityPaused(activity: Activity) {
                if (com.petal.browser.engine.gecko.PetalGeckoRuntime.getForegroundActivity() === activity) {
                    com.petal.browser.engine.gecko.PetalGeckoRuntime.setForegroundActivity(null)
                }
            }
            override fun onActivityStopped(activity: Activity) {
                startedActivityCount = maxOf(0, startedActivityCount - 1)
                if (startedActivityCount == 0 && activity.isFinishing) {
                    com.petal.browser.logger.PetalAppLogger.markCleanExit(this@PetalApplication)
                    val sp = PreferenceManager.getDefaultSharedPreferences(this@PetalApplication)
                    if (sp.getBoolean("sp_clear_quit", false) || sp.getBoolean("sp_clear_on_exit", false)) {
                        PetalBrowserUtils.clearOnExit(this@PetalApplication)
                    }
                }
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (activity.isFinishing) {
                    com.petal.browser.logger.PetalAppLogger.markCleanExit(this@PetalApplication)
                    val sp = PreferenceManager.getDefaultSharedPreferences(this@PetalApplication)
                    if (sp.getBoolean("sp_clear_quit", false) || sp.getBoolean("sp_clear_on_exit", false)) {
                        PetalBrowserUtils.clearOnExit(this@PetalApplication)
                    }
                }
            }
        })

        // One-time migration from sp_app_launch_animation -> sp_launch_style ("quantum" / "intro").
        try {
            com.petal.browser.compose.splash.PetalSplashState.migrateLaunchStyleIfNeeded(
                PreferenceManager.getDefaultSharedPreferences(this)
            )
        } catch (_: Throwable) {}

        // Apply any queued app icon alias switch cleanly once the whole app moves to background.
        try {
            androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(
                object : androidx.lifecycle.DefaultLifecycleObserver {
                    override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
                        com.petal.browser.appicon.PetalAppIcons.applyPendingIfNeeded(
                            context = this@PetalApplication,
                            activity = lastActivityRef?.get()
                        )
                    }
                }
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to register ProcessLifecycleOwner pending icon observer", t)
        }

        lastNightModeBits = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

        // Live theme updates + custom (seed-based) palette cache + palette mixing.
        try {
            com.petal.browser.ui.theme.PetalThemePrefs.install(this)
            com.petal.browser.ui.theme.PetalCustomPalette.install(this)
            com.petal.browser.ui.theme.PetalPaletteMix.install(this)
        } catch (t: Throwable) {
            Log.w(TAG, "Theme prefs init failed", t)
        }

        try {
            PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(widgetThemePrefListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register widget theme pref listener", e)
        }

        try {
            registerReceiver(wallpaperChangeReceiver, IntentFilter(Intent.ACTION_WALLPAPER_CHANGED))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register wallpaper change receiver", e)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (!isMainProcess()) return
        try {
            com.petal.browser.engine.gecko.PetalGeckoRuntime.onTrimMemory(this, level)
            com.petal.browser.engine.gecko.PetalEngineStore.onLowMemory(this, level)
        } catch (_: Throwable) {}
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (!isMainProcess()) return
        try {
            com.petal.browser.engine.gecko.PetalGeckoRuntime.onLowMemory(this)
            com.petal.browser.engine.gecko.PetalEngineStore.onLowMemory(this, android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
            com.petal.browser.unit.TabThumbnailCache.clearMemory()
        } catch (_: Throwable) {}
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
        @JvmField
        var instance: PetalApplication? = null

        @JvmStatic
        fun getInstance(): PetalApplication? = instance

        @JvmStatic
        fun getAppContext(): Context? = instance?.applicationContext ?: instance
    }
}
