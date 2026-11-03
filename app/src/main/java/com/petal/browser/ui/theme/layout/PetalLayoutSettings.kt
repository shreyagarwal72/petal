package com.petal.browser.ui.theme.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager

data class PetalLayoutSettings(
    val switchType: PetalSwitchType = PetalSwitchType.COMPOSE,
    val sliderType: PetalSliderType = PetalSliderType.FANCY,
    val shapeType: PetalShapeType = PetalShapeType.ROUNDED,
    val cornersSize: Float = 1.0f,
    val cornersThrottleMs: Long = 153L,
    val flingType: PetalFlingType = PetalFlingType.DEFAULT,
    val motionDurationScale: Float = 1.0f
) {
    companion object {
        const val KEY_SWITCH_TYPE = "sp_switch_type"
        const val KEY_SLIDER_TYPE = "sp_slider_type"
        const val KEY_SHAPE_TYPE = "sp_shapes_type"
        const val KEY_CORNERS_SIZE = "sp_corners_size"
        const val KEY_CORNERS_THROTTLE = "sp_corners_animation_throttle"
        const val KEY_FLING_TYPE = "sp_fling_type"
        const val KEY_MOTION_DURATION_SCALE = "sp_motion_duration_scale"

        fun fromPreferences(sp: android.content.SharedPreferences): PetalLayoutSettings {
            return PetalLayoutSettings(
                switchType = PetalSwitchType.fromKey(sp.getString(KEY_SWITCH_TYPE, PetalSwitchType.COMPOSE.key)),
                sliderType = PetalSliderType.fromKey(sp.getString(KEY_SLIDER_TYPE, PetalSliderType.FANCY.key)),
                shapeType = PetalShapeType.fromKey(sp.getString(KEY_SHAPE_TYPE, PetalShapeType.ROUNDED.key)),
                cornersSize = sp.getFloat(KEY_CORNERS_SIZE, 1.0f),
                cornersThrottleMs = sp.getLong(KEY_CORNERS_THROTTLE, 153L),
                flingType = PetalFlingType.fromKey(sp.getString(KEY_FLING_TYPE, PetalFlingType.DEFAULT.key)),
                motionDurationScale = sp.getFloat(KEY_MOTION_DURATION_SCALE, 1.0f)
            )
        }
    }
}

val LocalPetalLayoutSettings = compositionLocalOf { PetalLayoutSettings() }

@Composable
fun rememberPetalLayoutSettings(): PetalLayoutSettings {
    val context = LocalContext.current
    val sp = PreferenceManager.getDefaultSharedPreferences(context)
    return PetalLayoutSettings.fromPreferences(sp)
}
