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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest

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
    content: @Composable () -> Unit,
) {
    val junctionPredictiveEnabled by PetalPredictiveJunction.isPredictiveBackEnabled.collectAsState()
    val junctionBlurEnabled by PetalPredictiveJunction.isDepthBlurEnabled.collectAsState()

    val isFullyEnabled = enabled && junctionPredictiveEnabled

    var backState by remember { mutableStateOf(PredictiveBackState.Idle) }
    // Used only during cancel settling so the smooth relaxation has something to follow.
    val settleProgress = remember { Animatable(0f) }

    if (isFullyEnabled) {
        PredictiveBackHandler(enabled = true) { progressFlow ->
            try {
                progressFlow.collectLatest { backEvent ->
                    backState = PredictiveBackState(
                        isActive = true,
                        progress = backEvent.progress,
                        swipeEdge = backEvent.swipeEdge,
                    )
                }
                // Gesture committed — let the system drive the dismissal.
                // Reset immediately so the revealed screen doesn't hold a stale dim/blur.
                backState = PredictiveBackState.Idle
                onBack()
            } catch (e: CancellationException) {
                // Gesture cancelled: animate progress back to 0 so blur/scale relax smoothly
                // instead of snapping clear. 220 ms matches the system's cancel spring duration.
                settleProgress.snapTo(backState.progress)
                settleProgress.animateTo(0f, animationSpec = tween(durationMillis = 220))
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
        content()
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
 * - Scale: 1.0 at rest → 0.92 at full gesture (foreground screen shrinking).
 * - Corner radius: 0 dp → 28 dp as gesture progresses (foreground only).
 * - Dim + blur on [isBehind] screens — clears live with the finger so the reveal
 *   feels attached to the swipe, not triggered by the commit.
 * - Revealed-screen scale: 0.96 → 1.0 as gesture progresses (subtle grow-to-meet).
 * - Ease: quadratic (1-(1-p)²) applied to the raw progress so blur/dim clear
 *   noticeably ahead of the finger — matches the system's own back-reveal feel.
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

    // Gate active reveal tracking: only the second-from-top screen should react to the
    // live finger position. When not behind, only the foreground effects (scale, corner)
    // apply, and they are driven purely by predictiveBack.progress.
    val isPredictiveBackTarget = isBehind && predictiveBack.isActive

    // Quadratic ease: blur/dim clear noticeably faster than the raw swipe progress.
    val backProgressEased =
        if (predictiveBack.isActive) 1f - (1f - predictiveBack.progress).let { it * it }
        else 0f

    // -----------------------------------------------------------------------
    // Foreground effects — active screen shrinks + grows rounded corners
    // -----------------------------------------------------------------------

    // Scale: 1.0 → 0.92 during gesture (8 % matching Pixel system chrome).
    val foregroundProgress = if (predictiveEnabled && !isBehind) predictiveBack.progress else 0f
    val scale = 1f - (0.08f * foregroundProgress)

    // Corner radius: 0 dp → 28 dp as gesture progresses.
    val cornerRadius = if (!isBehind) 28f * foregroundProgress else 0f

    // -----------------------------------------------------------------------
    // Behind-screen effects — dim + blur clear live with the finger
    // -----------------------------------------------------------------------

    // Settled target dim: If strictly behind top -> 0.4f (or 0.75f if blur is disabled). Else -> 0f.
    val settledTargetDim = if (isBehind) {
        if (disableBlurAllOver) 0.75f else 0.4f
    } else {
        0f
    }
    // Fallback tween: animates dim on screen push/pop lifecycle events.
    val fallbackDimAlpha = remember { Animatable(settledTargetDim) }
    LaunchedEffect(settledTargetDim) {
        fallbackDimAlpha.animateTo(
            settledTargetDim,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        )
    }
    // Live tracking overrides the tween while a gesture is in flight.
    val animatedDimAlpha = if (predictiveEnabled && isPredictiveBackTarget) {
        settledTargetDim * (1f - backProgressEased)
    } else {
        fallbackDimAlpha.value
    }

    // Settled target blur: If strictly behind top -> 24dp (or 0dp if blur disabled).
    val settledTargetBlur = if (isBehind && !disableBlurAllOver) 24f else 0f
    val fallbackBlurRadius = remember { Animatable(settledTargetBlur) }
    LaunchedEffect(settledTargetBlur) {
        fallbackBlurRadius.animateTo(
            settledTargetBlur,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        )
    }
    val animatedBlurRadius = if (predictiveEnabled && isPredictiveBackTarget && !disableBlurAllOver) {
        (settledTargetBlur * (1f - backProgressEased)).dp
    } else {
        fallbackBlurRadius.value.dp
    }

    // Subtle scale-up on the revealed screen — mirrors the system back-to-home animation
    // where the destination grows slightly to meet the finger.
    val revealScale = if (predictiveEnabled && isPredictiveBackTarget) {
        0.96f + 0.04f * backProgressEased
    } else {
        1f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Keep CompositingStrategy STABLE when predictive is enabled.
            .graphicsLayer {
                compositingStrategy = if (predictiveEnabled) {
                    CompositingStrategy.Offscreen
                } else {
                    CompositingStrategy.Auto
                }
                scaleX = if (isBehind) revealScale else scale
                scaleY = if (isBehind) revealScale else scale
                if (predictiveEnabled && cornerRadius > 0.5f) {
                    this.shape = RoundedCornerShape(cornerRadius.dp)
                    this.clip = true
                } else {
                    this.clip = false
                }
            }
            .blur(radius = if (!disableBlurAllOver) animatedBlurRadius else 0.dp)
            .background(MaterialTheme.colorScheme.background)
    ) {
        content()

        // Dim overlay — only visible on the revealed (behind) screen.
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

