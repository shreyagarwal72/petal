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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.ui.glass.SLAB_MAGNIFY
import com.petal.browser.ui.glass.StrideGlassPrefs
import com.petal.browser.ui.glass.TabWeights
import com.petal.browser.ui.glass.bouncyClickable
import com.petal.browser.ui.glass.glassTile
import com.petal.browser.ui.glass.liquidGlass
import com.petal.browser.ui.glass.navIconMotion
import com.petal.browser.ui.glass.rememberGlassBar
import kotlin.math.roundToInt

private val NavOrder = listOf(PetalNavTab.HOME, PetalNavTab.NEW_TAB, PetalNavTab.TABS, PetalNavTab.MENU)

/**
 * What the bar needs besides the selected label: four icon slots (24 dp icon + 14 dp
 * padding each side), the gaps between them, the row's own padding and the label gap.
 */
private val NAV_FIXED_WIDTH = (4 * 52 + 3 * 4 + 20 + 8).dp

/**
 * Stride's floating nav bar, in Petal: a glass pill whose selected item opens its label
 * while a clear glass slab slides under it and magnifies it. The labels, the bar's width
 * and the slab are all driven by one spring ([TabWeights]), so they move as one.
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
    onNewTabClick: () -> Unit,
    onTabsClick: () -> Unit,
    onMenuClick: () -> Unit,
    onSwipeTabLeft: () -> Unit,
    onSwipeTabRight: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val pill = CircleShape
    val context = androidx.compose.ui.platform.LocalContext.current
    com.petal.browser.ui.glass.LocalPetalGlassRevision.current
    val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
    val themeGlass = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_bottom_nav")
    val glassOn = themeGlass || (StrideGlassPrefs.isEnabled(context) && !prefs.contains("sp_liquid_glass_enabled"))
    val glassBar = rememberGlassBar(if (glassOn) glassSource else null)

    // Swiping along the bar switches tabs; the bar follows the finger with resistance.
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val maxDragPx = with(LocalDensity.current) { 28.dp.toPx() }
    val dragOffsetPx by animateFloatAsState(
        targetValue = if (isDragging) (dragAccumulator * 0.35f).coerceIn(-maxDragPx, maxDragPx) else 0f,
        animationSpec = if (isDragging) snap() else spring(dampingRatio = 0.50f, stiffness = 320f),
        label = "swipe_rubber_band"
    )

    val isLiquidGlassActive = glassBar != null || themeGlass
    BoxWithConstraints(
        modifier = modifier
            .graphicsLayer { translationX = dragOffsetPx }
            .then(if (!isLiquidGlassActive) Modifier.shadow(12.dp, pill, clip = false) else Modifier)
            .liquidGlass(pill, scheme.surfaceContainerHigh, glassBar)
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
        // Whatever width the four icons don't need is the most the selected label may
        // take. Past that it shrinks its type rather than wrapping or pushing the bar
        // off a narrow screen.
        val labelMax = with(LocalDensity.current) {
            (constraints.maxWidth - NAV_FIXED_WIDTH.roundToPx()).coerceAtLeast(24.dp.roundToPx())
        }
        // Where each item sits, so the glass slab can slide to the selected one.
        val itemBounds = remember { mutableStateMapOf<PetalNavTab, Rect>() }
        val weights = remember { TabWeights(NavOrder, selectedTab) }
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
                .glassTile(slab, pill, scheme.primary, glassBar)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            NavOrder.forEach { tab ->
                val selected = selectedTab == tab
                val weight = { weights[tab].coerceIn(0f, 1f) }
                val label = when (tab) {
                    PetalNavTab.HOME -> "Home"
                    PetalNavTab.NEW_TAB -> "New"
                    PetalNavTab.TABS -> "Tabs"
                    PetalNavTab.MENU -> "Menu"
                }
                val onClick = when (tab) {
                    PetalNavTab.HOME -> onHomeClick
                    PetalNavTab.NEW_TAB -> onNewTabClick
                    PetalNavTab.TABS -> onTabsClick
                    PetalNavTab.MENU -> onMenuClick
                }
                val tint = if (selected) scheme.primary else scheme.onSurfaceVariant
                val labelStyle = MaterialTheme.typography.labelLarge.copy(color = scheme.primary)
                Box(
                    modifier = Modifier
                        .onGloballyPositioned { itemBounds[tab] = it.boundsInRoot() }
                        .bouncyClickable(scaleDown = 0.88f, onClick = onClick)
                        .clip(pill)
                        .semantics { contentDescription = label }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            // Magnified under the glass slab, by as much of the slab as is
                            // over this item — so it grows as the slab arrives.
                            .graphicsLayer {
                                val s = 1f + (SLAB_MAGNIFY - 1f) * weight()
                                scaleX = s
                                scaleY = s
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(24.dp).navIconMotion(selected),
                            contentAlignment = Alignment.Center
                        ) {
                            NavIcon(tab, selected, tint, tabCount, badgeScale)
                        }
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
                                minFontSize = 9.sp, maxFontSize = labelStyle.fontSize
                            ),
                            modifier = Modifier
                                .graphicsLayer {
                                    alpha = weight()
                                    clip = true
                                }
                                .layout { measurable, _ ->
                                    val text = measurable.measure(Constraints(maxWidth = labelMax))
                                    val gap = 8.dp.roundToPx()
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
        PetalNavTab.NEW_TAB -> Icon(Icons.Rounded.Add, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        PetalNavTab.TABS -> TabCountBadge(color = tint, count = tabCount, scale = badgeScale)
        PetalNavTab.MENU -> Icon(Icons.Rounded.MoreVert, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}
