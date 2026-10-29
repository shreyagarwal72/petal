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

// Advanced Liquid Glass Shader Preferences (calibrated from Liquid Glass v17.2)
const val PREF_GLASS_EDGE_WIDTH = "sp_liquid_glass_edge_width"
const val PREF_GLASS_CHROMATIC = "sp_liquid_glass_chromatic"
const val PREF_GLASS_RIM_ANGLE = "sp_liquid_glass_rim_angle"
const val PREF_GLASS_RIM_STRENGTH = "sp_liquid_glass_rim_strength"
const val PREF_GLASS_SOFT_RIM_STRENGTH = "sp_liquid_glass_soft_rim_strength"
const val PREF_GLASS_SOFT_RIM_WIDTH = "sp_liquid_glass_soft_rim_width"
const val PREF_GLASS_SOFT_RIM_TINT = "sp_liquid_glass_soft_rim_tint"
const val PREF_GLASS_BORDER_STRENGTH = "sp_liquid_glass_border_strength"
const val PREF_GLASS_CONTRAST = "sp_liquid_glass_contrast"
const val PREF_GLASS_SQUIRCLE_N = "sp_liquid_glass_squircle_n"
const val PREF_GLASS_PERF_NO_CHROMA = "sp_liquid_glass_perf_no_chroma"

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

/**
 * Reads the full set of AGSL Liquid Glass shader tuning parameters.
 * Recomposes whenever [LocalPetalGlassRevision] changes.
 */
@Composable
fun rememberPetalLiquidGlassTuning(
    refractionOverride: Float? = null,
    refractionWidthOverride: Float? = null,
    edgeWidthOverride: Float? = null,
    chromaticOverride: Float? = null,
    rimAngleOverride: Float? = null,
    rimStrengthOverride: Float? = null,
    contrastOverride: Float? = null,
    softRimStrengthOverride: Float? = null,
    borderStrengthOverride: Float? = null,
    unitPxOverride: Float? = null
): PetalLiquidGlassTuning {
    LocalPetalGlassRevision.current
    val sp = PreferenceManager.getDefaultSharedPreferences(LocalContext.current)
    val refMult = (refractionOverride ?: sp.getFloat(PREF_GLASS_REFRACTION, 1.0f)).coerceIn(0f, 2f)
    val widthMult = (refractionWidthOverride ?: sp.getFloat(PREF_GLASS_REFRACTION_WIDTH, 1.0f)).coerceIn(0f, 2f)

    val baseEdge = (edgeWidthOverride ?: sp.getFloat(PREF_GLASS_EDGE_WIDTH, 0.040f)).coerceIn(0.005f, 0.20f)
    val baseChromatic = (chromaticOverride ?: sp.getFloat(PREF_GLASS_CHROMATIC, 0.018f)).coerceIn(0f, 0.06f)
    val baseRimAngle = (rimAngleOverride ?: sp.getFloat(PREF_GLASS_RIM_ANGLE, 45.0f)).coerceIn(0f, 360f)
    val baseRimStrength = (rimStrengthOverride ?: sp.getFloat(PREF_GLASS_RIM_STRENGTH, 1.20f)).coerceIn(0f, 3.0f)
    val baseContrast = (contrastOverride ?: sp.getFloat(PREF_GLASS_CONTRAST, 1.08f)).coerceIn(0.8f, 1.6f)

    val softRimStrength = (softRimStrengthOverride ?: sp.getFloat(PREF_GLASS_SOFT_RIM_STRENGTH, 0.80f)).coerceIn(0f, 2.5f)
    val softRimWidth = sp.getFloat(PREF_GLASS_SOFT_RIM_WIDTH, 1.00f).coerceIn(0.2f, 3.0f)
    val softRimTint = sp.getFloat(PREF_GLASS_SOFT_RIM_TINT, 0.50f).coerceIn(0f, 1.0f)
    val borderStrength = (borderStrengthOverride ?: sp.getFloat(PREF_GLASS_BORDER_STRENGTH, 0.35f)).coerceIn(0f, 1.0f)
    val squircleN = sp.getFloat(PREF_GLASS_SQUIRCLE_N, 3.0f).coerceIn(2.0f, 4.0f)
    val perfNoChroma = sp.getBoolean(PREF_GLASS_PERF_NO_CHROMA, false)
    val unitPx = (unitPxOverride ?: 1000.0f).coerceAtLeast(1f)

    return PetalLiquidGlassTuning(
        blurRadiusDp = 24.0f,
        refractionStrength = 0.085f * refMult,
        edgeWidth = baseEdge * widthMult,
        chromaticAberration = baseChromatic,
        rimAngleDeg = baseRimAngle,
        rimStrength = baseRimStrength,
        softRimStrength = softRimStrength,
        softRimWidth = softRimWidth,
        softRimTint = softRimTint,
        borderStrength = borderStrength,
        contrastBoost = baseContrast,
        squircleN = squircleN,
        unitPx = unitPx,
        perfDisableChromatic = perfNoChroma,
        perfDisableRimLight = false
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
