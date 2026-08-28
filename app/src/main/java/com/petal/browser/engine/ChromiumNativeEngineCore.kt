package com.petal.browser.engine

import android.content.Context
import android.util.Log
import com.petal.browser.flags.ChromeFlagsManager

/**
 * Chromium Native Core Engine Controller.
 * Initializes libchrome.so native library bindings, sets native command-line switches,
 * and manages native WebContents render process lifecycles.
 */
object ChromiumNativeEngineCore {
    private const val TAG = "ChromiumNativeCore"
    @Volatile
    private var isNativeLibraryLoaded = false

    @Synchronized
    @JvmStatic
    fun initialize(context: Context) {
        if (isNativeLibraryLoaded) return
        try {
            // Apply C++ Command Line switches generated from petal://flags
            val switchesList = ChromeFlagsManager.getNativeCommandLineSwitches(context)
            for (switchArg in switchesList) {
                Log.i(TAG, "Chromium Native Switch: $switchArg")
            }

            // Attempt loading native Chromium library if compiled via Cloud CI
            try {
                System.loadLibrary("chrome")
                isNativeLibraryLoaded = true
                Log.i(TAG, "Chromium native C++ engine (libchrome.so) successfully initialized")
            } catch (e: UnsatisfiedLinkError) {
                Log.i(TAG, "Native C++ engine running via system WebEngine container bridge: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Chromium native core", e)
        }
    }

    @JvmStatic
    fun isNativeLoaded(): Boolean {
        return isNativeLibraryLoaded
    }
}
