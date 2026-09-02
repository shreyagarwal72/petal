/*
 * GeckoRuntimeHolder.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Lazy, opt-in application-scoped singleton for the Mozilla GeckoRuntime.
 *
 * The runtime is NOT created at app startup. It is only created when the
 * user explicitly enables "Gecko Engine" in Experimental Settings
 * (SharedPreferences key: "sp_gecko_engine_enabled").
 *
 * This avoids:
 *   • ~300–800 ms cold-start latency hit on every app launch
 *   • ~80–150 MB idle RAM consumption for users who never use Gecko
 *
 * Lifecycle:
 *   1. PetalApplication.onCreate() calls initIfEnabled(context) — a no-op
 *      unless the user has already opted in from a previous session.
 *   2. When the user enables the toggle in Settings, enable(context) is
 *      called and the runtime is created immediately.
 *   3. On next app launch with the toggle on, initIfEnabled() picks it up
 *      again from SharedPreferences.
 *   4. When the user disables the toggle, disable() shuts the runtime down
 *      and clears the flag; a restart is required for full cleanup.
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.gecko

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/** SharedPreferences key that persists the user's opt-in choice. */
const val PREF_GECKO_ENGINE_ENABLED = "sp_gecko_engine_enabled"

/**
 * Holds the single application-wide [GeckoRuntime] instance.
 * Must be obtained only after calling [enable] or [initIfEnabled].
 */
object GeckoRuntimeHolder {

    private const val TAG = "GeckoRuntimeHolder"

    @Volatile
    private var _runtime: GeckoRuntime? = null

    /**
     * True if the runtime is currently live.
     * Use this to guard any GeckoView-specific code paths.
     */
    val isInitialized: Boolean get() = _runtime != null

    /**
     * The live GeckoRuntime. Throws [IllegalStateException] if the runtime
     * has not been initialized yet (i.e. user has not enabled Gecko Engine).
     */
    val runtime: GeckoRuntime
        get() = _runtime
            ?: error("GeckoRuntime not initialized — user has not enabled Gecko Engine in Settings")

    // ── Called from Application.onCreate() ───────────────────────────────

    /**
     * Reads SharedPreferences and creates the runtime only if the user
     * previously opted in. Safe to call unconditionally at startup.
     *
     * @param context application context
     */
    fun initIfEnabled(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        if (sp.getBoolean(PREF_GECKO_ENGINE_ENABLED, false)) {
            createRuntime(context)
        } else {
            Log.d(TAG, "Gecko Engine is disabled — skipping runtime creation")
        }
    }

    // ── Called from Settings toggle ───────────────────────────────────────

    /**
     * Creates the runtime and persists the opt-in preference.
     * Idempotent — safe to call even if already initialized.
     *
     * @param context any context; applicationContext is used internally.
     */
    fun enable(context: Context) {
        val appCtx = context.applicationContext
        PreferenceManager.getDefaultSharedPreferences(appCtx)
            .edit().putBoolean(PREF_GECKO_ENGINE_ENABLED, true).apply()

        createRuntime(appCtx)
        GeckoExtensionManager.init(appCtx)
    }

    /**
     * Shuts down the runtime, frees its memory, and clears the opt-in flag.
     * A full cleanup requires the app to be restarted (GeckoView retains
     * some native state until process death).
     *
     * @param context any context
     */
    fun disable(context: Context) {
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .edit().putBoolean(PREF_GECKO_ENGINE_ENABLED, false).apply()

        _runtime?.shutdown()
        _runtime = null
        Log.i(TAG, "GeckoRuntime shut down and flag cleared")
    }

    // ── Internal ──────────────────────────────────────────────────────────

    private fun createRuntime(context: Context) {
        if (_runtime != null) return
        synchronized(this) {
            if (_runtime != null) return

            val settings = GeckoRuntimeSettings.Builder()
                .remoteDebuggingEnabled(false)
                .consoleOutput(false)
                .javaScriptEnabled(true)
                .build()

            _runtime = GeckoRuntime.create(context.applicationContext, settings)
            Log.i(TAG, "GeckoRuntime created successfully (opt-in)")
        }
    }
}
