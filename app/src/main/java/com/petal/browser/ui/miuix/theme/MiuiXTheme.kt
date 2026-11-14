package com.petal.browser.ui.miuix.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.petal.browser.ui.containment.PetalBackdrop
import com.petal.browser.ui.containment.rememberPetalBackdrop

/**
 * Accessor for the active MiuiX design tokens.
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
}

/**
 * Xiaomi HyperOS / MIUIX Theme Provider.
 */
@Composable
fun MiuiXTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    backdrop: PetalBackdrop? = null,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) MiuiXDarkColors else MiuiXLightColors

    // Map M3 shapes to continuous squircle equivalents
    val squircleShapes = Shapes(
        extraSmall = MiuiXShapes.ExtraSmall,
        small = MiuiXShapes.Small,
        medium = MiuiXShapes.Medium,
        large = MiuiXShapes.Large,
        extraLarge = MiuiXShapes.ExtraLarge,
    )

    CompositionLocalProvider(
        LocalMiuiXColors provides colors,
    ) {
        content()
    }
}
