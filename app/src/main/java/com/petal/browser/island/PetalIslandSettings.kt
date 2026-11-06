package com.petal.browser.island

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import java.util.Locale

/**
 * One reactive snapshot of every island / live-update setting.
 *
 * The old island read each preference once with `remember(sp) { ... }`, so changing a setting
 * did nothing until the activity was recreated. Everything now flows from this class instead.
 */
data class IslandSettings(
    val liveUpdates: Boolean = true,
    /** AUTO, ALWAYS_ISLAND or NOTIFICATION_ONLY. */
    val mode: String = MODE_AUTO,
    val animStyle: IslandAnimationStyle = IslandAnimationStyle.EXPRESSIVE,
    val animSpeed: IslandAnimationSpeed = IslandAnimationSpeed.DEFAULT,
    val animBounce: IslandAnimationBounce = IslandAnimationBounce.NORMAL,
    val wavyProgress: Boolean = true,
    /** Show the island on top of other apps (needs "Display over other apps"). */
    val overlayEnabled: Boolean = false,
    /** Placement alignment mode: AUTO (detect physical camera), CENTER, LEFT, RIGHT. */
    val alignmentMode: String = ALIGN_AUTO,
    /** Manual fine-tuning horizontal offset in dp. */
    val offsetXDp: Int = 0,
    /** Manual fine-tuning vertical offset down in dp. */
    val offsetYDp: Int = 0,
    /** True if hanging from top edge as a notch with concave flare ears. */
    val isNotch: Boolean = false,
    /** Concave ear flare radius in dp for notch mode. */
    val notchFlareDp: Int = 20,
) {
    val motion: IslandMotion
        get() = IslandMotion(style = animStyle, speed = animSpeed, bounce = animBounce)

    companion object {
        const val MODE_AUTO = "AUTO"
        const val MODE_ALWAYS = "ALWAYS_ISLAND"
        const val MODE_NOTIFICATION_ONLY = "NOTIFICATION_ONLY"

        const val ALIGN_AUTO = "AUTO"
        const val ALIGN_CENTER = "CENTER"
        const val ALIGN_LEFT = "LEFT"
        const val ALIGN_RIGHT = "RIGHT"

        const val KEY_LIVE_UPDATES = "sp_live_updates"
        const val KEY_MODE = "sp_island_mode"
        const val KEY_STYLE = "sp_island_anim_style"
        const val KEY_SPEED = "sp_island_anim_speed"
        const val KEY_BOUNCE = "sp_island_anim_bounce"
        const val KEY_WAVY = "sp_island_wavy_progress"
        const val KEY_OVERLAY = "sp_island_overlay"
        const val KEY_ALIGNMENT = "sp_island_alignment"
        const val KEY_OFFSET_X = "sp_island_offset_x"
        const val KEY_OFFSET_Y = "sp_island_offset_y"
        const val KEY_IS_NOTCH = "sp_island_is_notch"
        const val KEY_NOTCH_FLARE = "sp_island_notch_flare"

        private val KEYS = setOf(
            KEY_LIVE_UPDATES, KEY_MODE, KEY_STYLE, KEY_SPEED, KEY_BOUNCE, KEY_WAVY, KEY_OVERLAY,
            KEY_ALIGNMENT, KEY_OFFSET_X, KEY_OFFSET_Y, KEY_IS_NOTCH, KEY_NOTCH_FLARE
        )

        fun read(sp: SharedPreferences): IslandSettings = IslandSettings(
            liveUpdates = sp.getBoolean(KEY_LIVE_UPDATES, true),
            mode = (sp.getString(KEY_MODE, MODE_AUTO) ?: MODE_AUTO).uppercase(Locale.US),
            animStyle = if ("EASE_IN_OUT".equals(sp.getString(KEY_STYLE, "EXPRESSIVE"), true))
                IslandAnimationStyle.EASE_IN_OUT else IslandAnimationStyle.EXPRESSIVE,
            animSpeed = when ((sp.getString(KEY_SPEED, "DEFAULT") ?: "DEFAULT").uppercase(Locale.US)) {
                "SLOW" -> IslandAnimationSpeed.SLOW
                "FAST" -> IslandAnimationSpeed.FAST
                else -> IslandAnimationSpeed.DEFAULT
            },
            animBounce = when ((sp.getString(KEY_BOUNCE, "NORMAL") ?: "NORMAL").uppercase(Locale.US)) {
                "SMALL" -> IslandAnimationBounce.SMALL
                "BIG" -> IslandAnimationBounce.BIG
                else -> IslandAnimationBounce.NORMAL
            },
            wavyProgress = sp.getBoolean(KEY_WAVY, true),
            overlayEnabled = sp.getBoolean(KEY_OVERLAY, false),
            alignmentMode = (sp.getString(KEY_ALIGNMENT, ALIGN_AUTO) ?: ALIGN_AUTO).uppercase(Locale.US),
            offsetXDp = sp.getInt(KEY_OFFSET_X, 0).coerceIn(-120, 120),
            offsetYDp = sp.getInt(KEY_OFFSET_Y, 0).coerceIn(0, 40),
            isNotch = sp.getBoolean(KEY_IS_NOTCH, false),
            notchFlareDp = sp.getInt(KEY_NOTCH_FLARE, 20).coerceIn(12, 36),
        )

        fun flow(context: Context): Flow<IslandSettings> {
            val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            return callbackFlow {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == null || key in KEYS) trySend(read(sp))
                }
                sp.registerOnSharedPreferenceChangeListener(listener)
                awaitClose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
            }.onStart { emit(read(sp)) }.distinctUntilChanged()
        }
    }
}

@Composable
fun rememberIslandSettings(): State<IslandSettings> {
    val context = LocalContext.current
    val flow = remember(context) { IslandSettings.flow(context) }
    val initial = remember(context) {
        IslandSettings.read(PreferenceManager.getDefaultSharedPreferences(context))
    }
    return flow.collectAsStateWithLifecycle(initialValue = initial)
}
