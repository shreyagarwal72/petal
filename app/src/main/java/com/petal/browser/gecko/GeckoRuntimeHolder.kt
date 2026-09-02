/*
 * GeckoRuntimeHolder.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Application-scoped singleton for the Mozilla GeckoRuntime.
 * Initialize once in PetalApplication.onCreate(), then access the shared
 * instance from anywhere in the app without recreating it.
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.gecko

import android.content.Context
import android.util.Log
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * Holds the single application-wide [GeckoRuntime] instance.
 *
 * Call [init] exactly once from [com.petal.browser.PetalApplication.onCreate].
 * All other callers use [runtime] to obtain the pre-created instance.
 */
object GeckoRuntimeHolder {

    private const val TAG = "GeckoRuntimeHolder"

    @Volatile
    private var _runtime: GeckoRuntime? = null

    /** The live runtime. Throws if [init] has not been called first. */
    val runtime: GeckoRuntime
        get() = _runtime ?: error("GeckoRuntime not initialized — call GeckoRuntimeHolder.init(context) in Application.onCreate()")

    /**
     * Creates and stores the [GeckoRuntime].
     * Safe to call multiple times; subsequent calls are no-ops.
     *
     * @param context application context (never an Activity context)
     */
    fun init(context: Context) {
        if (_runtime != null) return
        synchronized(this) {
            if (_runtime != null) return

            val settings = GeckoRuntimeSettings.Builder()
                .remoteDebuggingEnabled(false)
                .consoleOutput(false)
                .javaScriptEnabled(true)
                .build()

            _runtime = GeckoRuntime.create(context.applicationContext, settings)
            Log.i(TAG, "GeckoRuntime created successfully")
        }
    }

    /** Shuts down the runtime. Only call when the entire process is exiting. */
    fun shutdown() {
        _runtime?.shutdown()
        _runtime = null
    }
}
