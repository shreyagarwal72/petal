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

import android.content.SharedPreferences
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Global settings & state junction for Predictive Back and Depth Blur effects across the Petal App.
 * Changes here automatically propagate to every page and route without needing per-screen logic.
 */
object PetalPredictiveJunction {
    private const val KEY_PREDICTIVE_BACK_ENABLED = "sp_predictive_back_junction_enabled"
    private const val KEY_DEPTH_BLUR_ENABLED = "sp_depth_blur_junction_enabled"

    private val _isPredictiveBackEnabled = MutableStateFlow(true)
    val isPredictiveBackEnabled: StateFlow<Boolean> = _isPredictiveBackEnabled.asStateFlow()

    private val _isDepthBlurEnabled = MutableStateFlow(true)
    val isDepthBlurEnabled: StateFlow<Boolean> = _isDepthBlurEnabled.asStateFlow()

    @JvmStatic
    fun init(prefs: SharedPreferences) {
        _isPredictiveBackEnabled.value = prefs.getBoolean(KEY_PREDICTIVE_BACK_ENABLED, true)
        _isDepthBlurEnabled.value = prefs.getBoolean(KEY_DEPTH_BLUR_ENABLED, true)
    }

    @JvmStatic
    fun setPredictiveBackEnabled(prefs: SharedPreferences, enabled: Boolean) {
        _isPredictiveBackEnabled.value = enabled
        prefs.edit().putBoolean(KEY_PREDICTIVE_BACK_ENABLED, enabled).apply()
    }

    @JvmStatic
    fun setDepthBlurEnabled(prefs: SharedPreferences, enabled: Boolean) {
        _isDepthBlurEnabled.value = enabled
        prefs.edit().putBoolean(KEY_DEPTH_BLUR_ENABLED, enabled).apply()
    }
}

val LocalPetalPredictiveJunctionState = compositionLocalOf { true }
val LocalPetalDepthBlurJunctionState = compositionLocalOf { true }

/**
 * PixelPlayer-inspired predictive back handler for overlay surfaces (dialogs, sheets, sub-pages).
 * Smoothly interpolates gesture progress and handles release/cancellation with spring physics.
 */
@Composable
fun PetalPredictiveBackJunctionHandler(
    enabled: Boolean = true,
    animationDurationMs: Int = 350,
    onProgressChanged: (Float) -> Unit = {},
    onBack: () -> Unit
) {
    val junctionPredictiveEnabled by remember { derivedStateOf { PetalPredictiveJunction.isPredictiveBackEnabled.value } }
    val isFullyEnabled = enabled && junctionPredictiveEnabled
    val scope = rememberCoroutineScope()
    val progressAnim = remember { Animatable(0f) }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && isFullyEnabled) {
        PredictiveBackHandler(enabled = true) { progressFlow ->
            try {
                progressFlow.collect { backEvent ->
                    progressAnim.snapTo(backEvent.progress)
                    onProgressChanged(backEvent.progress)
                }

                scope.launch {
                    progressAnim.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(animationDurationMs / 2)
                    ) { onProgressChanged(value) }
                    onBack()
                }
            } catch (_: CancellationException) {
                scope.launch {
                    progressAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(animationDurationMs)
                    ) { onProgressChanged(value) }
                }
            }
        }
    } else {
        BackHandler(enabled = isFullyEnabled, onBack = onBack)
    }
}

/**
 * PixelPlayer ScreenWrapper implementation ported for Petal.
 * Wraps page surfaces with offscreen compositing strategy, corner radius morphing,
 * background depth blur, and dim overlay layers during navigation transitions.
 */
@Composable
fun PetalScreenWrapper(
    isBehind: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    
    // Auto-sync with Junction global state
    val predictiveEnabled = PetalPredictiveJunction.isPredictiveBackEnabled.value
    val blurEnabled = PetalPredictiveJunction.isDepthBlurEnabled.value

    val targetRadius = if (predictiveEnabled && isBehind) 32f else 0f
    val fallbackCornerRadius = remember { Animatable(targetRadius) }
    LaunchedEffect(targetRadius) {
        fallbackCornerRadius.animateTo(targetRadius, animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing))
    }
    val animatedCornerRadius = fallbackCornerRadius.value

    val targetDim = if (predictiveEnabled && isBehind) {
        if (!blurEnabled) 0.75f else 0.4f
    } else 0f

    val fallbackDimAlpha = remember { Animatable(targetDim) }
    LaunchedEffect(targetDim) {
        fallbackDimAlpha.animateTo(targetDim, animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing))
    }
    val animatedDimAlpha = fallbackDimAlpha.value

    val targetBlur = if (predictiveEnabled && isBehind && blurEnabled) 24f else 0f
    val fallbackBlurRadius = remember { Animatable(targetBlur) }
    LaunchedEffect(targetBlur) {
        fallbackBlurRadius.animateTo(targetBlur, animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing))
    }
    val animatedBlurRadius = fallbackBlurRadius.value.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                compositingStrategy = if (predictiveEnabled) {
                    CompositingStrategy.Offscreen
                } else {
                    CompositingStrategy.Auto
                }
                if (predictiveEnabled && animatedCornerRadius > 0.5f) {
                    this.shape = RoundedCornerShape(animatedCornerRadius.dp)
                    this.clip = true
                } else {
                    this.clip = false
                }
            }
            .blur(radius = if (predictiveEnabled && blurEnabled) animatedBlurRadius else 0.dp)
            .background(MaterialTheme.colorScheme.background)
    ) {
        content()

        // Dim Layer Overlay from PixelPlayer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = animatedDimAlpha }
                .background(Color.Black)
        )
    }
}
