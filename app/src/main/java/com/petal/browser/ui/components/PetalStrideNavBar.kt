package com.petal.browser.ui.components

import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.res.stringResource
import com.petal.browser.R
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.ui.glass.SLAB_MAGNIFY
import com.petal.browser.ui.glass.TabWeights
import com.petal.browser.ui.glass.bouncyClickable
import com.petal.browser.ui.glass.glassTile
import com.petal.browser.ui.glass.liquidGlass
import com.petal.browser.ui.glass.glassPressOverlay
import com.petal.browser.ui.glass.glassPressScale
import com.petal.browser.ui.glass.navIconMotion
import com.petal.browser.ui.glass.rememberGlassBar
import kotlin.math.roundToInt
import androidx.compose.material3.Icon
import com.petal.browser.ui.theme.PetalText

private val NavOrder4 = listOf(PetalNavTab.HOME, PetalNavTab.AI, PetalNavTab.NEW_TAB, PetalNavTab.TABS, PetalNavTab.MENU)
/** Off the home page: no AI tab. */
private val NavOrder4NoAi = listOf(PetalNavTab.HOME, PetalNavTab.NEW_TAB, PetalNavTab.TABS, PetalNavTab.MENU)
private val NavOrder5NoAi = listOf(PetalNavTab.HOME, PetalNavTab.NEW_TAB, PetalNavTab.SEARCH, PetalNavTab.TABS, PetalNavTab.MENU)
/** With search in the centre. */
private val NavOrder5 = listOf(PetalNavTab.HOME, PetalNavTab.AI, PetalNavTab.NEW_TAB, PetalNavTab.SEARCH, PetalNavTab.TABS, PetalNavTab.MENU)

/**
 * What the bar needs besides the selected label: four icon slots (24 dp icon + 14 dp
 * padding each side), the gaps between them, the row's own padding and the label gap.
 */
private val NAV_FIXED_WIDTH = (4 * 52 + 3 * 4 + 20 + 8).dp

/** Same, when the bar shares the row with the separate search button: tighter item padding. */
private val NAV_FIXED_WIDTH_COMPACT = (3 * 38 + 24 + 16 + 3 * 2 + 12 + 6).dp
private val NAV_FIXED_WIDTH_COMPACT5 = (4 * 38 + 24 + 16 + 4 * 2 + 12 + 6).dp

/**
 * Stride's floating nav bar, in Petal: a glass pill whose selected item opens its label
 * while a clear glass slab slides under it and magnifies it. The labels, the bar's width
 * and the slab are all driven by one PetalSpring.calm(), so they move as one.
 *
 * [glassSource] is the View the bar floats over; its picture is what the glass bends.
 */
