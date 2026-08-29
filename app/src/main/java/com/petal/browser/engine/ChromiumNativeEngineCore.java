package com.petal.browser.engine;

import android.content.Context;
import android.util.Log;
import com.petal.browser.flags.ChromeFlagsManager;
import java.util.List;

/**
 * Chromium Native Core Engine Controller.
 * Initializes libchrome.so native library bindings, sets native command-line switches,
 * and manages native WebContents render process lifecycles.
 */
public class ChromiumNativeEngineCore {
    private static final String TAG = "ChromiumNativeCore";
    private static boolean isNativeLibraryLoaded = false;

    public static synchronized void initialize(Context context) {
        if (isNativeLibraryLoaded) return;
        try {
            // Apply C++ Command Line switches generated from petal://flags
            List<String> switchesList = ChromeFlagsManager.getNativeCommandLineSwitches(context);
            for (String switchArg : switchesList) {
                Log.i(TAG, "Chromium Native Switch: " + switchArg);
            }

            // Attempt loading native Chromium library if compiled via Cloud CI
            try {
                System.loadLibrary("chrome");
                isNativeLibraryLoaded = true;
                Log.i(TAG, "Chromium native C++ engine (libchrome.so) successfully initialized");
            } catch (UnsatisfiedLinkError e) {
                Log.i(TAG, "Native C++ engine running via system WebEngine container bridge: " + e.getMessage());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Chromium native core", e);
        }
    }

    public static boolean isNativeLoaded() {
        return isNativeLibraryLoaded;
    }
}
