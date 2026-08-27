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

package com.petal.browser.predictive

import android.content.SharedPreferences
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global settings & state junction for Predictive Back across the Petal App. Changes here
 * automatically propagate to every page and route without needing per-screen logic.
 *
 * Ported 1:1 from RvSystem-Monitor and PixelPlayer official navigation transitions -
 * slide + scale with M3 emphasized curve and no artificial mockup previews.
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

// ---------------------------------------------------------------------------
// Predictive back gesture state — published downward via CompositionLocal so
// any descendant (ScreenWrapper, PetalScreenWrapper) can track the live finger
// position instead of waiting for the settled navigation event.
// ---------------------------------------------------------------------------

/**
 * Live state of an in-flight predictive back gesture.
 *
 * [progress] is 0f (finger at edge) → 1f (fully committed). It updates every frame
 * while the thumb is moving, which lets the foreground screen's slide/scale track
 * the finger instead of snapping at commit time.
 */
data class PredictiveBackState(
    val isActive: Boolean = false,
    val progress: Float = 0f,
    val swipeEdge: Int = BackEventCompat.EDGE_LEFT,
) {
    companion object {
        val Idle = PredictiveBackState()
    }
}

/** CompositionLocal that carries the current [PredictiveBackState] down the tree. */
val LocalPredictiveBackState = compositionLocalOf { PredictiveBackState.Idle }

// ---------------------------------------------------------------------------
// PetalPredictiveBackSurface — predictive back gesture wrapper
// ---------------------------------------------------------------------------

/**
 * Wraps [content] with a [PredictiveBackHandler] and republishes gesture progress
 * through [LocalPredictiveBackState] so any descendant can react to it live.
 *
 * Only intercepts back when [enabled] is true AND the junction setting is on.
 * Matches official RvSystem-Monitor implementation.
 */
@Composable
fun PetalPredictiveBackSurface(
    enabled: Boolean = true,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val junctionPredictiveEnabled by PetalPredictiveJunction.isPredictiveBackEnabled.collectAsState()
    val junctionBlurEnabled by PetalPredictiveJunction.isDepthBlurEnabled.collectAsState()

    var backState by remember { mutableStateOf(PredictiveBackState.Idle) }
    val progressAnim = remember { Animatable(0f) }

    if (enabled && junctionPredictiveEnabled) {
        PredictiveBackHandler(enabled = true) { progressFlow ->
            val canWebGoBack = try {
                com.petal.browser.activity.BrowserActivity.canNinjaGoBack()
            } catch (_: Exception) {
                false
            }
            if (canWebGoBack) {
                try {
                    (context as? com.petal.browser.activity.BrowserActivity)?.handleBackPress()
                } catch (_: Exception) {
                    (context as? androidx.activity.ComponentActivity)?.onBackPressedDispatcher?.onBackPressed()
                }
                return@PredictiveBackHandler
            }

            try {
                progressFlow.collect { backEvent ->
                    backState = PredictiveBackState(
                        isActive = true,
                        progress = backEvent.progress,
                        swipeEdge = backEvent.swipeEdge,
                    )
                }
                backState = PredictiveBackState.Idle
                onBack()
            } catch (e: CancellationException) {
                progressAnim.snapTo(backState.progress)
                progressAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = PETAL_TRANSITION_DURATION, easing = PetalM3EmphasizedEasing),
                ) {
                    backState = backState.copy(isActive = true, progress = value)
                }
                backState = PredictiveBackState.Idle
            }
        }
    }

    CompositionLocalProvider(
        LocalPetalPredictiveJunctionState provides junctionPredictiveEnabled,
        LocalPetalDepthBlurJunctionState provides junctionBlurEnabled,
        LocalPredictiveBackState provides backState,
    ) {
        content()
    }
}

// ---------------------------------------------------------------------------
// PetalScreenWrapper — visual layer driven by LocalPredictiveBackState
// ---------------------------------------------------------------------------

/**
 * Screen wrapper that applies predictive back visual effects to full-screen Petal surfaces.
 *
 * Ported 1:1 from RvSystem-Monitor's `aospSharedAxisPopExit` & PixelPlayer's official spec:
 * - Slide: full screen width toward the swipe edge with cubic easing.
 * - Scale: 1.0 at rest → 0.85 at full gesture with M3 emphasized easing.
 */
@Composable
fun PetalScreenWrapper(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val junctionPredictiveEnabled by PetalPredictiveJunction.isPredictiveBackEnabled.collectAsState()
    val predictiveBackState = LocalPredictiveBackState.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val isActive = predictiveBackState.isActive
                val progress = if (junctionPredictiveEnabled && isActive) predictiveBackState.progress else 0f

                val slideEased = PetalPopExitSlideEasing.transform(progress)
                val scaleEased = PetalM3EmphasizedEasing.transform(progress)

                val swipeEdge = predictiveBackState.swipeEdge
                val translationXFactor = if (isActive) {
                    if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
                } else {
                    0f
                }

                val scale = 1f - (PETAL_POP_EXIT_MAX_SCALE_DELTA * scaleEased)

                scaleX = scale
                scaleY = scale
                translationX = size.width * translationXFactor * slideEased
                compositingStrategy = if (isActive) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            }
    ) {
        content()
    }
}
