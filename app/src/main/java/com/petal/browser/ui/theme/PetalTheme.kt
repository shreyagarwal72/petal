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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = isDynamicColorSupported,
    useAmoled: Boolean = false,
    expressiveColors: Boolean = false,
    appFont: AppFont = AppFont.SYSTEM,
    fontWidth: Float = 100f,
    fontWeight: Int = 400,
    fontRoundness: Float = 0f,
    gsFlexPreset: GSFlexPreset = GSFlexPreset.DEFAULT,
    colorStyle: ColorStyle = ColorStyle.TONAL_SPOT,
    paletteId: String = defaultPaletteId,
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

    if (expressiveColors) {
        baseScheme = if (darkTheme) {
            baseScheme.copy(
                background = baseScheme.surfaceContainerLow,
                surface = baseScheme.surfaceContainerLow,
                surfaceContainer = baseScheme.surfaceContainerHigh,
                surfaceContainerLow = baseScheme.surfaceContainerHigh,
                surfaceContainerHigh = baseScheme.surfaceContainerHigh,
                surfaceContainerHighest = baseScheme.surfaceContainerHigh,
                surfaceContainerLowest = baseScheme.surfaceContainerHigh
            )
        } else {
            baseScheme.copy(
                background = baseScheme.surfaceContainerLow,
                surface = baseScheme.surfaceContainerLow,
                surfaceContainer = Color.White,
                surfaceContainerLow = Color.White,
                surfaceContainerHigh = Color.White,
                surfaceContainerHighest = Color.White,
                surfaceContainerLowest = Color.White
            )
        }
    }

    var colorScheme = baseScheme
        .applyStyle(colorStyle)

    if (darkTheme && useAmoled) {
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = petalTypography(appFont, fontWidth, fontWeight, fontRoundness, gsFlexPreset),
        content = content
    )
}
