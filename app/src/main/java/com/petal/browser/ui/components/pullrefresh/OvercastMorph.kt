package com.petal.browser.ui.components.pullrefresh

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import com.petal.browser.ui.components.PullPhase
import com.petal.browser.ui.components.PullRefreshTimeline
import com.petal.browser.ui.components.pullRefreshGlyphChrome
import com.petal.browser.ui.components.scaledMs
import com.petal.browser.ui.components.scaledStiffness
import kotlin.math.pow

/**
 * OVERCAST_MORPH: adaptation of Overcast's Material 3 Expressive LoadingIndicator.
 * (Overcast has no real pull-to-refresh, so this borrows its shape sequence and its
 * rubber-band, staged pull feel.)
 *
 *  - Pulling: a circle opens into a soft burst. Morph.progress follows the pull with rubber-band
 *    resistance (fast at first, then it tightens) and the shape turns in proportion to the drag.
 *  - Threshold: haptic + spring pop (both come from the shared timeline).
 *  - Refreshing: the shape keeps cycling through the M3 loader sequence while it spins.
 *  - Finish: shrink + fade (shared glyph chrome).
 */
@Composable
fun OvercastMorphIndicator(timeline: PullRefreshTimeline) {
    val color = MaterialTheme.colorScheme.primary
    val sequence = remember {
        listOf(
            MaterialShapes.SoftBurst,
            MaterialShapes.Cookie9Sided,
            MaterialShapes.Pentagon,
            MaterialShapes.Pill,
            MaterialShapes.Sunny,
            MaterialShapes.Cookie4Sided,
            MaterialShapes.Oval,
        ).map { it.unit() }
    }
    val pullMorph = remember { Morph(MaterialShapes.Circle.unit(), sequence[0]) }

    // Refresh cycle: step i morphs sequence[i] -> sequence[i + 1].
    var step by remember { mutableIntStateOf(0) }
    val stepMorph = remember(step) { Morph(sequence[step % sequence.size], sequence[(step + 1) % sequence.size]) }
    val stepProgress = remember { Animatable(0f) }
    val spin = remember { Animatable(0f) }

    val refreshing = timeline.phase == PullPhase.REFRESHING
    val active = timeline.animationsActive

    LaunchedEffect(refreshing, active) {
        if (!refreshing || !active) return@LaunchedEffect
        // Continue from the burst reached at the end of the pull.
        step = 0
        stepProgress.snapTo(0f)
        while (true) {
            stepProgress.animateTo(
                1f,
                spring(Spring.DampingRatioMediumBouncy, scaledStiffness(Spring.StiffnessLow))
            )
            kotlinx.coroutines.delay(scaledMs(260).toLong())
            step += 1
            stepProgress.snapTo(0f)
        }
    }
    LaunchedEffect(refreshing, active) {
        if (!refreshing || !active) return@LaunchedEffect
        spin.snapTo(0f)
        spin.animateTo(360f, infiniteRepeatable(tween(scaledMs(3000), easing = LinearEasing)))
    }

    val scratch = remember { MorphPathScratch() }

    Canvas(Modifier.pullRefreshGlyphChrome(timeline).size(44.dp)) {
        val sizePx = size.minDimension
        val inPull = timeline.phase == PullPhase.PULLING || (!refreshing && step == 0)
        val path: androidx.compose.ui.graphics.Path
        val rotation: Float
        if (inPull || !active && !refreshing) {
            val p = timeline.shown.coerceIn(0f, 1f)
            // Rubber band: strong response at the start, increasing resistance towards 1.
            val rubber = 1f - (1f - p).pow(2.2f)
            path = scratch.build(pullMorph, rubber, sizePx)
            // Drag-proportional turn; ends at 270 degrees so the refresh spin continues from there.
            rotation = p * 270f
        } else {
            path = scratch.build(stepMorph, if (refreshing) stepProgress.value else 1f, sizePx)
            rotation = 270f + spin.value
        }
        rotate(rotation, pivot = center) {
            drawPath(path, color)
        }
    }
}
