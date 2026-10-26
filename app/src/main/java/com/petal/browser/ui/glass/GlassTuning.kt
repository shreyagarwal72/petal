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
