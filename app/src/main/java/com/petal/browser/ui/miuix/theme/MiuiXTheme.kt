package com.petal.browser.ui.miuix.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.petal.browser.ui.containment.PetalBackdrop
import top.yukonga.miuix.kmp.material.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** True only when the OS4 effects (blur, glass, bg effect) can really run; solid fallback otherwise. */
val LocalUseMiuixOs4 = staticCompositionLocalOf { false }
val LocalIsDark = staticCompositionLocalOf { false }

/**
 * Accessor for the active Miuix design tokens. Colors and text styles come from the real
 * [MiuixTheme]; the legacy [colors] view is an adapter over MiuixTheme.colorScheme.
 */
object MiuiXTheme {
    val colors: MiuiXColors
        @Composable
        get() = MiuixTheme.colorScheme.toMiuiXColors(LocalIsDark.current)

    val shapes: MiuiXShapes
        get() = MiuiXShapes

    val motion: MiuiXMotion
        get() = MiuiXMotion

    val dimensions: MiuiXDimensions
        get() = MiuiXDimensions

    val useMiuixOs4: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalUseMiuixOs4.current

    val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalIsDark.current
}

/**
 * Petal's Miuix mode theme, built on the real Miuix library (same structure as InstallerX's
 * InstallerTheme.kt): ThemeController + MiuixTheme, AMOLED surface override in dark mode, OS4
 * effects gated to Android 13+ with render effect support. A Material 3 bridge derived from
 * MiuixTheme.colorScheme keeps not-yet-migrated Material widgets visually consistent. This
 * never touches Material 3 Expressive or Liquid Glass modes.
 */
@Composable
fun PetalMiuixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useAmoled: Boolean = false,
    useMiuixOs4: Boolean = true,
    @Suppress("UNUSED_PARAMETER") backdrop: PetalBackdrop? = null,
    typography: Typography = MaterialTheme.typography,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
            }
        }
    }

    val os4Active = useMiuixOs4 &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        isRenderEffectSupported()

    // Petal already resolves system/light/dark into darkTheme, so pass an explicit mode.
    val controller = remember(darkTheme) {
        ThemeController(
            colorSchemeMode = if (darkTheme) ColorSchemeMode.Dark else ColorSchemeMode.Light,
            isDark = darkTheme,
        )
    }

    CompositionLocalProvider(
        LocalUseMiuixOs4 provides os4Active,
        LocalIsDark provides darkTheme,
    ) {
        MiuixTheme(controller = controller) {
            val colors = MiuixTheme.colorScheme
            // Keep the controller's mode and the card palette intact; only AMOLED darkens surfaces.
            MiuixTheme(
                colors = if (useAmoled && darkTheme) colors.copy(surface = Color.Black) else colors,
            ) {
                PetalMiuixMaterialBridge(darkTheme = darkTheme, fontSource = typography, content = content)
            }
        }
    }
}

/**
 * Material 3 bridge derived from the real Miuix tokens. Standard MaterialTheme (not Expressive),
 * so unmigrated widgets pick up Miuix colors, squircle shapes and Miuix text styles.
 */
@Composable
private fun PetalMiuixMaterialBridge(
    darkTheme: Boolean,
    fontSource: Typography,
    content: @Composable () -> Unit,
) {
    val c = MiuixTheme.colorScheme
    val t = MiuixTheme.textStyles

    val bridgeColorScheme = if (darkTheme) {
        darkColorScheme(
            primary = c.primary,
            onPrimary = c.onPrimary,
            primaryContainer = c.primaryContainer,
            onPrimaryContainer = c.onPrimaryContainer,
            secondary = c.secondary,
            onSecondary = c.onSecondary,
            secondaryContainer = c.secondaryContainer,
            onSecondaryContainer = c.onSecondaryContainer,
            tertiaryContainer = c.tertiaryContainer,
            onTertiaryContainer = c.onTertiaryContainer,
            background = c.background,
            onBackground = c.onBackground,
            surface = c.surface,
            onSurface = c.onSurface,
            surfaceVariant = c.surfaceVariant,
            onSurfaceVariant = c.onSurfaceVariantSummary,
            surfaceContainer = c.surfaceContainer,
            surfaceContainerHigh = c.surfaceContainerHigh,
            surfaceContainerHighest = c.surfaceContainerHighest,
            outline = c.outline,
            outlineVariant = c.dividerLine,
            error = c.error,
            onError = c.onError,
            errorContainer = c.errorContainer,
            onErrorContainer = c.onErrorContainer,
            scrim = c.windowDimming,
        )
    } else {
        lightColorScheme(
            primary = c.primary,
            onPrimary = c.onPrimary,
            primaryContainer = c.primaryContainer,
            onPrimaryContainer = c.onPrimaryContainer,
            secondary = c.secondary,
            onSecondary = c.onSecondary,
            secondaryContainer = c.secondaryContainer,
            onSecondaryContainer = c.onSecondaryContainer,
            tertiaryContainer = c.tertiaryContainer,
            onTertiaryContainer = c.onTertiaryContainer,
            background = c.background,
            onBackground = c.onBackground,
            surface = c.surface,
            onSurface = c.onSurface,
            surfaceVariant = c.surfaceVariant,
            onSurfaceVariant = c.onSurfaceVariantSummary,
            surfaceContainer = c.surfaceContainer,
            surfaceContainerHigh = c.surfaceContainerHigh,
            surfaceContainerHighest = c.surfaceContainerHighest,
            outline = c.outline,
            outlineVariant = c.dividerLine,
            error = c.error,
            onError = c.onError,
            errorContainer = c.errorContainer,
            onErrorContainer = c.onErrorContainer,
            scrim = c.windowDimming,
        )
    }

    val squircleShapes = remember {
        Shapes(
            extraSmall = MiuiXShapes.ExtraSmall,
            small = MiuiXShapes.Small,
            medium = MiuiXShapes.Medium,
            large = MiuiXShapes.Large,
            extraLarge = MiuiXShapes.ExtraLarge,
        )
    }

    // Real Miuix text styles, with the user's chosen font family carried over from Petal's typography.
    val family = fontSource.bodyMedium.fontFamily
    val bridgeTypography = Typography().copy(
        headlineLarge = t.headline1.copy(fontFamily = family),
        headlineMedium = t.headline2.copy(fontFamily = family),
        titleLarge = t.title1.copy(fontFamily = family),
        titleMedium = t.title3.copy(fontFamily = family),
        titleSmall = t.subtitle.copy(fontFamily = family),
        bodyLarge = t.body1.copy(fontFamily = family),
        bodyMedium = t.body2.copy(fontFamily = family),
        bodySmall = t.footnote1.copy(fontFamily = family),
        labelLarge = t.button.copy(fontFamily = family),
        labelMedium = t.footnote1.copy(fontFamily = family),
        labelSmall = t.footnote2.copy(fontFamily = family),
    )

    MaterialTheme(
        colorScheme = bridgeColorScheme,
        shapes = squircleShapes,
        typography = bridgeTypography,
        content = content,
    )
}
