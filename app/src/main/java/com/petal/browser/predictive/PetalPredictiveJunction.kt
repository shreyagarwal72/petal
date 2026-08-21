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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
 * PixelPlayer predictive back handler for overlay surfaces (dialogs, sheets, sub-pages).
 * Smoothly tracks gesture progress and swipe edge with spring physics for release and cancellation.
 */
@Composable
fun PetalPredictiveBackJunctionHandler(
    enabled: Boolean = true,
    onProgressChanged: (Float) -> Unit = {},
    onBack: () -> Unit
) {
    val junctionPredictiveEnabled by remember { derivedStateOf { PetalPredictiveJunction.isPredictiveBackEnabled.value } }
    val isFullyEnabled = enabled && junctionPredictiveEnabled
    val scope = rememberCoroutineScope()
    val progressAnim = remember { Animatable(0f) }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && isFullyEnabled) {
        PredictiveBackHandler(enabled = true) { progressFlow ->
            try {
                progressFlow.collect { backEvent ->
                    // Apply PixelPlayer smooth gesture easing curve: FastOutSlowInEasing
                    val easedProgress = FastOutSlowInEasing.transform(backEvent.progress)
                    progressAnim.snapTo(easedProgress)
                    onProgressChanged(easedProgress)
                }

                // Gesture completed: animate to 1f with snappy spring before triggering back action
                scope.launch {
                    progressAnim.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ) { onProgressChanged(value) }
                    onBack()
                    progressAnim.snapTo(0f)
                    onProgressChanged(0f)
                }
            } catch (_: CancellationException) {
                // Gesture cancelled: spring smoothly back to rest (0f)
                scope.launch {
                    progressAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) { onProgressChanged(value) }
                }
            }
        }
    } else {
        BackHandler(enabled = isFullyEnabled, onBack = onBack)
    }
}

/**
 * PixelPlayer ScreenWrapper implementation.
 * Applies offscreen compositing, subtle scale down, rounded corner morphing, depth blur,
 * and dim overlay layers to match native Pixel predictive back motion design.
 */
@Composable
fun PetalScreenWrapper(
    progress: Float = 0f,
    isBehind: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val predictiveEnabled = PetalPredictiveJunction.isPredictiveBackEnabled.value
    val blurEnabled = PetalPredictiveJunction.isDepthBlurEnabled.value

    // Two independent progress signals, matching PixelPlayer's ScreenWrapper:
    // - foregroundProgress drives the screen actively being dragged away (scale + corner
    //   morph only — it must stay sharp and legible while the user is looking at it).
    // - behindProgress drives whatever sits underneath it (corner + dim + blur), and is only
    //   ever non-zero for a screen explicitly marked isBehind. Previously both scale/corner
    //   AND dim/blur were driven off the same `progress`, so the screen being dragged blurred
    //   and dimmed itself into illegibility mid-gesture instead of the destination behind it.
    val foregroundProgress = if (predictiveEnabled) progress else 0f
    val behindProgress = if (predictiveEnabled && isBehind) 1f else 0f

    // PixelPlayer predictive back motion curves:
    // Scale ranges from 1.0 down to 0.92 (8% scale reduction at max gesture)
    val scale = 1f - (0.08f * foregroundProgress)
    // Corner radius morphs up to 28dp (applies to whichever layer is animating, front or behind)
    val activeProgress = if (isBehind) behindProgress else foregroundProgress
    val targetRadius = 28f * activeProgress
    // Subtle backdrop dimming overlay — behind screen only
    val targetDim = (if (!blurEnabled) 0.5f else 0.25f) * behindProgress
    // Depth blur (up to 16dp on supported devices) — behind screen only
    val targetBlur = if (blurEnabled) 16f * behindProgress else 0f

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                compositingStrategy = if (predictiveEnabled && (foregroundProgress > 0f || isBehind)) {
                    CompositingStrategy.Offscreen
                } else {
                    CompositingStrategy.Auto
                }
                scaleX = scale
                scaleY = scale
                if (predictiveEnabled && targetRadius > 0.5f) {
                    this.shape = RoundedCornerShape(targetRadius.dp)
                    this.clip = true
                } else {
                    this.clip = false
                }
            }
            .blur(radius = targetBlur.dp)
            .background(MaterialTheme.colorScheme.background)
    ) {
        content()

        // PixelPlayer Dim Layer Overlay
        if (targetDim > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = targetDim }
                    .background(Color.Black)
            )
        }
    }
}
