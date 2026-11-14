package com.petal.browser.ui.miuix.theme

import android.app.Activity
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.petal.browser.ui.containment.PetalBackdrop

val LocalUseMiuixOs4 = staticCompositionLocalOf { true }
val LocalIsDark = staticCompositionLocalOf { false }

/**
 * Accessor for the active Xiaomi HyperOS / MIUIX design tokens.
 */
object MiuiXTheme {
    val colors: MiuiXColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMiuiXColors.current

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
 * Independent Xiaomi HyperOS / MIUIX Theme Provider.
 *
 * This theme operates completely separately from Google's Material 3 Expressive theme,
 * utilizing pure continuous G2 squircles, HyperOS spring motion, acrylic glass materials,
 * and authentic MIUIX palette tokens.
 */
@Composable
fun PetalMiuixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useAmoled: Boolean = false,
    useMiuixOs4: Boolean = true,
    backdrop: PetalBackdrop? = null,
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

    val baseColors = if (darkTheme) MiuiXDarkColors else MiuiXLightColors
    val effectiveColors = remember(baseColors, useAmoled, darkTheme) {
        if (useAmoled && darkTheme) {
            baseColors.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceVariant = Color(0xFF0F0F10)
            )
        } else {
            baseColors
        }
    }

    // Material 3 bridge: allows unmigrated Compose standard widgets to blend seamlessly
    // into the MIUIX color and shape world without pulling in Material 3 Expressive motion or bouncy shapes.
    val bridgeColorScheme = remember(effectiveColors, darkTheme) {
        if (darkTheme) {
            darkColorScheme(
                primary = effectiveColors.primary,
                onPrimary = effectiveColors.onPrimary,
                primaryContainer = effectiveColors.primaryContainer,
                onPrimaryContainer = effectiveColors.onPrimaryContainer,
                background = effectiveColors.background,
                onBackground = effectiveColors.onBackground,
                surface = effectiveColors.surface,
                onSurface = effectiveColors.onSurface,
                surfaceVariant = effectiveColors.surfaceVariant,
                onSurfaceVariant = effectiveColors.onSurfaceVariant,
                outline = effectiveColors.outline,
                error = effectiveColors.error,
                onError = effectiveColors.onError,
            )
        } else {
            lightColorScheme(
                primary = effectiveColors.primary,
                onPrimary = effectiveColors.onPrimary,
                primaryContainer = effectiveColors.primaryContainer,
                onPrimaryContainer = effectiveColors.onPrimaryContainer,
                background = effectiveColors.background,
                onBackground = effectiveColors.onBackground,
                surface = effectiveColors.surface,
                onSurface = effectiveColors.onSurface,
                surfaceVariant = effectiveColors.surfaceVariant,
                onSurfaceVariant = effectiveColors.onSurfaceVariant,
                outline = effectiveColors.outline,
                error = effectiveColors.error,
                onError = effectiveColors.onError,
            )
        }
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

    CompositionLocalProvider(
        LocalMiuiXColors provides effectiveColors,
        LocalUseMiuixOs4 provides useMiuixOs4,
        LocalIsDark provides darkTheme,
    ) {
        // Provide standard MaterialTheme (NOT MaterialExpressiveTheme) as a compatibility foundation
        MaterialTheme(
            colorScheme = bridgeColorScheme,
            shapes = squircleShapes,
            content = content,
        )
    }
}
