package com.petal.browser.scanner

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * Settings configuration for Petal Scanner.
 */
object PetalScannerSettings {

    const val PREF_AUTO_OPEN = "sp_scanner_auto_open"
    const val PREF_VIBRATE = "sp_scanner_vibrate"
    const val PREF_BEEP = "sp_scanner_beep"
    const val PREF_HISTORY_ENABLED = "sp_scanner_history_enabled"
    const val PREF_SAFETY_WARNINGS = "sp_scanner_safety_warnings"
    const val PREF_DEFAULT_URL_ACTION = "sp_scanner_default_url_action" // "ASK", "CURRENT_TAB", "NEW_TAB"

    fun isAutoOpenEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PREF_AUTO_OPEN, false)
    }

    fun setAutoOpenEnabled(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putBoolean(PREF_AUTO_OPEN, enabled).apply()
    }

    fun isVibrateEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PREF_VIBRATE, true)
    }

    fun setVibrateEnabled(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putBoolean(PREF_VIBRATE, enabled).apply()
    }

    fun isBeepEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PREF_BEEP, false)
    }

    fun setBeepEnabled(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putBoolean(PREF_BEEP, enabled).apply()
    }

    fun isHistoryEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PREF_HISTORY_ENABLED, true)
    }

    fun setHistoryEnabled(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putBoolean(PREF_HISTORY_ENABLED, enabled).apply()
    }

    fun isSafetyWarningsEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PREF_SAFETY_WARNINGS, true)
    }

    fun setSafetyWarningsEnabled(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putBoolean(PREF_SAFETY_WARNINGS, enabled).apply()
    }

    fun getDefaultUrlAction(context: Context): String {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREF_DEFAULT_URL_ACTION, "ASK") ?: "ASK"
    }

    fun setDefaultUrlAction(context: Context, action: String) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putString(PREF_DEFAULT_URL_ACTION, action).apply()
    }
}
