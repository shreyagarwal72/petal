package com.petal.browser.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.petal.browser.R
import com.petal.browser.motion.MotionGate
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.containment.rememberPetalGroupShape
import kotlinx.coroutines.delay

/**
 * Loading skeletons. Use ONLY where data really loads asynchronously and there is a visible
 * wait; never for anything that comes straight from memory.
 *
 * - Colors come from surfaceContainerHigh / surfaceContainerHighest. No borders.
 * - A slow linear sweep moves between the two tones. When reduce-motion is on,
 *   MotionGate.shouldSimplifyEffects() is true, or the host is not resumed, the skeleton is one
 *   static solid color with no animation.
 * - Wrap the screen's list in [PetalSkeletonSwitcher] so the skeleton stays at least
 *   [PETAL_SKELETON_MIN_VISIBLE_MS] and then fades into the content with PetalSpring.calm().
 */
const val PETAL_SKELETON_MIN_VISIBLE_MS = 250L
private const val SWEEP_MS = 1800

@Composable
private fun rememberSkeletonStatic(): Boolean {
    val reduceMotion = rememberReduceMotion()
    val animationActive = rememberAnimationActive()
    return reduceMotion || !animationActive || MotionGate.shouldSimplifyEffects()
}

/** Paints the shimmer (or the solid fallback) behind a skeleton shape. */
@Composable
private fun Modifier.skeletonFill(shape: Shape): Modifier {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    if (rememberSkeletonStatic()) return this.clip(shape).background(base)
    val progress by rememberInfiniteTransition(label = "PetalSkeleton").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SWEEP_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "PetalSkeletonSweep"
    )
    return this
        .clip(shape)
        .background(base)
        .drawWithCache {
            val w = size.width.coerceAtLeast(1f)
            onDrawBehind {
                val x = (progress * 2f - 0.5f) * w
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, highlight, Color.Transparent),
                        start = Offset(x - w * 0.4f, 0f),
                        end = Offset(x + w * 0.4f, size.height),
                    )
                )
            }
        }
}

/** A rounded block. Pass the real element's size and corner radius so layout does not jump. */
@Composable
fun PetalSkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    Box(modifier.skeletonFill(shape))
}

/** One line of text. [height] should match the real line height. */
@Composable
fun PetalSkeletonText(
    modifier: Modifier = Modifier,
    widthFraction: Float = 0.6f,
    height: Dp = 14.dp,
) {
    PetalSkeletonBlock(
        modifier = modifier.fillMaxWidth(widthFraction).height(height),
        shape = RoundedCornerShape(percent = 50),
    )
}

/** A circle, e.g. an avatar or favicon placeholder. */
@Composable
fun PetalSkeletonCircle(size: Dp, modifier: Modifier = Modifier) {
    PetalSkeletonBlock(modifier.size(size), CircleShape)
}

/**
 * Ready-made list row matching PetalGroupListRow: same grouped corners, 64dp min height,
 * 12x10dp padding, 10dp gap, and a 44dp rounded-14 leading tile (the icon tile History,
 * Bookmarks and Passwords use).
 */
@Composable
fun PetalSkeletonListRow(
    position: PetalGroupPosition,
    modifier: Modifier = Modifier,
    minHeight: Dp = 64.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    leadingSize: Dp = 44.dp,
    leadingShape: Shape = RoundedCornerShape(14.dp),
) {
    val shape = rememberPetalGroupShape(position)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(petalGroupSurfaceColor())
            .heightIn(min = minHeight)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PetalSkeletonBlock(Modifier.size(leadingSize), leadingShape)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PetalSkeletonText(widthFraction = 0.55f, height = 16.dp)
            PetalSkeletonText(widthFraction = 0.8f, height = 12.dp)
        }
    }
}

/** A grouped stack of [PetalSkeletonListRow]s, spaced like the real grouped list (3dp). */
@Composable
fun PetalSkeletonList(
    modifier: Modifier = Modifier,
    rows: Int = 8,
    spacing: Dp = 3.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val loadingLabel = stringResource(R.string.petal_skeleton_loading)
    Column(
        modifier = modifier
            .padding(contentPadding)
            .semantics(mergeDescendants = true) { contentDescription = loadingLabel },
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        repeat(rows) { i ->
            Box(Modifier.clearAndSetSemantics { }) {
                PetalSkeletonListRow(petalSkeletonPosition(i, rows))
            }
        }
    }
}

private fun petalSkeletonPosition(index: Int, count: Int): PetalGroupPosition = when {
    count <= 1 -> PetalGroupPosition.SINGLE
    index == 0 -> PetalGroupPosition.TOP
    index == count - 1 -> PetalGroupPosition.BOTTOM
    else -> PetalGroupPosition.MIDDLE
}

/**
 * True while [isLoading], and for at least [minVisibleMs] once it has been shown, so a fast
 * load never flashes. If loading is already finished on first composition it is never shown.
 */
@Composable
fun rememberSkeletonVisible(isLoading: Boolean, minVisibleMs: Long = PETAL_SKELETON_MIN_VISIBLE_MS): Boolean {
    var visible by remember { mutableStateOf(isLoading) }
    var shownAt by remember { mutableStateOf(if (isLoading) System.currentTimeMillis() else 0L) }
    LaunchedEffect(isLoading) {
        if (isLoading) {
            if (!visible) shownAt = System.currentTimeMillis()
            visible = true
        } else if (visible) {
            val remaining = minVisibleMs - (System.currentTimeMillis() - shownAt)
            if (remaining > 0) delay(remaining)
            visible = false
        }
    }
    return visible
}

/** Shows [skeleton] while loading, then fades to [content] with the calm preset. */
@Composable
fun PetalSkeletonSwitcher(
    isLoading: Boolean,
    skeleton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val showSkeleton = rememberSkeletonVisible(isLoading)
    Crossfade(
        targetState = showSkeleton,
        modifier = modifier,
        animationSpec = PetalSpring.calm(),
        label = "PetalSkeletonSwitch",
    ) { skeletonShown ->
        if (skeletonShown) skeleton() else content()
    }
}
