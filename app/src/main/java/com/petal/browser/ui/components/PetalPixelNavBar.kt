package com.petal.browser.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import com.petal.browser.ui.theme.ExpressiveButtonShape
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.R
import com.petal.browser.ui.glass.TabWeights
import com.petal.browser.ui.glass.bouncyClickable
import com.petal.browser.ui.glass.navIconMotion
import kotlin.math.roundToInt

private val PixelNavOrder = listOf(PetalNavTab.HOME, PetalNavTab.NEW_TAB, PetalNavTab.TABS, PetalNavTab.MENU)

/**
 * Material 3 Expressive floating nav pill in PixelMusic's style (used when liquid glass is off).
 *
 * One filled capsule slides between the tabs and the selected tab's label opens, both driven by the
 * same spring ([TabWeights]) as the glass bar, so the capsule, the label and the icon colours move as one.
 * Every colour comes from [MaterialTheme.colorScheme], so light, dark and dynamic themes all work.
 * Holds no one-shot intro animation, so it can be swapped in and out of the glass bar without replaying.
 */
@Composable
internal fun PetalPixelNavPill(
    selectedTab: PetalNavTab,
    tabCount: Int,
    badgeScale: Float,
    onHomeClick: () -> Unit,
    onNewTabClick: () -> Unit,
    onTabsClick: () -> Unit,
    onMenuClick: () -> Unit,
    onSwipeTabLeft: () -> Unit,
    onSwipeTabRight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme

    // Swiping along the pill switches tabs; the pill follows the finger with resistance.
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val maxDragPx = with(LocalDensity.current) { 28.dp.toPx() }
    val dragOffsetPx by animateFloatAsState(
        targetValue = if (isDragging) (dragAccumulator * 0.35f).coerceIn(-maxDragPx, maxDragPx) else 0f,
        animationSpec = if (isDragging) snap() else PetalSpring.sheetDragSettle(),
        label = "pixel_nav_rubber_band"
    )

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
        shadowElevation = 6.dp,
        modifier = modifier
            .height(64.dp)
            .graphicsLayer { translationX = dragOffsetPx }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = {
                        isDragging = false
                        if (dragAccumulator > 70f) onSwipeTabLeft()
                        else if (dragAccumulator < -70f) onSwipeTabRight()
                        dragAccumulator = 0f
                    },
                    onDragCancel = {
                        isDragging = false
                        dragAccumulator = 0f
                    },
                    onHorizontalDrag = { _, dragAmount -> dragAccumulator += dragAmount }
                )
            }
    ) {
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            // Adaptive layout calculations: unselected tabs take a compact icon slot (38-42dp),
            // leaving generous space for the selected tab's label so short titles like "Home" or "Menu"
            // are never truncated with an ellipsis.
            val isCompact = maxWidth < 285.dp
            val unselectedSlotWidth = if (isCompact) 38.dp else 42.dp
            val selectedFixedParts = 24.dp /*icon*/ + 6.dp /*gap*/ + (if (isCompact) 20.dp else 24.dp) /*pad*/ + 12.dp /*rowPad*/
            val minUnselectedWidth = (PixelNavOrder.size - 1) * unselectedSlotWidth
            val labelMaxPx = with(LocalDensity.current) {
                (maxWidth - minUnselectedWidth - selectedFixedParts).coerceIn(54.dp, 120.dp).roundToPx()
            }
            val weights = remember { TabWeights(PixelNavOrder, selectedTab) }
            LaunchedEffect(selectedTab) { weights.animateTo(selectedTab) }
            // Item positions in the root, and the row's own origin, so the capsule can be drawn
            // behind the row at the blend of the item positions.
            val itemBounds = remember { mutableStateMapOf<PetalNavTab, Rect>() }
            var rowOrigin by remember { mutableStateOf(Offset.Zero) }
            val capsuleColor = scheme.primary

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { rowOrigin = it.positionInRoot() }
                    .drawBehind {
                        var total = 0f
                        var l = 0f; var t = 0f; var r = 0f; var b = 0f
                        PixelNavOrder.forEach { tab ->
                            val w = weights[tab].coerceAtLeast(0f)
                            val rc = itemBounds[tab]
                            if (rc != null && w > 0f) {
                                total += w
                                l += rc.left * w; t += rc.top * w; r += rc.right * w; b += rc.bottom * w
                            }
                        }
                        if (total > 0.001f) {
                            val left = l / total - rowOrigin.x
                            val top = t / total - rowOrigin.y
                            val width = r / total - l / total
                            val height = b / total - t / total
                            drawRoundRect(
                                color = capsuleColor,
                                topLeft = Offset(left, top),
                                size = androidx.compose.ui.geometry.Size(width, height),
                                cornerRadius = CornerRadius(height / 2f)
                            )
                        }
                    }
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                PixelNavOrder.forEach { tab ->
                    val selected = selectedTab == tab
                    val weight = { weights[tab].coerceIn(0f, 1f) }
                    val label = when (tab) {
                        PetalNavTab.HOME -> "Home"
                        PetalNavTab.NEW_TAB -> "New"
                        PetalNavTab.TABS -> "Tabs"
                        else -> "Menu"
                    }
                    val onClick = when (tab) {
                        PetalNavTab.HOME -> onHomeClick
                        PetalNavTab.NEW_TAB -> onNewTabClick
                        PetalNavTab.TABS -> onTabsClick
                        else -> onMenuClick
                    }
                    // Colour follows the capsule: it is on-primary as the capsule arrives.
                    val tint = lerp(scheme.onPrimaryContainer, scheme.onPrimary, weight())
                    val padH = if (isCompact) {
                        if (selected) 12.dp else 8.dp
                    } else {
                        if (selected) 14.dp else 10.dp
                    }
                    Box(
                        modifier = Modifier
                            .onGloballyPositioned {
                                val p = it.positionInRoot()
                                itemBounds[tab] = Rect(p.x, p.y, p.x + it.size.width, p.y + it.size.height)
                            }
                            .bouncyClickable(scaleDown = 0.90f, onClick = onClick)
                            .clip(RoundedCornerShape(26.dp))
                            .semantics { contentDescription = label }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = padH, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(24.dp).navIconMotion(selected),
                                contentAlignment = Alignment.Center
                            ) {
                                when (tab) {
                                    PetalNavTab.HOME -> Icon(
                                        painter = painterResource(if (selected) R.drawable.home_filled else R.drawable.home),
                                        contentDescription = null,
                                        tint = tint,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    PetalNavTab.NEW_TAB -> Icon(
                                        Icons.Rounded.Add, contentDescription = null, tint = tint,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    PetalNavTab.TABS -> TabCountBadge(color = tint, count = tabCount, scale = badgeScale)
                                    else -> Icon(
                                        Icons.Rounded.MoreVert, contentDescription = null, tint = tint,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            // Opens by its item's weight: measured at full width, laid out at that
                            // fraction of it and clipped. Read in layout and draw only.
                            // Uses BasicText with StepBased auto-sizing so custom variable fonts
                            // or large display scaling adapt gracefully without truncating labels.
                            BasicText(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onPrimary,
                                    letterSpacing = 0.sp
                                ),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                autoSize = TextAutoSize.StepBased(
                                    minFontSize = 9.sp,
                                    maxFontSize = 13.sp,
                                    stepSize = 0.5.sp
                                ),
                                modifier = Modifier
                                    .graphicsLayer {
                                        alpha = weight()
                                        clip = true
                                    }
                                    .layout { measurable, _ ->
                                        val text = measurable.measure(Constraints(maxWidth = labelMaxPx))
                                        val gap = 6.dp.roundToPx()
                                        val width = ((text.width + gap) * weight()).roundToInt()
                                        layout(width, text.height) { text.placeRelative(gap, 0) }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Separate ghost-shaped search button for the floating nav bar when liquid glass is off.
 *
 * Sits to the right of [PetalPixelNavPill] and morphs its corners to [ExpressiveButtonShape].
 * Uses a safe convex shadow hull to prevent HWUI concave shadow ANRs.
 */
@Composable
internal fun PetalGhostSearchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    val scheme = MaterialTheme.colorScheme
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(140L)
        enter.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 320f))
    }

    Surface(
        shape = ExpressiveButtonShape,
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
        shadowElevation = 0.dp,
        modifier = modifier
            .graphicsLayer {
                val s = enter.value.coerceAtLeast(0f)
                scaleX = s
                scaleY = s
                alpha = enter.value.coerceIn(0f, 1f)
            }
            .size(size)
            .shadow(6.dp, ExpressiveButtonShape.shadowShape, clip = false)
            .clip(ExpressiveButtonShape)
            .bouncyClickable(scaleDown = 0.86f, onClick = onClick)
            .semantics { contentDescription = "Search" }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_petal_search),
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

