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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
        _isPredictiveBackEnabled.value = true
        _isDepthBlurEnabled.value = prefs.getBoolean(KEY_DEPTH_BLUR_ENABLED, true)
    }

    @JvmStatic
    fun setPredictiveBackEnabled(prefs: SharedPreferences, enabled: Boolean) {
        _isPredictiveBackEnabled.value = true
        prefs.edit().putBoolean(KEY_PREDICTIVE_BACK_ENABLED, true).apply()
    }

    @JvmStatic
    fun setDepthBlurEnabled(prefs: SharedPreferences, enabled: Boolean) {
        _isDepthBlurEnabled.value = enabled
        prefs.edit().putBoolean(KEY_DEPTH_BLUR_ENABLED, enabled).apply()
    }
}

val LocalPetalPredictiveJunctionState = compositionLocalOf { true }
val LocalPetalDepthBlurJunctionState = compositionLocalOf { true }
val LocalIsUnderlayPreview = compositionLocalOf { false }

// ---------------------------------------------------------------------------
// Predictive back gesture state — published downward via CompositionLocal so
// any descendant (ScreenWrapper, PetalScreenWrapper) can track the live finger
// position instead of waiting for the settled navigation event.
// ---------------------------------------------------------------------------

/**
 * Live state of an in-flight predictive back gesture.
 *
 * [progress] is 0f (finger at edge) → 1f (fully committed). It updates every frame
 * while the thumb is moving, which lets the revealed screen's blur/dim/scale track
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
 * - Only intercepts back when [enabled] is true AND the junction setting is on.
 * - On cancel: animates progress smoothly back to 0 (220 ms tween) so blur/scale
 *   relax instead of snapping — matches system back-cancel feel.
 * - On commit: resets state immediately and calls [onBack]. The system chrome
 *   handles the dismissal animation; do NOT spring-to-1f first.
 */
@Composable
fun PetalPredictiveBackSurface(
    enabled: Boolean = true,
    onBack: () -> Unit,
    underlayContent: (@Composable () -> Unit)? = { com.petal.browser.compose.home.PetalHomeScreen() },
    content: @Composable () -> Unit,
) {
    val junctionPredictiveEnabled by PetalPredictiveJunction.isPredictiveBackEnabled.collectAsState()
    val junctionBlurEnabled by PetalPredictiveJunction.isDepthBlurEnabled.collectAsState()
    val isUnderlayPreview = LocalIsUnderlayPreview.current

    val isFullyEnabled = enabled && junctionPredictiveEnabled && !isUnderlayPreview

    var backState by remember { mutableStateOf(PredictiveBackState.Idle) }
    val progressAnim = remember { Animatable(0f) }

    if (isFullyEnabled) {
        PredictiveBackHandler(enabled = true) { progressFlow ->
            try {
                progressFlow.collectLatest { backEvent ->
                    progressAnim.snapTo(backEvent.progress)
                    backState = PredictiveBackState(
                        isActive = true,
                        progress = backEvent.progress,
                        swipeEdge = backEvent.swipeEdge,
                    )
                }
                // Gesture committed — smoothly animate remaining progress to 1f before firing back
                progressAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)
                ) {
                    backState = backState.copy(isActive = true, progress = value)
                }
                backState = backState.copy(isActive = true, progress = 1f)
                onBack()
                backState = PredictiveBackState.Idle
            } catch (e: CancellationException) {
                // Gesture cancelled — smoothly relax progress back to 0f
                progressAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                ) {
                    backState = backState.copy(isActive = true, progress = value)
                }
                backState = PredictiveBackState.Idle
                throw e
            }
        }
    }

    CompositionLocalProvider(
        LocalPetalPredictiveJunctionState provides junctionPredictiveEnabled,
        LocalPetalDepthBlurJunctionState provides junctionBlurEnabled,
        LocalPredictiveBackState provides backState
    ) {
        if (underlayContent != null && !isUnderlayPreview) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (backState.isActive) {
                    PetalScreenWrapper(isBehind = true) {
                        underlayContent()
                    }
                }
                content()
            }
        } else {
            content()
        }
    }
}

// ---------------------------------------------------------------------------
// PetalScreenWrapper — visual layer driven by LocalPredictiveBackState
// ---------------------------------------------------------------------------

