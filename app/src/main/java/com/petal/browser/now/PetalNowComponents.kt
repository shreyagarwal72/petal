package com.petal.browser.now

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.petal.browser.ui.components.PetalMorphShape
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.theme.LocalExpressiveShapesEnabled
import com.petal.browser.ui.theme.PetalMaterialShapes
import kotlin.math.PI
import kotlin.math.sin

internal data class PetalNowResolvedAccent(
    val container: Color,
    val onContainer: Color,
)

@Composable
internal fun rememberPetalNowAccent(
    entry: PetalNowEntry,
    motion: PetalNowMotion,
): PetalNowResolvedAccent {
    val cs = MaterialTheme.colorScheme
    val rawTarget = when {
        entry.accentOverrideColor != null -> entry.accentOverrideColor
        entry.accentRole == PetalNowAccentRole.PRIMARY -> cs.primary
        entry.accentRole == PetalNowAccentRole.SECONDARY -> cs.secondary
        entry.accentRole == PetalNowAccentRole.TERTIARY -> cs.tertiary
        entry.accentRole == PetalNowAccentRole.ERROR -> cs.error
        entry.accentRole == PetalNowAccentRole.WARNING -> cs.tertiary
        entry.accentRole == PetalNowAccentRole.SUCCESS -> cs.primary
        else -> cs.onSurfaceVariant
    }
    val containerTarget = when {
        entry.accentOverrideColor != null -> entry.accentOverrideColor.copy(alpha = 0.22f)
        entry.accentRole == PetalNowAccentRole.PRIMARY -> cs.primaryContainer
        entry.accentRole == PetalNowAccentRole.SECONDARY -> cs.secondaryContainer
        entry.accentRole == PetalNowAccentRole.TERTIARY -> cs.tertiaryContainer
        entry.accentRole == PetalNowAccentRole.ERROR -> cs.errorContainer
        entry.accentRole == PetalNowAccentRole.WARNING -> cs.tertiaryContainer.copy(alpha = 0.85f)
        entry.accentRole == PetalNowAccentRole.SUCCESS -> cs.primaryContainer
        else -> cs.surfaceContainerHighest
    }
    val contentTarget = when {
        entry.accentOverrideColor != null -> rawTarget
        entry.accentRole == PetalNowAccentRole.PRIMARY -> cs.onPrimaryContainer
        entry.accentRole == PetalNowAccentRole.SECONDARY -> cs.onSecondaryContainer
        entry.accentRole == PetalNowAccentRole.TERTIARY -> cs.onTertiaryContainer
        entry.accentRole == PetalNowAccentRole.ERROR -> cs.onErrorContainer
        entry.accentRole == PetalNowAccentRole.WARNING -> cs.onTertiaryContainer
        entry.accentRole == PetalNowAccentRole.SUCCESS -> cs.onPrimaryContainer
        else -> cs.onSurface
    }

    val colorSpec = tween<Color>(durationMillis = if (motion.reduceMotion) 1 else 220)
    val animatedContainer by animateColorAsState(containerTarget, animationSpec = colorSpec, label = "nowAccentBg")
    val animatedContent by animateColorAsState(contentTarget, animationSpec = colorSpec, label = "nowAccentFg")
    return PetalNowResolvedAccent(animatedContainer, animatedContent)
}

internal fun PetalNowBadgeShapeKind.toPolygon(): RoundedPolygon = when (this) {
    PetalNowBadgeShapeKind.CIRCLE -> PetalMaterialShapes.Circle.polygon
    PetalNowBadgeShapeKind.COOKIE_7 -> PetalMaterialShapes.Cookie7Sided.polygon
    PetalNowBadgeShapeKind.FLOWER -> PetalMaterialShapes.Flower.polygon
    PetalNowBadgeShapeKind.SUNNY -> PetalMaterialShapes.Sunny.polygon
    PetalNowBadgeShapeKind.BUN -> PetalMaterialShapes.Bun.polygon
    PetalNowBadgeShapeKind.GEM -> PetalMaterialShapes.Gem.polygon
    PetalNowBadgeShapeKind.CLOVER_4 -> PetalMaterialShapes.Clover4Leaf.polygon
    PetalNowBadgeShapeKind.GHOSTISH -> PetalMaterialShapes.Ghostish.polygon
    PetalNowBadgeShapeKind.PUFFY -> PetalMaterialShapes.Puffy.polygon
}

/**
 * M3 Expressive morphing badge for Petal Now.
 *
 * When [PetalNowEntry.stateFlipActive] flips (such as Ad Blocker ON <-> OFF), both the polygon
 * silhouette and a subtle 3D rotation / spring scale pulse run cleanly in the graphics layer.
 */
