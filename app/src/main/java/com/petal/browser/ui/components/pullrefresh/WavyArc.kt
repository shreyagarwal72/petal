package com.petal.browser.ui.components.pullrefresh

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.components.PullPhase
import com.petal.browser.ui.components.PullRefreshTimeline
import com.petal.browser.ui.components.pullRefreshGlyphChrome
import com.petal.browser.ui.components.scaledMs
import com.petal.browser.ui.components.scaledStiffness
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val WAVES = 8
private val TWO_PI = (2.0 * PI).toFloat()

/**
 * WAVY_ARC: a wavy ring drawn by hand with a Path (CircularWavyProgressIndicator only animates
 * its own indeterminate loop and offers no way to tie the wave to the pull, so it is not reused).
 *
 *  - Sweep = pull fraction * 360 degrees.
 *  - Wave amplitude eases in with the pull; the wave phase advances with the drag.
 *  - Refreshing: a gap opens in the ring, the ring rotates and the wave keeps travelling.
 */
@Composable
fun WavyArcIndicator(timeline: PullRefreshTimeline) {
    val scheme = MaterialTheme.colorScheme
    val color = scheme.primary
    val track = scheme.primary.copy(alpha = 0.16f)
    val refreshing = timeline.phase == PullPhase.REFRESHING
    val active = timeline.animationsActive

    val wave = remember { Animatable(0f) }   // radians, travelling wave while refreshing
    val rotation = remember { Animatable(0f) } // degrees
    val gap = remember { Animatable(0f) }    // degrees removed from the full ring while refreshing

    LaunchedEffect(refreshing, active) {
        if (refreshing && active) {
            wave.snapTo(0f)
            wave.animateTo(TWO_PI, infiniteRepeatable(tween(scaledMs(900), easing = LinearEasing)))
        }
    }
    LaunchedEffect(refreshing, active) {
        if (refreshing && active) {
            rotation.snapTo(0f)
            rotation.animateTo(360f, infiniteRepeatable(tween(scaledMs(1800), easing = LinearEasing)))
        }
    }
    LaunchedEffect(refreshing) {
        gap.animateTo(
            if (refreshing) 70f else 0f,
            spring(Spring.DampingRatioLowBouncy, scaledStiffness(Spring.StiffnessLow))
        )
    }

    val path = remember { Path() }

    Canvas(Modifier.pullRefreshGlyphChrome(timeline).size(48.dp)) {
        val p = timeline.shown.coerceIn(0f, 1f)
        val pulling = timeline.phase == PullPhase.PULLING
        val strokePx = 4.dp.toPx()
        val maxAmp = 2.2.dp.toPx()
        val baseRadius = size.minDimension / 2f - strokePx / 2f - maxAmp
        val c = Offset(size.width / 2f, size.height / 2f)

        // Track ring (fades out once refreshing so the moving arc reads clearly).
        drawCircle(
            color = track,
            radius = baseRadius,
            center = c,
            style = Stroke(strokePx)
        )

        val sweep: Float
        val startDeg: Float
        val amp: Float
        val phase: Float
        if (pulling || !refreshing && timeline.phase != PullPhase.REFRESHING) {
            sweep = (p * 360f).coerceAtLeast(8f)
            startDeg = -90f + p * 90f
            // Smoothstep: amplitude eases in with the pull.
            val e = p * p * (3f - 2f * p)
            amp = maxAmp * e
            phase = p * TWO_PI * 1.5f
        } else {
            sweep = (360f - gap.value).coerceAtLeast(8f)
            startDeg = rotation.value
            amp = maxAmp
            phase = TWO_PI * 1.5f + wave.value
        }

        // Build the wavy arc as a polyline with ~3 degree steps (about 2 px at this radius).
        path.rewind()
        val steps = (sweep / 3f).toInt().coerceIn(2, 130)
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val deg = startDeg + sweep * t
            val rad = Math.toRadians(deg.toDouble()).toFloat()
            // Round the ends of the wave so the arc tapers instead of ending on a crest.
            val taper = min(1f, min(t, 1f - t) * 6f)
            val r = baseRadius + amp * taper * sin(WAVES * (rad) + phase)
            val x = c.x + r * cos(rad)
            val y = c.y + r * sin(rad)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
