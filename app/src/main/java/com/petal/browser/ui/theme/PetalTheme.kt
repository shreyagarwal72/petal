package com.petal.browser.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import com.petal.browser.ui.theme.layout.cornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
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
    get() = if (SeasonalEvent.isHalloweenActive) "halloween" else "petal"

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

/** Reads the live theme prefs; subscribes the caller to [PetalThemePrefs] so changes apply instantly. */
@Composable
private fun themePrefs(): android.content.SharedPreferences {
    @Suppress("UNUSED_VARIABLE") val revision = PetalThemePrefs.revision
    return androidx.preference.PreferenceManager.getDefaultSharedPreferences(LocalContext.current)
}

/**
 * Petal Material 3 Theme with Android 12+ Dynamic Color, Stride Palettes, Custom Fonts (Width, Weight, Roundness), Color Styles & AMOLED Black support.
 * For Android 12+ devices, defaults to system Material You colors; for devices below Android 12, defaults to Petal Pinkish theme.
 */
@Composable
fun PetalExpressiveTheme(
    darkTheme: Boolean = run {
        val sp = themePrefs()
        val configStr = sp.getString("sp_theme_config", "FOLLOW_SYSTEM") ?: "FOLLOW_SYSTEM"
        val config = try { ThemeConfig.valueOf(configStr) } catch (e: Exception) { ThemeConfig.FOLLOW_SYSTEM }
        when (config) {
            ThemeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
            ThemeConfig.LIGHT -> false
            ThemeConfig.DARK -> true
        }
    },
    dynamicColor: Boolean = run {
        val sp = themePrefs()
        if (SeasonalEvent.isHalloweenActive && !sp.contains("useDynamicColor")) {
            false
        } else {
            sp.getBoolean("useDynamicColor", isDynamicColorSupported)
        }
    },
    useAmoled: Boolean = run {
        val sp = themePrefs()
        sp.getBoolean("sp_amoled", false)
    },
    expressiveColors: Boolean = run {
        val sp = themePrefs()
        sp.getBoolean("sp_expressive_colors", false)
    },
    appFont: AppFont? = null,
    customFontPath: String? = null,
    customFontSettings: CustomFontSettings? = null,
    fontWidth: Float = run {
        val sp = themePrefs()
        sp.getFloat("sp_font_width", 92f)
    },
    fontWeight: Int = run {
        val sp = themePrefs()
        sp.getInt("sp_font_weight", 750)
    },
    fontRoundness: Float = run {
        val sp = themePrefs()
        sp.getFloat("sp_font_roundness", 100f)
    },
    gsFlexSettings: GSFlexSettings? = null,
    colorStyle: ColorStyle = run {
        val sp = themePrefs()
        val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
        try { ColorStyle.valueOf(styleName) } catch (e: Exception) { ColorStyle.TONAL_SPOT }
    },
    paletteId: String = run {
        val sp = themePrefs()
        val stored = sp.getString("sp_palette_id", null)
        if (stored == null) {
            defaultPaletteId
        } else if (SeasonalEvent.isHalloweenActive && stored == "petal" && !sp.getBoolean("sp_palette_manually_selected", false)) {
            "halloween"
        } else {
            stored
        }
    },
    blurEffectEnabled: Boolean = run {
        val sp = themePrefs()
        sp.getBoolean("sp_blur_effect_enabled", true)
    },
    expressiveShapesEnabled: Boolean = run {
        val sp = themePrefs()
        sp.getBoolean(PetalMaterialShapes.PREF_EXPRESSIVE_SHAPES_ENABLED, true)
    },
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val sp = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    var glassRevision by remember(sp) { androidx.compose.runtime.mutableIntStateOf(0) }
    var fontRevision by remember(sp) { androidx.compose.runtime.mutableIntStateOf(0) }
    var themeRevision by remember(sp) { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.DisposableEffect(sp) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key.startsWith("sp_liquid_glass_") || key == "sp_stride_glass_nav") {
                glassRevision++
            } else if (key == "sp_app_font" || key.startsWith("sp_gs_") || key.startsWith("sp_font_") || key.startsWith("sp_custom_")) {
                fontRevision++
            } else if (key == "sp_expressive_colors" || key == "sp_color_style" || key == "sp_palette_id" || key == "sp_amoled" || key == "useDynamicColor" || key == "sp_theme_config") {
                themeRevision++
            }
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val effectiveExpressiveColors = if (themeRevision >= 0) sp.getBoolean("sp_expressive_colors", expressiveColors) else expressiveColors
    val effectiveUseAmoled = if (themeRevision >= 0) sp.getBoolean("sp_amoled", useAmoled) else useAmoled

    var baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        paletteId == CUSTOM_PALETTE_ID -> {
            // Seed-based palette: the style is part of the generation, so it is not applied again below.
            PetalCustomPalette.scheme(PetalCustomPalette.spec(context), colorStyle, darkTheme)
        }
        else -> {
            val palette = paletteById(paletteId)
            if (darkTheme) palette.dark else palette.light
        }
    }

    val isCustomGenerated = !(dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) && paletteId == CUSTOM_PALETTE_ID
    var colorScheme = if (isCustomGenerated) baseScheme else baseScheme
        .applyStyle(colorStyle)

    // Palette mixing: secondary / tertiary can come from other palettes at the same time.
    val mixConfig = PetalPaletteMix.config(context)
    if (mixConfig.active) {
        colorScheme = PetalPaletteMix.apply(
            colorScheme, mixConfig, PetalCustomPalette.spec(context), darkTheme, colorStyle, context
        )
    }

    if (effectiveExpressiveColors) {
        colorScheme = if (darkTheme) {
            if (effectiveUseAmoled) {
                // When AMOLED is active with expressive colors, surface containers use high contrast dark ladder
                colorScheme.copy(
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceContainerLowest = Color.Black,
                    surfaceContainerLow = Color(0xFF0B0B0B),
                    surfaceContainer = Color(0xFF141414),
                    surfaceContainerHigh = Color(0xFF1E1E1E),
                    surfaceContainerHighest = Color(0xFF282828),
                    surfaceVariant = Color(0xFF1C1C1C)
                )
            } else {
                // Authentic Material 3 Expressive elevation ladder
                colorScheme.copy(
                    background = colorScheme.surfaceContainerLowest,
                    surface = colorScheme.surfaceContainerLowest,
                    surfaceContainerLow = colorScheme.surfaceContainerLow,
                    surfaceContainer = colorScheme.surfaceContainer,
                    surfaceContainerHigh = colorScheme.surfaceContainerHigh,
                    surfaceContainerHighest = colorScheme.surfaceContainerHighest
                )
            }
        } else {
            // Light theme expressive tonal container depth
            colorScheme.copy(
                surfaceContainerLowest = Color(0xFFFFFFFF),
                surfaceContainerLow = colorScheme.surfaceContainerLow,
                surfaceContainer = colorScheme.surfaceContainer,
                surfaceContainerHigh = colorScheme.surfaceContainerHigh,
                surfaceContainerHighest = colorScheme.surfaceContainerHighest
            )
        }
    }

    if (darkTheme && effectiveUseAmoled && !effectiveExpressiveColors) {
        colorScheme = colorScheme.applyAmoled()
    }

    // Legacy View system (dialogs, popups, snackbars, XML) follows the exact same scheme.
    val bridgeContext = LocalContext.current
    SideEffect { PetalViewThemeBridge.publish(bridgeContext, colorScheme) }

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

    val glassEnabled = com.petal.browser.ui.containment.isLiquidGlassEnabled()
    val wallpaper = if (glassEnabled) com.petal.browser.ui.glass.rememberPetalGlassWallpaper() else null
    val hapticFeedback = androidx.compose.runtime.remember(context) { PetalHapticFeedback(context) }
    var hapticsEnabled by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(sp.getBoolean("sp_touch_haptics", true))
    }

    var expressiveShapesState by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(expressiveShapesEnabled)
    }

    // Single live source of truth for switch/slider/shape/corner/fling/motion settings.
    com.petal.browser.ui.theme.layout.PetalLayoutState.install(androidx.compose.ui.platform.LocalContext.current)
    val layoutSettingsState = com.petal.browser.ui.theme.layout.PetalLayoutState.settings

    // Expressive button shape system: seed the live state, then follow preference changes.
    androidx.compose.runtime.remember(sp) {
        PetalShapeState.preset = ExpressiveButtonPreset.fromId(sp.getString(PetalShapePrefs.KEY_BUTTON_PRESET, null))
        PetalShapeState.amount = ExpressiveShapeAmount.fromId(sp.getString(PetalShapePrefs.KEY_SHAPE_AMOUNT, null))
        PetalShapeState.enabled = expressiveShapesEnabled
        true
    }
    androidx.compose.runtime.SideEffect { PetalShapeState.enabled = expressiveShapesState }

    androidx.compose.runtime.DisposableEffect(sp) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PetalShapePrefs.KEY_BUTTON_PRESET) {
                PetalShapeState.preset = ExpressiveButtonPreset.fromId(sp.getString(key, null))
            } else if (key == PetalShapePrefs.KEY_SHAPE_AMOUNT) {
                PetalShapeState.amount = ExpressiveShapeAmount.fromId(sp.getString(key, null))
            } else if (key == "sp_touch_haptics") {
                hapticsEnabled = sp.getBoolean("sp_touch_haptics", true)
            } else if (key == PetalMaterialShapes.PREF_EXPRESSIVE_SHAPES_ENABLED) {
                expressiveShapesState = sp.getBoolean(PetalMaterialShapes.PREF_EXPRESSIVE_SHAPES_ENABLED, true)
            }
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val effectiveAppFont = remember(fontRevision, appFont) {
        if (fontRevision > 0 || appFont == null) {
            val fontName = sp.getString("sp_app_font", "PETAL") ?: "PETAL"
            AppFont.fromName(fontName)
        } else {
            appFont
        }
    }

    val effectiveGsFlexSettings = remember(fontRevision, gsFlexSettings) {
        if (fontRevision > 0 || gsFlexSettings == null) {
            loadGSFlexSettings(sp)
        } else {
            gsFlexSettings
        }
    }

    val effectiveCustomFontPath = remember(fontRevision, customFontPath) {
        if (fontRevision > 0 || customFontPath == null) {
            val path = sp.getString("sp_custom_font_path", null)
            if (!path.isNullOrBlank() && java.io.File(path).exists()) {
                path
            } else {
                val defaultCustom = context.filesDir.resolve("custom_font.ttf")
                if (defaultCustom.exists()) defaultCustom.absolutePath else null
            }
        } else {
            customFontPath
        }
    }

    val effectiveCustomFontSettings = remember(fontRevision, customFontSettings) {
        if (fontRevision > 0 || customFontSettings == null) {
            getCustomFontSettings(sp)
        } else {
            customFontSettings
        }
    }

    val effectiveFontWidth = remember(fontRevision, fontWidth) {
        if (fontRevision > 0) sp.getFloat("sp_font_width", fontWidth) else fontWidth
    }
    val effectiveFontWeight = remember(fontRevision, fontWeight) {
        if (fontRevision > 0) sp.getInt("sp_font_weight", fontWeight) else fontWeight
    }
    val effectiveFontRoundness = remember(fontRevision, fontRoundness) {
        if (fontRevision > 0) sp.getFloat("sp_font_roundness", fontRoundness) else fontRoundness
    }

    val effectiveTypography = remember(
        effectiveAppFont,
        effectiveFontWidth,
        effectiveFontWeight,
        effectiveFontRoundness,
        effectiveGsFlexSettings,
        effectiveCustomFontPath,
        effectiveCustomFontSettings
    ) {
        petalTypography(
            appFont = effectiveAppFont,
            fontWidth = effectiveFontWidth,
            fontWeight = effectiveFontWeight,
            fontRoundness = effectiveFontRoundness,
            gsFlexSettings = effectiveGsFlexSettings,
            customFontPath = effectiveCustomFontPath,
            customFontSettings = effectiveCustomFontSettings
        )
    }

    val reduceMotion = com.petal.browser.ui.components.rememberReduceMotion()
    val isMiuix = layoutSettingsState.uiExperience == com.petal.browser.ui.theme.layout.UiExperience.MIUIX
    val miuixColors = if (darkTheme) com.petal.browser.ui.miuix.theme.MiuiXDarkColors else com.petal.browser.ui.miuix.theme.MiuiXLightColors

    val corners = layoutSettingsState.cornersSize
    val expressiveShapes = remember(isMiuix, expressiveShapesState, layoutSettingsState.shapeType, corners) {
        if (isMiuix) {
            Shapes(
                extraSmall = com.petal.browser.ui.miuix.theme.MiuiXShapes.ExtraSmall,
                small = com.petal.browser.ui.miuix.theme.MiuiXShapes.Small,
                medium = com.petal.browser.ui.miuix.theme.MiuiXShapes.Medium,
                large = com.petal.browser.ui.miuix.theme.MiuiXShapes.Large,
                extraLarge = com.petal.browser.ui.miuix.theme.MiuiXShapes.ExtraLarge,
            )
        } else {
            val type = layoutSettingsState.shapeType
            fun c(dp: Int) = type.cornerShape(dp.dp, multiplier = corners)
            if (expressiveShapesState) {
                Shapes(extraSmall = c(8), small = c(12), medium = c(16), large = c(24), extraLarge = c(32))
            } else {
                Shapes(extraSmall = c(4), small = c(8), medium = c(12), large = c(16), extraLarge = c(28))
            }
        }
    }

    val baseMotion = if (reduceMotion) com.petal.browser.ui.components.PetalInstantMotionScheme
    else androidx.compose.material3.MotionScheme.expressive()
    val motion = remember(baseMotion, layoutSettingsState.motionDurationScale, reduceMotion) {
        val scale = layoutSettingsState.motionDurationScale
        if (reduceMotion || scale == 1f) baseMotion
        else com.petal.browser.ui.theme.layout.ScaledMotionScheme(baseMotion, scale)
    }

    CompositionLocalProvider(
        com.petal.browser.ui.miuix.theme.LocalMiuiXColors provides miuixColors,
        com.petal.browser.ui.glass.LocalPetalGlassWallpaper provides wallpaper,
        com.petal.browser.ui.glass.LocalPetalGlassRevision provides glassRevision,
        LocalPetalBlurEffectEnabled provides blurEffectEnabled,
        LocalExpressiveShapesEnabled provides expressiveShapesState,
        com.petal.browser.ui.theme.layout.LocalPetalLayoutSettings provides layoutSettingsState,
        LocalPetalShapes provides PetalShapes,
        com.petal.browser.haptics.LocalHapticEnabled provides hapticsEnabled,
        com.petal.browser.haptics.LocalVibrationIntensity provides com.petal.browser.haptics.VibrationIntensity.LIGHT,
        androidx.compose.ui.platform.LocalHapticFeedback provides hapticFeedback
    ) {
        androidx.compose.material3.MaterialExpressiveTheme(
            colorScheme = colorScheme,
            shapes = expressiveShapes,
            motionScheme = motion,
            typography = effectiveTypography,
            content = { com.petal.browser.ui.selection.PetalTextToolbarProvider(content) }
        )
    }
}

private class PetalHapticFeedback(private val context: Context) : androidx.compose.ui.hapticfeedback.HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: androidx.compose.ui.hapticfeedback.HapticFeedbackType) {
        com.petal.browser.haptics.PetalHapticEngine.getInstance(context).playIfEnabled(
            context,
            com.petal.browser.haptics.PetalHapticEngine.Pattern.CLICK,
            0.85f
        )
    }
}
