package com.petal.browser.scanner

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Snapshot of user-facing scanner preferences shown in [PetalScannerSettingsScreen].
 */
@Immutable
data class PetalScannerSettingsUiState(
    val autoOpen: Boolean = false,
    val vibrate: Boolean = true,
    val beep: Boolean = false,
    val historyEnabled: Boolean = true,
    val safetyWarnings: Boolean = true,
    val defaultUrlAction: String = "ASK",
)

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

    fun readState(sp: SharedPreferences): PetalScannerSettingsUiState {
        return PetalScannerSettingsUiState(
            autoOpen = sp.getBoolean(PREF_AUTO_OPEN, false),
            vibrate = sp.getBoolean(PREF_VIBRATE, true),
            beep = sp.getBoolean(PREF_BEEP, false),
            historyEnabled = sp.getBoolean(PREF_HISTORY_ENABLED, true),
            safetyWarnings = sp.getBoolean(PREF_SAFETY_WARNINGS, true),
            defaultUrlAction = sp.getString(PREF_DEFAULT_URL_ACTION, "ASK") ?: "ASK",
        )
    }

    fun readState(context: Context): PetalScannerSettingsUiState {
        return readState(PreferenceManager.getDefaultSharedPreferences(context))
    }

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

/**
 * Reactive state holder synchronized with [PetalScannerSettings] via [SharedPreferences],
 * keeping [PetalScannerSettingsScreen], the scanner camera screen, and [ScannerHost] in sync.
 */
class PetalScannerSettingsStateHolder(
    context: Context,
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext ?: context),
) {
    private val _state = MutableStateFlow(PetalScannerSettings.readState(prefs))
    val state: StateFlow<PetalScannerSettingsUiState> = _state.asStateFlow()

    private var started = false
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
        if (key == null || key in OBSERVED_KEYS) {
            _state.value = PetalScannerSettings.readState(sp)
        }
    }

    fun start() {
        if (!started) {
            prefs.registerOnSharedPreferenceChangeListener(listener)
            started = true
        }
        _state.value = PetalScannerSettings.readState(prefs)
    }

    fun dispose() {
        if (started) {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            started = false
        }
    }

    fun setAutoOpenEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PetalScannerSettings.PREF_AUTO_OPEN, enabled).apply()
        _state.value = _state.value.copy(autoOpen = enabled)
    }

    fun setVibrateEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PetalScannerSettings.PREF_VIBRATE, enabled).apply()
        _state.value = _state.value.copy(vibrate = enabled)
    }

    fun setBeepEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PetalScannerSettings.PREF_BEEP, enabled).apply()
        _state.value = _state.value.copy(beep = enabled)
    }

    fun setHistoryEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PetalScannerSettings.PREF_HISTORY_ENABLED, enabled).apply()
        _state.value = _state.value.copy(historyEnabled = enabled)
    }

    fun setSafetyWarningsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PetalScannerSettings.PREF_SAFETY_WARNINGS, enabled).apply()
        _state.value = _state.value.copy(safetyWarnings = enabled)
    }

    fun setDefaultUrlAction(action: String) {
        prefs.edit().putString(PetalScannerSettings.PREF_DEFAULT_URL_ACTION, action).apply()
        _state.value = _state.value.copy(defaultUrlAction = action)
    }

    private companion object {
        val OBSERVED_KEYS = setOf(
            PetalScannerSettings.PREF_AUTO_OPEN,
            PetalScannerSettings.PREF_VIBRATE,
            PetalScannerSettings.PREF_BEEP,
            PetalScannerSettings.PREF_HISTORY_ENABLED,
            PetalScannerSettings.PREF_SAFETY_WARNINGS,
            PetalScannerSettings.PREF_DEFAULT_URL_ACTION,
        )
    }
}

@Composable
fun rememberScannerSettingsStateHolder(): PetalScannerSettingsStateHolder {
    val context = LocalContext.current
    val holder = remember(context) { PetalScannerSettingsStateHolder(context) }
    DisposableEffect(holder) {
        holder.start()
        onDispose { holder.dispose() }
    }
    return holder
}
