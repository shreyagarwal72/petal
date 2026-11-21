package com.petal.browser.ui.components.pullrefresh

import com.petal.browser.ui.components.PetalSpring

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.components.PullPhase
import com.petal.browser.ui.components.PullRefreshTimeline
import com.petal.browser.ui.components.scaledMs
import com.petal.browser.ui.components.scaledStiffness
import com.petal.browser.ui.containment.isLiquidGlassEnabled

/**
 * LIQUID_BRIDGE: a droplet that peels off the top edge. Pure Path + Canvas (no RenderEffect, no
 * shader), so it works on every API level.
 *
 *  - Pulling: the droplet hangs from the top edge on a neck made of two bezier curves. The neck
 *    stretches and thins as the pull grows.
 *  - Threshold (ready): the neck pinches off; its two halves retract (up to the edge / into the
 *    droplet) and the droplet drifts to its rest position.
 *  - Refreshing: a circle with a breathing inner dot.
 *  - Finish: the droplet fuses back up into the edge and disappears.
 *
 * Glass-aware: with Liquid Glass on, the fill is slightly translucent with a thin highlight rim.
 * With glass off it is a plain solid primary fill. Nothing in the glass internals is touched.
 */
@Composable
fun LiquidBridgeIndicator(timeline: PullRefreshTimeline) {
    val scheme = MaterialTheme.colorScheme
    val glass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val fill = if (glass) scheme.primary.copy(alpha = 0.86f) else scheme.primary
    val dot = scheme.onPrimary
    val rim = if (glass) Color.White.copy(alpha = 0.35f) else Color.Transparent

    // 0 = connected by the neck, 1 = pinched off. Springs so the pinch snaps and settles.
    val detach = remember { Animatable(0f) }
    val breathe = remember { Animatable(0f) }
    val detached = (timeline.ready || timeline.phase == PullPhase.REFRESHING) &&
        timeline.phase != PullPhase.EXITING
    val refreshing = timeline.phase == PullPhase.REFRESHING

    LaunchedEffect(detached, timeline.animationsActive) {
        val target = if (detached) 1f else 0f
        if (timeline.animationsActive) {
            detach.animateTo(
                target,
                PetalSpring.bouncy()
            )
        } else {
            detach.snapTo(target)
        }
    }
    LaunchedEffect(refreshing, timeline.animationsActive) {
        if (refreshing && timeline.animationsActive) {
            breathe.animateTo(
                1f,
                infiniteRepeatable(tween(scaledMs(700)), RepeatMode.Reverse)
            )
        } else {
            breathe.snapTo(0f)
        }
    }

    val neckPath = remember { Path() }

    Canvas(Modifier.fillMaxSize()) {
        val s = timeline.shown.coerceAtLeast(0f)
        if (s <= 0.001f) return@Canvas
        val d = detach.value.coerceIn(0f, 1.15f)
        val cx = size.width / 2f

        // Geometry (dp -> px).
        val baseR = 18.dp.toPx()
        val topHalf = 26.dp.toPx()         // half width of the neck where it meets the edge
        val pullReach = 30.dp.toPx()       // blob centre y at the end of the pull
        val restReach = 46.dp.toPx()       // blob centre y while refreshing
        val reach = pullReach + (restReach - pullReach) * d.coerceIn(0f, 1f)

        val sc = s.coerceAtMost(1.15f)
        val cy = reach * sc
        val growth = (0.5f + 0.5f * (sc * 2f).coerceAtMost(1f))
        val r = baseR * growth * timeline.pop
        val alpha = (s * 4f).coerceIn(0f, 1f)

        // Neck: width at the droplet thins as the pull grows (stretch), and both ends re-shape
        // when pinched off.
        val stretch = (sc).coerceIn(0f, 1f)
        val neckHalfAtBlob = r * (0.95f - 0.8f * stretch)
        val pinchY = cy * 0.5f
        val neckHalfAtPinch = (topHalf * 0.35f + neckHalfAtBlob * 0.65f) * (1f - d.coerceIn(0f, 1f))

        val color = fill.copy(alpha = fill.alpha * alpha)

        // Upper half: edge -> pinch. On detach it retracts towards the edge and thins out.
        val stubEnd = pinchY * (1f - d.coerceIn(0f, 1f))
        if (stubEnd > 0.5f) {
            taper(neckPath, cx, 0f, topHalf, stubEnd, neckHalfAtPinch)
            drawPath(neckPath, color)
        }
        // Lower half: pinch -> droplet. On detach it retracts down into the droplet.
        val tailStart = pinchY + (cy - pinchY) * d.coerceIn(0f, 1f)
        if (cy - tailStart > 0.5f) {
            taper(neckPath, cx, tailStart, neckHalfAtPinch, cy, neckHalfAtBlob)
            drawPath(neckPath, color)
        }

        drawBlob(Offset(cx, cy), r, d, color, rim)

        // Inner dot while refreshing / once detached.
        val dotAlpha = (d.coerceIn(0f, 1f)) * alpha
        if (dotAlpha > 0.01f) {
            val dotR = r * (0.28f + 0.07f * breathe.value)
            drawCircle(dot.copy(alpha = dot.alpha * dotAlpha), dotR, Offset(cx, cy))
        }
    }
}

/** Closed symmetric shape between (cx, y1) with half width w1 and (cx, y2) with half width w2. */
private fun taper(path: Path, cx: Float, y1: Float, w1: Float, y2: Float, w2: Float) {
    path.rewind()
    val mid = (y1 + y2) / 2f
    path.moveTo(cx - w1, y1)
    path.cubicTo(cx - w1, mid, cx - w2, mid, cx - w2, y2)
    path.lineTo(cx + w2, y2)
    path.cubicTo(cx + w2, mid, cx + w1, mid, cx + w1, y1)
    path.close()
}

private fun DrawScope.drawBlob(center: Offset, r: Float, d: Float, color: Color, rim: Color) {
    // Slightly teardrop (taller) while still attached, round once pinched off.
    val squash = 1f + 0.12f * (1f - d.coerceIn(0f, 1f))
    val rx = r / squash
    val ry = r * squash
    drawOval(
        color = color,
        topLeft = Offset(center.x - rx, center.y - ry),
        size = androidx.compose.ui.geometry.Size(rx * 2f, ry * 2f)
    )
    if (rim.alpha > 0f) {
        drawOval(
            color = rim,
            topLeft = Offset(center.x - rx, center.y - ry),
            size = androidx.compose.ui.geometry.Size(rx * 2f, ry * 2f),
            style = Stroke(width = 1.2f * density)
        )
    }
}
