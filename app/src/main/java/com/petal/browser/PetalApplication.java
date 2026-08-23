package com.petal.browser;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.util.Log;
import com.petal.browser.engine.ChromiumNativeEngineCore;

/**
 * Custom Application class for Petal Browser.
 * Initializes ChromiumNativeEngineCore during early app process launch.
 */
public class PetalApplication extends Application {
    private static final String TAG = "PetalApplication";

    /**
     * Night-mode bits ({@link Configuration#UI_MODE_NIGHT_MASK}) as of the last time we
     * checked, so {@link #onConfigurationChanged} can tell a real light/dark flip apart
     * from any other configuration change (rotation, keyboard, etc.) that also routes
     * through this callback thanks to the {@code uiMode} entry in
     * {@code android:configChanges} on BrowserActivity.
     */
    private int lastNightModeBits;

    /**
     * Live-refreshes every placed instance of the home screen search widget whenever the
     * system dark/light mode or the wallpaper's Material You colors change — see
     * {@link #onConfigurationChanged} and {@link #wallpaperChangeReceiver} below. Without
     * this, {@link com.petal.browser.widget.glance.PetalSearchGlanceWidget} (which has
     * {@code updatePeriodMillis="0"}, i.e. no periodic auto-refresh) only ever picks up a
     * theme/palette change the next time the user visits Petal's own theme settings and
     * explicitly changes something there.
     */
    private final BroadcastReceiver wallpaperChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            com.petal.browser.widget.PetalSearchWidgetProvider.updateAllWidgets(PetalApplication.this);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            ChromiumNativeEngineCore.initialize(this);
            com.petal.browser.predictive.PetalPredictiveJunction.init(
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            );
            com.petal.browser.unit.TabThumbnailCache.initDiskCache(this);
            Log.i(TAG, "Early Chromium Native Engine & Predictive Junction initialization complete");
        } catch (Exception e) {
            Log.e(TAG, "Failed early Chromium Native Engine init", e);
        }

        lastNightModeBits = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;

        try {
            // A dynamically (context-)registered receiver, unlike a manifest-declared one,
            // isn't subject to the Android 8+ implicit-broadcast restrictions, so this
            // reliably fires for as long as the process is alive — which covers the
            // overwhelming majority of "I changed my wallpaper" cases without needing any
            // periodic polling.
            registerReceiver(wallpaperChangeReceiver, new IntentFilter(Intent.ACTION_WALLPAPER_CHANGED));
        } catch (Exception e) {
            Log.e(TAG, "Failed to register wallpaper change receiver", e);
        }
    }

    /**
     * Because BrowserActivity declares {@code uiMode} in {@code android:configChanges}, a
     * system dark/light mode flip does not recreate the activity — it's delivered here
     * (Application-level {@code onConfigurationChanged} fires regardless of which, if any,
     * activity is in the foreground) as well as to the activity. We use the
     * process-wide callback so the home screen widget's colors stay live even while
     * Petal itself is only in the background, not the foreground.
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int nightModeBits = newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (nightModeBits != lastNightModeBits) {
            lastNightModeBits = nightModeBits;
            try {
                com.petal.browser.widget.PetalSearchWidgetProvider.updateAllWidgets(this);
            } catch (Exception e) {
                Log.e(TAG, "Failed to refresh widgets after night mode change", e);
            }
        }
    }
}
