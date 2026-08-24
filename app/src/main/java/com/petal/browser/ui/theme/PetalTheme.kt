package com.petal.browser.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Whether the Monitor-style blur effect (scrim blur behind sheets)
 * is enabled. Ported from RV System Monitor's `LocalBlurEffectEnabled`. Backed by the
 * "sp_blur_effect_enabled" preference, defaulting to on.
 */
val LocalPetalBlurEffectEnabled = compositionLocalOf { true }

@RequiresOptIn(message = "This API is experimental and subject to change.")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
annotation class ExperimentalMaterial3ExpressiveApi

val isDynamicColorSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

val defaultPaletteId: String
    get() = if (isDynamicColorSupported) "tide" else "petal"

enum class ThemeConfig {
    FOLLOW_SYSTEM, LIGHT, DARK
}

/** Pure-black window with a near-black elevation ladder for AMOLED panels. */
fun ColorScheme.applyAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF101010),
    surfaceContainerHigh = Color(0xFF181818),
    surfaceContainerHighest = Color(0xFF222222),
    surfaceVariant = Color(0xFF1C1C1C)
)

/**
 * Petal Material 3 Theme with Android 12+ Dynamic Color, Stride Palettes, Custom Fonts (Width, Weight, Roundness), Color Styles & AMOLED Black support.
 * For Android 12+ devices, defaults to system Material You colors; for devices below Android 12, defaults to Petal Pinkish theme.
 */
@Composable
fun PetalExpressiveTheme(
    darkTheme: Boolean = run {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(LocalContext.current)
        val configStr = sp.getString("sp_theme_config", "FOLLOW_SYSTEM") ?: "FOLLOW_SYSTEM"
        val config = try { ThemeConfig.valueOf(configStr) } catch (e: Exception) { ThemeConfig.FOLLOW_SYSTEM }
        when (config) {
            ThemeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
            ThemeConfig.LIGHT -> false
            ThemeConfig.DARK -> true
        }
    },
    dynamicColor: Boolean = run {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(LocalContext.current)
        sp.getBoolean("useDynamicColor", isDynamicColorSupported)
    },
    useAmoled: Boolean = run {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(LocalContext.current)
        sp.getBoolean("sp_amoled", false)
    },
    expressiveColors: Boolean = false,
    appFont: AppFont = run {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(LocalContext.current)
        val fontName = sp.getString("sp_app_font", "PETAL") ?: "PETAL"
        try { AppFont.valueOf(fontName) } catch (e: Exception) { AppFont.PETAL }
    },
    fontWidth: Float = run {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(LocalContext.current)
        sp.getFloat("sp_font_width", 92f)
    },
    fontWeight: Int = run {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(LocalContext.current)
        sp.getInt("sp_font_weight", 750)
    },
    fontRoundness: Float = run {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(LocalContext.current)
        sp.getFloat("sp_font_roundness", 100f)
    },
    gsFlexSettings: GSFlexSettings = run {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(LocalContext.current)
        val presetName = sp.getString("sp_gs_flex_preset", "PETAL") ?: "PETAL"
        val preset = try { GSFlexPreset.valueOf(presetName) } catch (e: Exception) { GSFlexPreset.PETAL }
        GSFlexSettings(preset = preset)
    },
    colorStyle: ColorStyle = run {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(LocalContext.current)
        val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
        try { ColorStyle.valueOf(styleName) } catch (e: Exception) { ColorStyle.TONAL_SPOT }
    },
    paletteId: String = run {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(LocalContext.current)
        sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
    },
    blurEffectEnabled: Boolean = run {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(LocalContext.current)
            .getBoolean("sp_blur_effect_enabled", true)
    },
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> {
            val palette = paletteById(paletteId)
            if (darkTheme) palette.dark else palette.light
        }
    }

    var colorScheme = baseScheme
        .applyStyle(colorStyle)

    if (expressiveColors) {
        colorScheme = if (darkTheme) {
            if (useAmoled) {
                // When AMOLED is active with expressive colors, surface containers use high contrast dark ladder
                colorScheme.copy(
                    background = Color(0xFF0F0F0F),
                    surface = Color(0xFF0F0F0F),
                    surfaceContainerLowest = Color(0xFF0A0A0A),
                    surfaceContainerLow = Color(0xFF141414),
                    surfaceContainer = Color(0xFF1E1E1E),
                    surfaceContainerHigh = Color(0xFF262626),
                    surfaceContainerHighest = Color(0xFF303030)
                )
            } else {
                colorScheme.copy(
                    background = colorScheme.surfaceContainerLowest,
                    surface = colorScheme.surfaceContainerLowest,
                    surfaceContainerLowest = colorScheme.surfaceContainerLowest,
                    surfaceContainerLow = colorScheme.surfaceContainerLow,
                    surfaceContainer = colorScheme.surfaceContainer,
                    surfaceContainerHigh = colorScheme.surfaceContainerHigh,
                    surfaceContainerHighest = colorScheme.surfaceContainerHighest
                )
            }
        } else {
            colorScheme.copy(
                background = Color(0xFFF6F8FC),
                surface = Color(0xFFF6F8FC),
                surfaceContainerLowest = Color(0xFFFFFFFF),
                surfaceContainerLow = Color(0xFFF1F4F9),
                surfaceContainer = Color(0xFFEBEFF5),
                surfaceContainerHigh = Color(0xFFE3E8F0),
                surfaceContainerHighest = Color(0xFFDAE1EC)
            )
        }
    }

    if (darkTheme && useAmoled && !expressiveColors) {
        colorScheme = colorScheme.applyAmoled()
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
            activity.window.navigationBarColor = android.graphics.Color.TRANSPARENT
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalPetalBlurEffectEnabled provides blurEffectEnabled) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = petalTypography(appFont, fontWidth, fontWeight, fontRoundness, gsFlexSettings),
            content = content
        )
    }
}
