package com.petal.browser;

import android.app.Application;
import android.util.Log;
import com.petal.browser.engine.ChromiumNativeEngineCore;

/**
 * Custom Application class for Petal Browser.
 * Initializes ChromiumNativeEngineCore during early app process launch.
 */
public class PetalApplication extends Application {
    private static final String TAG = "PetalApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            ChromiumNativeEngineCore.initialize(this);
            com.petal.browser.predictive.PetalPredictiveJunction.init(
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            );
            Log.i(TAG, "Early Chromium Native Engine & Predictive Junction initialization complete");
        } catch (Exception e) {
            Log.e(TAG, "Failed early Chromium Native Engine init", e);
        }
    }
}
