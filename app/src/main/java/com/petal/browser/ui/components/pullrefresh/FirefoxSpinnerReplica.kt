package com.petal.browser.ui.components.pullrefresh

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.components.PullPhase
import com.petal.browser.ui.components.PullRefreshTimeline
import com.petal.browser.ui.components.scaledMs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * PREVIEW ONLY. A Compose replica of what SwipeRefreshLayout draws for the Firefox style:
 * a 40dp elevated disc holding a CircularProgressDrawable (DEFAULT size: center radius 7.5dp,
 * stroke 2.5dp, arrow 10x5dp). The browser itself shows the real SwipeRefreshLayout spinner; this
 * exists because that view cannot be hosted inside the settings sheet.
 *
 * Mirrors SwipeRefreshLayout.moveSpinner for the pull (trim 0..0.8, arrow scale, rotation,
 * alpha 76..255) and the spinning arc while refreshing.
 */
@Composable
fun FirefoxSpinnerReplica(timeline: PullRefreshTimeline) {
    val scheme = MaterialTheme.colorScheme
    val arcColor = scheme.primary
    val disc = scheme.surfaceContainerHigh
    val refreshing = timeline.phase == PullPhase.REFRESHING
    val spin = remember { Animatable(0f) }
    LaunchedEffect(refreshing, timeline.animationsActive) {
        if (refreshing && timeline.animationsActive) {
            spin.snapTo(0f)
            spin.animateTo(1f, infiniteRepeatable(tween(scaledMs(1332), easing = LinearEasing)))
        }
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    val s = timeline.shown.coerceAtLeast(0f)
                    // Scale-in while pulling (setProgressViewOffset(scale = true, ...)); the
                    // overshoot past 1 is the slingshot / bounce back.
                    val sc = min(1f, s)
                    scaleX = sc
                    scaleY = sc
                    translationY = (4.dp + 24.dp * s).toPx()
                    alpha = if (sc > 0f) 1f else 0f
                }
                .size(40.dp)
                .shadow(4.dp, CircleShape)
                .background(disc, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(40.dp)) {
                val p = timeline.shown.coerceIn(0f, 1f)
                val centerR = 7.5.dp.toPx()
                val stroke = 2.5.dp.toPx()
                val c = Offset(size.width / 2f, size.height / 2f)
                val trimStart: Float
                val trimEnd: Float
                val rotation: Float
                val arrow: Float
                var alpha = 1f
                if (!refreshing && timeline.phase != PullPhase.REFRESHING) {
                    val adj = max(p - 0.4f, 0f) * 5f / 3f
                    trimStart = 0f
                    trimEnd = min(0.8f, adj * 0.8f)
                    rotation = (-0.25f + 0.4f * adj) * 0.5f
                    arrow = min(1f, adj)
                    alpha = (76f + (255f - 76f) * p) / 255f
                } else {
                    // Spinning arc, no arrow (arrow is disabled once refreshing).
                    val t = spin.value
                    val breathe = 0.5f + 0.5f * sin(2f * PI.toFloat() * t)
                    trimStart = 0f
                    trimEnd = 0.1f + 0.6f * breathe
                    rotation = t
                    arrow = 0f
                }
                val startDeg = (trimStart + rotation) * 360f - 90f
                val sweep = (trimEnd - trimStart) * 360f
                if (sweep > 0.5f) {
                    drawArc(
                        color = arcColor.copy(alpha = alpha),
                        startAngle = startDeg,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(c.x - centerR, c.y - centerR),
                        size = Size(centerR * 2f, centerR * 2f),
                        style = Stroke(width = stroke, cap = StrokeCap.Butt)
                    )
                    if (arrow > 0.01f) {
                        val endRad = Math.toRadians((startDeg + sweep).toDouble())
                        val ex = c.x + centerR * cos(endRad).toFloat()
                        val ey = c.y + centerR * sin(endRad).toFloat()
                        // Tangent (clockwise) and outward normal at the arc end.
                        val tx = -sin(endRad).toFloat()
                        val ty = cos(endRad).toFloat()
                        val nx = cos(endRad).toFloat()
                        val ny = sin(endRad).toFloat()
                        val halfW = 5.dp.toPx() * arrow
                        val h = 5.dp.toPx() * arrow
                        val tri = Path().apply {
                            moveTo(ex + nx * halfW, ey + ny * halfW)
                            lineTo(ex - nx * halfW, ey - ny * halfW)
                            lineTo(ex + tx * h, ey + ty * h)
                            close()
                        }
                        drawPath(tri, arcColor.copy(alpha = alpha))
                    }
                }
            }
        }
    }
}
