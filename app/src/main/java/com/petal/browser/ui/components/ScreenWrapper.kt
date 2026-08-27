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

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController

/**
 * ScreenWrapper ported 1:1 from RvSystem-Monitor & PixelPlayer:
 * Dynamically applies 24.dp depth blur, 0.4f black dim overlay, 32.dp corner radius, and offscreen
 * compositing strategy when the screen goes behind top screen or during predictive navigation.
 */
@UnstableApi
@Composable
fun ScreenWrapper(
    navController: NavController? = null,
    modifier: Modifier = Modifier,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    isBehind: Boolean = false,
    content: @Composable () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val initialCurrentState = lifecycleOwner.lifecycle.currentStateAsState().value
    var isResumed by remember { mutableStateOf(initialCurrentState.isAtLeast(Lifecycle.State.RESUMED)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isResumed = true
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                isResumed = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val shouldDim = isBehind || !isResumed
    val targetBlur = if (shouldDim) 24f else 0f
    val animatedBlurRadius = remember { Animatable(targetBlur) }
    LaunchedEffect(shouldDim) {
        animatedBlurRadius.animateTo(targetBlur, animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing))
    }

    val targetDim = if (shouldDim) 0.4f else 0f
    val animatedDimAlpha = remember { Animatable(targetDim) }
    LaunchedEffect(shouldDim) {
        animatedDimAlpha.animateTo(targetDim, animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing))
    }

    val targetCorner = if (shouldDim) 32f else 0f
    val animatedCornerRadius = remember { Animatable(targetCorner) }
    LaunchedEffect(shouldDim) {
        animatedCornerRadius.animateTo(targetCorner, animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                compositingStrategy = if (shouldDim) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                if (animatedCornerRadius.value > 0.5f) {
                    this.shape = RoundedCornerShape(animatedCornerRadius.value.dp)
                    this.clip = true
                } else {
                    this.clip = false
                }
            }
            .blur(radius = animatedBlurRadius.value.dp)
            .background(MaterialTheme.colorScheme.background)
    ) {
        content()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = animatedDimAlpha.value }
                .background(Color.Black)
        )
    }
}
