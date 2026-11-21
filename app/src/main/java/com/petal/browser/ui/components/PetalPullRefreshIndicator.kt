package com.petal.browser.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.petal.browser.browser.PetalVerticalSwipeRefreshLayout
import com.petal.browser.compose.composable.RefreshBarLoadingIndicatorLazy
import com.petal.browser.ui.components.pullrefresh.LiquidBridgeIndicator
import com.petal.browser.ui.components.pullrefresh.MorphLadderIndicator
import com.petal.browser.ui.components.pullrefresh.OvercastMorphIndicator
import com.petal.browser.ui.components.pullrefresh.WavyArcIndicator
import com.petal.browser.ui.theme.layout.PetalPullRefreshStyle
import com.petal.browser.ui.theme.layout.PetalLayoutState
import com.petal.browser.ui.theme.layout.rememberPetalLayoutSettings
import kotlinx.coroutines.flow.collectLatest

/** Where the glyph styles wait (hidden behind the bar) and where they rest while refreshing. */
private val GlyphStartY = (-40).dp
private val GlyphRestY = 12.dp

/** Room for the whole indicator travel (same as the original RefreshBar container). */
val PullRefreshContainerHeight = 120.dp

enum class PullPhase { IDLE, PULLING, REFRESHING, EXITING }

/**
 * The one timeline shared by every non-CURRENT style (logic copied from RefreshBarLoadingIndicatorLazy):
 *  - [shown]: 0 hidden .. 1 committed (can overshoot 1 on the bouncy settle),
 *  - [ready]: the pull reached the threshold (haptic fired, "release to refresh" pop),
 *  - [pop]: scale accent for [ready],
 *  - [phase]: coarse state so styles know whether they are being pulled, spinning or leaving.
 *
 * Every property reads Compose state, so reading it inside graphicsLayer / drawBehind / Canvas
 * lambdas keeps per-touch updates out of composition.
 */
@Stable
class PullRefreshTimeline internal constructor(
    private val shownAnim: Animatable<Float, AnimationVector1D>,
    private val phaseState: State<PullPhase>,
    private val readyState: State<Boolean>,
    private val popState: State<Float>,
    /** False when reduced motion is on or the screen is not resumed: styles must not loop. */
    val animationsActive: Boolean,
) {
    val shown: Float get() = shownAnim.value
    val phase: PullPhase get() = phaseState.value
    val ready: Boolean get() = readyState.value
    val pop: Float get() = popState.value
}

internal fun scaledStiffness(base: Float): Float {
    val s = PetalLayoutState.settings.motionDurationScale.coerceIn(0.25f, 3f)
    return (base / (s * s)).coerceIn(10f, 10_000f)
}

/** Duration in ms multiplied by the user's Motion Duration Scale. */
internal fun scaledMs(ms: Int): Int =
    (ms * PetalLayoutState.settings.motionDurationScale.coerceIn(0.25f, 3f)).toInt().coerceAtLeast(1)

@Composable
fun rememberPullRefreshTimeline(
    isRefreshing: Boolean,
    pullProgressProvider: () -> Float,
): PullRefreshTimeline {
    val currentProvider by rememberUpdatedState(pullProgressProvider)
    val haptics = LocalHapticFeedback.current
    val animActive = rememberAnimationActive()
    val scale = PetalLayoutState.settings.motionDurationScale.coerceIn(0.25f, 3f)

    val shown = remember { Animatable(0f) }
    val phaseState = remember { mutableStateOf(PullPhase.IDLE) }
    val readyState = remember { mutableStateOf(false) }

    LaunchedEffect(isRefreshing, animActive, scale) {
        snapshotFlow { currentProvider() }.collectLatest { raw ->
            val p = raw.coerceIn(0f, 1f)
            if (p > 0f) {
                phaseState.value = if (isRefreshing) PullPhase.REFRESHING else PullPhase.PULLING
                shown.snapTo(p)
                val nowReady = p >= 1f
                if (nowReady && !readyState.value) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                // Hysteresis like the original: leave "ready" only once the pull drops below 85%.
                if (nowReady != readyState.value && (nowReady || p < 0.85f)) readyState.value = nowReady
            } else {
                readyState.value = false
                if (isRefreshing) {
                    phaseState.value = PullPhase.REFRESHING
                    val spec: AnimationSpec<Float> = if (animActive) {
                        PetalSpring.bouncy()
                    } else snap()
                    shown.animateTo(1f, spec)
                } else {
                    if (shown.value > 0.001f) {
                        phaseState.value = PullPhase.EXITING
                        val spec: AnimationSpec<Float> = if (animActive) {
                            PetalSpring.calm()
                        } else snap()
                        shown.animateTo(0f, spec)
                    }
                    shown.snapTo(0f)
                    phaseState.value = PullPhase.IDLE
                }
            }
        }
    }

    val popState = animateFloatAsState(
        targetValue = if (readyState.value) 1.14f else 1f,
        animationSpec = if (animActive) {
            PetalSpring.bouncy()
        } else snap(),
        label = "PullRefreshPop"
    )
    return remember(shown, animActive) {
        PullRefreshTimeline(shown, phaseState, readyState, popState, animActive)
    }
}

