/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT/TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.petal.browser.ui.components

import androidx.activity.BackEventCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * State holder for Predictive Back and Forward Edge Indicators.
 * Tracks edge (left vs right), normalized gesture progress (0.0 .. 1.0), and active visibility.
 */
@Stable
class PredictiveEdgeIndicatorState {
    var isActive by mutableStateOf(false)
    var swipeEdge by mutableIntStateOf(BackEventCompat.EDGE_LEFT)
    var progress by mutableFloatStateOf(0f)
    var canGoBack by mutableStateOf(true)
    var canGoForward by mutableStateOf(false)

    fun onGestureStarted(edge: Int, backPossible: Boolean = true, forwardPossible: Boolean = false) {
        swipeEdge = edge
        progress = 0f
        canGoBack = backPossible
        canGoForward = forwardPossible
        isActive = true
    }

    fun onGestureProgress(newProgress: Float) {
        progress = newProgress.coerceIn(0f, 1f)
    }

    fun onGestureFinished() {
        isActive = false
        progress = 0f
    }
}

/**
 * Material 3 Expressive edge indicator styled identically to Petal's contained refresh spinner.
 * Features a circular tactile surface with elevation, responsive spring entrance,
 * directional arrow morph, and smooth scale-up with gesture progress.
 */
@Composable
fun PredictiveEdgeIndicator(
    isBack: Boolean,
    progress: Float,
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val density = LocalDensity.current
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "edge_indicator_progress"
    )

    // Pull translation: moves out from the screen edge as progress increases
    val maxOffsetPx = with(density) { 48.dp.toPx() }
    val currentOffsetPx = animatedProgress * maxOffsetPx
    val directionalOffsetX = if (isBack) currentOffsetPx else -currentOffsetPx

    // Scale grows with pull progress, matching refresh spinner physics
    val scale = (0.75f + (animatedProgress * 0.35f)).coerceIn(0.75f, 1.15f)

    AnimatedVisibility(
        visible = isVisible && progress > 0.05f,
        enter = fadeIn(tween(120)) + scaleIn(spring(dampingRatio = 0.75f, stiffness = 500f)),
        exit = fadeOut(tween(160)) + scaleOut(tween(160, easing = FastOutSlowInEasing))
    ) {
        Surface(
            modifier = modifier
                .offset { IntOffset(directionalOffsetX.roundToInt(), 0) }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .size(48.dp)
                .shadow(
                    elevation = 6.dp,
                    shape = CircleShape,
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                ),
            shape = CircleShape,
            color = containerColor,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isBack) {
                        Icons.AutoMirrored.Rounded.ArrowBack
                    } else {
                        Icons.AutoMirrored.Rounded.ArrowForward
                    },
                    contentDescription = if (isBack) "Navigate Back" else "Navigate Forward",
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Full-screen overlay composable that mounts left or right edge bubble based on in-flight gesture.
 */
@Composable
fun PredictiveEdgeIndicatorOverlay(
    state: PredictiveEdgeIndicatorState,
    modifier: Modifier = Modifier
) {
    val isLeft = state.swipeEdge == BackEventCompat.EDGE_LEFT
    val isBack = isLeft
    val isActionPossible = if (isBack) state.canGoBack else state.canGoForward

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = if (isLeft) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        if (state.isActive && isActionPossible) {
            PredictiveEdgeIndicator(
                isBack = isBack,
                progress = state.progress,
                isVisible = state.isActive,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}
