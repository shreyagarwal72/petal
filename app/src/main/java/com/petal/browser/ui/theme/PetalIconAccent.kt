/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.containment.petalBadgeShape

/**
 * Petal's universal icon accent classification:
 * - [PRIMARY]: Main actions, primary navigation, selected states.
 * - [SECONDARY]: Supporting features and tools (downloads, extensions, media, store).
 * - [TERTIARY]: Expressive and special features (AI, appearance, glass, scanner, themes).
 * - [ERROR]: Destructive actions, warnings, critical state changes.
 * - [NEUTRAL]: Standard row icons, inert list items, secondary labels.
 */
enum class PetalIconAccent {
    PRIMARY,
    SECONDARY,
    TERTIARY,
    ERROR,
    NEUTRAL;

    /**
     * Resolves the (container, tint) color pair for badge-enclosed icons from [colorScheme].
     * Integrates with dynamic color, ColorStyle, custom palette, and AMOLED.
     */
    fun resolveColors(colorScheme: ColorScheme): Pair<Color, Color> = when (this) {
        PRIMARY -> colorScheme.primaryContainer to colorScheme.onPrimaryContainer
        SECONDARY -> colorScheme.secondaryContainer to colorScheme.onSecondaryContainer
        TERTIARY -> colorScheme.tertiaryContainer to colorScheme.onTertiaryContainer
        ERROR -> colorScheme.errorContainer to colorScheme.onErrorContainer
        NEUTRAL -> colorScheme.surfaceContainerHighest to colorScheme.onSurfaceVariant
    }

    /**
     * Resolves standalone icon tint when rendered directly on a surface (without container).
     */
    fun resolveStandaloneTint(colorScheme: ColorScheme): Color = when (this) {
        PRIMARY -> colorScheme.primary
        SECONDARY -> colorScheme.secondary
        TERTIARY -> colorScheme.tertiary
        ERROR -> colorScheme.error
        NEUTRAL -> colorScheme.onSurfaceVariant
    }
}

/**
 * Ambient composition local providing the default [PetalIconAccent] for a sub-tree.
 */
val LocalPetalIconAccent = compositionLocalOf { PetalIconAccent.NEUTRAL }

/**
 * Standalone icon component adhering to the Petal Accent system.
 */
@Composable
fun PetalIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    accent: PetalIconAccent = LocalPetalIconAccent.current,
) {
    val tint = accent.resolveStandaloneTint(MaterialTheme.colorScheme)
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier
    )
}

/**
 * Standalone painter icon component adhering to the Petal Accent system.
 */
@Composable
fun PetalIcon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    accent: PetalIconAccent = LocalPetalIconAccent.current,
) {
    val tint = accent.resolveStandaloneTint(MaterialTheme.colorScheme)
    Icon(
        painter = painter,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier
    )
}

/**
 * Canonical Icon Badge component: renders an icon inside the user's selected [PetalBadgeShape]
 * (Mixed, Follow containment, Circle, Soft square, Cookie, Sunny, Flower, Clover, Soft burst, Puffy, Scallop).
 *
 * Guaranteed to be 100% stable across launches and recompositions by anchoring shape selection
 * to the icon's unique identifier.
 */
@Composable
fun PetalIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accent: PetalIconAccent = LocalPetalIconAccent.current,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    shape: Shape = petalBadgeShape(icon.name),
    contentDescription: String? = null,
) {
    val (container, tint) = accent.resolveColors(MaterialTheme.colorScheme)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Canonical Painter Icon Badge component: renders a painter inside the user's selected [PetalBadgeShape].
 */
@Composable
fun PetalIconBadge(
    painter: Painter,
    modifier: Modifier = Modifier,
    accent: PetalIconAccent = LocalPetalIconAccent.current,
    shapeKey: Any = "painter_badge",
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    shape: Shape = petalBadgeShape(shapeKey),
    contentDescription: String? = null,
) {
    val (container, tint) = accent.resolveColors(MaterialTheme.colorScheme)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}
