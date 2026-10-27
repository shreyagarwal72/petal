package com.petal.browser.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager

/**
 * The user's refraction settings (Appearance → Liquid Glass). Both are multipliers on each
 * surface's own tuned lens, so 1 is the look as designed and 0 switches the lens off:
 *  - [strength] scales how far the rim pulls the image.
 *  - [width] scales how far in from the rim the glass bends.
 */
@Immutable
data class RefractionTuning(val strength: Float = 1f, val width: Float = 1f) {
    val enabled: Boolean get() = strength > 0.001f && width > 0.001f
}

const val PREF_GLASS_REFRACTION = "sp_liquid_glass_refraction"
const val PREF_GLASS_REFRACTION_WIDTH = "sp_liquid_glass_refraction_width"

/**
 * Reads the refraction prefs. Reading [LocalPetalGlassRevision] makes the caller recompose
 * whenever any liquid-glass pref changes, so the sliders apply live. The overrides let the
 * settings preview show an unsaved slider value.
 */
@Composable
fun rememberRefractionTuning(
    strengthOverride: Float? = null,
    widthOverride: Float? = null
): RefractionTuning {
    LocalPetalGlassRevision.current
    val sp = PreferenceManager.getDefaultSharedPreferences(LocalContext.current)
    return RefractionTuning(
        strength = (strengthOverride ?: sp.getFloat(PREF_GLASS_REFRACTION, 1f)).coerceIn(0f, 2f),
        width = (widthOverride ?: sp.getFloat(PREF_GLASS_REFRACTION_WIDTH, 1f)).coerceIn(0f, 2f)
    )
}

const val PREF_GLASS_SHEEN = "sp_liquid_glass_sheen"
const val PREF_GLASS_ALPHA = "sp_liquid_glass_alpha"

/**
 * Live sheen value (0..1). Reads [LocalPetalGlassRevision] so every surface that uses it
 * recomposes the moment the slider moves, instead of keeping whatever it read when it was
 * first composed. [enabled] = false returns the neutral default for non-glass surfaces.
 */
@Composable
fun rememberGlassSheen(enabled: Boolean = true, default: Float = 0.60f): Float {
    LocalPetalGlassRevision.current
    if (!enabled) return default
    val sp = PreferenceManager.getDefaultSharedPreferences(LocalContext.current)
    return sp.getFloat(PREF_GLASS_SHEEN, default).coerceIn(0f, 1f)
}

/** Live translucency value (0.10..0.95); same live-update contract as [rememberGlassSheen]. */
@Composable
fun rememberGlassAlpha(default: Float = 0.70f): Float {
    LocalPetalGlassRevision.current
    val sp = PreferenceManager.getDefaultSharedPreferences(LocalContext.current)
    return sp.getFloat(PREF_GLASS_ALPHA, default).coerceIn(0.10f, 0.95f)
}

