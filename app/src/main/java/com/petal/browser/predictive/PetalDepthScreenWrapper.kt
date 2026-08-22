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
 * LIABILITY, WHETHER IN CONTRACT/TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.petal.browser.predictive

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

/**
 * Screen depth wrapper ported from RvSystem-Monitor.
 * When a screen is not the topmost route (i.e. covered by a backstack entry above it),
 * animates corner radius 0 -> 32dp, dim overlay alpha 0 -> 0.4 (0.75 if blur disabled),
 * and background blur 0 -> 24dp via 350ms FastOutSlowInEasing tween.
 */
@Composable
fun PetalDepthScreenWrapper(
    navController: NavController? = null,
    isTopmost: Boolean? = null,
    modifier: Modifier = Modifier,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
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

    val currentBackStackEntryState = navController?.currentBackStackEntryAsState()
    val visibleEntriesState = navController?.visibleEntries?.collectAsState()

    val myEntry = lifecycleOwner as? NavBackStackEntry
    val computedNotTopmost = if (isTopmost != null) {
        !isTopmost
    } else if (navController != null && myEntry != null) {
        val visibleEntries = visibleEntriesState?.value ?: emptyList()
        val currentBackStackEntry = currentBackStackEntryState?.value
        val topEntryId = currentBackStackEntry?.id ?: visibleEntries.lastOrNull()?.id
        topEntryId != null && topEntryId != myEntry.id
    } else {
        !isResumed
    }

    val depthBlurEnabled = PetalPredictiveJunction.isDepthBlurEnabled.value
    val transition = animatedVisibilityScope?.transition

    // Corner Radius Animation: 0dp -> 32dp when not topmost route
    val targetRadius = if (computedNotTopmost) 32f else 0f
    val animatedCornerRadius = if (transition != null) {
        val animatedValue by transition.animateFloat(
            transitionSpec = { tween(durationMillis = 350, easing = FastOutSlowInEasing) },
            label = "depthCornerRadius"
        ) { state ->
            if (computedNotTopmost || state == EnterExitState.PostExit || state == EnterExitState.PreEnter) {
                32f
            } else {
                0f
            }
        }
        animatedValue
    } else {
        val fallbackCornerRadius = remember { Animatable(targetRadius) }
        LaunchedEffect(targetRadius) {
            fallbackCornerRadius.animateTo(targetRadius, animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing))
        }
        fallbackCornerRadius.value
    }

    // Dim Overlay Alpha Animation: 0 -> 0.4 (or 0.75 if depth blur disabled)
    val dimMax = if (!depthBlurEnabled) 0.75f else 0.4f
    val targetDim = if (computedNotTopmost) dimMax else 0f
    val animatedDimAlpha = if (transition != null) {
        val animatedValue by transition.animateFloat(
            transitionSpec = { tween(durationMillis = 350, easing = FastOutSlowInEasing) },
            label = "depthDimAlpha"
        ) { state ->
            if (computedNotTopmost || state == EnterExitState.PostExit || state == EnterExitState.PreEnter) {
                dimMax
            } else {
                0f
            }
        }
        animatedValue
    } else {
        val fallbackDimAlpha = remember { Animatable(targetDim) }
        LaunchedEffect(targetDim) {
            fallbackDimAlpha.animateTo(targetDim, animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing))
        }
        fallbackDimAlpha.value
    }

    // Blur Animation: 0 -> 24dp when not topmost route (if blur enabled)
    val targetBlur = if (computedNotTopmost && depthBlurEnabled) 24f else 0f
    val animatedBlurRadius = if (transition != null) {
        val animatedValue by transition.animateDp(
            transitionSpec = { tween(durationMillis = 350, easing = FastOutSlowInEasing) },
            label = "depthBlurRadius"
        ) { state ->
            if (depthBlurEnabled && (computedNotTopmost || state == EnterExitState.PostExit || state == EnterExitState.PreEnter)) {
                24.dp
            } else {
                0.dp
            }
        }
        animatedValue
    } else {
        val fallbackBlurRadius = remember { Animatable(targetBlur) }
        LaunchedEffect(targetBlur) {
            fallbackBlurRadius.animateTo(targetBlur, animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing))
        }
        fallbackBlurRadius.value.dp
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                if (animatedCornerRadius > 0.5f) {
                    this.shape = RoundedCornerShape(animatedCornerRadius.dp)
                    this.clip = true
                } else {
                    this.clip = false
                }
            }
            .blur(radius = animatedBlurRadius)
            .background(MaterialTheme.colorScheme.background)
    ) {
        content()

        // Dim Layer Overlay
        if (animatedDimAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = animatedDimAlpha }
                    .background(Color.Black)
            )
        }
    }
}