/**
 * Slide / scale / fade used by the compact glyph styles. The progress value is read in the draw
 * phase only.
 */
fun Modifier.pullRefreshGlyphChrome(timeline: PullRefreshTimeline): Modifier = graphicsLayer {
    val s = timeline.shown.coerceAtLeast(0f)
    val t = if (s <= 1f) 1f - (1f - s) * (1f - s) else s
    val startY = GlyphStartY.toPx()
    val restY = GlyphRestY.toPx()
    translationY = startY + (restY - startY) * t
    val sc = (0.4f + 0.6f * t) * timeline.pop
    scaleX = sc
    scaleY = sc
    alpha = (s * 2f).coerceIn(0f, 1f)
}

/** Convenience overload: reads the live style from [PetalLayoutState]. */
@Composable
fun PetalPullRefreshIndicator(
    isRefreshing: Boolean,
    pullProgressProvider: () -> Float,
    modifier: Modifier = Modifier,
) {
    PetalPullRefreshIndicator(
        style = rememberPetalLayoutSettings().pullRefreshStyle,
        isRefreshing = isRefreshing,
        pullProgressProvider = pullProgressProvider,
        modifier = modifier
    )
}

/**
 * Dispatcher for the Compose pull-to-refresh overlay. FIREFOX draws nothing here: that style is
 * SwipeRefreshLayout's own circle, shown by [PetalVerticalSwipeRefreshLayout].
 */
@Composable
fun PetalPullRefreshIndicator(
    style: PetalPullRefreshStyle,
    isRefreshing: Boolean,
    pullProgressProvider: () -> Float,
    modifier: Modifier = Modifier,
) {
    when (style) {
        PetalPullRefreshStyle.CURRENT -> RefreshBarLoadingIndicatorLazy(
            isRefreshing = isRefreshing,
            pullProgressProvider = pullProgressProvider,
            modifier = modifier
        )
        PetalPullRefreshStyle.FIREFOX -> Unit
        else -> PullRefreshStyleHost(style, isRefreshing, pullProgressProvider, modifier)
    }
}

@Composable
private fun PullRefreshStyleHost(
    style: PetalPullRefreshStyle,
    isRefreshing: Boolean,
    pullProgressProvider: () -> Float,
    modifier: Modifier,
) {
    val timeline = rememberPullRefreshTimeline(isRefreshing, pullProgressProvider)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(PullRefreshContainerHeight),
        contentAlignment = Alignment.TopCenter
    ) {
        // Composed only while something is visible, so idle tabs run no infinite animations.
        if (timeline.phase != PullPhase.IDLE) {
            when (style) {
                PetalPullRefreshStyle.OVERCAST_MORPH -> OvercastMorphIndicator(timeline)
                PetalPullRefreshStyle.WAVY_ARC -> WavyArcIndicator(timeline)
                PetalPullRefreshStyle.MORPH_LADDER -> MorphLadderIndicator(timeline)
                PetalPullRefreshStyle.LIQUID_BRIDGE -> LiquidBridgeIndicator(timeline)
                else -> Unit
            }
        }
    }
}

/**
 * Pushes the live MaterialTheme colors (dark, AMOLED and dynamic color included) into the
 * layout's own Firefox-style spinner. Call it from any host composable inside the app theme.
 */
@Composable
fun PetalPullRefreshNativeTheme(layout: PetalVerticalSwipeRefreshLayout?) {
    val scheme = MaterialTheme.colorScheme
    val arrow = scheme.primary.toArgb()
    val disc = scheme.surfaceContainerHigh.toArgb()
    SideEffect { layout?.applyNativeSpinnerTheme(arrow, disc) }
}
