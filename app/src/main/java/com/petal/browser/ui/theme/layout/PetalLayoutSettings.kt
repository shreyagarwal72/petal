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
 * Top-level system UI Experience choice: Material 3 Expressive or Liquid Glass.
 */
enum class UiExperience(val key: String, val title: String, val subtitle: String) {
    MATERIAL_EXPRESSIVE("M3_EXPRESSIVE", "Material 3 Expressive", "Google Expressive M3 with bouncy shapes & kinetic pills"),
    LIQUID_GLASS("LIQUID_GLASS", "Liquid Glass", "Translucent glass with backdrop refraction & specular lighting");

    companion object {
        fun fromKey(key: String?): UiExperience =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: MATERIAL_EXPRESSIVE
    }
}

/**
 * Visual style of the pull-to-refresh indicator. Only the drawing changes: the gesture, the Gecko
 * "can the page scroll up" check and the refresh commit stay Mozilla's (SwipeRefreshFeature).
 */
enum class PetalPullRefreshStyle(val key: String, val title: String, val subtitle: String) {
    CURRENT("CURRENT", "Current", "Contained expressive loader (default)"),
    OVERCAST_MORPH("OVERCAST_MORPH", "Overcast Morph", "Shape-morphing loader with rubber-band pull"),
    FIREFOX("FIREFOX", "Firefox", "Original Firefox for Android pull spinner"),
    WAVY_ARC("WAVY_ARC", "Wavy Arc", "A wavy ring that grows with the pull"),
    MORPH_LADDER("MORPH_LADDER", "Morph Ladder", "Shape steps up at 33%, 66% and 100% of the pull"),
    LIQUID_BRIDGE("LIQUID_BRIDGE", "Liquid Bridge", "A droplet that peels off the top edge");

    companion object {
        fun fromKey(key: String?): PetalPullRefreshStyle =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: CURRENT
    }
}

/**
 * Expressive layout & control settings (ported from ImageToolbox's appearance settings).
 * Liquid Glass has no switch/slider "type" of its own: when Liquid Glass is active every switch
 * and slider automatically renders in its glass form (see PetalSwitch / PetalSlider).
 */
