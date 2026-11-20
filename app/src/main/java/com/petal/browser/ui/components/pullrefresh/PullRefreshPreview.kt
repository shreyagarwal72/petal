package com.petal.browser.ui.components.pullrefresh

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.components.PetalPullRefreshIndicator
import com.petal.browser.ui.components.PullPhase
import com.petal.browser.ui.components.PullRefreshContainerHeight
import com.petal.browser.ui.components.rememberAnimationActive
import com.petal.browser.ui.components.rememberPullRefreshTimeline
import com.petal.browser.ui.components.scaledMs
import com.petal.browser.ui.theme.layout.PetalPullRefreshStyle
import kotlinx.coroutines.delay

/** Previews must not buzz the phone every loop. */
private object SilentHaptics : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

private class PreviewFlags { var commitOnRelease = false }

/**
 * Looping simulated pull for the Settings picker: pull 0 -> 1, hold at the threshold, release,
 * refresh, finish, repeat. Dragging on it scrubs the pull by hand (release past the end commits).
 *
 * It drives the exact composables the browser uses ([PetalPullRefreshIndicator], and the
 * Firefox replica for [PetalPullRefreshStyle.FIREFOX]) with a fake progress.
 */
@Composable
fun PullRefreshStylePreview(
    style: PetalPullRefreshStyle,
    modifier: Modifier = Modifier,
) {
    val animActive = rememberAnimationActive()
    var pull by remember { mutableFloatStateOf(0f) }
    var refreshing by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    val flags = remember { PreviewFlags() }

    LaunchedEffect(animActive, scrubbing) {
        if (scrubbing) return@LaunchedEffect
        if (!animActive) {
            // Paused: a still frame partway through the pull.
            refreshing = false
            pull = 0.62f
            return@LaunchedEffect
        }
        if (flags.commitOnRelease) {
            flags.commitOnRelease = false
            refreshing = true
            pull = 0f
            delay(scaledMs(1800).toLong())
            refreshing = false
            delay(scaledMs(900).toLong())
        } else if (pull > 0f || refreshing) {
            refreshing = false
            pull = 0f
            delay(scaledMs(600).toLong())
        }
        while (true) {
            refreshing = false
            pull = 0f
            delay(scaledMs(500).toLong())
            animate(0f, 1f, animationSpec = tween(scaledMs(1400), easing = FastOutSlowInEasing)) { v, _ -> pull = v }
            delay(scaledMs(450).toLong())      // hold at the threshold ("release to refresh")
            refreshing = true                  // release: the layout commits and resets the pull
            pull = 0f
            delay(scaledMs(1900).toLong())     // refreshing
            refreshing = false                 // finish
            delay(scaledMs(1100).toLong())
        }
    }

    val dragRangePx = with(LocalDensity.current) { 110.dp.toPx() }

    CompositionLocalProvider(LocalHapticFeedback provides SilentHaptics) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(PullRefreshContainerHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .pointerInput(dragRangePx) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            scrubbing = true
                            refreshing = false
                            pull = 0f
                        },
                        onDragEnd = {
                            flags.commitOnRelease = pull >= 1f
                            scrubbing = false
                        },
                        onDragCancel = {
                            flags.commitOnRelease = false
                            scrubbing = false
                        },
                        onVerticalDrag = { change, dy ->
                            change.consume()
                            pull = (pull + dy / dragRangePx).coerceIn(0f, 1f)
                        }
                    )
                }
        ) {
            // Skeleton page content under the indicator.
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = 22.dp)
                    .width(150.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = 8.dp)
                    .width(100.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            )
            Text(
                text = "Drag to try",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 8.dp)
            )

            if (style == PetalPullRefreshStyle.FIREFOX) {
                val timeline = rememberPullRefreshTimeline(refreshing) { pull }
                if (timeline.phase != PullPhase.IDLE) FirefoxSpinnerReplica(timeline)
            } else {
                PetalPullRefreshIndicator(
                    style = style,
                    isRefreshing = refreshing,
                    pullProgressProvider = { pull }
                )
            }
        }
    }
}
