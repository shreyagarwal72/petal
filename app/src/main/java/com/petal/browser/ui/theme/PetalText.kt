package com.petal.browser.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

private fun TextStyle.withTallerLineHeight(extraSp: Float = 2f): TextStyle {
    val next = if (lineHeight != TextUnit.Unspecified && lineHeight.isSp) {
        (lineHeight.value + extraSp).sp
    } else if (fontSize != TextUnit.Unspecified && fontSize.isSp) {
        (fontSize.value + 6f + extraSp).sp
    } else {
        22.sp
    }
    return copy(lineHeight = next)
}

/**
 * Shared semantic typography role layer for Petal Browser.
 * Every role reads [MaterialTheme.typography] and [LocalPetalFontAccent] so the active font
 * family, variable preset/axes, and font accent always apply consistently across the app.
 */
object PetalText {

    private val accent: PetalFontAccentColors
        @Composable
        @ReadOnlyComposable
        get() = LocalPetalFontAccent.current

    /** Heavy, wide rounded headline tier for list/settings row titles. */
    val rowTitle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold
        )

    /** Supporting subtitle below [rowTitle] with extra breathing room for multi-line wrapping. */
    val rowSubtitle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyMedium
            .withTallerLineHeight(2f)
            .copy(color = accent.bodyOr(MaterialTheme.colorScheme.onSurfaceVariant))

    /** Section category label above grouped containment cards. */
    val sectionHeader: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.Bold,
            color = accent.headingOr(MaterialTheme.colorScheme.primary)
        )

    /** Top app bar / screen header title (matching Zenith's headlineSmall ExtraBold). */
    val pageTitle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.ExtraBold,
            color = accent.headingOr(MaterialTheme.colorScheme.onSurface)
        )

    /** Supporting subtitle in page headers / hero subheaders. */
    val pageSubtitle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyMedium
            .withTallerLineHeight(2f)
            .copy(color = accent.bodyOr(MaterialTheme.colorScheme.onSurfaceVariant))

    /** Card / feature tile heading. */
    val cardTitle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold
        )

    /** Card descriptive body copy. */
    val cardBody: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyMedium
            .withTallerLineHeight(2f)
            .copy(color = accent.bodyOr(MaterialTheme.colorScheme.onSurfaceVariant))

    /** Alert/modal dialog heading. */
    val dialogTitle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.Bold
        )

    /** Alert/modal dialog supporting body text. */
    val dialogBody: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyMedium
            .withTallerLineHeight(2f)
            .copy(color = accent.bodyOr(MaterialTheme.colorScheme.onSurfaceVariant))

    /** Bottom sheet header title. */
    val sheetTitle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.Bold
        )

    /** Compact, normal-width heading for bottom sheet headers holding filenames, hosts, or URLs. */
    val sheetTitleCompact: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.titleMedium.copy(
            fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp
        )

    /** Compact URL text based on bodySmall with normal width and zero extra letter spacing. */
    val urlText: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.sp,
            color = accent.bodyOr(MaterialTheme.colorScheme.onSurfaceVariant)
        )

    /** List item primary title (bookmarks, history, downloads, tabs, files, extensions). */
    val listItemTitle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold
        )

    /** List item secondary metadata (timestamps, sizes, URLs, domains). */
    val listItemMeta: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.bodySmall
            .withTallerLineHeight(2f)
            .copy(color = accent.bodyOr(MaterialTheme.colorScheme.onSurfaceVariant))

    /** Filter / assist / suggestion / input chip labels (inherits chip contentColor). */
    val chip: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.SemiBold,
            color = Color.Unspecified
        )

    /** Primary / tonal / outlined / text button labels (inherits button contentColor). */
    val button: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.Bold,
            color = Color.Unspecified
        )

    /** Tab strip / segmented switcher labels (inherits tab contentColor). */
    val tabLabel: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.Bold,
            color = Color.Unspecified
        )

    /** Small status pills, beta tags, and counter badges (inherits badge contentColor). */
    val badge: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            color = Color.Unspecified
        )

    /** Trailing numeric or selected-state values in rows and sliders. */
    val value: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.Bold,
            color = accent.labelOr(MaterialTheme.colorScheme.primary)
        )

    /** Helper captions, timestamps, and footnotes. */
    val caption: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.bodySmall
            .withTallerLineHeight(2f)
            .copy(color = accent.labelOr(MaterialTheme.colorScheme.onSurfaceVariant))

    /** Empty state headlines (empty bookmarks, history, downloads, search, tabs). */
    val emptyState: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold
        )
}
