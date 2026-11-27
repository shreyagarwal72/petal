package com.petal.browser.ui.components

import com.petal.browser.ui.theme.ExpressiveButtonShape
import android.graphics.Bitmap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import com.petal.browser.R
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.glass.LocalPetalGlassRevision
import com.petal.browser.ui.glass.liquidGlass
import com.petal.browser.ui.glass.rememberGlassBar

/**
 * Material 3 Expressive Unified "Merge" Bar.
 *
 * Combines the address bar and navigation actions into a single compact bar, placed at
 * either the top or bottom of the screen.
 *
 * Supports:
 * - [SINGLE] (one unified row: [leading actions] [AddressPill] [trailing actions])
 * - [STACKED] (two rows: [AddressPill] on top, actions row below)
 * - User-customizable toolbar buttons from [BarPrefs.getSanitizedMergeActions]
 * - Chrome Android-style scroll morphing into [PetalMinimizedPill]
 * - Live Liquid Glass refraction & Material 3 Expressive elevation
 */
@Stable
private class MergedBarCallbacks(
    val onBackClick: () -> Unit,
    val onForwardClick: () -> Unit,
    val onReloadClick: () -> Unit,
    val onStopClick: () -> Unit,
    val onShareClick: () -> Unit,
    val onHomeClick: () -> Unit,
    val onNewTabClick: () -> Unit,
    val onTabsClick: () -> Unit,
    val onMenuClick: () -> Unit,
    val onSearchClick: () -> Unit,
    val onDownloadsClick: () -> Unit,
    val onFindInPageClick: () -> Unit,
    val onBookmarkClick: () -> Unit,
    val onHistoryClick: () -> Unit,
    val onBookmarksManagerClick: () -> Unit,
    val onAiResearchClick: () -> Unit,
    val onReaderClick: () -> Unit,
    val onHardRefresh: () -> Unit,
    val onCustomizeClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalMergedBar(
    url: String,
    title: String,
    favicon: Bitmap? = null,
    progress: Float = 0f,
    isIncognito: Boolean = false,
    isLoading: Boolean = false,
    canGoBack: Boolean = true,
    canGoForward: Boolean = false,
    tabCount: Int = 1,
    onBackClick: () -> Unit,
    onForwardClick: () -> Unit = {},
    onReloadClick: () -> Unit = {},
    onStopClick: () -> Unit = {},
    onShareClick: () -> Unit,
    onAddressClick: () -> Unit,
    onHomeClick: () -> Unit = {},
    onNewTabClick: () -> Unit = {},
    onTabsClick: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    onFindInPageClick: () -> Unit = {},
    onBookmarkClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onBookmarksManagerClick: () -> Unit = onBookmarkClick,
    onAiResearchClick: () -> Unit = {},
    onMediaClick: () -> Unit = {},
    onSwipeNextTab: () -> Unit = {},
    onSwipePrevTab: () -> Unit = {},
    onSwipeUp: () -> Unit = {},
    onPasteAndGo: (String) -> Unit = {},
    onHardRefresh: () -> Unit = {},
    isReaderEligible: Boolean = false,
    onReaderClick: () -> Unit = {},
    onCustomizeClick: () -> Unit = {},
    isBottom: Boolean = false,
    isPreview: Boolean = false,
    suppressStatusBarTopInset: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val settings = rememberBarSettings()
    val mergeLayout = settings.mergeLayout
    val (leadingActions, trailingActions) = settings.mergeActions

    val addressBarGlassEnabled = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_address_bar")
    val bottomNavGlassEnabled = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_bottom_nav")
    val chrome = rememberBarChrome(
        isBottomParam = isBottom,
        isPreview = isPreview,
        isIncognito = isIncognito,
        suppressStatusBarTopInset = suppressStatusBarTopInset,
        floatingForShape = { bottom -> bottom && settings.floatingNav },
        floatingForWallpaper = { bottom -> bottom && settings.floatingNav },
        glassPref = { bottom -> addressBarGlassEnabled || (bottom && bottomNavGlassEnabled) }
    )
    val currentBack by rememberUpdatedState(onBackClick)
    val currentForward by rememberUpdatedState(onForwardClick)
    val currentReload by rememberUpdatedState(onReloadClick)
    val currentStop by rememberUpdatedState(onStopClick)
    val currentShare by rememberUpdatedState(onShareClick)
    val currentHome by rememberUpdatedState(onHomeClick)
    val currentNewTab by rememberUpdatedState(onNewTabClick)
    val currentTabs by rememberUpdatedState(onTabsClick)
    val currentMenu by rememberUpdatedState(onMenuClick)
    val currentSearch by rememberUpdatedState(onSearchClick)
    val currentDownloads by rememberUpdatedState(onDownloadsClick)
    val currentFindInPage by rememberUpdatedState(onFindInPageClick)
    val currentBookmark by rememberUpdatedState(onBookmarkClick)
    val currentHistory by rememberUpdatedState(onHistoryClick)
    val currentBookmarksManager by rememberUpdatedState(onBookmarksManagerClick)
    val currentAiResearch by rememberUpdatedState(onAiResearchClick)
    val currentReader by rememberUpdatedState(onReaderClick)
    val currentHardRefresh by rememberUpdatedState(onHardRefresh)
    val currentCustomize by rememberUpdatedState(onCustomizeClick)
    val callbacks = remember {
        MergedBarCallbacks(
            onBackClick = { currentBack() },
            onForwardClick = { currentForward() },
            onReloadClick = { currentReload() },
            onStopClick = { currentStop() },
            onShareClick = { currentShare() },
            onHomeClick = { currentHome() },
            onNewTabClick = { currentNewTab() },
            onTabsClick = { currentTabs() },
            onMenuClick = { currentMenu() },
            onSearchClick = { currentSearch() },
            onDownloadsClick = { currentDownloads() },
            onFindInPageClick = { currentFindInPage() },
            onBookmarkClick = { currentBookmark() },
            onHistoryClick = { currentHistory() },
            onBookmarksManagerClick = { currentBookmarksManager() },
            onAiResearchClick = { currentAiResearch() },
            onReaderClick = { currentReader() },
            onHardRefresh = { currentHardRefresh() },
            onCustomizeClick = { currentCustomize() }
        )
    }

    val resolvedIsBottom = chrome.isBottom
    val isFloatingBar = chrome.isFloating
    val addressBarHeight = chrome.height
    val containerShape = chrome.containerShape
    val isLiquidGlass = chrome.isLiquidGlass
    val glassBar = chrome.glassBar
    val baseGlassColor = chrome.baseGlassColor
    val statusBarTopInset = chrome.statusBarTopInset
    val navBarBottomInset = chrome.navBarBottomInset
    var fullBarHeightPx by remember { mutableIntStateOf(0) }
    val barVisible by remember(chrome) {
        derivedStateOf { chrome.currentVisuals().barVisible }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                // HIDE (Full Slide): slides off using the settled full bar height.
                val v = chrome.currentVisuals()
                translationY = (if (resolvedIsBottom) 1f else -1f) * fullBarHeightPx * v.slideFraction
            },
        contentAlignment = Alignment.Center
    ) {
        if (barVisible) {
            Surface(
                shape = containerShape,
                color = chrome.containerColor,
                tonalElevation = chrome.staticElevation,
                shadowElevation = chrome.staticElevation,
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { if (it.height > 0) fullBarHeightPx = it.height }
                    .then(
                        if (resolvedIsBottom) {
                            if (isFloatingBar) {
                                Modifier.padding(
                                    start = 12.dp,
                                    end = 12.dp,
                                    top = 2.dp,
                                    bottom = if (!isPreview) navBarBottomInset + 8.dp else 4.dp
                                )
                            } else {
                                Modifier.padding(horizontal = 0.dp, vertical = 0.dp)
                            }
                        } else {
                            Modifier.padding(
                                start = 12.dp,
                                end = 12.dp,
                                // The status bar gap sits OUTSIDE the glass, so the glass is just the bar.
                                top = statusBarTopInset + addressBarHeight.verticalMarginDp.dp,
                                bottom = addressBarHeight.verticalMarginDp.dp
                            )
                        }
                    )
                    .then(
                        if (isLiquidGlass) {
                            Modifier.liquidGlass(
                                shape = containerShape,
                                tint = baseGlassColor,
                                bar = glassBar,
                                scrollSolidFallback = true,
                                alphaProvider = { chrome.currentVisuals().fullBarAlpha }
                            )
                        } else Modifier
                    )
                    .graphicsLayer {
                        val v = chrome.currentVisuals()
                        alpha = v.fullBarAlpha
                        scaleX = v.fullBarScaleX
                        scaleY = v.fullBarScaleY
                        translationY = v.fullBarTranslationDp.dp.toPx()
                    }
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            com.petal.browser.haptics.PetalHapticEngine.getInstance(context).thresholdCrossed(context)
                            callbacks.onCustomizeClick()
                        }
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = 2.dp,
                            bottom = if (resolvedIsBottom && !isFloatingBar && !isPreview) navBarBottomInset + 4.dp else 2.dp
                        )
                ) {
                if (mergeLayout == MergeLayout.STACKED) {
                    // STACKED 2-Row Layout: Pill on top, buttons in row below
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AddressPill(
                            url = url,
                            title = title,
                            favicon = favicon,
                            progress = progress,
                            isIncognito = isIncognito,
                            isLoading = isLoading,
                            onAddressClick = onAddressClick,
                            onAiResearchClick = onAiResearchClick,
                            onMediaClick = onMediaClick,
                            onSwipeNextTab = onSwipeNextTab,
                            onSwipePrevTab = onSwipePrevTab,
                            onSwipeUp = onSwipeUp,
                            onPasteAndGo = onPasteAndGo,
                            onHardRefresh = onHardRefresh,
                            isReaderEligible = isReaderEligible,
                            onReaderClick = onReaderClick,
                            isBottom = resolvedIsBottom,
                            inMergedBar = true,
                            isLiquidGlass = isLiquidGlass,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Material 3 Expressive Progress Bar (between AddressPill and Action Buttons)
                        if (settings.progressStyle == BarProgressStyle.LINE && isLoading && progress > 0f) {
                            Material3ExpressiveProgressIndicator(
                                progress = progress,
                                visible = isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        val allActions = remember(leadingActions, trailingActions) {
                            (leadingActions + trailingActions).distinct().take(BarPrefs.MAX_MERGE_ACTIONS_TOTAL)
                        }
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val stackedBtnWidth = if (48.dp * allActions.size > maxWidth) 40.dp else 48.dp
                            val maxFit = (maxWidth / stackedBtnWidth).toInt().coerceAtLeast(1)
                            val (shownStacked, droppedStacked) = remember(allActions, maxFit) {
                                budgetMergedStackedActions(allActions, maxFit)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .then(
                                        if (isLiquidGlass) {
                                            Modifier
                                                .clip(RoundedCornerShape(22.dp))
                                                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.12f))
                                        } else Modifier
                                    )
                                    .graphicsLayer {
                                        val v = chrome.currentVisuals()
                                        val collapseProg = if (chrome.isPillCollapse) {
                                            chrome.collapsedFractionProvider().coerceIn(0f, 1f)
                                        } else 0f
                                        alpha = v.sideButtonAlpha
                                        scaleX = v.sideButtonScale
                                        scaleY = v.sideButtonScale
                                        translationY = (if (resolvedIsBottom) 12.dp else (-12).dp).toPx() * collapseProg
                                    },
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                shownStacked.forEach { action ->
                                    BarActionButton(
                                        action = action,
                                        isLoading = isLoading,
                                        canGoBack = canGoBack,
                                        canGoForward = canGoForward,
                                        tabCount = tabCount,
                                        isLiquidGlass = isLiquidGlass,
                                        visualWidth = stackedBtnWidth,
                                        callbacks = callbacks
                                    )
                                }
                                if (droppedStacked.isNotEmpty()) {
                                    MergedOverflowButton(
                                        droppedActions = droppedStacked,
                                        isLoading = isLoading,
                                        canGoBack = canGoBack,
                                        canGoForward = canGoForward,
                                        visualWidth = stackedBtnWidth,
                                        callbacks = callbacks
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // SINGLE Row Layout: [leading buttons] [AddressPill] [trailing buttons]
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val minPillWidth = minOf(176.dp, (maxWidth * 0.48f).coerceAtLeast(120.dp))
                        val availableForButtons = (maxWidth - 8.dp - minPillWidth).coerceAtLeast(40.dp)
                        val totalWanted = leadingActions.size + trailingActions.size
                        val btnWidth = if (48.dp * totalWanted > availableForButtons) 40.dp else 48.dp
                        val maxButtons = (availableForButtons / btnWidth).toInt().coerceAtLeast(1)
                        val budget = remember(leadingActions, trailingActions, maxButtons) {
                            budgetMergedSingleActions(leadingActions, trailingActions, maxButtons)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(addressBarHeight.barDp.dp)
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Leading Buttons
                            Row(
                                modifier = Modifier.graphicsLayer {
                                    val v = chrome.currentVisuals()
                                    alpha = v.sideButtonAlpha
                                    scaleX = v.sideButtonScale
                                    scaleY = v.sideButtonScale
                                    translationX = v.leftButtonOffsetDp.dp.toPx()
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                budget.leading.forEach { action ->
                                    BarActionButton(
                                        action = action,
                                        isLoading = isLoading,
                                        canGoBack = canGoBack,
                                        canGoForward = canGoForward,
                                        tabCount = tabCount,
                                        isLiquidGlass = isLiquidGlass,
                                        visualWidth = btnWidth,
                                        callbacks = callbacks
                                    )
                                }
                            }

                            // Center URL Pill
                            AddressPill(
                                url = url,
                                title = title,
                                favicon = favicon,
                                progress = progress,
                                isIncognito = isIncognito,
                                isLoading = isLoading,
                                onAddressClick = onAddressClick,
                                onAiResearchClick = onAiResearchClick,
                                onMediaClick = onMediaClick,
                                onSwipeNextTab = onSwipeNextTab,
                                onSwipePrevTab = onSwipePrevTab,
                                onSwipeUp = onSwipeUp,
                                onPasteAndGo = onPasteAndGo,
                                onHardRefresh = onHardRefresh,
                                isReaderEligible = isReaderEligible,
                                onReaderClick = onReaderClick,
                                isBottom = resolvedIsBottom,
                                inMergedBar = true,
                                isLiquidGlass = isLiquidGlass,
                                modifier = Modifier
                                    .weight(1f)
                                    .widthIn(min = minPillWidth)
                            )

                            // Trailing Buttons + optional More overflow
                            Row(
                                modifier = Modifier.graphicsLayer {
                                    val v = chrome.currentVisuals()
                                    alpha = v.sideButtonAlpha
                                    scaleX = v.sideButtonScale
                                    scaleY = v.sideButtonScale
                                    translationX = v.rightButtonOffsetDp.dp.toPx()
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (budget.dropped.isNotEmpty()) {
                                    MergedOverflowButton(
                                        droppedActions = budget.dropped,
                                        isLoading = isLoading,
                                        canGoBack = canGoBack,
                                        canGoForward = canGoForward,
                                        visualWidth = btnWidth,
                                        callbacks = callbacks
                                    )
                                }
                                budget.trailing.forEach { action ->
                                    BarActionButton(
                                        action = action,
                                        isLoading = isLoading,
                                        canGoBack = canGoBack,
                                        canGoForward = canGoForward,
                                        tabCount = tabCount,
                                        isLiquidGlass = isLiquidGlass,
                                        visualWidth = btnWidth,
                                        callbacks = callbacks
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

        BarMinimizedPillHost(
            chrome = chrome,
            isIncognito = isIncognito,
            progress = progress,
            isLoading = isLoading,
            onExpand = { BarCollapseState.requestExpand() },
            onPillClick = {
                BarCollapseState.requestExpand()
                onAddressClick()
            }
        )
    }
}

private data class MergedSingleBudget(
    val leading: List<BarAction>,
    val trailing: List<BarAction>,
    val dropped: List<BarAction>
)

private fun budgetMergedSingleActions(
    leading: List<BarAction>,
    trailing: List<BarAction>,
    maxSlots: Int
): MergedSingleBudget {
    if (leading.size + trailing.size <= maxSlots) {
        return MergedSingleBudget(leading, trailing, emptyList())
    }
    val leadMut = leading.toMutableList()
    val trailMut = trailing.toMutableList()
    val dropped = mutableListOf<BarAction>()
    // Reserve 1 slot for the More button once anything is dropped.
    val actionLimit = (maxSlots - 1).coerceAtLeast(1)
    while (leadMut.size + trailMut.size > actionLimit) {
        // Priority: drop extra trailing actions beyond the first (preserving MENU), then leading from the end.
        val nonMenuTrailIdx = trailMut.indexOfLast { it != BarAction.MENU }
        val nonMenuTrailCount = trailMut.count { it != BarAction.MENU }
        if (nonMenuTrailCount > 1 || (leadMut.isEmpty() && nonMenuTrailIdx >= 0)) {
            dropped.add(trailMut.removeAt(nonMenuTrailIdx))
        } else {
            val nonMenuLeadIdx = leadMut.indexOfLast { it != BarAction.MENU }
            if (nonMenuLeadIdx >= 0) {
                dropped.add(leadMut.removeAt(nonMenuLeadIdx))
            } else if (nonMenuTrailIdx >= 0) {
                dropped.add(trailMut.removeAt(nonMenuTrailIdx))
            } else {
                break
            }
        }
    }
    return MergedSingleBudget(leadMut, trailMut, dropped)
}

private fun budgetMergedStackedActions(
    allActions: List<BarAction>,
    maxSlots: Int
): Pair<List<BarAction>, List<BarAction>> {
    if (allActions.size <= maxSlots) return allActions to emptyList()
    val kept = allActions.toMutableList()
    val dropped = mutableListOf<BarAction>()
    val actionLimit = (maxSlots - 1).coerceAtLeast(1)
    while (kept.size > actionLimit) {
        val dropIdx = kept.indexOfLast { it != BarAction.MENU }
        if (dropIdx >= 0) {
            dropped.add(kept.removeAt(dropIdx))
        } else {
            break
        }
    }
    return kept to dropped
}

@Composable
private fun MergedOverflowButton(
    droppedActions: List<BarAction>,
    isLoading: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    visualWidth: Dp,
    callbacks: MergedBarCallbacks
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        BarIconButton(
            icon = Icons.Rounded.MoreHoriz,
            contentDescription = "More toolbar actions",
            visualWidth = visualWidth,
            onClick = { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            droppedActions.forEach { action ->
                val enabled = when (action) {
                    BarAction.BACK -> canGoBack
                    BarAction.FORWARD -> canGoForward
                    else -> true
                }
                val icon = if (action == BarAction.RELOAD && isLoading) Icons.Rounded.Close else action.icon
                val label = if (action == BarAction.RELOAD && isLoading) {
                    stringResource(R.string.bar_stop_loading)
                } else {
                    action.localizedLabel()
                }
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = { Icon(icon, contentDescription = null) },
                    enabled = enabled,
                    onClick = {
                        expanded = false
                        dispatchBarAction(action, isLoading, callbacks)
                    }
                )
            }
        }
    }
}

private fun dispatchBarAction(
    action: BarAction,
    isLoading: Boolean,
    callbacks: MergedBarCallbacks
) {
    when (action) {
        BarAction.TABS -> callbacks.onTabsClick()
        BarAction.NEW_TAB -> callbacks.onNewTabClick()
        BarAction.HOME -> callbacks.onHomeClick()
        BarAction.SEARCH -> callbacks.onSearchClick()
        BarAction.BACK -> callbacks.onBackClick()
        BarAction.FORWARD -> callbacks.onForwardClick()
        BarAction.RELOAD -> if (isLoading) callbacks.onStopClick() else callbacks.onReloadClick()
        BarAction.SHARE -> callbacks.onShareClick()
        BarAction.BOOKMARK -> callbacks.onBookmarkClick()
        BarAction.AI_RESEARCH, BarAction.AI -> callbacks.onAiResearchClick()
        BarAction.READER -> callbacks.onReaderClick()
        BarAction.DOWNLOADS -> callbacks.onDownloadsClick()
        BarAction.FIND_IN_PAGE -> callbacks.onFindInPageClick()
        BarAction.HISTORY -> callbacks.onHistoryClick()
        BarAction.MENU -> callbacks.onMenuClick()
    }
}

/**
 * Standard touch-target action button rendered in the Petal Merge Bar.
 */
@Composable
private fun BarActionButton(
    action: BarAction,
    isLoading: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    tabCount: Int,
    isLiquidGlass: Boolean = false,
    visualWidth: Dp = 48.dp,
    callbacks: MergedBarCallbacks
) {
    val isEnabled = when (action) {
        BarAction.BACK -> canGoBack
        BarAction.FORWARD -> canGoForward
        else -> true
    }

    val icon = when (action) {
        // Back is always Back; only Reload swaps to Stop while the page loads.
        BarAction.RELOAD -> if (isLoading) Icons.Rounded.Close else action.icon
        else -> action.icon
    }

    // Real bounds of this button, so a SEARCH action grows the omnibox out of the tapped icon.
    val buttonBounds = remember { arrayOfNulls<androidx.compose.ui.geometry.Rect>(1) }
    val clickHandler: () -> Unit = {
        if (action == BarAction.SEARCH) PetalOmniboxOrigin.mark(buttonBounds[0], -1f)
        dispatchBarAction(action, isLoading, callbacks)
    }

    val longClickHandler: (() -> Unit)? = if (action.supportsLongPress) {
        {
            when (action) {
                // Long-press Back: this tab's back/forward popup (falls back to full history).
                BarAction.BACK -> if (canGoBack) {
                    com.petal.browser.browser.PetalTabHistoryState.requestOpen()
                } else {
                    callbacks.onHistoryClick()
                }
                BarAction.BOOKMARK -> callbacks.onBookmarksManagerClick()
                BarAction.RELOAD -> if (!isLoading) callbacks.onHardRefresh()
                BarAction.TABS -> callbacks.onNewTabClick()
                BarAction.NEW_TAB -> callbacks.onNewTabClick()
                BarAction.MENU -> callbacks.onCustomizeClick()
                else -> {}
            }
        }
    } else null

    val tint = if (isEnabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = PetalSpring.quick(),
        label = "merge_btn_press_scale"
    )

    Surface(
        shape = ExpressiveButtonShape,
        color = if (isLiquidGlass && isPressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
        modifier = Modifier
            .width(visualWidth)
            .heightIn(min = 48.dp)
            .onGloballyPositioned { buttonBounds[0] = it.boundsInWindow() }
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(ExpressiveButtonShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = clickHandler,
                onLongClick = longClickHandler
            )
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (action == BarAction.TABS) {
                TabCountBadge(color = tint, count = tabCount, scale = 0.95f)
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = if (action == BarAction.RELOAD && isLoading) stringResource(R.string.bar_stop_loading) else action.localizedLabel(),
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
