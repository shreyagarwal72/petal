package com.petal.browser.setup

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.preference.PreferenceManager

/**
 * Single owner of "should the first-run setup be on screen".
 *
 * The previous `PetalSetupBridge.showSetup()` was never called from anywhere, so first-run never
 * appeared and `sp_welcome_shown` (read by the default-browser prompt) was never written. The setup
 * is now a Compose overlay mounted in `PetalRootApp`, driven by [visible].
 */
object PetalSetupGate {
    const val KEY_DONE = "sp_setup_v2_done"
    const val KEY_WELCOME_SHOWN = "sp_welcome_shown"
    const val KEY_XP_AWARDED = "sp_setup_xp_awarded"
    const val WELCOME_XP = 25L

    private const val FRESH_INSTALL_WINDOW_MS = 5 * 60_000L
    private const val FRESH_PREFS_MAX_KEYS = 12

    var visible: Boolean by mutableStateOf(false)
        private set

    private var evaluated = false

    /** Call once from the root composable. Safe to call on every recomposition. */
    fun evaluate(context: Context) {
        if (evaluated) return
        evaluated = true
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        if (sp.getBoolean(KEY_DONE, false)) return
        if (isFreshInstall(context) || looksLikeEmptyProfile(sp)) {
            visible = true
        } else {
            // Existing users who update into this version should not be dropped into a first-run flow.
            sp.edit().putBoolean(KEY_DONE, true).putBoolean(KEY_WELCOME_SHOWN, true).apply()
        }
    }

    /** Re-open the guided setup on demand (e.g. from Settings). */
    fun show() {
        evaluated = true
        visible = true
    }

    fun complete(context: Context) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(KEY_DONE, true)
            .putBoolean(KEY_WELCOME_SHOWN, true)
            .apply()
        visible = false
    }

    /**
     * Cleared app data (or a reinstall that kept the original install time) leaves firstInstallTime
     * untouched, so the timestamp check alone silently skipped the welcome for those users.
     * A near-empty preference file means there is no existing profile to protect.
     */
    private fun looksLikeEmptyProfile(sp: android.content.SharedPreferences): Boolean = runCatching {
        sp.all.keys.count { it != KEY_DONE && it != KEY_WELCOME_SHOWN && it != KEY_XP_AWARDED } <= FRESH_PREFS_MAX_KEYS
    }.getOrDefault(false)

    private fun isFreshInstall(context: Context): Boolean = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.lastUpdateTime - info.firstInstallTime < FRESH_INSTALL_WINDOW_MS
    }.getOrDefault(false)
}