@Immutable
data class PetalLayoutSettings(
    val uiExperience: UiExperience = UiExperience.MATERIAL_EXPRESSIVE,
    val switchType: PetalSwitchType = PetalSwitchType.COMPOSE,
    val sliderType: PetalSliderType = PetalSliderType.FANCY,
    val shapeType: PetalShapeType = PetalShapeType.ROUNDED,
    val cornersSize: Float = 1.0f,
    val cornersThrottleMs: Long = 153L,
    val flingType: PetalFlingType = PetalFlingType.DEFAULT,
    val motionDurationScale: Float = 1.0f,
    val pullRefreshStyle: PetalPullRefreshStyle = PetalPullRefreshStyle.CURRENT,
    /** Containment (cards, rows, chips, hero surfaces) shape style. [PetalContainmentShapeChoice.FOLLOW_APP] = use [shapeType]. */
    val containmentShape: PetalContainmentShapeChoice = PetalContainmentShapeChoice.FOLLOW_APP,
    /** When false containment uses the app-wide [cornersSize]; when true it uses [containmentCornersSize]. */
    val containmentOwnCorners: Boolean = false,
    val containmentCornersSize: Float = 1.0f,
    /** Shape of the icon containers (badges) used across containment rows and cards. */
    val badgeShape: PetalBadgeShape = PetalBadgeShape.MIXED,
) {
    /** Switch and slider thumbs always carry their soft shadow (the on/off options were removed). */
    val drawSwitchShadows: Boolean get() = true
    val drawSliderShadows: Boolean get() = true

    /** Shape style actually used by containment surfaces. */
    val containmentShapeType: PetalShapeType
        get() = containmentShape.type ?: shapeType

    companion object {
        const val KEY_UI_EXPERIENCE = "sp_ui_experience"
        const val KEY_SWITCH_TYPE = "sp_switch_type"
        const val KEY_SLIDER_TYPE = "sp_slider_type"
        const val KEY_SHAPE_TYPE = "sp_shapes_type"
        const val KEY_CORNERS_SIZE = "sp_corners_size"
        const val KEY_CORNERS_THROTTLE = "sp_corners_animation_throttle"
        const val KEY_FLING_TYPE = "sp_fling_type"
        const val KEY_MOTION_DURATION_SCALE = "sp_motion_duration_scale"
        const val KEY_PULL_REFRESH_STYLE = "sp_pull_refresh_style"
        const val KEY_CONTAINMENT_SHAPE = "sp_containment_shape_type"
        const val KEY_CONTAINMENT_OWN_CORNERS = "sp_containment_own_corners"
        const val KEY_CONTAINMENT_CORNERS_SIZE = "sp_containment_corners_size"
        const val KEY_BADGE_SHAPE = "sp_containment_badge_shape"

        fun fromPreferences(sp: SharedPreferences): PetalLayoutSettings {
            val expRaw = sp.getString(KEY_UI_EXPERIENCE, null)
            val exp = if (expRaw != null) {
                UiExperience.fromKey(expRaw)
            } else if (sp.getBoolean("sp_liquid_glass_enabled", false)) {
                UiExperience.LIQUID_GLASS
            } else {
                UiExperience.MATERIAL_EXPRESSIVE
            }
            return PetalLayoutSettings(
                uiExperience = exp,
                switchType = PetalSwitchType.fromKey(sp.getString(KEY_SWITCH_TYPE, PetalSwitchType.COMPOSE.key)),
                sliderType = PetalSliderType.fromKey(sp.getString(KEY_SLIDER_TYPE, PetalSliderType.FANCY.key)),
                shapeType = PetalShapeType.fromKey(sp.getString(KEY_SHAPE_TYPE, PetalShapeType.ROUNDED.key)),
                cornersSize = sp.safeFloat(KEY_CORNERS_SIZE, 1.0f).coerceIn(0f, 2f),
                cornersThrottleMs = sp.safeLong(KEY_CORNERS_THROTTLE, 153L).coerceIn(0L, 500L),
                flingType = PetalFlingType.fromKey(sp.getString(KEY_FLING_TYPE, PetalFlingType.DEFAULT.key)),
                motionDurationScale = sp.safeFloat(KEY_MOTION_DURATION_SCALE, 1.0f).coerceIn(0.25f, 3f),
                pullRefreshStyle = PetalPullRefreshStyle.fromKey(sp.getString(KEY_PULL_REFRESH_STYLE, PetalPullRefreshStyle.CURRENT.key)),
                containmentShape = PetalContainmentShapeChoice.fromKey(sp.getString(KEY_CONTAINMENT_SHAPE, null)),
                containmentOwnCorners = sp.getBoolean(KEY_CONTAINMENT_OWN_CORNERS, false),
                containmentCornersSize = sp.safeFloat(KEY_CONTAINMENT_CORNERS_SIZE, 1.0f).coerceIn(0f, 2f),
                badgeShape = PetalBadgeShape.fromKey(sp.getString(KEY_BADGE_SHAPE, null)),
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

    /**
     * Corner multiplier for containment surfaces: its own slider when "own corners" is on, otherwise the
     * app-wide (eased) value. Reads snapshot state, so callers recompose when either changes.
     */
    val containmentCorners: Float
        get() = if (settings.containmentOwnCorners) settings.containmentCornersSize else displayCorners

    // Strong reference: SharedPreferences only keeps listeners weakly.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
        if (key == null || key in KEYS) {
            settings = PetalLayoutSettings.fromPreferences(sp)
            displayCorners = settings.cornersSize
        }
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
        displayCorners = next.cornersSize
        val sp = prefs ?: PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        sp.edit()
            .putString(PetalLayoutSettings.KEY_UI_EXPERIENCE, next.uiExperience.key)
            .putString(PetalLayoutSettings.KEY_SWITCH_TYPE, next.switchType.key)
            .putString(PetalLayoutSettings.KEY_SLIDER_TYPE, next.sliderType.key)
            .putString(PetalLayoutSettings.KEY_SHAPE_TYPE, next.shapeType.key)
            .putFloat(PetalLayoutSettings.KEY_CORNERS_SIZE, next.cornersSize)
            .putLong(PetalLayoutSettings.KEY_CORNERS_THROTTLE, next.cornersThrottleMs)
            .putString(PetalLayoutSettings.KEY_FLING_TYPE, next.flingType.key)
            .putFloat(PetalLayoutSettings.KEY_MOTION_DURATION_SCALE, next.motionDurationScale)
            .putString(PetalLayoutSettings.KEY_PULL_REFRESH_STYLE, next.pullRefreshStyle.key)
            .putString(PetalLayoutSettings.KEY_CONTAINMENT_SHAPE, next.containmentShape.key)
            .putBoolean(PetalLayoutSettings.KEY_CONTAINMENT_OWN_CORNERS, next.containmentOwnCorners)
            .putFloat(PetalLayoutSettings.KEY_CONTAINMENT_CORNERS_SIZE, next.containmentCornersSize)
            .putString(PetalLayoutSettings.KEY_BADGE_SHAPE, next.badgeShape.key)
            .apply()
    }

    private val KEYS = setOf(
        PetalLayoutSettings.KEY_UI_EXPERIENCE,
        PetalLayoutSettings.KEY_SWITCH_TYPE,
        PetalLayoutSettings.KEY_SLIDER_TYPE,
        PetalLayoutSettings.KEY_SHAPE_TYPE,
        PetalLayoutSettings.KEY_CORNERS_SIZE,
        PetalLayoutSettings.KEY_CORNERS_THROTTLE,
        PetalLayoutSettings.KEY_FLING_TYPE,
        PetalLayoutSettings.KEY_MOTION_DURATION_SCALE,
        PetalLayoutSettings.KEY_PULL_REFRESH_STYLE,
        PetalLayoutSettings.KEY_CONTAINMENT_SHAPE,
        PetalLayoutSettings.KEY_CONTAINMENT_OWN_CORNERS,
        PetalLayoutSettings.KEY_CONTAINMENT_CORNERS_SIZE,
        PetalLayoutSettings.KEY_BADGE_SHAPE,
    )
}

val LocalPetalLayoutSettings = compositionLocalOf { PetalLayoutSettings() }

/** Live settings; recomposes the caller whenever any layout setting changes. */
@Composable
fun rememberPetalLayoutSettings(): PetalLayoutSettings {
    PetalLayoutState.install(LocalContext.current)
    return PetalLayoutState.settings
}
