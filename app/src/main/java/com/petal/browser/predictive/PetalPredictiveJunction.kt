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
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global settings & state junction for Predictive Back and Depth Blur effects across the Petal App.
 *
 * Ported 1:1 from RvSystem-Monitor & PixelPlayer's official architecture.
 */
object PetalPredictiveJunction {
    const val KEY_PREDICTIVE_BACK_ENABLED = "sp_predictive_back_junction_enabled"
    const val KEY_DEPTH_BLUR_ENABLED = "sp_depth_blur_junction_enabled"

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
val LocalIsUnderlayPreview = compositionLocalOf { false }

/**
 * Live state of an in-flight predictive back gesture.
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

val LocalPredictiveBackState = compositionLocalOf { PredictiveBackState.Idle }

/**
 * Wraps [content] with a [PredictiveBackHandler] and republishes gesture progress
 * through [LocalPredictiveBackState] so any descendant can react to it live.
 *
 * Renders the real Home Screen surface in the background with 24.dp depth blur
 * during in-flight back gestures, matching RvSystem-Monitor & PixelPlayer 1:1.
 */
@Composable
fun PetalPredictiveBackSurface(
    enabled: Boolean = true,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val junctionPredictiveEnabled by PetalPredictiveJunction.isPredictiveBackEnabled.collectAsState()
    val junctionBlurEnabled by PetalPredictiveJunction.isDepthBlurEnabled.collectAsState()
    val isUnderlayPreview = LocalIsUnderlayPreview.current
    val progressAnim = remember { Animatable(0f) }
    var backState by remember { mutableStateOf(PredictiveBackState.Idle) }

    if (!isUnderlayPreview && enabled) {
        if (junctionPredictiveEnabled) {
            PredictiveBackHandler(enabled = true) { progressFlow ->
                try {
                    progressFlow.collect { backEvent ->
                        progressAnim.snapTo(backEvent.progress)
                        backState = PredictiveBackState(
                            isActive = true,
                            progress = backEvent.progress,
                            swipeEdge = backEvent.swipeEdge,
                        )
                    }
                    progressAnim.snapTo(backState.progress)
                    progressAnim.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMedium,
                            dampingRatio = Spring.DampingRatioNoBouncy
                        )
                    ) {
                        backState = backState.copy(isActive = true, progress = value)
                    }
                    backState = backState.copy(isActive = true, progress = 1f)
                    onBack()
                    kotlinx.coroutines.delay(200)
                    backState = PredictiveBackState.Idle
                } catch (e: CancellationException) {
                    progressAnim.snapTo(backState.progress)
                    progressAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMedium,
                            dampingRatio = Spring.DampingRatioLowBouncy
                        )
                    ) {
                        backState = backState.copy(isActive = true, progress = value)
                    }
                    backState = PredictiveBackState.Idle
                }
            }
        } else {
            // Instant, standard back navigation when predictive animations are toggled off
            androidx.activity.compose.BackHandler(enabled = true, onBack = onBack)
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

/**
 * Screen wrapper that applies predictive back visual effects and depth blur to full-screen Petal surfaces.
 * Ported 1:1 from RvSystem-Monitor & PixelPlayer:
 * - Background (behind) surface: 24.dp depth blur + black dim overlay (0.40f/0.75f clearing as gesture completes)
 *   with aospSharedAxisPopEnter parallax slide in (-33% -> 0).
 * - Foreground (top) surface: crisp scale down (1.0 -> 0.85) + 32.dp corner clipping + 16.dp drop shadow + aospSharedAxisPopExit slide offset (+50% right / -50% left).
 * - AnimatedVisibilityScope support: live 32dp corner radius, 24dp depth blur, and dim alpha transition specs during NavHost transitions.
 */
@Composable
fun PetalScreenWrapper(
    navController: NavController? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    isBehind: Boolean = false,
    backgroundSnapshot: ImageBitmap? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
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

    val junctionPredictiveEnabled by PetalPredictiveJunction.isPredictiveBackEnabled.collectAsState()
    val junctionBlurEnabled by PetalPredictiveJunction.isDepthBlurEnabled.collectAsState()

    val predictiveEnabled = junctionPredictiveEnabled
    val blurEnabled = junctionBlurEnabled
    val predictiveBackState = LocalPredictiveBackState.current

    val myEntry = lifecycleOwner as? NavBackStackEntry
    val previousEntryId = navController?.previousBackStackEntry?.id
    val isBehindTopScreen = isBehind || (myEntry != null && previousEntryId == myEntry.id)

    val transition = animatedVisibilityScope?.transition

    val transitionCornerRadius = if (transition != null) {
        val animatedValue by transition.animateFloat(
            transitionSpec = { tween(durationMillis = 350, easing = FastOutSlowInEasing) },
            label = "cornerRadius"
        ) { state ->
            if (state == EnterExitState.PostExit || state == EnterExitState.PreEnter) 32f else 0f
        }
        animatedValue
    } else 0f

    val transitionDimAlpha = if (transition != null) {
        val animatedValue by transition.animateFloat(
            transitionSpec = { tween(durationMillis = 350, easing = CubicBezierEasing(0.5f, 0f, 0.8f, 0.2f)) },
            label = "dimAlpha"
        ) { state ->
            if (isBehindTopScreen && (state == EnterExitState.PostExit || state == EnterExitState.PreEnter)) {
                if (!blurEnabled) 0.75f else 0.40f
            } else 0f
        }
        animatedValue
    } else 0f

    val transitionBlurRadiusDp = if (transition != null) {
        val animatedValue by transition.animateDp(
            transitionSpec = { tween(durationMillis = 350, easing = CubicBezierEasing(0.5f, 0f, 0.8f, 0.2f)) },
            label = "blurRadius"
        ) { state ->
            if (isBehindTopScreen && blurEnabled && (state == EnterExitState.PostExit || state == EnterExitState.PreEnter)) {
                24.dp
            } else 0.dp
        }
        animatedValue
    } else 0.dp

    val isActive = predictiveBackState.isActive
    val progress = if (predictiveEnabled && isActive) predictiveBackState.progress else 0f
    val scaleEased = PetalM3EmphasizedEasing.transform(progress)

    val currentBlurRadiusDp = if ((isBehindTopScreen || transitionBlurRadiusDp > 0.dp) && blurEnabled) {
        if (isActive) {
            (24f * (1f - scaleEased)).dp
        } else if (transitionBlurRadiusDp > 0.dp) {
            transitionBlurRadiusDp
        } else {
            24.dp
        }
    } else 0.dp

    CompositionLocalProvider(LocalIsUnderlayPreview provides (isBehindTopScreen || LocalIsUnderlayPreview.current)) {
        Box(
            modifier = modifier.fillMaxSize()
        ) {
            // Layer 0: Background snapshot underlay (rendered when gesture is active or behind top screen)
            if (backgroundSnapshot != null && (isActive || isBehindTopScreen)) {
                val snapshotBlurRadius = if (blurEnabled) (24f * (1f - scaleEased)).dp else 0.dp
                val snapshotDimAlpha = if (!blurEnabled) 0.75f * (1f - scaleEased) else 0.40f * (1f - scaleEased)
                val snapshotScale = 0.94f + 0.06f * scaleEased
                val swipeEdge = predictiveBackState.swipeEdge
                val bgDirectionFactor = if (swipeEdge == BackEventCompat.EDGE_RIGHT) (1f / 3f) else (-1f / 3f)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (snapshotBlurRadius > 0.dp) Modifier.blur(radius = snapshotBlurRadius)
                            else Modifier
                        )
                        .graphicsLayer {
                            scaleX = snapshotScale
                            scaleY = snapshotScale
                            translationX = size.width * bgDirectionFactor * (1f - scaleEased)
                        }
                ) {
                    Image(
                        bitmap = backgroundSnapshot,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = snapshotDimAlpha))
                    )
                }
            }

            // Layer 1: Foreground interactive screen content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isBehindTopScreen && blurEnabled && currentBlurRadiusDp > 0.dp) Modifier.blur(radius = currentBlurRadiusDp)
                        else Modifier
                    )
                    .graphicsLayer {
                        val slideEased = PetalPopExitSlideEasing.transform(progress)

                        if (!isBehindTopScreen) {
                            val swipeEdge = predictiveBackState.swipeEdge
                            val translationXFactor = if (isActive) {
                                if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1.0f else 1.0f
                            } else {
                                0f
                            }

                            val scale = 1f - (PETAL_POP_EXIT_MAX_SCALE_DELTA * scaleEased)
                            val cornerRadius = maxOf(32f * scaleEased, transitionCornerRadius)

                            scaleX = scale
                            scaleY = scale
                            translationX = size.width * translationXFactor * slideEased
                            compositingStrategy = if (isActive || transitionCornerRadius > 0.5f) CompositingStrategy.Offscreen else CompositingStrategy.Auto

                            if (cornerRadius > 0.5f) {
                                this.shape = RoundedCornerShape(cornerRadius.dp)
                                this.clip = true
                                this.shadowElevation = (16f * scaleEased).dp.toPx()
                            } else {
                                this.clip = false
                                this.shadowElevation = 0f
                            }
                        } else {
                            val revealScale = if (isActive) 0.94f + 0.06f * scaleEased else 1f
                            val swipeEdge = predictiveBackState.swipeEdge
                            val bgDirectionFactor = if (swipeEdge == BackEventCompat.EDGE_RIGHT) (1f / 3f) else (-1f / 3f)
                            val bgParallaxOffset = if (isActive) size.width * bgDirectionFactor * (1f - scaleEased) else 0f

                            val cornerRadius = maxOf(32f * (1f - scaleEased), transitionCornerRadius)

                            scaleX = revealScale
                            scaleY = revealScale
                            translationX = bgParallaxOffset
                            alpha = 1f
                            compositingStrategy = if (isActive || currentBlurRadiusDp > 0.dp || cornerRadius > 0.5f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                            if (cornerRadius > 0.5f) {
                                this.shape = RoundedCornerShape(cornerRadius.dp)
                                this.clip = true
                            } else {
                                this.clip = false
                            }
                            this.shadowElevation = 0f
                        }
                    }
                    .then(
                        if (backgroundSnapshot == null && !isBehindTopScreen) Modifier.background(MaterialTheme.colorScheme.background)
                        else Modifier
                    )
            ) {
                content()

                if (isBehindTopScreen || transitionDimAlpha > 0f) {
                    val settledTargetDim = if (!blurEnabled) 0.75f else 0.40f
                    val effectiveDimAlpha = if (isActive) settledTargetDim * (1f - scaleEased) else maxOf(settledTargetDim, transitionDimAlpha)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = effectiveDimAlpha
                            }
                            .background(Color.Black)
                    )
                }
            }
        }
    }
}


