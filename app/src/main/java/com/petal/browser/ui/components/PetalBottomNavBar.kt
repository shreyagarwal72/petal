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

package com.petal.browser.ui.components

import com.petal.browser.ui.theme.ExpressiveButtonShape
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import com.petal.browser.ui.kit.IconButton
import com.petal.browser.ui.theme.PetalText
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import com.petal.browser.haptics.PetalHapticEngine
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.mutableIntStateOf
import com.petal.browser.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.ui.glass.liquidGlass
import com.petal.browser.ui.theme.ExperimentalMaterial3ExpressiveApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.Icon

enum class PetalNavTab {
    HOME, AI, NEW_TAB, TABS, MENU, SEARCH
}

@Stable
private class PetalNavCallbacks(
    val onHomeClick: () -> Unit,
    val onAiClick: () -> Unit,
    val onNewTabClick: () -> Unit,
    val onTabsClick: () -> Unit,
    val onMenuClick: () -> Unit,
    val onSwipeTabLeft: () -> Unit,
    val onSwipeTabRight: () -> Unit,
    val onSearchClick: () -> Unit,
    val onHistoryClick: () -> Unit,
    val onBookmarksClick: () -> Unit,
    val onDownloadsClick: () -> Unit,
)

internal data class NavMidSlotSpec(
    val icon: ImageVector,
    val labelRes: Int,
    val isAddSlot: Boolean,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null
)

internal fun resolveNavMidSlot(
    slot: NavMidSlot,
    onNewTabClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onDownloadsClick: () -> Unit
): NavMidSlotSpec = when (slot) {
    NavMidSlot.NEW_TAB -> NavMidSlotSpec(Icons.Rounded.Add, R.string.bar_new, true, onNewTabClick, onNewTabClick)
    NavMidSlot.BOOKMARKS -> NavMidSlotSpec(Icons.Rounded.Bookmarks, R.string.bar_bookmark, false, onBookmarksClick, onNewTabClick)
    NavMidSlot.HISTORY -> NavMidSlotSpec(Icons.Rounded.History, R.string.bar_history, false, onHistoryClick, onNewTabClick)
    NavMidSlot.DOWNLOADS -> NavMidSlotSpec(Icons.Rounded.Download, R.string.bar_downloads, false, onDownloadsClick, onNewTabClick)
}

// -------------------------------------------------------------------------------------------------
// Modern Floating Bottom Navigation Bar (Unified Normal & Incognito Architecture with Rich Motion)
// -------------------------------------------------------------------------------------------------

