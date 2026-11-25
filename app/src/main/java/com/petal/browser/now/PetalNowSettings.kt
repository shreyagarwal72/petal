package com.petal.browser.now

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import com.petal.browser.live.LiveEventCategory
import com.petal.browser.live.PetalLiveEvents

/**
 * Display modes backed by existing preference `sp_island_mode` (default `"AUTO"`).
 */
enum class PetalNowMode(val prefValue: String, val label: String, val description: String) {
    AUTO("AUTO", "Automatic", "Appears for active tasks, ad blocker events, and live alerts"),
    ALWAYS("ALWAYS", "Always visible", "Keeps a subtle resting capsule docked for quick controls"),
    OFF("OFF", "Off", "Hides the in-app Petal Now capsule");

    companion object {
        fun fromPref(value: String?): PetalNowMode = when (value?.uppercase()) {
            "ALWAYS" -> ALWAYS
            "OFF", "DISABLED", "NONE" -> OFF
            else -> AUTO
        }
    }
}

/**
 * Motion curve styles backed by existing preference `sp_island_anim_style` (default `"EXPRESSIVE"`).
 */
enum class PetalNowAnimStyle(val prefValue: String, val label: String) {
    EXPRESSIVE("EXPRESSIVE", "Expressive springs"),
    CALM("CALM", "Smooth easing");

    companion object {
        fun fromPref(value: String?): PetalNowAnimStyle = when (value?.uppercase()) {
            "CALM", "EASE_IN_OUT", "SMOOTH", "LINEAR" -> CALM
            else -> EXPRESSIVE
        }
    }
}

/**
 * Spring/duration speed presets backed by `sp_island_anim_speed` (default `"DEFAULT"`).
 */
enum class PetalNowAnimSpeed(val prefValue: String, val label: String) {
    SLOW("SLOW", "Relaxed"),
    DEFAULT("DEFAULT", "Balanced"),
    FAST("FAST", "Snappy");

    companion object {
        fun fromPref(value: String?): PetalNowAnimSpeed = when (value?.uppercase()) {
            "SLOW" -> SLOW
            "FAST" -> FAST
            else -> DEFAULT
        }
    }
}

/**
 * Damping presets backed by `sp_island_anim_bounce` (default `"NORMAL"`).
 */
enum class PetalNowAnimBounce(val prefValue: String, val label: String) {
    SMALL("SMALL", "Subtle"),
    NORMAL("NORMAL", "Balanced"),
    BIG("BIG", "Bouncy");

    companion object {
        fun fromPref(value: String?): PetalNowAnimBounce = when (value?.uppercase()) {
            "SMALL", "LOW" -> SMALL
            "BIG", "HIGH" -> BIG
            else -> NORMAL
        }
    }
}

@Immutable
data class PetalNowPrefs(
    val mode: PetalNowMode = PetalNowMode.AUTO,
    val animStyle: PetalNowAnimStyle = PetalNowAnimStyle.EXPRESSIVE,
    val animSpeed: PetalNowAnimSpeed = PetalNowAnimSpeed.DEFAULT,
    val animBounce: PetalNowAnimBounce = PetalNowAnimBounce.NORMAL,
    val wavyProgress: Boolean = true,
    val overlayCompat: Boolean = false,
    val eventsMaster: Boolean = true,
    val categoryEnabled: Map<LiveEventCategory, Boolean> = LiveEventCategory.entries.associateWith { true },
) {
    fun isCategoryOn(category: LiveEventCategory): Boolean =
        eventsMaster && (categoryEnabled[category] ?: true)
}

object PetalNowSettingsStore {
    const val KEY_MODE = "sp_island_mode"
    const val KEY_ANIM_STYLE = "sp_island_anim_style"
    const val KEY_ANIM_SPEED = "sp_island_anim_speed"
    const val KEY_ANIM_BOUNCE = "sp_island_anim_bounce"
    const val KEY_WAVY_PROGRESS = "sp_island_wavy_progress"
    const val KEY_OVERLAY = "sp_island_overlay"

    fun read(sp: SharedPreferences): PetalNowPrefs {
        val cats = LiveEventCategory.entries.associateWith { category ->
            sp.getBoolean(category.prefKey, true)
        }
        return PetalNowPrefs(
            mode = PetalNowMode.fromPref(sp.getString(KEY_MODE, "AUTO")),
            animStyle = PetalNowAnimStyle.fromPref(sp.getString(KEY_ANIM_STYLE, "EXPRESSIVE")),
            animSpeed = PetalNowAnimSpeed.fromPref(sp.getString(KEY_ANIM_SPEED, "DEFAULT")),
            animBounce = PetalNowAnimBounce.fromPref(sp.getString(KEY_ANIM_BOUNCE, "NORMAL")),
            wavyProgress = sp.getBoolean(KEY_WAVY_PROGRESS, true),
            overlayCompat = sp.getBoolean(KEY_OVERLAY, false),
            eventsMaster = sp.getBoolean(PetalLiveEvents.KEY_MASTER, true),
            categoryEnabled = cats,
        )
    }

    fun setEventsMaster(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .edit()
            .putBoolean(PetalLiveEvents.KEY_MASTER, enabled)
            .apply()
    }

    fun setCategoryEnabled(context: Context, category: LiveEventCategory, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .edit()
            .putBoolean(category.prefKey, enabled)
            .apply()
    }
}

@Composable
fun rememberPetalNowPrefs(): PetalNowPrefs {
    val context = LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context.applicationContext) }
    var prefs by remember(sp) { mutableStateOf(PetalNowSettingsStore.read(sp)) }

    DisposableEffect(sp) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null ||
                key.startsWith("sp_island_")
            ) {
                prefs = PetalNowSettingsStore.read(sp)
            }
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return prefs
}