@Composable
internal fun PetalNowShapeBadge(
    entry: PetalNowEntry,
    size: Dp,
    iconSize: Dp,
    motion: PetalNowMotion,
    modifier: Modifier = Modifier,
) {
    val expressiveShapesEnabled = LocalExpressiveShapesEnabled.current
    val accent = rememberPetalNowAccent(entry, motion)
    val flipActive = entry.stateFlipActive

    val morphTarget = if (flipActive == false) 1f else 0f
    val morphFraction by animateFloatAsState(
        targetValue = morphTarget,
        animationSpec = motion.shapeMorph(),
        label = "nowBadgeMorph",
    )

    val flipRotation by animateFloatAsState(
        targetValue = if (flipActive == true) 360f else 0f,
        animationSpec = motion.float(320),
        label = "nowBadgeFlip",
    )

    val flipPulse = remember { Animatable(1f) }
    LaunchedEffect(flipActive) {
        if (flipActive != null) {
            motion.pop(flipPulse, peak = 1.14f)
        }
    }

    val shape: Shape = remember(entry.badgeShape, entry.secondaryBadgeShape, expressiveShapesEnabled, morphFraction) {
        if (!expressiveShapesEnabled) {
            CircleShape
        } else if (entry.badgeShape == entry.secondaryBadgeShape) {
            PetalMorphShape(Morph(entry.badgeShape.toPolygon(), entry.badgeShape.toPolygon()), 0f)
        } else {
            val fromPoly = entry.badgeShape.toPolygon()
            val toPoly = entry.secondaryBadgeShape.toPolygon()
            PetalMorphShape(Morph(fromPoly, toPoly), morphFraction.coerceIn(0f, 1f))
        }
    }

    val bitmap = remember(entry.albumArt) { entry.albumArt?.asImageBitmap() }
    val densityPx = LocalDensity.current.density

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = flipPulse.value
                scaleY = flipPulse.value
                if (flipActive != null && !motion.reduceMotion) {
                    rotationY = flipRotation
                    cameraDistance = 10f * densityPx
                }
            }
            .clip(shape)
            .background(accent.container),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bitmap != null -> {
                Image(
                    bitmap = bitmap,
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            entry.kind == PetalNowCardKind.MEDIA && entry.isAudioPlaying -> {
                PetalNowWaveformVisualizer(
                    isPlaying = true,
                    color = accent.onContainer,
                    reduceMotion = motion.reduceMotion,
                )
            }
            else -> {
                Icon(
                    imageVector = entry.icon,
                    contentDescription = entry.title,
                    tint = accent.onContainer,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

/**
 * Animated equalizer bars reused in Petal Now's media capsule states.
 */
@Composable
internal fun PetalNowWaveformVisualizer(
    isPlaying: Boolean,
    color: Color,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val heights = if (isPlaying && !reduceMotion) {
        val transition = rememberInfiniteTransition(label = "petalNowWaveform")
        List(4) { idx ->
            transition.animateFloat(
                initialValue = 4f,
                targetValue = 14f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 320 + idx * 110, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar_$idx",
            ).value
        }
    } else {
        listOf(5f, 9f, 6f, 8f)
    }

    Row(
        modifier = modifier.height(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(h.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

/**
 * Material 3 Expressive wavy progress indicator for downloads, media, backups, and sync tasks.
 *
 * Uses a sinusoidal elapsed stroke paired with a rounded separator bar and flat remaining track,
 * gracefully degrading to a crisp flat progress line when [isWavy] or animation is disabled.
 */
@Composable
internal fun PetalNowWavyProgressBar(
    progress: PetalNowProgress,
    motion: PetalNowMotion,
    modifier: Modifier = Modifier,
    waveColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val activeWave = progress.isWavy && !progress.isPaused && !motion.reduceMotion
    val maxAmplitude = 3.5.dp
    val amplitude by animateDpAsState(
        targetValue = if (activeWave) maxAmplitude else 0.dp,
        animationSpec = tween(durationMillis = if (motion.reduceMotion) 1 else 320, easing = FastOutSlowInEasing),
        label = "nowWaveAmp",
    )

    val phase = if (activeWave) {
        rememberInfiniteTransition(label = "nowWavePhase").animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1350, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "nowPhase",
        ).value
    } else {
        0f
    }

    val indeterminatePos = if (progress.isIndeterminate && !motion.reduceMotion) {
        rememberInfiniteTransition(label = "nowIndet").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "nowIndetVal",
        ).value
    } else {
        progress.fraction ?: 0.5f
    }

    val targetFraction = (progress.fraction ?: indeterminatePos).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = if (progress.isIndeterminate) tween(16) else motion.fade(180),
        label = "nowProgFrac",
    )

    val barHeight = 14.dp

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight),
    ) {
        val w = size.width
        val sepPx = 3.dp.toPx()
        if (w < sepPx * 2f) return@Canvas

        val centerY = size.height / 2f
        val strokePx = 3.5.dp.toPx()
        val ampPx = amplitude.toPx().coerceAtLeast(0f)
        val gapPx = 3.5.dp.toPx()
        val sepHalfH = 6.dp.toPx()

        if (progress.isIndeterminate) {
            drawLine(
                color = trackColor,
                start = Offset(0f, centerY),
                end = Offset(w, centerY),
                strokeWidth = strokePx,
                cap = StrokeCap.Round,
            )
            val segW = (w * 0.34f).coerceAtLeast(sepPx * 2f)
            val startX = (w - segW) * animatedFraction
            val endX = (startX + segW).coerceAtMost(w)
            drawWavySegment(
                startX = startX,
                endX = endX,
                centerY = centerY,
                ampPx = ampPx,
                phase = phase,
                freq = 0.085f,
                color = waveColor,
                strokePx = strokePx,
            )
            return@Canvas
        }

        val sepX = (w * animatedFraction).coerceIn(sepPx / 2f, w - sepPx / 2f)
        val waveEnd = sepX - sepPx / 2f - gapPx
        val trackStart = sepX + sepPx / 2f + gapPx

        if (trackStart < w) {
            drawLine(
                color = trackColor,
                start = Offset(trackStart, centerY),
                end = Offset(w, centerY),
                strokeWidth = strokePx,
                cap = StrokeCap.Round,
            )
        }

        if (waveEnd > 0f) {
            drawWavySegment(
                startX = 0f,
                endX = waveEnd,
                centerY = centerY,
                ampPx = ampPx,
                phase = phase,
                freq = 0.085f,
                color = waveColor,
                strokePx = strokePx,
            )
        }

        drawLine(
            color = waveColor,
            start = Offset(sepX, centerY - sepHalfH + sepPx / 2f),
            end = Offset(sepX, centerY + sepHalfH - sepPx / 2f),
            strokeWidth = sepPx,
            cap = StrokeCap.Round,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWavySegment(
    startX: Float,
    endX: Float,
    centerY: Float,
    ampPx: Float,
    phase: Float,
    freq: Float,
    color: Color,
    strokePx: Float,
) {
    if (endX <= startX) return
    if (ampPx <= 0.1f) {
        drawLine(
            color = color,
            start = Offset(startX, centerY),
            end = Offset(endX, centerY),
            strokeWidth = strokePx,
            cap = StrokeCap.Round,
        )
        return
    }
    val path = Path()
    path.moveTo(startX, centerY + sin(startX * freq - phase) * ampPx)
    var x = startX + 2f
    while (x < endX) {
        path.lineTo(x, centerY + sin(x * freq - phase) * ampPx)
        x += 2f
    }
    path.lineTo(endX, centerY + sin(endX * freq - phase) * ampPx)
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokePx, cap = StrokeCap.Round),
    )
}

/**
 * Secondary circular satellite bubble docked beside the collapsed/compact capsule when two
 * activities are active at once.
 */
@Composable
internal fun PetalNowSatelliteBubble(
    entry: PetalNowEntry,
    isGlass: Boolean,
    motion: PetalNowMotion,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val diameter = 38.dp
    val accent = rememberPetalNowAccent(entry, motion)
    val surfaceColor = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh
    val borderStroke = if (!isGlass) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    } else null

    Surface(
        modifier = modifier
            .size(diameter)
            .liquidGlassChrome(shape = CircleShape, enabled = isGlass, pressFx = false)
            .pointerInput(entry.slotKey) {
                detectTapGestures(onTap = { onClick() })
            },
        shape = CircleShape,
        color = surfaceColor,
        tonalElevation = if (isGlass) 0.dp else 2.dp,
        shadowElevation = 0.dp,
        border = borderStroke,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (entry.progress?.fraction != null) {
                CircularProgressIndicator(
                    progress = { entry.progress.fraction },
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 2.5.dp,
                    color = accent.onContainer,
                    trackColor = accent.container.copy(alpha = 0.45f),
                )
            }
            PetalNowShapeBadge(
                entry = entry,
                size = 24.dp,
                iconSize = 14.dp,
                motion = motion,
            )
        }
    }
}
