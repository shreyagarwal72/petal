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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.currentStateAsState
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

/**
 * State object representing the active predictive back progress.
 */
data class PredictiveBackState(
    val isActive: Boolean = false,
    val progress: Float = 0f,
    val swipeEdge: Int = BackEventCompat.EDGE_LEFT,
) {
    companion object {
        val Idle = PredictiveBackState(isActive = false, progress = 0f, swipeEdge = BackEventCompat.EDGE_LEFT)
    }
}

val LocalPredictiveBackState = compositionLocalOf { PredictiveBackState.Idle }
val LocalPetalPredictiveJunctionState = compositionLocalOf { true }
val LocalPetalDepthBlurJunctionState = compositionLocalOf { true }
val LocalIsUnderlayPreview = compositionLocalOf { false }

/**
 * Petal Predictive Motion Junction
 * Pure, authentic AOSP/Pixel predictive motion ported 1:1 from RvSystem-Monitor & PixelPlayer.
 * Completely eliminates incorrect/stale underlay preview mismatches.
 */
object PetalPredictiveJunction {
    private val _isPredictiveBackEnabled = MutableStateFlow(true)
    val isPredictiveBackEnabled: StateFlow<Boolean> = _isPredictiveBackEnabled.asStateFlow()

    private val _isDepthBlurEnabled = MutableStateFlow(true)
    val isDepthBlurEnabled: StateFlow<Boolean> = _isDepthBlurEnabled.asStateFlow()

    private var prefListenerRef: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var prefsRef: WeakReference<SharedPreferences>? = null

    @JvmStatic
    fun init(prefs: SharedPreferences) {
        prefsRef = WeakReference(prefs)
        val predictive = prefs.getBoolean("sp_predictive_back_animation", true)
        val depthBlur = prefs.getBoolean("sp_depth_blur_effects", true)
        _isPredictiveBackEnabled.value = predictive
        _isDepthBlurEnabled.value = depthBlur

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (key == null || key == "sp_predictive_back_animation") {
                _isPredictiveBackEnabled.value = sp.getBoolean("sp_predictive_back_animation", true)
            }
            if (key == null || key == "sp_depth_blur_effects") {
                _isDepthBlurEnabled.value = sp.getBoolean("sp_depth_blur_effects", true)
            }
        }
        prefListenerRef = listener
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    @JvmStatic
    fun setPredictiveBackEnabled(enabled: Boolean) {
        _isPredictiveBackEnabled.value = enabled
        prefsRef?.get()?.edit()?.putBoolean("sp_predictive_back_animation", enabled)?.apply()
    }

    @JvmStatic
    fun setDepthBlurEnabled(enabled: Boolean) {
        _isDepthBlurEnabled.value = enabled
        prefsRef?.get()?.edit()?.putBoolean("sp_depth_blur_effects", enabled)?.apply()
    }
}

/**
 * Surface wrapper that hosts the PredictiveBackHandler and feeds real-time gesture progress.
 */
@Composable
fun PetalPredictiveBackSurface(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val junctionPredictiveEnabled by PetalPredictiveJunction.isPredictiveBackEnabled.collectAsState()
    val junctionBlurEnabled by PetalPredictiveJunction.isDepthBlurEnabled.collectAsState()
    var backState by remember { mutableStateOf(PredictiveBackState.Idle) }
    val progressAnim = remember { Animatable(0f) }

    if (enabled) {
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
 * Screen wrapper that applies authentic RvSystem-Monitor / PixelPlayer predictive pop-exit motion.
 * 100% clean, fluid, and hardware-accelerated without any wrong-preview underlays.
 */
@Composable
fun PetalScreenWrapper(
    navController: NavController? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    isBehind: Boolean = false,
    backgroundSnapshot: Any? = null,
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

    val isActive = predictiveBackState.isActive
    val progress = if (predictiveEnabled && isActive) predictiveBackState.progress else 0f
    val scaleEased = PetalM3EmphasizedEasing.transform(progress)

    val targetBlurRadius = if (isBehindTopScreen && junctionBlurEnabled) {
        if (isActive) 24f * (1f - scaleEased) else 24f
    } else 0f

    val blurRadius = targetBlurRadius.dp

    Box(
        modifier = modifier
            .fillMaxSize()
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
                    compositingStrategy = if (isActive) CompositingStrategy.Offscreen else CompositingStrategy.Auto

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
                    compositingStrategy = if (isActive || (isBehindTopScreen && junctionBlurEnabled)) CompositingStrategy.Offscreen else CompositingStrategy.Auto
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
                if (isBehindTopScreen && junctionBlurEnabled && blurRadius > 0.dp) {
                    Modifier.blur(blurRadius)
                } else {
                    Modifier
                }
            )
            .then(
                if (!isBehindTopScreen) Modifier.background(MaterialTheme.colorScheme.background)
                else Modifier
            )
    ) {
        content()

        if (isBehindTopScreen) {
            val settledTargetDim = if (junctionBlurEnabled) 0.40f else 0.70f
            val effectiveDimAlpha = if (isActive) settledTargetDim * (1f - scaleEased) else settledTargetDim
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
