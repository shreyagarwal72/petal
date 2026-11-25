package com.petal.browser.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/**
 * Runtime accent colors for typography, guaranteed >= 4.5:1 contrast against the resolved theme
 * surfaces so light/dark/AMOLED/Dynamic Color palettes always remain readable.
 */
@Immutable
data class PetalFontAccentColors(
    val mode: FontAccentMode,
    val headingColor: Color,
    val labelColor: Color,
    val bodyColor: Color
) {
    fun headingOr(fallback: Color): Color =
        if (mode == FontAccentMode.OFF || headingColor == Color.Unspecified) fallback else headingColor

    fun labelOr(fallback: Color): Color =
        if (mode == FontAccentMode.HEADINGS_AND_LABELS || mode == FontAccentMode.ALL) {
            if (labelColor != Color.Unspecified) labelColor else fallback
        } else fallback

    fun bodyOr(fallback: Color): Color =
        if (mode == FontAccentMode.ALL && bodyColor != Color.Unspecified) bodyColor else fallback

    companion object {
        val Off = PetalFontAccentColors(
            mode = FontAccentMode.OFF,
            headingColor = Color.Unspecified,
            labelColor = Color.Unspecified,
            bodyColor = Color.Unspecified
        )

        const val MIN_WCAG_CONTRAST = 4.5f

        fun contrastRatio(foreground: Color, background: Color): Float {
            val bgOpaque = if (background.alpha < 1f) background.copy(alpha = 1f) else background
            val fgResolved = if (foreground.alpha < 1f) foreground.compositeOver(bgOpaque) else foreground
            val l1 = fgResolved.luminance()
            val l2 = bgOpaque.luminance()
            return (max(l1, l2) + 0.05f) / (min(l1, l2) + 0.05f)
        }

        fun accessibleInk(
            accent: Color,
            onSurfaceFallback: Color,
            vararg surfaces: Color
        ): Color {
            if (accent == Color.Unspecified) return onSurfaceFallback
            val passes = surfaces.all { bg ->
                bg == Color.Unspecified || contrastRatio(accent, bg) >= MIN_WCAG_CONTRAST
            }
            return if (passes) accent else onSurfaceFallback
        }

        fun resolve(mode: FontAccentMode, colorScheme: ColorScheme): PetalFontAccentColors {
            if (mode == FontAccentMode.OFF) return Off
            val safeAccent = accessibleInk(
                accent = colorScheme.primary,
                onSurfaceFallback = colorScheme.onSurface,
                colorScheme.surface,
                colorScheme.background,
                colorScheme.surfaceContainerLow,
                colorScheme.surfaceContainerHigh
            )
            return PetalFontAccentColors(
                mode = mode,
                headingColor = safeAccent,
                labelColor = if (mode == FontAccentMode.HEADINGS_AND_LABELS || mode == FontAccentMode.ALL) safeAccent else Color.Unspecified,
                bodyColor = if (mode == FontAccentMode.ALL) safeAccent else Color.Unspecified
            )
        }
    }
}

val LocalPetalFontAccent = compositionLocalOf { PetalFontAccentColors.Off }

@Composable
fun petalAccentHeadingColor(default: Color): Color = LocalPetalFontAccent.current.headingOr(default)

@Composable
fun petalAccentLabelColor(default: Color): Color = LocalPetalFontAccent.current.labelOr(default)

@Composable
fun petalAccentBodyColor(default: Color): Color = LocalPetalFontAccent.current.bodyOr(default)

/**
 * Applies [accent] into Material 3 [Typography] styles. Note: `labelLarge` intentionally retains
 * [Color.Unspecified] so filled Material 3 `Button(containerColor = primary)` continues inheriting
 * `onPrimary` from `LocalContentColor`, while `labelMedium`/`labelSmall` and `LocalPetalFontAccent`
 * color standalone labels and captions cleanly.
 */
fun Typography.withFontAccent(accent: PetalFontAccentColors): Typography {
    if (accent.mode == FontAccentMode.OFF) return this
    val hColor = accent.headingColor
    val lColor = accent.labelColor
    val bColor = accent.bodyColor
    return copy(
        displayLarge = if (hColor != Color.Unspecified) displayLarge.copy(color = hColor) else displayLarge,
        displayMedium = if (hColor != Color.Unspecified) displayMedium.copy(color = hColor) else displayMedium,
        displaySmall = if (hColor != Color.Unspecified) displaySmall.copy(color = hColor) else displaySmall,
        headlineLarge = if (hColor != Color.Unspecified) headlineLarge.copy(color = hColor) else headlineLarge,
        headlineMedium = if (hColor != Color.Unspecified) headlineMedium.copy(color = hColor) else headlineMedium,
        headlineSmall = if (hColor != Color.Unspecified) headlineSmall.copy(color = hColor) else headlineSmall,
        titleLarge = if (hColor != Color.Unspecified) titleLarge.copy(color = hColor) else titleLarge,
        titleMedium = if (hColor != Color.Unspecified) titleMedium.copy(color = hColor) else titleMedium,
        titleSmall = if (hColor != Color.Unspecified) titleSmall.copy(color = hColor) else titleSmall,
        labelMedium = if (lColor != Color.Unspecified) labelMedium.copy(color = lColor) else labelMedium,
        labelSmall = if (lColor != Color.Unspecified) labelSmall.copy(color = lColor) else labelSmall,
        bodyLarge = if (bColor != Color.Unspecified) bodyLarge.copy(color = bColor) else bodyLarge,
        bodyMedium = if (bColor != Color.Unspecified) bodyMedium.copy(color = bColor) else bodyMedium,
        bodySmall = if (bColor != Color.Unspecified) bodySmall.copy(color = bColor) else bodySmall
    )
}
