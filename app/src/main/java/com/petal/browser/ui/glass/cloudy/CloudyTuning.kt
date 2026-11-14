package com.petal.browser.ui.glass.cloudy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import com.petal.browser.ui.theme.LocalPetalGlassRevision

/**
 * Chromatic dispersion intensity presets inspired by skydoves/Cloudy.
 */
enum class CloudyDispersionMode(val key: String, val title: String, val factor: Float) {
    OFF("OFF", "Off", 0f),
    SUBTLE("SUBTLE", "Subtle (Natural)", 0.25f),
    PRISM("PRISM", "Prism (Cloudy)", 0.55f),
    VIVID("VIVID", "Vivid (High)", 0.85f);

    companion object {
        fun fromKey(key: String?): CloudyDispersionMode =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: SUBTLE
    }
}

/**
 * Optional Cloudy Liquid Glass optical tuning parameters.
 */
@Immutable
data class CloudyGlassTuning(
    val enabled: Boolean = false,
    val dispersion: Float = 0.35f,
    val dispersionMode: CloudyDispersionMode = CloudyDispersionMode.SUBTLE,
    val sdfNormalsEnabled: Boolean = true,
    val specularGlint: Float = 0.60f,
) {
    /** True when chromatic dispersion is active. */
    val hasDispersion: Boolean get() = enabled && dispersion > 0.01f && dispersionMode != CloudyDispersionMode.OFF
}

object CloudyPrefs {
    const val PREF_CLOUDY_OPTICS = "sp_liquid_glass_cloudy_optics"
    const val PREF_CLOUDY_DISPERSION = "sp_liquid_glass_cloudy_dispersion"
    const val PREF_CLOUDY_DISPERSION_MODE = "sp_liquid_glass_cloudy_dispersion_mode"
    const val PREF_CLOUDY_SDF_NORMALS = "sp_liquid_glass_cloudy_sdf_normals"
    const val PREF_CLOUDY_SPECULAR_GLINT = "sp_liquid_glass_cloudy_specular_glint"
}

/**
 * Reads live Cloudy tuning preferences; recomposes when liquid glass settings change.
 */
@Composable
fun rememberCloudyTuning(
    enabledOverride: Boolean? = null,
    dispersionOverride: Float? = null,
    dispersionModeOverride: CloudyDispersionMode? = null,
    sdfNormalsOverride: Boolean? = null,
    specularGlintOverride: Float? = null,
): CloudyGlassTuning {
    LocalPetalGlassRevision.current
    val context = LocalContext.current
    val sp = PreferenceManager.getDefaultSharedPreferences(context)

    val enabled = enabledOverride ?: sp.getBoolean(CloudyPrefs.PREF_CLOUDY_OPTICS, false)
    val dispersion = (dispersionOverride ?: sp.getFloat(CloudyPrefs.PREF_CLOUDY_DISPERSION, 0.35f)).coerceIn(0f, 1f)
    val modeKey = sp.getString(CloudyPrefs.PREF_CLOUDY_DISPERSION_MODE, CloudyDispersionMode.SUBTLE.key)
    val dispersionMode = dispersionModeOverride ?: CloudyDispersionMode.fromKey(modeKey)
    val sdfNormals = sdfNormalsOverride ?: sp.getBoolean(CloudyPrefs.PREF_CLOUDY_SDF_NORMALS, true)
    val specularGlint = (specularGlintOverride ?: sp.getFloat(CloudyPrefs.PREF_CLOUDY_SPECULAR_GLINT, 0.60f)).coerceIn(0f, 1f)

    return CloudyGlassTuning(
        enabled = enabled,
        dispersion = dispersion,
        dispersionMode = dispersionMode,
        sdfNormalsEnabled = sdfNormals,
        specularGlint = specularGlint,
    )
}