/**
 * Modern Floating Navigation Bar with Material 3 Expressive HorizontalFloatingToolbar.
 *
 * Architecture & Animations:
 * - Unified vibrant styling across normal & incognito modes
 * - Active pill spring expansion with tactile low-bouncy overshoot
 * - Interactive touch press micro-scale reaction (bouncy touch feedback)
 * - Icon rotation and spring pop when selected
 * - Animated Tab Count badge with spring bounce
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PetalBottomNavBar(
    selectedTab: PetalNavTab,
    tabCount: Int,
    isIncognito: Boolean = false,
    isFloatingStyle: Boolean = true,
    isBottomAddressBar: Boolean = false,
    glassSource: android.view.View? = null,
    onHomeClick: () -> Unit,
    onAiClick: () -> Unit = {},
    onNewTabClick: () -> Unit,
    onTabsClick: () -> Unit,
    onMenuClick: () -> Unit,
    onSwipeTabLeft: () -> Unit = {},
    onSwipeTabRight: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onBookmarksClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    showSearch: Boolean = true,
    isOmniboxOpen: Boolean = false,
    isPreview: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val barSettings = rememberBarSettings()
    val currentHome by rememberUpdatedState(onHomeClick)
    val currentAi by rememberUpdatedState(onAiClick)
    val currentNewTab by rememberUpdatedState(onNewTabClick)
    val currentTabs by rememberUpdatedState(onTabsClick)
    val currentMenu by rememberUpdatedState(onMenuClick)
    val currentSwipeLeft by rememberUpdatedState(onSwipeTabLeft)
    val currentSwipeRight by rememberUpdatedState(onSwipeTabRight)
    val currentSearch by rememberUpdatedState(onSearchClick)
    val currentHistory by rememberUpdatedState(onHistoryClick)
    val currentBookmarks by rememberUpdatedState(onBookmarksClick)
    val currentDownloads by rememberUpdatedState(onDownloadsClick)
    val callbacks = remember {
        PetalNavCallbacks(
            onHomeClick = { currentHome() },
            onAiClick = { currentAi() },
            onNewTabClick = { currentNewTab() },
            onTabsClick = { currentTabs() },
            onMenuClick = { currentMenu() },
            onSwipeTabLeft = { currentSwipeLeft() },
            onSwipeTabRight = { currentSwipeRight() },
            onSearchClick = { currentSearch() },
            onHistoryClick = { currentHistory() },
            onBookmarksClick = { currentBookmarks() },
            onDownloadsClick = { currentDownloads() },
        )
    }

    // State-driven search launch animation (replaces timed 700ms reset that could flash a hidden nav).
    val searchLaunch = remember { Animatable(0f) }
    LaunchedEffect(isOmniboxOpen) {
        if (isOmniboxOpen) {
            searchLaunch.animateTo(1f, PetalSpring.calm())
        } else if (searchLaunch.value > 0f) {
            searchLaunch.animateTo(0f, PetalSpring.calm())
        }
    }

    // Bounds of the tapped search control (separate glass button, else the whole nav bar), so the
    // omnibox page grows out of it.
    val searchButtonBounds = remember { arrayOfNulls<androidx.compose.ui.geometry.Rect>(1) }
    val newTabButtonBounds = remember { arrayOfNulls<androidx.compose.ui.geometry.Rect>(1) }
    val tabsButtonBounds = remember { arrayOfNulls<androidx.compose.ui.geometry.Rect>(1) }
    val navBarBounds = remember { arrayOfNulls<androidx.compose.ui.geometry.Rect>(1) }
    val launchSearch = remember(callbacks) {
        {
            PetalOmniboxOrigin.mark(searchButtonBounds[0] ?: navBarBounds[0], -1f)
            callbacks.onSearchClick()
        }
    }

    // The badge pops when the count CHANGES, never on first draw.
    val badgeScale = remember { Animatable(1f) }
    var lastTabCount by remember { mutableIntStateOf(tabCount) }
    LaunchedEffect(tabCount) {
        if (tabCount != lastTabCount) {
            lastTabCount = tabCount
            badgeScale.snapTo(1.35f)
            badgeScale.animateTo(1f, PetalSpring.quick())
        }
    }

    val midSlot = barSettings.navMidSlot
    val midSlotSpec = resolveNavMidSlot(
        slot = midSlot,
        onNewTabClick = callbacks.onNewTabClick,
        onBookmarksClick = callbacks.onBookmarksClick,
        onHistoryClick = callbacks.onHistoryClick,
        onDownloadsClick = callbacks.onDownloadsClick
    )
    val newTabLabel = stringResource(midSlotSpec.labelRes)
    val navSearchStyle = barSettings.navSearchStyle
    val navSwipeEnabled = barSettings.navSwipeTabs && !isPreview

    val isHomeTab = selectedTab == PetalNavTab.HOME
    // AI lives on the home page only; the Search button is for every other page (unless hidden).
    val onHomePage = selectedTab == PetalNavTab.HOME || selectedTab == PetalNavTab.AI
    val searchAllowed = showSearch && !onHomePage && navSearchStyle != NavSearchButtonStyle.HIDDEN
    val aiShift = if (onHomePage) 1 else 0
    val ambientGlassSource = com.petal.browser.ui.glass.LocalPetalGlassSource.current
    val resolvedGlassSource = if (isHomeTab || isPreview) null else (glassSource ?: ambientGlassSource ?: (context as? android.app.Activity)?.findViewById<android.view.View>(com.petal.browser.R.id.main_content))

    val effectiveFloating = isFloatingStyle && !isBottomAddressBar
    com.petal.browser.ui.glass.LocalPetalGlassRevision.current
    val glassSupported = remember(context) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !com.petal.browser.motion.MotionGate.isLowEndDevice(context)
    }
    val themeGlassNav = !isPreview && com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_bottom_nav") && glassSupported
    val glassNavOn = themeGlassNav
    // Liquid glass + floating + SEPARATE: search is its own glass button on the right.
    val separateSearch = searchAllowed && effectiveFloating && glassNavOn && navSearchStyle == NavSearchButtonStyle.SEPARATE
    // Material 3 Expressive floating (glass off): PixelMusic-style pill + optional separate ghost-shaped search.
    val pixelStyle = effectiveFloating && !glassNavOn
    val pixelSearch = searchAllowed && pixelStyle && navSearchStyle == NavSearchButtonStyle.SEPARATE
    val centerSearch = searchAllowed && !separateSearch && !pixelSearch
    // The docked bar (glass or solid) runs through the gesture-bar area like Brave's, so no
    // gap shows between the bar and the screen edge and no page shows unblurred under it.
    val flatNavInset = if (!effectiveFloating && !isPreview) {
        with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
    } else 0.dp

    // Derived booleans flip only when visibility crosses threshold — scrolling never recomposes the nav.
    val collapseFractionProvider: () -> Float = remember(isPreview) {
        if (isPreview) ({ 0f }) else ({ BarCollapseState.collapsedFraction })
    }
    val collapseAlphaProvider: () -> Float = remember(barSettings.collapseStyle, isBottomAddressBar, isPreview) {
        if (isPreview) ({ 1f }) else ({
            BarCollapseMath.compute(
                barSettings.collapseStyle,
                collapseFractionProvider(),
                isBottomAddressBar
            ).navAlpha
        })
    }
    val navInteractive by remember(barSettings.collapseStyle, isBottomAddressBar, isPreview) {
        derivedStateOf {
            isPreview || BarCollapseMath.compute(
                barSettings.collapseStyle,
                collapseFractionProvider(),
                isBottomAddressBar
            ).navVisible
        }
    }
    val navMounted by remember(barSettings.collapseStyle, isBottomAddressBar, isPreview) {
        derivedStateOf {
            if (isPreview) true else {
                val v = BarCollapseMath.compute(
                    barSettings.collapseStyle,
                    collapseFractionProvider(),
                    isBottomAddressBar
                )
                v.navVisible && v.navAlpha >= 0.05f
            }
        }
    }
    if (!navMounted) return
    val showLabels = barSettings.showNavLabels
    val activeGlassSource = if (BarCollapseState.isPageScrolling || isPreview) null else resolvedGlassSource

    if (effectiveFloating) {
        // Stride's floating glass nav bar (see PetalStrideNavBar): the tab pill sits in the
        // middle, and search is its own glass button on the right.
        Row(
            modifier = modifier
                .graphicsLayer {
                    val v = BarCollapseMath.compute(
                        barSettings.collapseStyle,
                        collapseFractionProvider(),
                        isBottomAddressBar
                    )
                    translationY = size.height * v.slideFraction + v.navTranslationDp.dp.toPx()
                    scaleX = v.navScale
                    scaleY = v.navScale
                    alpha = v.navAlpha
                    if (v.navAlpha < 0.999f) {
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                    }
                }
                .fillMaxWidth()
                .then(if (!isPreview) Modifier.navigationBarsPadding() else Modifier)
                .padding(bottom = if (isPreview) 6.dp else 10.dp, start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .onGloballyPositioned { navBarBounds[0] = it.boundsInWindow() }
                    .graphicsLayer {
                        val p = searchLaunch.value
                        translationY = p * 22.dp.toPx()
                        val sc = 1f - 0.05f * p
                        scaleX = sc
                        scaleY = sc
                        alpha = 1f - p
                    },
                contentAlignment = Alignment.Center
            ) {
                if (pixelStyle) {
                    PetalPixelNavPill(
                        selectedTab = selectedTab,
                        tabCount = tabCount,
                        badgeScale = badgeScale.value,
                        onHomeClick = callbacks.onHomeClick,
                        onAiClick = callbacks.onAiClick,
                        onNewTabClick = callbacks.onNewTabClick,
                        onSearchClick = launchSearch,
                        onTabsClick = callbacks.onTabsClick,
                        onMenuClick = callbacks.onMenuClick,
                        onSwipeTabLeft = callbacks.onSwipeTabLeft,
                        onSwipeTabRight = callbacks.onSwipeTabRight,
                        onHistoryClick = callbacks.onHistoryClick,
                        onBookmarksClick = callbacks.onBookmarksClick,
                        onDownloadsClick = callbacks.onDownloadsClick,
                        includeSearch = centerSearch,
                        includeAi = onHomePage,
                        swipeTabsEnabled = navSwipeEnabled,
                        midSlot = midSlot,
                        enabled = navInteractive,
                        showLabels = showLabels,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    PetalStrideNavBar(
                        selectedTab = selectedTab,
                        tabCount = tabCount,
                        badgeScale = badgeScale.value,
                        glassSource = activeGlassSource,
                        onHomeClick = callbacks.onHomeClick,
                        onAiClick = callbacks.onAiClick,
                        onNewTabClick = callbacks.onNewTabClick,
                        onTabsClick = callbacks.onTabsClick,
                        onMenuClick = callbacks.onMenuClick,
                        onSwipeTabLeft = callbacks.onSwipeTabLeft,
                        onSwipeTabRight = callbacks.onSwipeTabRight,
                        onHistoryClick = callbacks.onHistoryClick,
                        onBookmarksClick = callbacks.onBookmarksClick,
                        onDownloadsClick = callbacks.onDownloadsClick,
                        compact = separateSearch,
                        includeSearch = centerSearch,
                        includeAi = onHomePage,
                        swipeTabsEnabled = navSwipeEnabled,
                        midSlot = midSlot,
                        enabled = navInteractive,
                        showLabels = showLabels,
                        collapseAlphaProvider = collapseAlphaProvider,
                        onSearchClick = launchSearch
                    )
                }
            }
            if (pixelSearch) {
                PetalGhostSearchButton(
                    onClick = launchSearch,
                    enabled = navInteractive,
                    modifier = Modifier.onGloballyPositioned { searchButtonBounds[0] = it.boundsInWindow() }.graphicsLayer {
                        val p = searchLaunch.value
                        val sc = 1f + 0.22f * p
                        scaleX = sc
                        scaleY = sc
                        alpha = 1f - 0.7f * p
                    }
                )
            }
            if (separateSearch) {
                PetalSearchGlassButton(
                    glassSource = activeGlassSource,
                    onClick = launchSearch,
                    enabled = navInteractive,
                    collapseAlphaProvider = collapseAlphaProvider,
                    modifier = Modifier.onGloballyPositioned { searchButtonBounds[0] = it.boundsInWindow() }.graphicsLayer {
                        val p = searchLaunch.value
                        val sc = 1f + 0.22f * p
                        scaleX = sc
                        scaleY = sc
                        alpha = 1f - 0.7f * p
                    }
                )
            }
        }
    } else {
        // Material 3 Expressive Non-Floating Bottom Navigation Bar (now supports horizontal tab swipe too)
        var dockedDrag by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
        val currentNavSwipeEnabled by rememberUpdatedState(navSwipeEnabled && navInteractive)
        Box(
            modifier = modifier
                .onGloballyPositioned { navBarBounds[0] = it.boundsInWindow() }
                .pointerInput(navSwipeEnabled) {
                    if (!navSwipeEnabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { dockedDrag = 0f },
                        onDragEnd = {
                            if (currentNavSwipeEnabled) {
                                val threshold = 90.dp.toPx()
                                if (dockedDrag > threshold) callbacks.onSwipeTabLeft()
                                else if (dockedDrag < -threshold) callbacks.onSwipeTabRight()
                            }
                            dockedDrag = 0f
                        },
                        onDragCancel = { dockedDrag = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            if (currentNavSwipeEnabled) dockedDrag += dragAmount
                        }
                    )
                }
                .graphicsLayer {
                    val v = BarCollapseMath.compute(
                        barSettings.collapseStyle,
                        collapseFractionProvider(),
                        isBottomAddressBar
                    )
                    val combinedAlpha = (1f - searchLaunch.value) * v.navAlpha
                    translationY = searchLaunch.value * 22.dp.toPx() + size.height * v.slideFraction + v.navTranslationDp.dp.toPx()
                    scaleX = v.navScale
                    scaleY = v.navScale
                    alpha = combinedAlpha
                    if (combinedAlpha < 0.999f) {
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                    }
                }
                .fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Non-floating (docked) bar is always square-edged across the screen width so
            // no window background or black overlay is exposed at the corners.
            val navBarShape = RoundedCornerShape(0.dp)
            com.petal.browser.ui.glass.LocalPetalGlassRevision.current
            val isLiquidGlass = !isPreview && com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_bottom_nav") && glassSupported
            val glassBar = com.petal.browser.ui.glass.rememberGlassBar(if (isLiquidGlass) activeGlassSource else null)
            val baseContainer = if (isIncognito) com.petal.browser.ui.theme.IncognitoSurfaceContainer else MaterialTheme.colorScheme.surfaceContainerHigh

            Surface(
                shape = navBarShape,
                color = when {
                    isLiquidGlass -> Color.Transparent
                    isIncognito -> com.petal.browser.ui.theme.IncognitoSurfaceContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                },
                // No tonal lift or drop shadow: the shadow was the dark blur smeared outside the
                // rounded corners. The bar is a clean flat fill (or glass), rounded or rectangular.
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp + flatNavInset)
                    .then(
                        if (isLiquidGlass) {
                            Modifier.liquidGlass(
                                shape = navBarShape,
                                tint = baseContainer,
                                bar = glassBar,
                                progressiveEdge = 1,
                                scrollSolidFallback = true,
                                alphaProvider = { (1f - searchLaunch.value) * collapseAlphaProvider() }
                            )
                        } else Modifier
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                        .padding(bottom = flatNavInset),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExpressiveNavTabItem(
                        selected = selectedTab == PetalNavTab.HOME,
                        label = stringResource(R.string.bar_home),
                        index = 0,
                        isLiquidGlass = isLiquidGlass,
                        enabled = navInteractive,
                        showLabel = showLabels,
                        modifier = Modifier.weight(1f),
                        icon = { isSelected, tint ->
                            val iconScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.18f else 1.0f,
                                animationSpec = PetalSpring.calm(),
                                label = "home_expressive_scale"
                            )
                            Crossfade(
                                targetState = isSelected,
                                animationSpec = PetalSpring.calm(),
                                label = "home_icon_crossfade",
                                modifier = Modifier.graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                            ) { filled ->
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(
                                        if (filled) com.petal.browser.R.drawable.home_filled else com.petal.browser.R.drawable.home
                                    ),
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        },
                        onClick = callbacks.onHomeClick,
                        onLongClick = callbacks.onHistoryClick
                    )

                    if (onHomePage) {
                    ExpressiveNavTabItem(
                        selected = selectedTab == PetalNavTab.AI,
                        label = stringResource(R.string.bar_ai),
                        index = 1,
                        isLiquidGlass = isLiquidGlass,
                        enabled = navInteractive,
                        showLabel = showLabels,
                        modifier = Modifier.weight(1f),
                        icon = { isSelected, tint ->
                            val iconScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.18f else 1.0f,
                                animationSpec = PetalSpring.calm(),
                                label = "ai_expressive_scale"
                            )
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    }
                            )
                        },
                        onClick = callbacks.onAiClick
                    )
                    }

                    ExpressiveNavTabItem(
                        selected = selectedTab == PetalNavTab.NEW_TAB,
                        label = newTabLabel,
                        index = 1 + aiShift,
                        isLiquidGlass = isLiquidGlass,
                        enabled = navInteractive,
                        showLabel = showLabels,
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { newTabButtonBounds[0] = it.boundsInWindow() },
                        icon = { isSelected, tint ->
                            val rotationAngle by animateFloatAsState(
                                targetValue = if (isSelected && midSlotSpec.isAddSlot) 90f else 0f,
                                animationSpec = PetalSpring.calm(),
                                label = "add_expressive_rotation"
                            )
                            val iconScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.18f else 1.0f,
                                animationSpec = PetalSpring.calm(),
                                label = "add_expressive_scale"
                            )
                            Icon(
                                imageVector = midSlotSpec.icon,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        rotationZ = rotationAngle
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    }
                            )
                        },
                        onClick = {
                            if (midSlotSpec.isAddSlot) {
                                val bounds = newTabButtonBounds[0] ?: navBarBounds[0]
                                val rad = if (bounds != null) bounds.height / 2f else 24f
                                PetalNewTabOrigin.mark(bounds, rad)
                            }
                            midSlotSpec.onClick()
                        },
                        onLongClick = {
                            val bounds = newTabButtonBounds[0] ?: navBarBounds[0]
                            val rad = if (bounds != null) bounds.height / 2f else 24f
                            PetalNewTabOrigin.mark(bounds, rad)
                            (midSlotSpec.onLongClick ?: callbacks.onNewTabClick).invoke()
                        }
                    )

                    if (centerSearch) {
                        ExpressiveNavTabItem(
                            selected = selectedTab == PetalNavTab.SEARCH,
                            label = stringResource(R.string.bar_search),
                            index = 2 + aiShift,
                            isLiquidGlass = isLiquidGlass,
                            enabled = navInteractive,
                            showLabel = showLabels,
                            modifier = Modifier.weight(1f),
                            icon = { _, _ ->
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(com.petal.browser.R.drawable.ic_petal_search),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            onClick = launchSearch
                        )
                    }

                    ExpressiveNavTabItem(
                        selected = selectedTab == PetalNavTab.TABS,
                        label = stringResource(R.string.bar_tabs),
                        isTabs = true,
                        contentLabel = stringResource(R.string.bar_tabs_count, tabCount),
                        badgeText = if (tabCount > 99) "99+" else tabCount.toString(),
                        index = (if (centerSearch) 4 else 3) - 1 + aiShift,
                        isLiquidGlass = isLiquidGlass,
                        enabled = navInteractive,
                        showLabel = showLabels,
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { tabsButtonBounds[0] = it.boundsInWindow() },
                        icon = { isSelected, tint ->
                            val iconScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.05f else 1.0f,
                                animationSpec = PetalSpring.calm(),
                                label = "tabs_expressive_scale"
                            )
                            TabCountBadge(
                                color = tint,
                                count = tabCount,
                                scale = badgeScale.value * iconScale
                            )
                        },
                        onClick = {
                            val bounds = tabsButtonBounds[0] ?: navBarBounds[0]
                            val rad = if (bounds != null) bounds.height / 2f else 24f
                            PetalNewTabOrigin.mark(bounds, rad)
                            callbacks.onTabsClick()
                        },
                        onLongClick = {
                            val bounds = tabsButtonBounds[0] ?: navBarBounds[0]
                            val rad = if (bounds != null) bounds.height / 2f else 24f
                            PetalNewTabOrigin.mark(bounds, rad)
                            callbacks.onNewTabClick()
                        }
                    )

                    ExpressiveNavTabItem(
                        selected = selectedTab == PetalNavTab.MENU,
                        label = stringResource(R.string.bar_menu),
                        index = (if (centerSearch) 5 else 4) - 1 + aiShift,
                        isLiquidGlass = isLiquidGlass,
                        enabled = navInteractive,
                        showLabel = showLabels,
                        modifier = Modifier.weight(1f),
                        icon = { isSelected, tint ->
                            val rotationAngle by animateFloatAsState(
                                targetValue = if (isSelected) 180f else 0f,
                                animationSpec = PetalSpring.calm(),
                                label = "menu_expressive_rotation"
                            )
                            val iconScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.18f else 1.0f,
                                animationSpec = PetalSpring.calm(),
                                label = "menu_expressive_scale"
                            )
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        rotationZ = rotationAngle
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    }
                            )
                        },
                        onClick = callbacks.onMenuClick,
                        onLongClick = callbacks.onBookmarksClick
                    )

                }
            }
        }
    }
}

/**
 * Chrome Android style live tab counter badge (bordered box with the current tab
 * count), reused as the icon slot for the Tabs item in both bar styles.
 */