/**
 * Screen wrapper that applies predictive back visual effects to full-screen Petal surfaces.
 *
 * Reads [LocalPredictiveBackState] directly instead of requiring a manually threaded
 * `progress` float parameter. Wrap with [PetalPredictiveBackSurface] at the call site
 * to publish gesture state into the composition tree.
 *
 * Visual effects:
 * - Scale: 1.0 at rest → 0.88 at full gesture (foreground screen shrinking).
 * - Corner radius: 0 dp → 32 dp as gesture progresses (foreground only).
 * - Dim + blur on [isBehind] screens — clears live with the finger so the reveal
 *   feels attached to the swipe, not triggered by the commit.
 * - Revealed-screen scale: 0.94 → 1.0 as gesture progresses (subtle grow-to-meet).
 * - Ease: FastOutSlowInEasing applied to the raw progress so blur/dim clear
 *   noticeably ahead of the finger — matches PixelPlayer & RvSystem-Monitor.
 */
@Composable
fun PetalScreenWrapper(
    isBehind: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val junctionPredictiveEnabled by PetalPredictiveJunction.isPredictiveBackEnabled.collectAsState()
    val junctionBlurEnabled by PetalPredictiveJunction.isDepthBlurEnabled.collectAsState()

    val predictiveEnabled = junctionPredictiveEnabled
    val blurEnabled = junctionBlurEnabled
    val disableBlurAllOver = !blurEnabled

    val predictiveBack = LocalPredictiveBackState.current

    val isPredictiveBackTarget = isBehind && predictiveBack.isActive

    // FastOutSlowInEasing matching PixelPlayer & RvSystem-Monitor feel
    val backProgressEased =
        if (predictiveBack.isActive) FastOutSlowInEasing.transform(predictiveBack.progress)
        else 0f

    // Foreground PixelPlayer / RvSystem-Monitor style predictive back card transformations:
    // Scale: 1.0 -> 0.88, Corner Radius: 0 -> 32dp, Alpha: 1.0 -> 0.85, shadow & edge-aware translation
    val foregroundProgress = if (predictiveEnabled && !isBehind) predictiveBack.progress else 0f
    val scale = 1f - (0.12f * foregroundProgress)
    val cornerRadius = if (!isBehind) 32f * foregroundProgress else 0f
    val alphaVal = if (!isBehind && predictiveBack.isActive) 1f - (0.15f * foregroundProgress) else 1f

    val swipeEdge = predictiveBack.swipeEdge
    val translationXFactor = if (!isBehind && predictiveBack.isActive) {
        if (swipeEdge == BackEventCompat.EDGE_LEFT) 0.35f
        else if (swipeEdge == BackEventCompat.EDGE_RIGHT) -0.35f
        else 0f
    } else 0f

    val settledTargetDim = if (isBehind) {
        if (disableBlurAllOver) 0.75f else 0.40f
    } else {
        0f
    }
    val animatedDimAlpha = if (predictiveEnabled && isBehind) {
        if (predictiveBack.isActive) {
            settledTargetDim * (1f - backProgressEased)
        } else {
            settledTargetDim
        }
    } else {
        0f
    }

    val settledTargetBlur = if (isBehind && !disableBlurAllOver) 24f else 0f
    val animatedBlurRadius = if (isBehind && !disableBlurAllOver) {
        if (predictiveEnabled && predictiveBack.isActive) {
            (settledTargetBlur * (1f - backProgressEased)).dp
        } else {
            settledTargetBlur.dp
        }
    } else {
        0.dp
    }

    val revealScale = if (predictiveEnabled && isBehind) {
        if (predictiveBack.isActive) {
            0.94f + 0.06f * backProgressEased
        } else {
            0.94f
        }
    } else {
        1f
    }

    CompositionLocalProvider(LocalIsUnderlayPreview provides (isBehind || LocalIsUnderlayPreview.current)) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = if (predictiveEnabled && predictiveBack.isActive) {
                        CompositingStrategy.Offscreen
                    } else {
                        CompositingStrategy.Auto
                    }
                    scaleX = if (isBehind) revealScale else scale
                    scaleY = if (isBehind) revealScale else scale
                    translationX = if (!isBehind) size.width * translationXFactor * foregroundProgress else 0f
                    translationY = if (!isBehind) size.height * 0.015f * foregroundProgress else 0f
                    alpha = alphaVal
                    if (predictiveEnabled && cornerRadius > 0.5f) {
                        this.shape = RoundedCornerShape(cornerRadius.dp)
                        this.clip = true
                        this.shadowElevation = (16f * foregroundProgress).dp.toPx()
                    } else {
                        this.clip = false
                        this.shadowElevation = 0f
                    }
                }
                .blur(radius = if (!disableBlurAllOver) animatedBlurRadius else 0.dp)
                .background(MaterialTheme.colorScheme.background)
        ) {
            content()

            if (isBehind) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = animatedDimAlpha }
                        .background(Color.Black),
                )
            }
        }
    }
}

