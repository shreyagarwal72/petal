/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.predictive

import androidx.activity.BackEventCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val PreviewWidth = 112.dp
private val PreviewHeight = 200.dp

/**
 * A miniature phone that loops a back swipe (a commit, then a cancel, alternating screen edges)
 * through the exact same [PetalBackMotion] maths the real screens use, so what you see while
 * moving the style slider is what the gesture will do.
 */
@Composable
fun PetalBackStylePreview(
    style: PetalBackStyle,
    blurEnabled: Boolean,
    exitDirection: PetalBackExitDirection,
    modifier: Modifier = Modifier,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var committing by remember { mutableStateOf(false) }
    var release by remember { mutableFloatStateOf(0f) }
    var settleTime by remember { mutableFloatStateOf(0f) }
    var touchYFraction by remember { mutableFloatStateOf(0.5f) }
    var edge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var showTop by remember { mutableStateOf(true) }

    LaunchedEffect(style) {
        var cycle = 0
        while (true) {
            committing = false
            release = 0f
            settleTime = 0f
            progress = 0f
            touchYFraction = 0.5f
            showTop = true
            edge = if (cycle % 4 < 2) BackEventCompat.EDGE_LEFT else BackEventCompat.EDGE_RIGHT
            val yDirection = if (cycle % 2 == 0) -1f else 1f
            delay(450)

            // Finger drags in from the edge.
            val drag = Animatable(0f)
            drag.animateTo(0.62f, tween(950, easing = FastOutSlowInEasing)) {
                progress = value
                touchYFraction = 0.5f + yDirection * 0.18f * (value / 0.62f)
            }
            delay(180)

            if (cycle % 2 == 0) {
                // Release past the threshold: commit.
                val from = progress
                release = from
                committing = true
                if (style.usesSpringCommit) {
                    val settle = Animatable(from)
                    settle.animateTo(1f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)) {
                        progress = value
                        settleTime = ((value - from) / (1f - from).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
                    }
                } else {
                    val settle = Animatable(0f)
                    settle.animateTo(1f, tween(style.commitDurationMs, easing = LinearEasing)) {
                        progress = from + (1f - from) * style.commitEasing.transform(value)
                        settleTime = value
                    }
                }
                showTop = false
                delay(650)
            } else {
                // Release early: cancel and spring back.
                val settle = Animatable(progress)
                settle.animateTo(
                    0f,
                    if (style.usesSpringCommit) spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium)
                    else spring(Spring.DampingRatioNoBouncy, 1500f),
                ) {
                    progress = value
                    touchYFraction = 0.5f + (touchYFraction - 0.5f) * 0.96f
                }
                delay(450)
            }
            cycle++
        }
    }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val widthPx = with(density) { PreviewWidth.toPx() }
    val heightPx = with(density) { PreviewHeight.toPx() }
    // The mock is smaller than a real screen, so dp-based constants shrink with it.
    val shrinkK = widthPx / (configuration.screenWidthDp * density.density)

    val latestExit by rememberUpdatedState(exitDirection)
    val frame = PetalBackMotion.frame(
        style,
        PetalBackInput(
            progress = progress,
            swipeEdge = edge,
            touchY = touchYFraction * heightPx,
            initialTouchY = 0.5f * heightPx,
            widthPx = widthPx,
            heightPx = heightPx,
            density = density.density * shrinkK,
            blurEnabled = blurEnabled,
            committing = committing,
            releaseProgress = release,
            releaseVelocity = 0.9f,
            settleTime = settleTime,
            exitDirection = latestExit,
        ),
    )

    Box(
        modifier = modifier
            .size(PreviewWidth, PreviewHeight)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        val behind = frame.behind
        val blurDp = (frame.behindBlurDp * shrinkK).coerceAtLeast(0f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (blurDp > 0.4f) Modifier.blur(blurDp.dp) else Modifier)
                .graphicsLayer {
                    scaleX = behind.scale
                    scaleY = behind.scale
                    translationX = behind.translationX
                    translationY = behind.translationY
                    alpha = behind.alpha
                    transformOrigin = TransformOrigin(behind.originX, behind.originY)
                    compositingStrategy = CompositingStrategy.Offscreen
                    val corner = behind.cornerDp * shrinkK
                    if (corner > 0.5f) {
                        shape = RoundedCornerShape(corner.dp)
                        clip = true
                    }
                },
        ) {
            MockPage(
                accent = MaterialTheme.colorScheme.tertiaryContainer,
                surface = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = frame.behindDim.coerceIn(0f, 1f))),
            )
        }

        if (showTop) {
            val top = frame.top
            val surface = MaterialTheme.colorScheme.surface
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = top.scale
                        scaleY = top.scale
                        translationX = top.translationX
                        translationY = top.translationY
                        alpha = top.alpha
                        transformOrigin = TransformOrigin(top.originX, top.originY)
                        compositingStrategy = CompositingStrategy.Offscreen
                        val corner = top.cornerDp * shrinkK
                        if (corner > 0.5f) {
                            shape = RoundedCornerShape(corner.dp)
                            clip = true
                            shadowElevation = (top.shadowDp * shrinkK).dp.toPx()
                        }
                    }
                    .background(surface),
            ) {
                MockPage(
                    accent = MaterialTheme.colorScheme.primaryContainer,
                    surface = MaterialTheme.colorScheme.surfaceContainer,
                )
            }
        }
    }
}

@Composable
private fun MockPage(accent: Color, surface: Color) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).background(accent, CircleShape))
            Spacer(Modifier.width(6.dp))
            Bar(width = 46.dp, height = 7.dp, color = accent)
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(accent, RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.height(10.dp))
        repeat(3) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .background(surface, RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun Bar(width: Dp, height: Dp, color: Color) {
    Box(Modifier.size(width, height).background(color, RoundedCornerShape(50)))
}
