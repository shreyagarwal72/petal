package com.petal.browser.ui.components.pullrefresh

import com.petal.browser.ui.components.PetalSpring

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import com.petal.browser.ui.components.PullPhase
import com.petal.browser.ui.components.PullRefreshTimeline
import com.petal.browser.ui.components.pullRefreshGlyphChrome
import com.petal.browser.ui.components.scaledMs
import com.petal.browser.ui.components.scaledStiffness

/**
 * MORPH_LADDER: the shape steps up at 33% / 66% / 100% of the pull:
 * circle (seed) -> 6-point cookie -> 12-point burst -> 12-sided cookie, then it spins at 100%
 * and keeps spinning while refreshing. Each step is a short spring Morph (never a hard swap)
 * with a light haptic tick when climbing.
 */
@Composable
fun MorphLadderIndicator(timeline: PullRefreshTimeline) {
    val color = MaterialTheme.colorScheme.primary
    val haptics = LocalHapticFeedback.current
    val rungs = remember {
        listOf(
            MaterialShapes.Circle,
            MaterialShapes.Cookie6Sided,
            MaterialShapes.Burst,
            MaterialShapes.Cookie12Sided,
        ).map { it.unit() }
    }

    var from by remember { mutableIntStateOf(0) }
    var to by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(1f) }
    val morph = remember(from, to) { Morph(rungs[from], rungs[to]) }
    val spin = remember { Animatable(0f) }

    LaunchedEffect(timeline) {
        var lastRung = 0
        snapshotFlow { rungFor(timeline) }.collect { rung ->
            if (rung < 0 || rung == lastRung) return@collect
            if (rung > lastRung && timeline.phase == PullPhase.PULLING) {
            }
            lastRung = rung
            // If a step is still in flight, continue from whichever end is visually closer.
            val current = if (progress.value > 0.5f) to else from
            from = current
            to = rung
            if (from == to) {
                progress.snapTo(1f)
            } else if (!timeline.animationsActive) {
                progress.snapTo(1f)
            } else {
                progress.snapTo(0f)
                progress.animateTo(
                    1f,
                    PetalSpring.bouncy()
                )
            }
        }
    }

    val spinning = timeline.phase == PullPhase.REFRESHING || timeline.ready
    LaunchedEffect(spinning, timeline.animationsActive) {
        if (spinning && timeline.animationsActive) {
            spin.animateTo(
                spin.value + 360f,
                infiniteRepeatable(tween(scaledMs(1400), easing = LinearEasing))
            )
        }
    }

    val scratch = remember { MorphPathScratch() }
    Canvas(Modifier.pullRefreshGlyphChrome(timeline).size(44.dp)) {
        val path = scratch.build(morph, progress.value, size.minDimension)
        rotate(spin.value, pivot = center) { drawPath(path, color) }
    }
}

/** Rung 0 below 33%, 1 from 33%, 2 from 66%, 3 at 100% (and for the whole refresh). */
private fun rungFor(timeline: PullRefreshTimeline): Int = when (timeline.phase) {
    PullPhase.PULLING -> {
        val p = timeline.shown
        when {
            p >= 1f -> 3
            p >= 2f / 3f -> 2
            p >= 1f / 3f -> 1
            else -> 0
        }
    }
    PullPhase.REFRESHING -> 3
    // Leaving or idle: hold whatever was reached, never step down while fading away.
    else -> -1
}
