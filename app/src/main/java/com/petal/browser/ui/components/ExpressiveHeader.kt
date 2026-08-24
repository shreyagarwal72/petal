package com.petal.browser.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Only the bottom corners are rounded (24dp) for a clean Material 3 Expressive header look */
private val HeaderShape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)

/**
 * Specular dressing that makes a translucent surface read as glass:
 * a soft top sheen plus a 1dp diagonal hairline border.
 */
fun Modifier.liquidGlassChrome(shape: Shape, enabled: Boolean = true): Modifier =
    if (!enabled) this else drawWithContent {
        drawContent()
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = when (outline) {
            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
            is Outline.Generic -> outline.path
            is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
        }

        clipPath(path) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.10f),
                    0.55f to Color.White.copy(alpha = 0.02f),
                    1f to Color.Transparent,
                    startY = 0f,
                    endY = size.height,
                ),
            )
        }

        drawPath(
            path = path,
            brush = Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.34f),
                0.45f to Color.White.copy(alpha = 0.07f),
                1f to Color.White.copy(alpha = 0.16f),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
            style = Stroke(width = 1.dp.toPx()),
        )
    }

/**
 * Material 3 Expressive Header component ported from LastWave-native (duxtami).
 * Features full-bleed edge-to-edge width, dynamic radial glow animations,
 * optional leading back button, title/subtitle layout, and trailing actions.
 */
@Composable
fun ExpressiveHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    enableLiquidGlass: Boolean = false,
    maxTitleLines: Int = 2,
    maxSubtitleLines: Int = 2,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val glow = MaterialTheme.colorScheme.primary
    val secondaryGlow = MaterialTheme.colorScheme.tertiary

    Box(modifier.fillMaxWidth().zIndex(1f)) {
        Surface(
            shape = HeaderShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth().liquidGlassChrome(HeaderShape, enableLiquidGlass),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .drawGlowBackground(glow, secondaryGlow)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    )
                    .padding(horizontal = 20.dp)
                    .padding(top = 2.dp, bottom = 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBack != null) {
                        FilledTonalIconButton(
                            onClick = onBack,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(Modifier.width(12.dp))
                    }

                    Column(Modifier.weight(1f)) {
                        Text(
                            title,
                            style = if (onBack != null) {
                                if (maxTitleLines == 1) MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp)
                                else MaterialTheme.typography.titleLarge
                            } else MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = maxTitleLines,
                            overflow = TextOverflow.Ellipsis,
                        )
                        subtitle?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = maxSubtitleLines,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        actions()
                    }
                }
            }
        }
    }
}

/**
 * Paints two soft radial glows whose centers drift side-to-side when foregrounded.
 */
@Composable
private fun Modifier.drawGlowBackground(color: Color, secondaryColor: Color): Modifier {
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            resumed = event == Lifecycle.Event.ON_RESUME
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val drift: Float = if (resumed) {
        val transition = rememberInfiniteTransition(label = "headerGlowDrift")
        val animated by transition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 7000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "headerGlowDriftX",
        )
        animated
    } else {
        0f
    }
    return this.drawBehind {
        val cx = size.width / 2f + drift * size.width * 0.30f
        val cy = size.height * 0.46f
        val radius = (size.width.coerceAtLeast(size.height) * 1.08f).coerceAtLeast(1f)
        val secondaryCx = size.width / 2f - drift * size.width * 0.24f
        val secondaryRadius = (size.width.coerceAtLeast(size.height) * 0.72f).coerceAtLeast(1f)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    secondaryColor.copy(alpha = 0.13f),
                    secondaryColor.copy(alpha = 0.045f),
                    secondaryColor.copy(alpha = 0f),
                ),
                center = Offset(secondaryCx, size.height * 0.78f),
                radius = secondaryRadius,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = 0.23f),
                    color.copy(alpha = 0.07f),
                    color.copy(alpha = 0f),
                ),
                center = Offset(cx, cy),
                radius = radius,
            ),
        )
    }
}

/**
 * Trailing action icon for [ExpressiveHeader].
 */
@Composable
fun HeaderActionIcon(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}
