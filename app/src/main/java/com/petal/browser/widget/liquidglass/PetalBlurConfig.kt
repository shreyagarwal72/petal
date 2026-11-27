package com.petal.browser.widget.liquidglass

import android.content.Context
import android.content.SharedPreferences

/**
 * Configuration holder for the Petal Liquid Glass widget rendering engine.
 * Mirrors the physical parameters, specular highlights, and tint settings from the Liquid Glass engine.
 */
data class PetalBlurConfig(
    val blurRadius: Float = DEFAULT_BLUR_RADIUS,
    val refractionHeight: Float = DEFAULT_REFRACTION_HEIGHT,
    val refractionOffset: Float = DEFAULT_REFRACTION_OFFSET,
    val cornerRadius: Float = DEFAULT_CORNER_RADIUS,
    val edgeWidthDp: Float = DEFAULT_EDGE_WIDTH_DP,
    val edgeAlpha: Int = DEFAULT_EDGE_ALPHA,
    val darkLightMode: Int = DARK_LIGHT_OFF,
    val darkLightIntensity: Int = DEFAULT_DARK_LIGHT_INTENSITY,
    val tintRgb: Int = 0,
    val tintAlpha: Int = 0,
    val tintHue: Float = 0f
) {
    companion object {
        const val DARK_LIGHT_OFF = 0
        const val DARK_LIGHT_DARK = 1
        const val DARK_LIGHT_LIGHT = 2

        const val DEFAULT_BLUR_RADIUS = 0.01f
        const val DEFAULT_REFRACTION_HEIGHT = 20.0f
        const val DEFAULT_REFRACTION_OFFSET = 70.0f
        const val DEFAULT_CORNER_RADIUS = 23.0f
        const val DEFAULT_EDGE_WIDTH_DP = 1.25f
        const val DEFAULT_EDGE_ALPHA = 22 // Percentage 0..100 (~22% specular edge rim)
        const val DEFAULT_DARK_LIGHT_INTENSITY = 50

        private const val PREFS_NAME = "com.petal.browser.widget.liquidglass.BlurConfigPrefs"
        private const val KEY_BLUR_RADIUS = "blur_radius"
        private const val KEY_REFRACTION_HEIGHT = "refraction_height"
        private const val KEY_REFRACTION_OFFSET = "refraction_offset"
        private const val KEY_CORNER_RADIUS = "corner_radius"
        private const val KEY_EDGE_WIDTH = "edge_width_dp"
        private const val KEY_EDGE_ALPHA = "edge_alpha_pct"
        private const val KEY_DARK_LIGHT_MODE = "widget_dark_light_mode"
        private const val KEY_DARK_LIGHT_INTENSITY = "widget_dark_light_intensity_v2"
        private const val KEY_TINT_RGB = "tint_rgb"
        private const val KEY_TINT_ALPHA = "widget_tint_alpha"
        private const val KEY_TINT_HUE = "widget_tint_hue"

        fun getPrefs(context: Context): SharedPreferences {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

        fun normalize(config: PetalBlurConfig): PetalBlurConfig = config.copy(
            blurRadius = config.blurRadius.coerceIn(0f, 50f),
            refractionHeight = config.refractionHeight.coerceIn(1f, 60f),
            refractionOffset = config.refractionOffset.coerceIn(0f, 150f),
            cornerRadius = config.cornerRadius.coerceIn(0f, 60f),
            edgeWidthDp = config.edgeWidthDp.coerceIn(0.5f, 4f),
            edgeAlpha = config.edgeAlpha.coerceIn(0, 100),
            darkLightMode = config.darkLightMode.coerceIn(DARK_LIGHT_OFF, DARK_LIGHT_LIGHT),
            darkLightIntensity = config.darkLightIntensity.coerceIn(0, 100),
            tintAlpha = config.tintAlpha.coerceIn(0, 255)
        )

        fun load(context: Context): PetalBlurConfig {
            val sp = getPrefs(context)
            return normalize(
                PetalBlurConfig(
                    blurRadius = sp.getFloat(KEY_BLUR_RADIUS, DEFAULT_BLUR_RADIUS),
                    refractionHeight = sp.getFloat(KEY_REFRACTION_HEIGHT, DEFAULT_REFRACTION_HEIGHT),
                    refractionOffset = sp.getFloat(KEY_REFRACTION_OFFSET, DEFAULT_REFRACTION_OFFSET),
                    cornerRadius = sp.getFloat(KEY_CORNER_RADIUS, DEFAULT_CORNER_RADIUS),
                    edgeWidthDp = sp.getFloat(KEY_EDGE_WIDTH, DEFAULT_EDGE_WIDTH_DP),
                    edgeAlpha = sp.getInt(KEY_EDGE_ALPHA, DEFAULT_EDGE_ALPHA),
                    darkLightMode = sp.getInt(KEY_DARK_LIGHT_MODE, DARK_LIGHT_OFF),
                    darkLightIntensity = sp.getInt(KEY_DARK_LIGHT_INTENSITY, DEFAULT_DARK_LIGHT_INTENSITY),
                    tintRgb = sp.getInt(KEY_TINT_RGB, 0),
                    tintAlpha = sp.getInt(KEY_TINT_ALPHA, 0),
                    tintHue = sp.getFloat(KEY_TINT_HUE, 0f)
                )
            )
        }

        fun save(context: Context, config: PetalBlurConfig) {
            val clean = normalize(config)
            getPrefs(context).edit()
                .putFloat(KEY_BLUR_RADIUS, clean.blurRadius)
                .putFloat(KEY_REFRACTION_HEIGHT, clean.refractionHeight)
                .putFloat(KEY_REFRACTION_OFFSET, clean.refractionOffset)
                .putFloat(KEY_CORNER_RADIUS, clean.cornerRadius)
                .putFloat(KEY_EDGE_WIDTH, clean.edgeWidthDp)
                .putInt(KEY_EDGE_ALPHA, clean.edgeAlpha)
                .putInt(KEY_DARK_LIGHT_MODE, clean.darkLightMode)
                .putInt(KEY_DARK_LIGHT_INTENSITY, clean.darkLightIntensity)
                .putInt(KEY_TINT_RGB, clean.tintRgb)
                .putInt(KEY_TINT_ALPHA, clean.tintAlpha)
                .putFloat(KEY_TINT_HUE, clean.tintHue)
                .apply()
        }
    }

    fun getDarkLightColor(): Int {
        if (darkLightMode == DARK_LIGHT_OFF || darkLightIntensity <= 0) return 0
        val alpha = ((darkLightIntensity.coerceIn(0, 100) / 100f) * 255f).toInt()
        return if (darkLightMode == DARK_LIGHT_DARK) {
            (alpha shl 24) or 0x000000
        } else {
            (alpha shl 24) or 0x00FFFFFF
        }
    }

    fun getTintColor(): Int {
        if (tintAlpha <= 0 || tintRgb == 0) return 0
        val a = tintAlpha.coerceIn(0, 255)
        return (a shl 24) or (tintRgb and 0x00FFFFFF)
    }
}
