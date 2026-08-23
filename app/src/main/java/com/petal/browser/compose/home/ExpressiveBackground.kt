/*
 * ExpressiveBackground.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Subtle, layered Material 3 Expressive "living" background used behind the
 * Petal home screen. Three soft, asymmetric blob shapes sit at different
 * depths and gently morph over a long cycle. Everything here is purely
 * decorative and drawn *behind* content — it never intercepts touch and
 * never affects layout of anything else on the screen.
 *
 * Deliberately built on plain Canvas + trig-based Path generation (the same
 * technique already used by FlowerShape/CloverShape/StarburstShape in
 * PetalHomeScreen.kt) instead of androidx.compose.material3 MaterialShapes.
 * MaterialShapes/shape-morphing APIs require ExperimentalMaterial3ExpressiveApi
 * and have been the source of several CI build failures in this project
 * (unresolved MaterialShapes/toShape imports, wrong local opt-in annotation
 * shadowing the real one in PetalTheme.kt). A hand-rolled path avoids that
 * fragility entirely while producing the same organic morphing look.
 *
 * MIT License — Copyright (c) 2026
 */

package com.petal.browser.compose.home

import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** True when the system animator scale is 0 (Settings > Accessibility > Remove animations). */
@Composable
private fun isReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        val scale = try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (_: Throwable) {
            1f
        }
        scale == 0f
    }
}

/** True when the device is in battery saver — background motion is skipped to save power. */
@Composable
private fun isBatterySaverEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        try {
            val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as? PowerManager
            pm?.isPowerSaveMode == true
        } catch (_: Throwable) {
            false
        }
    }
}

private data class BlobSpec(
    val centerFraction: Offset, // position of blob center as a fraction of canvas size
    val radiusFraction: Float,  // blob radius as a fraction of the shorter canvas dimension
    val lobes: Int,             // how many soft "petal-like" lobes the blob has
    val variance: Float,        // how much the radius wobbles per lobe (0..1)
    val phaseSpeed: Float,      // relative animation speed for this layer
    val phaseOffset: Float      // starting phase so layers don't sync up
)

private val blobLayers = listOf(
    BlobSpec(Offset(0.92f, -0.06f), 0.62f, lobes = 5, variance = 0.16f, phaseSpeed = 1f, phaseOffset = 0f),
    BlobSpec(Offset(-0.12f, 0.86f), 0.50f, lobes = 4, variance = 0.20f, phaseSpeed = 0.7f, phaseOffset = 2.1f),
    BlobSpec(Offset(0.78f, 0.62f), 0.30f, lobes = 6, variance = 0.14f, phaseSpeed = 1.3f, phaseOffset = 4.3f)
)

/**
 * Builds a smooth, closed organic blob path around [center] with base [radius],
 * softly bulging in/out across [lobes] lobes. [phase] shifts the wobble over time
 * to produce a gentle morphing effect — the silhouette breathes, it never spins.
 */
private fun blobPath(center: Offset, radius: Float, lobes: Int, variance: Float, phase: Float): Path {
    val path = Path()
    val steps = 90
    var first = true
    for (i in 0..steps) {
        val t = i.toDouble() / steps
        val angle = t * 2.0 * Math.PI
        val wobble = 1f + variance * sin(lobes * angle + phase).toFloat()
        val r = radius * wobble
        val x = center.x + (r * cos(angle)).toFloat()
        val y = center.y + (r * sin(angle)).toFloat()
        if (first) {
            path.moveTo(x, y)
            first = false
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()
    return path
}

/**
 * Layered, gently morphing Material 3 Expressive background. Draw this as the
 * bottom-most layer inside a Box, with real content composed on top of it.
 * Extremely low alpha by design — it should read as depth and warmth, not as
 * a pattern competing with the search bar, shortcuts, or text.
 */
@Composable
fun ExpressiveMorphingBackground(
    modifier: Modifier = Modifier,
    colors: List<Color>
) {
    val animate = !isReducedMotionEnabled() && !isBatterySaverEnabled()
    val transition = rememberInfiniteTransition(label = "petal_bg_morph")
    val phase = if (animate) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 26000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "petal_bg_phase"
        ).value
    } else {
        0f
    }

    Canvas(
        modifier = modifier
            .blur(56.dp)
    ) {
        val shortSide = kotlin.math.min(size.width, size.height)
        blobLayers.forEachIndexed { index, spec ->
            val center = Offset(size.width * spec.centerFraction.x, size.height * spec.centerFraction.y)
            val radius = shortSide * spec.radiusFraction
            val layerPhase = spec.phaseOffset + phase * spec.phaseSpeed
            val path = blobPath(center, radius, spec.lobes, spec.variance, layerPhase)
            val color = colors[index % colors.size]
            drawPath(path = path, color = color)
        }
    }
}

@Composable
fun defaultPetalBackgroundColors(): List<Color> {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    return remember(scheme) {
        listOf(
            scheme.primaryContainer.copy(alpha = 0.20f),
            scheme.tertiaryContainer.copy(alpha = 0.16f),
            scheme.secondaryContainer.copy(alpha = 0.14f)
        )
    }
}
