package com.petal.browser.ui.components

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.currentBackStackEntryAsState
import com.petal.browser.predictive.LocalPredictiveBackState
import com.petal.browser.predictive.PetalPredictiveJunction

@OptIn(UnstableApi::class)
@Composable
fun ScreenWrapper(
    navController: NavController? = null,
    modifier: Modifier = Modifier,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    content: @Composable () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // Lifecycle State
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

    val syncVisibleEntries = navController?.visibleEntries?.collectAsState()?.value.also { _ -> visibleEntriesState?.value }

    val myEntry = lifecycleOwner as? NavBackStackEntry
    val shouldRunDepthEffects = true

    val previousEntryId = navController?.previousBackStackEntry?.id.also { _ -> currentBackStackEntryState?.value }
    val shouldDim = myEntry != null && previousEntryId == myEntry.id

    val junctionPredictiveEnabled by PetalPredictiveJunction.isPredictiveBackEnabled.collectAsState()
    val junctionBlurEnabled by PetalPredictiveJunction.isDepthBlurEnabled.collectAsState()

    val predictiveEnabled = junctionPredictiveEnabled
    val blurEnabled = junctionBlurEnabled
    val disableBlurAllOver = !blurEnabled

    val predictiveBack = LocalPredictiveBackState.current

    // This ScreenWrapper is being revealed underneath the swiped-away top screen when
    // shouldDim is true, so gate live gesture tracking on the same condition.
    val isPredictiveBackTarget = shouldRunDepthEffects && shouldDim && predictiveBack.isActive
    // Quadratic ease: blur/dim clear noticeably ahead of the finger.
    val backProgressEased =
        if (predictiveBack.isActive) 1f - (1f - predictiveBack.progress).let { it * it }
        else 0f

    // Subtle scale-up on the revealed screen as it comes forward.
    val revealScale = if (predictiveEnabled && isPredictiveBackTarget) {
        0.96f + 0.04f * backProgressEased
    } else {
        1f
    }

    val transition = animatedVisibilityScope?.transition

    // Declarative Animations
    val targetRadius = if (shouldRunDepthEffects && !isResumed) 32f else 0f
    val animatedCornerRadius = if (transition != null) {
        val animatedValue by transition.animateFloat(
            transitionSpec = { tween(durationMillis = 350, easing = FastOutSlowInEasing) },
            label = "cornerRadius"
        ) { state ->
            if (shouldRunDepthEffects && (state == EnterExitState.PostExit || state == EnterExitState.PreEnter)) {
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

    // Dim: If strictly behind Top -> 0.4f (or 0.75f if blur is disabled). Else -> 0f.
    val settledTargetDim = if (shouldRunDepthEffects && shouldDim) {
        if (disableBlurAllOver) 0.75f else 0.4f
    } else {
        0f
    }
    val animatedDimAlpha = if (transition != null) {
        val animatedValue by transition.animateFloat(
            transitionSpec = { tween(durationMillis = 350, easing = CubicBezierEasing(0.5f, 0f, 0.8f, 0.2f)) },
            label = "dimAlpha"
        ) { state ->
            if (shouldRunDepthEffects && shouldDim && (state == EnterExitState.PostExit || state == EnterExitState.PreEnter)) {
                if (disableBlurAllOver) 0.75f else 0.4f
            } else {
                0f
            }
        }
        animatedValue
    } else {
        val fallbackDimAlpha = remember { Animatable(settledTargetDim) }
        LaunchedEffect(settledTargetDim) {
            fallbackDimAlpha.animateTo(settledTargetDim, animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing))
        }
        // While a predictive back gesture is live, dim clears in step with the finger
        // instead of waiting for the settled tween — makes the reveal feel attached to the swipe.
        if (predictiveEnabled && isPredictiveBackTarget) {
            settledTargetDim * (1f - backProgressEased)
        } else {
            fallbackDimAlpha.value
        }
    }

    // Blur: If strictly behind Top -> 24dp. Else -> 0dp. Disabled if disableBlurAllOver is true.
    val settledTargetBlur = if (shouldRunDepthEffects && shouldDim && !disableBlurAllOver) 24f else 0f
    val animatedBlurRadius = if (transition != null) {
        val animatedValue by transition.animateDp(
            transitionSpec = { tween(durationMillis = 350, easing = CubicBezierEasing(0.5f, 0f, 0.8f, 0.2f)) },
            label = "blurRadius"
        ) { state ->
            if (shouldRunDepthEffects && shouldDim && !disableBlurAllOver && (state == EnterExitState.PostExit || state == EnterExitState.PreEnter)) {
                24.dp
            } else {
                0.dp
            }
        }
        animatedValue
    } else {
        val fallbackBlurRadius = remember { Animatable(settledTargetBlur) }
        LaunchedEffect(settledTargetBlur) {
            fallbackBlurRadius.animateTo(settledTargetBlur, animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing))
        }
        // Same live-tracking treatment for blur.
        if (predictiveEnabled && isPredictiveBackTarget && blurEnabled) {
            (settledTargetBlur * (1f - backProgressEased)).dp
        } else {
            fallbackBlurRadius.value.dp
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                compositingStrategy = if (shouldRunDepthEffects) {
                    CompositingStrategy.Offscreen
                } else {
                    CompositingStrategy.Auto
                }
                scaleX = revealScale
                scaleY = revealScale
                if (shouldRunDepthEffects && animatedCornerRadius > 0.5f) {
                    this.shape = RoundedCornerShape(animatedCornerRadius.dp)
                    this.clip = true
                } else {
                    this.clip = false
                }
            }
            .blur(radius = if (shouldRunDepthEffects) animatedBlurRadius else 0.dp)
            .background(MaterialTheme.colorScheme.background)
    ) {
        content()

        // Dim Layer Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = animatedDimAlpha }
                .background(Color.Black)
        )
    }
}
