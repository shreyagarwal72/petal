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

// --- Default Material 3 Petal Colors ---
private val PetalLightColors = lightColorScheme(
    primary = Color(0xFF006960),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9EF2E4),
    onPrimaryContainer = Color(0xFF00201C),
    secondaryContainer = Color(0xFFCCE8E2),
    onSecondaryContainer = Color(0xFF051F1C),
    tertiaryContainer = Color(0xFFFFDCC6),
    onTertiaryContainer = Color(0xFF321300),
    background = Color(0xFFEBF3F0),
    onBackground = Color(0xFF171D1B),
    surfaceContainer = Color(0xFFFDFFFD),
    surfaceContainerHigh = Color(0xFFF3F9F6),
    surfaceContainerHighest = Color(0xFFE7EEEB),
    outline = Color(0xFF6F7975),
    outlineVariant = Color(0xFFBEC9C5),
)

private val PetalDarkColors = darkColorScheme(
    primary = Color(0xFF82D5C8),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFF9EF2E4),
    secondaryContainer = Color(0xFF334B47),
    onSecondaryContainer = Color(0xFFCCE8E2),
    tertiaryContainer = Color(0xFF743500),
    onTertiaryContainer = Color(0xFFFFDCC6),
    background = Color(0xFF0D1513),
    onBackground = Color(0xFFDDE4E1),
    surfaceContainer = Color(0xFF1A2422),
    surfaceContainerHigh = Color(0xFF242F2C),
    surfaceContainerHighest = Color(0xFF2F3A37),
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFF3F4946),
)

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
 */
@Composable
fun PetalExpressiveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    useAmoled: Boolean = false,
    appFont: AppFont = AppFont.SYSTEM,
    fontWidth: Float = 100f,
    fontWeight: Int = 400,
    fontRoundness: Float = 0f,
    colorStyle: ColorStyle = ColorStyle.TONAL_SPOT,
    paletteId: String = "tide",
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val baseScheme = when {
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
        typography = petalTypography(appFont, fontWidth, fontWeight, fontRoundness),
        content = content
    )
}
