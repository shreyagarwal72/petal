package com.petal.browser.compose.settings.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.android.material.color.utilities.Hct
import com.petal.browser.ui.components.PetalSpring
import com.petal.browser.ui.components.ScrollFadeRow
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.theme.ColorStyle

/**
 * Palette harmony style picker, ported from WiFi Weakspot: a connected button group with no words.
 * Each segment is its style's three accents blended into one soft mesh, so it shows the style's
 * mood for the current palette. The chosen segment widens into a pill with a frosted check; names
 * are kept for screen readers.
 *
 * With Liquid Glass on, every segment becomes a glass tile (translucent mesh over the wallpaper
 * backdrop, specular sheen and rim light) and the check turns into a glass badge.
 *
 * Up to [MAX_FILL_STYLES] styles share the row's width; with more (custom palettes add three) the
 * row keeps fixed widths and scrolls.
 */
@Composable
internal fun PetalColorStylePicker(
    styles: List<ColorStyle>,
    schemes: Map<ColorStyle, ColorScheme>,
    fallbackScheme: ColorScheme,
    selected: ColorStyle,
    dark: Boolean,
    edgeColor: Color,
    onSelect: (ColorStyle) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
) {
    val glass = isLiquidGlassEnabled()
    val description = "Palette harmony style"
    val fill = styles.size <= MAX_FILL_STYLES

    if (fill) {
        Row(
            modifier
                .fillMaxWidth()
                .height(height)
                .selectableGroup()
                .semantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(CONNECTED_GAP),
        ) {
            StyleSegments(styles, schemes, fallbackScheme, selected, dark, glass, height, onSelect) { weight ->
                Modifier.weight(weight)
            }
        }
    } else {
        val scrollState = rememberScrollState()
        ScrollFadeRow(scrollState = scrollState, edgeColor = edgeColor, modifier = modifier) {
            Row(
                Modifier
                    .height(height)
                    .horizontalScroll(scrollState)
                    .selectableGroup()
                    .semantics { contentDescription = description },
                horizontalArrangement = Arrangement.spacedBy(CONNECTED_GAP),
            ) {
                StyleSegments(styles, schemes, fallbackScheme, selected, dark, glass, height, onSelect) { weight ->
                    Modifier.width(SCROLL_SEGMENT_WIDTH * weight)
                }
            }
        }
    }
}

@Composable
private fun RowScope.StyleSegments(
    styles: List<ColorStyle>,
    schemes: Map<ColorStyle, ColorScheme>,
    fallbackScheme: ColorScheme,
    selected: ColorStyle,
    dark: Boolean,
    glass: Boolean,
    height: Dp,
    onSelect: (ColorStyle) -> Unit,
    widthFor: RowScope.(Float) -> Modifier,
) {
    styles.forEachIndexed { index, style ->
        val scheme = schemes[style] ?: fallbackScheme
        val mood = remember(scheme, dark) {
            moodColors(listOf(scheme.primary, scheme.secondary, scheme.tertiary), dark)
        }
        StyleSegment(
            label = style.label,
            mood = mood,
            selected = style == selected,
            first = index == 0,
            last = index == styles.lastIndex,
            height = height,
            glass = glass,
            widthFor = widthFor,
            onClick = { onSelect(style) },
        )
    }
}

private const val MAX_FILL_STYLES = 6
private val CONNECTED_GAP = 2.dp
private val SCROLL_SEGMENT_WIDTH = 52.dp

/** The three accents at one bright tone (a deeper one in dark mode), so every style is compared fairly. */
private fun moodColors(accents: List<Color>, dark: Boolean): List<Color> {
    val tones = if (dark) listOf(62.0, 68.0, 64.0) else listOf(72.0, 78.0, 74.0)
    return accents.zip(tones) { color, tone ->
        try {
            val hct = Hct.fromInt(color.toArgb())
            hct.setTone(tone)
            Color(hct.toInt())
        } catch (_: Throwable) {
            color
        }
    }
}

@Composable
private fun RowScope.StyleSegment(
    label: String,
    mood: List<Color>,
    selected: Boolean,
    first: Boolean,
    last: Boolean,
    height: Dp,
    glass: Boolean,
    widthFor: RowScope.(Float) -> Modifier,
    onClick: () -> Unit,
) {
    val width by animateFloatAsState(if (selected) 1.7f else 1f, PetalSpring.quick(), label = "styleWidth")
    // Outer ends stay round; inner corners open into a pill when chosen.
    val start by animateDpAsState(if (selected || first) height / 2 else 8.dp, PetalSpring.quick(), label = "styleStart")
    val end by animateDpAsState(if (selected || last) height / 2 else 8.dp, PetalSpring.quick(), label = "styleEnd")
    val shape = RoundedCornerShape(
        topStart = start.coerceAtLeast(0.dp),
        bottomStart = start.coerceAtLeast(0.dp),
        topEnd = end.coerceAtLeast(0.dp),
        bottomEnd = end.coerceAtLeast(0.dp),
    )
    // Glass lets the wallpaper backdrop through, so the mesh is drawn translucent over it.
    val meshAlpha = if (glass) 0.62f else 1f
    val primary = mood[0].copy(alpha = meshAlpha)
    val secondary = mood[1].copy(alpha = meshAlpha)
    val tertiary = mood[2].copy(alpha = meshAlpha)
    val topLight = if (glass) 0.22f else 0.35f

    Box(
        Modifier
            .then(widthFor(width.coerceAtLeast(0.1f)))
            .fillMaxHeight()
            .then(
                if (glass) Modifier.liquidGlassChrome(
                    shape = shape,
                    enabled = true,
                    sheenIntensity = 0.6f,
                    tintColor = mood[0],
                    pressFx = true,
                ) else Modifier
            )
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = { if (!selected) onClick() })
            .semantics { contentDescription = label }
            .drawBehind {
                // A soft mesh: primary to secondary across, tertiary glowing from the lower right, primary
                // from the upper left, and a little light along the top.
                drawRect(Brush.horizontalGradient(listOf(primary, secondary)))
                val low = Offset(size.width * .85f, size.height)
                drawCircle(
                    Brush.radialGradient(listOf(tertiary, tertiary.copy(alpha = 0f)), low, size.width * .7f),
                    size.width * .7f, low,
                )
                val high = Offset(size.width * .1f, 0f)
                drawCircle(
                    Brush.radialGradient(listOf(primary, primary.copy(alpha = 0f)), high, size.width * .6f),
                    size.width * .6f, high,
                )
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = topLight), Color.Transparent), endY = size.height * .55f))
            },
        contentAlignment = Alignment.Center,
    ) {
        // The frosted check grows in with the pill.
        val shown by animateFloatAsState(if (selected) 1f else 0f, PetalSpring.quick(), label = "styleCheck")
        if (shown > .01f) {
            val badge = Modifier
                .size(height * .58f)
                .graphicsLayer {
                    alpha = shown.coerceIn(0f, 1f)
                    scaleX = .6f + .4f * shown
                    scaleY = .6f + .4f * shown
                }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (glass) .60f else .78f))
                .then(if (glass) Modifier.border(1.dp, Color.White.copy(alpha = .55f), CircleShape) else Modifier)
            Box(badge, contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color(0xFF1B1B1F),
                    modifier = Modifier.size(height * .38f),
                )
            }
        }
    }
}