@Composable
internal fun TabCountBadge(color: Color, count: Int, scale: Float = 1f) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(
                width = 2.dp,
                color = color,
                shape = RoundedCornerShape(7.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                val up = targetState > initialState
                (slideInVertically(PetalSpring.calm()) { if (up) it else -it } + fadeIn(PetalSpring.calm()))
                    .togetherWith(
                        slideOutVertically(PetalSpring.calm()) { if (up) -it else it } + fadeOut(PetalSpring.calm())
                    )
                    .using(SizeTransform(clip = true))
            },
            label = "tab_count_roll"
        ) { value ->
            Text(
                text = if (value > 99) "99+" else value.toString(),
                style = PetalText.badge,
                color = color,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Material 3 Expressive Tab Item for the non-floating (flat) bottom navigation bar.
 * Features:
 * - Active pill indicator with smooth spring expansion & morphing
 * - Tactile press scaling (bouncy feedback)
 * - Animated icon colors and typography scaling
 * - Dynamic badge overlay
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ExpressiveNavTabItem(
    selected: Boolean,
    label: String,
    index: Int,
    icon: @Composable (isSelected: Boolean, tint: Color) -> Unit,
    onClick: () -> Unit,
    badgeText: String? = null,
    isTabs: Boolean = false,
    contentLabel: String = label,
    isLiquidGlass: Boolean = false,
    enabled: Boolean = true,
    showLabel: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Tactile squash-and-stretch micro-interaction when pressed
    val pressScaleX by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = PetalSpring.quick(),
        label = "expressive_press_scale_x_$index"
    )
    val pressScaleY by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = PetalSpring.quick(),
        label = "expressive_press_scale_y_$index"
    )

    // Active pill background color & indicator lift
    val activeLiftY by animateDpAsState(
        targetValue = if (selected) (-1).dp else 0.dp,
        animationSpec = PetalSpring.calm(),
        label = "expressive_lift_$index"
    )

    // Fluid indicator pill geometry: expands from 40dp to 58dp on selection
    val activeIndicatorWidth by animateDpAsState(
        targetValue = if (selected) 58.dp else 40.dp,
        animationSpec = PetalSpring.calm(),
        label = "expressive_indicator_width_$index"
    )

    val activeIndicatorHeight = 32.dp

    val activeIndicatorColor = if (isLiquidGlass) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val indicatorBgColor by animateColorAsState(
        targetValue = if (selected) activeIndicatorColor else Color.Transparent,
        animationSpec = PetalSpring.calm(),
        label = "expressive_indicator_bg_$index"
    )

    val contentTint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = PetalSpring.calm(),
        label = "expressive_content_tint_$index"
    )

    val labelColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = PetalSpring.calm(),
        label = "expressive_label_color_$index"
    )

    val labelWeight = if (selected) FontWeight.Bold else FontWeight.Medium

    val indicatorShape = RoundedCornerShape(14.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = pressScaleX
                scaleY = pressScaleY
                translationY = activeLiftY.toPx()
            }
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(bounded = true, radius = 32.dp),
                role = androidx.compose.ui.semantics.Role.Tab,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 2.dp, vertical = 1.dp)
            // One announcement per item: the label (with the open-tab count for Tabs), the selected
            // state and the Tab role. Icons carry no description of their own, so nothing is read twice.
            .semantics(mergeDescendants = true) {
                contentDescription = contentLabel
                this.selected = selected
                if (isTabs) liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
            }
    ) {
        Surface(
            shape = indicatorShape,
            color = indicatorBgColor,
            border = null,
            modifier = Modifier
                .width(activeIndicatorWidth)
                .height(activeIndicatorHeight)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                icon(selected, contentTint)
            }
        }

        if (showLabel) {
            Spacer(Modifier.height(2.dp))

            Text(
                text = label,
                style = PetalText.tabLabel.copy(
                    fontWeight = labelWeight
                ),
                color = labelColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