@Composable
internal fun PetalStrideNavBar(
    selectedTab: PetalNavTab,
    tabCount: Int,
    badgeScale: Float,
    glassSource: View?,
    onHomeClick: () -> Unit,
    onAiClick: () -> Unit = {},
    onNewTabClick: () -> Unit,
    onTabsClick: () -> Unit,
    onMenuClick: () -> Unit,
    onSwipeTabLeft: () -> Unit,
    onSwipeTabRight: () -> Unit,
    modifier: Modifier = Modifier,
    onHistoryClick: () -> Unit = {},
    onBookmarksClick: () -> Unit = {},
    compact: Boolean = false,
    includeSearch: Boolean = false,
    includeAi: Boolean = true,
    enabled: Boolean = true,
    showLabels: Boolean = true,
    onSearchClick: () -> Unit = {}
) {
    val NavOrder = when {
        includeAi && includeSearch -> NavOrder5
        includeAi -> NavOrder4
        includeSearch -> NavOrder5NoAi
        else -> NavOrder4NoAi
    }
    val scheme = MaterialTheme.colorScheme
    val pill = CircleShape
    val compactAll = compact || includeSearch
    val itemPadH = if (compactAll) 11.dp else 14.dp
    val rowPadH = if (compactAll) 8.dp else 10.dp
    val itemGap = if (compactAll) 2.dp else 4.dp
    val fixedWidth = if (includeSearch) NAV_FIXED_WIDTH_COMPACT5 else if (compact) NAV_FIXED_WIDTH_COMPACT else NAV_FIXED_WIDTH
    val context = androidx.compose.ui.platform.LocalContext.current
    com.petal.browser.ui.glass.LocalPetalGlassRevision.current
    val glassSupported = remember(context) {
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            !com.petal.browser.motion.MotionGate.isLowEndDevice(context)
    }
    val themeGlass = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_bottom_nav") && glassSupported
    val glassOn = themeGlass
    val glassBar = rememberGlassBar(if (glassOn) glassSource else null)

    // Swiping along the bar switches tabs; the bar follows the finger with resistance.
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val maxDragPx = with(LocalDensity.current) { 28.dp.toPx() }
    val dragOffsetPx by animateFloatAsState(
        targetValue = if (isDragging) (dragAccumulator * 0.35f).coerceIn(-maxDragPx, maxDragPx) else 0f,
        animationSpec = if (isDragging) snap() else PetalSpring.calm(),
        label = "swipe_rubber_band"
    )

    // Read through updated state so the long-lived pointerInput never calls a stale lambda.
    val currentSwipeLeft by androidx.compose.runtime.rememberUpdatedState(onSwipeTabLeft)
    val currentSwipeRight by androidx.compose.runtime.rememberUpdatedState(onSwipeTabRight)
    val currentEnabled by androidx.compose.runtime.rememberUpdatedState(enabled)

    val isLiquidGlassActive = glassSupported && (glassBar != null || themeGlass)
    // Kyant's interaction effects: the bar swells under the finger, a light follows it, and
    // the selection slab deepens its lens and stretches with its motion.
    val glassPress = com.petal.browser.ui.glass.rememberGlassPress()
    BoxWithConstraints(
        modifier = modifier
            .graphicsLayer { translationX = dragOffsetPx }
            .then(if (!isLiquidGlassActive) Modifier.shadow(1.dp, pill, clip = false) else Modifier)
            .then(if (isLiquidGlassActive) Modifier.glassPressScale(glassPress, 16f) else Modifier)
            .liquidGlass(pill, scheme.surfaceContainerHigh, glassBar, pressFx = false)
            .then(if (isLiquidGlassActive) Modifier.glassPressOverlay(glassPress, pill) else Modifier)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { if (currentEnabled) isDragging = true },
                    onDragEnd = {
                        if (currentEnabled && isDragging) {
                            // 90dp like AddressPill, so the threshold is the same distance on every density.
                            val threshold = 90.dp.toPx()
                            if (dragAccumulator > threshold) currentSwipeLeft()
                            else if (dragAccumulator < -threshold) currentSwipeRight()
                        }
                        isDragging = false
                        dragAccumulator = 0f
                    },
                    onDragCancel = {
                        isDragging = false
                        dragAccumulator = 0f
                    },
                    onHorizontalDrag = { _, dragAmount -> if (currentEnabled) dragAccumulator += dragAmount }
                )
            }
    ) {
        // Whatever width the other icons don't need is the most the selected label may
        // take. Past that it shrinks its type rather than wrapping or pushing the bar
        // off a narrow screen.
        val labelMax = with(LocalDensity.current) {
            (constraints.maxWidth - fixedWidth.roundToPx()).coerceIn(52.dp.roundToPx(), 120.dp.roundToPx())
        }
        // Where each item sits, so the glass slab can slide to the selected one.
        val itemBounds = remember { mutableStateMapOf<PetalNavTab, Rect>() }
        val weights = remember(NavOrder) { TabWeights(NavOrder, selectedTab) }
        LaunchedEffect(selectedTab) { weights.animateTo(selectedTab) }
        // The slab sits at the weights' blend of the item positions.
        val slab: () -> Rect? = {
            var total = 0f
            var l = 0f; var t = 0f; var r = 0f; var b = 0f
            NavOrder.forEach { tab ->
                val w = weights[tab].coerceAtLeast(0f)
                val rc = itemBounds[tab]
                if (rc != null && w > 0f) {
                    total += w
                    l += rc.left * w; t += rc.top * w; r += rc.right * w; b += rc.bottom * w
                }
            }
            if (total < 0.001f) null else Rect(l / total, t / total, r / total, b / total)
        }
        Row(
            modifier = Modifier
                .glassTile(slab, pill, scheme.primary, glassBar, if (isLiquidGlassActive) glassPress else null)
                .padding(horizontal = rowPadH, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(itemGap)
        ) {
            NavOrder.forEach { tab ->
                val selected = selectedTab == tab
                val weight = { weights[tab].coerceIn(0f, 1f) }
                val label = when (tab) {
                    PetalNavTab.HOME -> stringResource(R.string.bar_home)
                    PetalNavTab.AI -> stringResource(R.string.bar_ai)
                    PetalNavTab.NEW_TAB -> stringResource(R.string.bar_new)
                    PetalNavTab.TABS -> stringResource(R.string.bar_tabs)
                    PetalNavTab.MENU -> stringResource(R.string.bar_menu)
                    PetalNavTab.SEARCH -> stringResource(R.string.bar_search)
                }
                val contentLabel = if (tab == PetalNavTab.TABS) stringResource(R.string.bar_tabs_count, tabCount) else label
                val onLongClick: (() -> Unit)? = when (tab) {
                    PetalNavTab.HOME -> onHistoryClick
                    PetalNavTab.NEW_TAB, PetalNavTab.TABS -> onNewTabClick
                    PetalNavTab.MENU -> onBookmarksClick
                    else -> null
                }
                val onClick = when (tab) {
                    PetalNavTab.HOME -> onHomeClick
                    PetalNavTab.AI -> onAiClick
                    PetalNavTab.NEW_TAB -> onNewTabClick
                    PetalNavTab.TABS -> onTabsClick
                    PetalNavTab.MENU -> onMenuClick
                    PetalNavTab.SEARCH -> onSearchClick
                }
                val tint = if (selected) scheme.primary else scheme.onSurfaceVariant
                val labelStyle = PetalText.tabLabel.copy(color = scheme.primary)
                Box(
                    modifier = Modifier
                        .onGloballyPositioned { itemBounds[tab] = it.boundsInRoot() }
                        .bouncyClickable(
                            scaleDown = 0.88f,
                            enabled = enabled,
                            role = androidx.compose.ui.semantics.Role.Tab,
                            onLongClick = onLongClick,
                            onClick = onClick
                        )
                        .clip(pill)
                        // One announcement per item (label / tab count, selected, Tab role); icons are silent.
                        .semantics(mergeDescendants = true) {
                            contentDescription = contentLabel
                            this.selected = selected
                            if (tab == PetalNavTab.TABS) liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .widthIn(min = 48.dp) // 48dp touch target; the visual stays the same
                            .heightIn(min = 48.dp)
                            .padding(horizontal = itemPadH, vertical = 12.dp)
                            // Magnified under the glass slab, by as much of the slab as is
                            // over this item — so it grows as the slab arrives.
                            .graphicsLayer {
                                val s = 1f + (SLAB_MAGNIFY - 1f) * weight()
                                scaleX = s
                                scaleY = s
                            },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(24.dp).navIconMotion(selected),
                            contentAlignment = Alignment.Center
                        ) {
                            NavIcon(tab, selected, tint, tabCount, badgeScale)
                        }
                        if (showLabels) {
                            // Opens by its item's weight: measured at full width, laid out at
                            // that fraction of it, and clipped. Read in layout and draw only,
                            // so the animation never recomposes.
                            BasicText(
                                label,
                                style = labelStyle,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                autoSize = TextAutoSize.StepBased(
                                    minFontSize = 9.sp, maxFontSize = 13.sp, stepSize = 0.5.sp
                                ),
                                modifier = Modifier
                                    .graphicsLayer {
                                        alpha = weight()
                                        clip = true
                                    }
                                    .layout { measurable, _ ->
                                        val text = measurable.measure(Constraints(maxWidth = labelMax))
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

@Composable
private fun NavIcon(tab: PetalNavTab, selected: Boolean, tint: Color, tabCount: Int, badgeScale: Float) {
    when (tab) {
        PetalNavTab.HOME -> Icon(
            painter = painterResource(
                if (selected) com.petal.browser.R.drawable.home_filled else com.petal.browser.R.drawable.home
            ),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        PetalNavTab.AI -> Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        PetalNavTab.NEW_TAB -> Icon(Icons.Rounded.Add, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        PetalNavTab.TABS -> TabCountBadge(color = tint, count = tabCount, scale = badgeScale)
        PetalNavTab.MENU -> Icon(Icons.Rounded.MoreVert, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        PetalNavTab.SEARCH -> Icon(painterResource(com.petal.browser.R.drawable.ic_petal_search), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}
