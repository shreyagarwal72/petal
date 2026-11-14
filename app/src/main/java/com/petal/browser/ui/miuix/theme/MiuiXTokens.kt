package com.petal.browser.ui.miuix.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Xiaomi HyperOS / MIUIX Design System Color Tokens.
 */
@Immutable
data class MiuiXColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val divider: Color,
    val track: Color,
    val error: Color,
    val onError: Color,
    val isDark: Boolean,
)

val MiuiXLightColors = MiuiXColors(
    primary = Color(0xFF007DFF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5F1FF),
    onPrimaryContainer = Color(0xFF0056B3),
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF111111),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFF7F7FA),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0x0F000000),
    divider = Color(0x0C000000),
    track = Color(0xFFE5E5EA),
    error = Color(0xFFFF3B30),
    onError = Color(0xFFFFFFFF),
    isDark = false,
)

val MiuiXDarkColors = MiuiXColors(
    primary = Color(0xFF1A8CFF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF0D3366),
    onPrimaryContainer = Color(0xFF99CCFF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF28282A),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0x1FFFFFFF),
    divider = Color(0x14FFFFFF),
    track = Color(0xFF38383A),
    error = Color(0xFFFF453A),
    onError = Color(0xFFFFFFFF),
    isDark = true,
)

val LocalMiuiXColors = compositionLocalOf { MiuiXLightColors }

/** HyperOS fluid spring motion tokens. */
object MiuiXMotion {
    /** Standard snappy HyperOS interaction spring. */
    fun <T> standard() = spring<T>(
        dampingRatio = 0.75f,
        stiffness = 420f,
    )

    /** Bouncy toggle and thumb spring. */
    fun <T> bouncy() = spring<T>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = 380f,
    )

    /** Gentle continuous sheet and dialog entrance spring. */
    fun <T> gentle() = spring<T>(
        dampingRatio = 0.82f,
        stiffness = 320f,
    )

    /** Touch down card/button feedback press scale. */
    const val PRESS_SCALE = 0.965f
}

/** Standard layout spacing tokens for MiuiX containment. */
object MiuiXDimensions {
    val ScreenMarginHorizontal: Dp = 16.dp
    val InsetCardCorner: Dp = 20.dp
    val LargeSheetCorner: Dp = 28.dp
    val DialogCorner: Dp = 24.dp
    val ButtonCorner: Dp = 16.dp
    val PillCorner: Dp = 9999.dp
    val BadgeSize: Dp = 38.dp
    val BadgeCorner: Dp = 13.dp
    val RowMinHeight: Dp = 56.dp
    val DividerIndent: Dp = 54.dp // BadgeSize (38) + 16dp padding
    val DragHandleWidth: Dp = 38.dp
    val DragHandleHeight: Dp = 4.dp
}
