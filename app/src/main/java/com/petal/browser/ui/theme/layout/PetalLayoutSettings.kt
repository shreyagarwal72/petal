package com.petal.browser.ui.theme.layout

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager

/**
 * Expressive layout & control settings (ported from ImageToolbox's appearance settings).
 * Liquid Glass has no switch/slider "type" of its own: when Liquid Glass is active every switch
 * and slider automatically renders in its glass form (see PetalSwitch / PetalSlider).
 */
@Immutable
data class PetalLayoutSettings(
    val switchType: PetalSwitchType = PetalSwitchType.COMPOSE,
    val sliderType: PetalSliderType = PetalSliderType.FANCY,
    val shapeType: PetalShapeType = PetalShapeType.ROUNDED,
    val cornersSize: Float = 1.0f,
    val cornersThrottleMs: Long = 153L,
    val flingType: PetalFlingType = PetalFlingType.DEFAULT,
    val motionDurationScale: Float = 1.0f,
    val drawSwitchShadows: Boolean = true,
    val drawSliderShadows: Boolean = true,
) {
    companion object {
        const val KEY_SWITCH_TYPE = "sp_switch_type"
        const val KEY_SLIDER_TYPE = "sp_slider_type"
        const val KEY_SHAPE_TYPE = "sp_shapes_type"
        const val KEY_CORNERS_SIZE = "sp_corners_size"
        const val KEY_CORNERS_THROTTLE = "sp_corners_animation_throttle"
        const val KEY_FLING_TYPE = "sp_fling_type"
        const val KEY_MOTION_DURATION_SCALE = "sp_motion_duration_scale"
        const val KEY_DRAW_SWITCH_SHADOWS = "sp_draw_switch_shadows"
        const val KEY_DRAW_SLIDER_SHADOWS = "sp_draw_slider_shadows"

        fun fromPreferences(sp: SharedPreferences): PetalLayoutSettings {
            return PetalLayoutSettings(
                switchType = PetalSwitchType.fromKey(sp.getString(KEY_SWITCH_TYPE, PetalSwitchType.COMPOSE.key)),
                sliderType = PetalSliderType.fromKey(sp.getString(KEY_SLIDER_TYPE, PetalSliderType.FANCY.key)),
                shapeType = PetalShapeType.fromKey(sp.getString(KEY_SHAPE_TYPE, PetalShapeType.ROUNDED.key)),
                cornersSize = sp.safeFloat(KEY_CORNERS_SIZE, 1.0f).coerceIn(0f, 2f),
                cornersThrottleMs = sp.safeLong(KEY_CORNERS_THROTTLE, 153L).coerceIn(0L, 500L),
                flingType = PetalFlingType.fromKey(sp.getString(KEY_FLING_TYPE, PetalFlingType.DEFAULT.key)),
                motionDurationScale = sp.safeFloat(KEY_MOTION_DURATION_SCALE, 1.0f).coerceIn(0.25f, 3f),
                drawSwitchShadows = sp.getBoolean(KEY_DRAW_SWITCH_SHADOWS, true),
                drawSliderShadows = sp.getBoolean(KEY_DRAW_SLIDER_SHADOWS, true),
            )
        }

        private fun SharedPreferences.safeFloat(key: String, def: Float): Float =
            runCatching { getFloat(key, def) }.getOrDefault(def)

        private fun SharedPreferences.safeLong(key: String, def: Long): Long =
            runCatching { getLong(key, def) }.getOrDefault(def)
    }
}

/**
 * Single source of truth for the layout settings. It is Compose snapshot state, so anything that
 * reads [settings] (or [displayCorners]) during composition updates instantly when the value
 * changes - no screen restart, no manual refresh.
 */
object PetalLayoutState {
    private var installed = false
    private var prefs: SharedPreferences? = null

    var settings by mutableStateOf(PetalLayoutSettings())
        private set

    /** Corner multiplier actually drawn: eased towards [PetalLayoutSettings.cornersSize] by the theme. */
    var displayCorners by mutableFloatStateOf(1f)

    // Strong reference: SharedPreferences only keeps listeners weakly.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
        if (key == null || key in KEYS) settings = PetalLayoutSettings.fromPreferences(sp)
    }

    fun install(context: Context) {
        if (installed) return
        installed = true
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        prefs = sp
        settings = PetalLayoutSettings.fromPreferences(sp)
        displayCorners = settings.cornersSize
        sp.registerOnSharedPreferenceChangeListener(listener)
    }

    /** Applies a change immediately (in memory) and persists it. */
    fun update(context: Context, transform: (PetalLayoutSettings) -> PetalLayoutSettings) {
        install(context)
        val next = transform(settings)
        settings = next
        val sp = prefs ?: PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        sp.edit()
            .putString(PetalLayoutSettings.KEY_SWITCH_TYPE, next.switchType.key)
            .putString(PetalLayoutSettings.KEY_SLIDER_TYPE, next.sliderType.key)
            .putString(PetalLayoutSettings.KEY_SHAPE_TYPE, next.shapeType.key)
            .putFloat(PetalLayoutSettings.KEY_CORNERS_SIZE, next.cornersSize)
            .putLong(PetalLayoutSettings.KEY_CORNERS_THROTTLE, next.cornersThrottleMs)
            .putString(PetalLayoutSettings.KEY_FLING_TYPE, next.flingType.key)
            .putFloat(PetalLayoutSettings.KEY_MOTION_DURATION_SCALE, next.motionDurationScale)
            .putBoolean(PetalLayoutSettings.KEY_DRAW_SWITCH_SHADOWS, next.drawSwitchShadows)
            .putBoolean(PetalLayoutSettings.KEY_DRAW_SLIDER_SHADOWS, next.drawSliderShadows)
            .apply()
    }

    private val KEYS = setOf(
        PetalLayoutSettings.KEY_SWITCH_TYPE,
        PetalLayoutSettings.KEY_SLIDER_TYPE,
        PetalLayoutSettings.KEY_SHAPE_TYPE,
        PetalLayoutSettings.KEY_CORNERS_SIZE,
        PetalLayoutSettings.KEY_CORNERS_THROTTLE,
        PetalLayoutSettings.KEY_FLING_TYPE,
        PetalLayoutSettings.KEY_MOTION_DURATION_SCALE,
        PetalLayoutSettings.KEY_DRAW_SWITCH_SHADOWS,
        PetalLayoutSettings.KEY_DRAW_SLIDER_SHADOWS,
    )
}

val LocalPetalLayoutSettings = compositionLocalOf { PetalLayoutSettings() }

/** Live settings; recomposes the caller whenever any layout setting changes. */
@Composable
fun rememberPetalLayoutSettings(): PetalLayoutSettings {
    PetalLayoutState.install(LocalContext.current)
    return PetalLayoutState.settings
}
