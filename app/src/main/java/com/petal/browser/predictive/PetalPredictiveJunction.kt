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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
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
 * Predictive back handler for Petal full-screen surfaces (Settings, History, Downloads, etc.).
 *
 * Matches PixelPlayer's LyricsPredictiveBackHandler pattern exactly:
 * - Requires API 34 (UPSIDE_DOWN_CAKE) — PredictiveBackHandler was unreliable on API 33.
 * - Does NOT re-ease backEvent.progress: Android already delivers eased values. Double-easing
 *   makes the animation feel sluggish and mismatched with the system chrome.
 * - On gesture commit: calls onBack() directly. The system chrome handles the dismissal
 *   animation. Applying an extra spring-to-1f before onBack() makes the screen fight the
 *   system animation and looks broken (oscillates past dismissed state).
 * - On gesture cancel: spring-animates back to 0f with a crisp no-bounce spring so the
 *   screen snaps back responsively without feeling elastic or over-damped.
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

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && isFullyEnabled) {
        PredictiveBackHandler(enabled = true) { progressFlow ->
            try {
                progressFlow.collect { backEvent ->
                    // Use backEvent.progress directly — the system already applies easing.
                    // Applying FastOutSlowInEasing on top causes double-easing / sluggish feel.
                    progressAnim.snapTo(backEvent.progress)
                    onProgressChanged(backEvent.progress)
                }

                // Gesture committed: call onBack immediately. The system handles the
                // dismiss animation. Do NOT animate to 1f here — it fights the system chrome.
                scope.launch {
                    onBack()
                    progressAnim.snapTo(0f)
                    onProgressChanged(0f)
                }
            } catch (_: CancellationException) {
                // Gesture cancelled: spring smoothly back to 0f with no bounce.
                // NoBouncy + StiffnessMedium gives a crisp, responsive snap-back that
                // matches the system's cancel animation speed.
                scope.launch {
                    progressAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
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
 * Screen wrapper that applies predictive back visual effects to full-screen Petal surfaces.
 *
 * Visual effects match PixelPlayer's ScreenWrapper:
 * - Scale: foreground screen scales from 1.0 → 0.92 during the gesture (same 8% reduction).
 * - Corner radius: morphs from 0 → 28dp as gesture progresses.
 * - CompositingStrategy stays STABLE (always Offscreen when predictive is on) to avoid
 *   a one-frame flash when the strategy toggles mid-transition.
 * - Dim + blur: only applied to a screen marked `isBehind = true` (the underlying screen
 *   being revealed). The foreground screen stays sharp and legible throughout.
 * - All values are driven through [Animatable] so they animate smoothly even when
 *   the raw progress Float changes discontinuously (e.g., on a fast gesture cancel).
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

    // Animatable that tracks the gesture progress. LaunchedEffect keeps it in sync with
    // the incoming `progress` Float. Using an Animatable (rather than using `progress`
    // directly in graphicsLayer) means cancel snap-backs animate smoothly instead of
    // snapping — the cancel spring in PetalPredictiveBackJunctionHandler drives progress
    // from its current value to 0f, and this animatable follows frame-by-frame.
    val progressAnim = remember { Animatable(0f) }
    LaunchedEffect(progress) {
        progressAnim.snapTo(progress)
    }

    // foregroundProgress: drives scale + corner radius of the active (front) screen.
    // behindProgress: drives dim + blur of whatever sits underneath (isBehind screens only).
    val foregroundProgress = if (predictiveEnabled) progressAnim.value else 0f
    val behindProgress = if (predictiveEnabled && isBehind) 1f else 0f

    // Scale: 1.0 at rest → 0.92 at full gesture (8% reduction matching Pixel system chrome).
    val scale = 1f - (0.08f * foregroundProgress)

    // Corner radius: morphs 0dp → 28dp as gesture progresses.
    val activeProgress = if (isBehind) behindProgress else foregroundProgress
    val cornerRadius = 28f * activeProgress

    // Dim overlay: behind screen only — stronger when blur is disabled.
    val dimAlpha = (if (!blurEnabled) 0.5f else 0.25f) * behindProgress

    // Depth blur: behind screen only, up to 16dp.
    val blurRadius = if (blurEnabled) 16f * behindProgress else 0f

    Box(
        modifier = modifier
            .fillMaxSize()
            // Keep CompositingStrategy STABLE when predictive is enabled.
            // Toggling between Auto ↔ Offscreen mid-gesture causes a one-frame RenderNode
            // flash (same bug documented in PixelPlayer's ScreenWrapper comments).
            .graphicsLayer {
                compositingStrategy = if (predictiveEnabled) {
                    CompositingStrategy.Offscreen
                } else {
                    CompositingStrategy.Auto
                }
                scaleX = scale
                scaleY = scale
                if (predictiveEnabled && cornerRadius > 0.5f) {
                    this.shape = RoundedCornerShape(cornerRadius.dp)
                    this.clip = true
                } else {
                    this.clip = false
                }
            }
            .blur(radius = blurRadius.dp)
            .background(MaterialTheme.colorScheme.background)
    ) {
        content()
    }
}
