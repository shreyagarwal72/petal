package com.petal.browser.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val IncognitoDarkBackground = Color(0xFF121214)
val IncognitoSurface = Color(0xFF1E1E22)
val IncognitoSurfaceContainer = Color(0xFF26262B)
val IncognitoSurfaceContainerHigh = Color(0xFF2E2E35)
val IncognitoPrimary = Color(0xFFD0BCFF)
val IncognitoOnPrimary = Color(0xFF381E72)
val IncognitoPrimaryContainer = Color(0xFF4F378B)
val IncognitoOnPrimaryContainer = Color(0xFFEADDFF)
val IncognitoSecondary = Color(0xFFCCC2DC)
val IncognitoOnSecondary = Color(0xFF332D41)
val IncognitoOutline = Color(0xFF938F99)
val IncognitoOnSurface = Color(0xFFE6E1E5)
val IncognitoOnSurfaceVariant = Color(0xFFCAC4D0)

val IncognitoColorScheme: ColorScheme = darkColorScheme(
    primary = IncognitoPrimary,
    onPrimary = IncognitoOnPrimary,
    primaryContainer = IncognitoPrimaryContainer,
    onPrimaryContainer = IncognitoOnPrimaryContainer,
    secondary = IncognitoSecondary,
    onSecondary = IncognitoOnSecondary,
    background = IncognitoDarkBackground,
    onBackground = IncognitoOnSurface,
    surface = IncognitoSurface,
    onSurface = IncognitoOnSurface,
    surfaceVariant = IncognitoSurfaceContainer,
    onSurfaceVariant = IncognitoOnSurfaceVariant,
    surfaceContainer = IncognitoSurfaceContainer,
    surfaceContainerHigh = IncognitoSurfaceContainerHigh,
    outline = IncognitoOutline
)

@Composable
fun PetalIncognitoTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = IncognitoColorScheme,
        typography = StrideTypography,
        content = content
    )
}
