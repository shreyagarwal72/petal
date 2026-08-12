package com.petal.browser.ui.theme

import android.app.Activity
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

// --- Material 3 Petal Colors ---
private val PetalLightColors = lightColorScheme(
    primary = Color(0xFF676013),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEFE58B),
    onPrimaryContainer = Color(0xFF1F1C00),
    secondaryContainer = Color(0xFFEAE3BD),
    onSecondaryContainer = Color(0xFF1E1C05),
    tertiaryContainer = Color(0xFFD3EC9E),
    onTertiaryContainer = Color(0xFF141F00),
    background = Color(0xFFFEF9EB),
    onBackground = Color(0xFF1D1C14),
    surfaceContainer = Color(0xFFF3EEE0),
    surfaceContainerHigh = Color(0xFFEDE8DA),
    surfaceContainerHighest = Color(0xFFE7E2D5),
    outline = Color(0xFF7A7768),
    outlineVariant = Color(0xFFCBC7B5),
)

private val PetalDarkColors = darkColorScheme(
    primary = Color(0xFFC8CC78),
    onPrimary = Color(0xFF313300),
    primaryContainer = Color(0xFF3C3F00),
    onPrimaryContainer = Color(0xFFD1D480),
    secondaryContainer = Color(0xFF48473B),
    onSecondaryContainer = Color(0xFFE5E2D9),
    tertiaryContainer = Color(0xFFA5653C),
    onTertiaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFF14140E),
    onBackground = Color(0xFFE5E2D9),
    surfaceContainer = Color(0xFF201F17),
    surfaceContainerHigh = Color(0xFF2A2A1F),
    surfaceContainerHighest = Color(0xFF353429),
    outline = Color(0xFF929181),
    outlineVariant = Color(0xFF48483A),
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
 * Petal Material 3 Theme with Android 12+ (API 31+) Material You Dynamic Color and AMOLED Black support.
 */
@Composable
fun PetalExpressiveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    useAmoled: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> PetalDarkColors
        else -> PetalLightColors
    }

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
        content = content
    )
}
