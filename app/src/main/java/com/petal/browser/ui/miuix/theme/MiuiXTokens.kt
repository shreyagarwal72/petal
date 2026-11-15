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

/**
 * Maps the real Miuix color scheme ([top.yukonga.miuix.kmp.theme.Colors]) onto the legacy
 * [MiuiXColors] shape so not-yet-migrated call sites keep compiling while reading real tokens.
 * There are no guessed colors left in Petal: every value below comes from MiuixTheme.colorScheme.
 */
fun top.yukonga.miuix.kmp.theme.Colors.toMiuiXColors(isDark: Boolean): MiuiXColors = MiuiXColors(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariantSummary,
    outline = outline,
    divider = dividerLine,
    track = secondaryContainer,
    error = error,
    onError = onError,
    isDark = isDark,
)

/**
 * Source-compatible shim for the old CompositionLocal. `LocalMiuiXColors.current` now resolves
 * to the real Miuix tokens (MiuixTheme.colorScheme); there is nothing to provide any more.
 * Removed once the Step 3/4 adapters replace the legacy MiuiX components.
 */
object LocalMiuiXColors {
    val current: MiuiXColors
        @Composable
        get() = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.toMiuiXColors(LocalIsDark.current)
}
