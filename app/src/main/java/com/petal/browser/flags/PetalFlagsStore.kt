package com.petal.browser.flags

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * PetalFlagsStore
 * ─────────────────────────────────────────────────────────────────────────
 * Persistent storage and synchronization engine for Petal experimental flags.
 * Tracks user overrides and translates them directly into Gecko `user.js` rules.
 */
object PetalFlagsStore {

    private const val TAG = "PetalFlagsStore"
    private const val PREF_PREFIX = "sp_petal_flag_state_"
    const val PREF_CUSTOM_USER_PREFS = "sp_petal_custom_raw_prefs"

    private val _hasPendingRelaunch = MutableStateFlow(false)
    val hasPendingRelaunch: StateFlow<Boolean> = _hasPendingRelaunch.asStateFlow()

    private val _modifiedFlagsCount = MutableStateFlow(0)
    val modifiedFlagsCount: StateFlow<Int> = _modifiedFlagsCount.asStateFlow()

    fun initialize(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        refreshModifiedCount(sp)
    }

    fun isEnabled(context: Context, flagId: String): Boolean {
        val state = getFlagState(context, flagId)
        if (state == FlagState.ENABLED) return true
        if (state == FlagState.DISABLED) return false
        val flag = PetalFlagsRegistry.flags.find { it.id == flagId }
        return flag?.defaultState == FlagState.ENABLED
    }

    fun getFlagState(context: Context, flagId: String): FlagState {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val stateName = sp.getString(PREF_PREFIX + flagId, FlagState.DEFAULT.name) ?: FlagState.DEFAULT.name
        return try {
            FlagState.valueOf(stateName)
        } catch (_: Exception) {
            FlagState.DEFAULT
        }
    }

    fun setFlagState(context: Context, flagId: String, state: FlagState) {
        val appContext = context.applicationContext
        val sp = PreferenceManager.getDefaultSharedPreferences(appContext)
        val current = getFlagState(appContext, flagId)
        if (current == state) return

        if (state == FlagState.DEFAULT) {
            sp.edit().remove(PREF_PREFIX + flagId).apply()
        } else {
            sp.edit().putString(PREF_PREFIX + flagId, state.name).apply()
        }

        _hasPendingRelaunch.value = true
        refreshModifiedCount(sp)
        syncToUserJs(appContext)
    }

    fun resetAll(context: Context) {
        val appContext = context.applicationContext
        val sp = PreferenceManager.getDefaultSharedPreferences(appContext)
        val editor = sp.edit()
        PetalFlagsRegistry.flags.forEach { flag ->
            editor.remove(PREF_PREFIX + flag.id)
        }
        editor.apply()
        _hasPendingRelaunch.value = true
        refreshModifiedCount(sp)
        syncToUserJs(appContext)
    }

    fun acknowledgeRelaunch() {
        _hasPendingRelaunch.value = false
    }

    private fun refreshModifiedCount(sp: SharedPreferences) {
        var count = 0
        PetalFlagsRegistry.flags.forEach { flag ->
            val stateName = sp.getString(PREF_PREFIX + flag.id, null)
            if (stateName != null && stateName != FlagState.DEFAULT.name) {
                count++
            }
        }
        _modifiedFlagsCount.value = count
    }

    /**
     * Builds and synchronizes all active flag overrides into `user.js`.
     */
    fun syncToUserJs(context: Context) {
        val appContext = context.applicationContext
        val sp = PreferenceManager.getDefaultSharedPreferences(appContext)

        try {
            val sb = StringBuilder()
            sb.appendLine("// ── Petal Flags Experimental Engine Overrides ──")

            PetalFlagsRegistry.flags.forEach { flag ->
                val state = getFlagState(appContext, flag.id)
                when (state) {
                    FlagState.ENABLED -> {
                        when (flag.type) {
                            FlagType.BOOLEAN -> sb.appendLine("user_pref(\"${flag.prefKey}\", ${flag.enabledValue});")
                            FlagType.INT -> sb.appendLine("user_pref(\"${flag.prefKey}\", ${flag.enabledValue.toIntOrNull() ?: 1});")
                            FlagType.STRING -> sb.appendLine("user_pref(\"${flag.prefKey}\", \"${flag.enabledValue}\");")
                        }
                    }
                    FlagState.DISABLED -> {
                        when (flag.type) {
                            FlagType.BOOLEAN -> sb.appendLine("user_pref(\"${flag.prefKey}\", ${flag.disabledValue});")
                            FlagType.INT -> sb.appendLine("user_pref(\"${flag.prefKey}\", ${flag.disabledValue.toIntOrNull() ?: 0});")
                            FlagType.STRING -> sb.appendLine("user_pref(\"${flag.prefKey}\", \"${flag.disabledValue}\");")
                        }
                    }
                    FlagState.DEFAULT -> {
                        // Let Gecko / Petal default rule apply
                    }
                }
            }

            val content = sb.toString() + "\n" + com.petal.browser.privacy.PetalPrivacyHardening.buildArkenfoxUserJs(appContext)
            val possibleDirs = listOf(
                File(appContext.filesDir, "mozilla"),
                appContext.getDir("mozilla", Context.MODE_PRIVATE),
                File(appContext.filesDir, "geckoview")
            )

            for (dir in possibleDirs) {
                if (dir.exists() && dir.isDirectory) {
                    val subdirs = dir.listFiles { f -> f.isDirectory } ?: emptyArray()
                    val targetDirs = if (subdirs.isNotEmpty()) subdirs.toList() + dir else listOf(dir)
                    for (target in targetDirs) {
                        try {
                            File(target, "petal-flags.js").writeText(content)
                            File(target, "user.js").writeText(content)
                        } catch (_: Throwable) {}
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to write flags user.js: ${t.message}", t)
        }
    }
}
