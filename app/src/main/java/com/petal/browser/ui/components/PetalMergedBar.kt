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
    val resolvedIsBottom = chrome.isBottom
    val isFloatingBar = chrome.isFloating
    val addressBarHeight = chrome.height
    val isPillCollapse = chrome.isPillCollapse
    val collapseStyle = chrome.collapseStyle
    val containerShape = chrome.containerShape
    val isLiquidGlass = chrome.isLiquidGlass
    val glassBar = chrome.glassBar
    val pillGlassBar = chrome.pillGlassBar
    val glassSource = chrome.glassSource
    val baseGlassColor = chrome.baseGlassColor
    val containerColor = chrome.containerColor
    val statusBarTopInset = chrome.statusBarTopInset
    val navBarBottomInset = chrome.navBarBottomInset
    var barHeightPx by remember { mutableIntStateOf(0) }
    val barVisible by remember(collapseStyle, isPreview) {
        derivedStateOf {
            isPreview || BarCollapseMath.isBarVisible(collapseStyle, BarCollapseState.collapsedFraction)
        }
    }
    val showPill by remember(collapseStyle, isPreview, isPillCollapse) {
        derivedStateOf {
            !isPreview && isPillCollapse && BarCollapseMath.isPillShown(collapseStyle, BarCollapseState.collapsedFraction)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { barHeightPx = it.height }
            .graphicsLayer {
                // HIDE (Full Slide): the whole bar slides off its own edge as the fraction grows.
                val f = if (isPreview) 0f else BarCollapseState.collapsedFraction
                translationY = (if (resolvedIsBottom) 1f else -1f) * barHeightPx * BarCollapseMath.slideFraction(collapseStyle, f)
            },
        contentAlignment = Alignment.Center
    ) {
        if (barVisible) {
            Surface(
                shape = containerShape,
                color = containerColor,
                tonalElevation = chrome.staticElevation,
                shadowElevation = chrome.staticElevation,
                modifier = Modifier
                    .fillMaxWidth()
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
                        if (isLiquidGlass) Modifier.liquidGlass(containerShape, baseGlassColor, glassBar)
                        else Modifier
                    )
                    .graphicsLayer {
                        val f = if (isPreview) 0f else BarCollapseState.collapsedFraction
                        alpha = BarCollapseMath.fullBarAlpha(collapseStyle, f)
                        scaleX = BarCollapseMath.fullBarScaleX(collapseStyle, f)
                        scaleY = BarCollapseMath.fullBarScaleY(collapseStyle, f)
                        translationY = BarCollapseMath.fullBarTranslationDp(collapseStyle, f, resolvedIsBottom).dp.toPx()
                    }
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            com.petal.browser.haptics.PetalHapticEngine.getInstance(context).thresholdCrossed(context)
                            onCustomizeClick()
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
                            .padding(horizontal = 8.dp, vertical = 6.dp),
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

                        // Material 3 Expressive Progress Bar (relocated to blue arrow position: directly between AddressPill and Action Buttons)
                        if (isLoading && progress > 0f) {
                            Material3ExpressiveProgressIndicator(
                                progress = progress,
                                visible = isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        val allActions = (leadingActions + trailingActions).distinct().take(BarPrefs.MAX_MERGE_ACTIONS_TOTAL)
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
                                    val f = if (isPreview) 0f else BarCollapseState.collapsedFraction
                                    val s = BarCollapseMath.sideButtonScale(collapseStyle, f)
                                    val progress = if (isPillCollapse) f.coerceIn(0f, 1f) else 0f
                                    alpha = BarCollapseMath.sideButtonAlpha(collapseStyle, f)
                                    scaleX = s
                                    scaleY = s
                                    translationY = (if (resolvedIsBottom) 12.dp else (-12).dp).toPx() * progress
                                },
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            allActions.forEach { action ->
                                BarActionButton(
                                    action = action,
                                    isLoading = isLoading,
                                    canGoBack = canGoBack,
                                    canGoForward = canGoForward,
                                    tabCount = tabCount,
                                    isLiquidGlass = isLiquidGlass,
                                    onBackClick = onBackClick,
                                    onForwardClick = onForwardClick,
                                    onReloadClick = onReloadClick,
                                    onStopClick = onStopClick,
                                    onShareClick = onShareClick,
                                    onHomeClick = onHomeClick,
                                    onNewTabClick = onNewTabClick,
                                    onTabsClick = onTabsClick,
                                    onMenuClick = onMenuClick,
                                    onSearchClick = onSearchClick,
                                    onDownloadsClick = onDownloadsClick,
                                    onFindInPageClick = onFindInPageClick,
                                    onBookmarkClick = onBookmarkClick,
                                    onHistoryClick = onHistoryClick,
                                    onBookmarksManagerClick = onBookmarksManagerClick,
                                    onAiResearchClick = onAiResearchClick,
                                    onReaderClick = onReaderClick,
                                    onHardRefresh = onHardRefresh,
                                    onCustomizeClick = onCustomizeClick
                                )
                            }
                        }
                    }
                } else {
                    // SINGLE Row Layout: [leading buttons] [AddressPill] [trailing buttons]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(addressBarHeight.barDp.dp)
                            .padding(horizontal = if (resolvedIsBottom) 4.dp else 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Leading Buttons
                        Row(
                            modifier = Modifier.graphicsLayer {
                                val f = if (isPreview) 0f else BarCollapseState.collapsedFraction
                                val s = BarCollapseMath.sideButtonScale(collapseStyle, f)
                                alpha = BarCollapseMath.sideButtonAlpha(collapseStyle, f)
                                scaleX = s
                                scaleY = s
                                translationX = BarCollapseMath.leftButtonOffsetDp(collapseStyle, f).dp.toPx()
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            leadingActions.forEach { action ->
                                BarActionButton(
                                    action = action,
                                    isLoading = isLoading,
                                    canGoBack = canGoBack,
                                    canGoForward = canGoForward,
                                    tabCount = tabCount,
                                    isLiquidGlass = isLiquidGlass,
                                    onBackClick = onBackClick,
                                    onForwardClick = onForwardClick,
                                    onReloadClick = onReloadClick,
                                    onStopClick = onStopClick,
                                    onShareClick = onShareClick,
                                    onHomeClick = onHomeClick,
                                    onNewTabClick = onNewTabClick,
                                    onTabsClick = onTabsClick,
                                    onMenuClick = onMenuClick,
                                    onSearchClick = onSearchClick,
                                    onDownloadsClick = onDownloadsClick,
                                    onFindInPageClick = onFindInPageClick,
                                    onBookmarkClick = onBookmarkClick,
                                    onHistoryClick = onHistoryClick,
                                    onBookmarksManagerClick = onBookmarksManagerClick,
                                    onAiResearchClick = onAiResearchClick,
                                    onReaderClick = onReaderClick,
                                    onHardRefresh = onHardRefresh,
                                    onCustomizeClick = onCustomizeClick
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
                            modifier = Modifier.weight(1f)
                        )

                        // Trailing Buttons
                        Row(
                            modifier = Modifier.graphicsLayer {
                                val f = if (isPreview) 0f else BarCollapseState.collapsedFraction
                                val s = BarCollapseMath.sideButtonScale(collapseStyle, f)
                                alpha = BarCollapseMath.sideButtonAlpha(collapseStyle, f)
                                scaleX = s
                                scaleY = s
                                translationX = BarCollapseMath.rightButtonOffsetDp(collapseStyle, f).dp.toPx()
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            trailingActions.forEach { action ->
                                BarActionButton(
                                    action = action,
                                    isLoading = isLoading,
                                    canGoBack = canGoBack,
                                    canGoForward = canGoForward,
                                    tabCount = tabCount,
                                    isLiquidGlass = isLiquidGlass,
                                    onBackClick = onBackClick,
                                    onForwardClick = onForwardClick,
                                    onReloadClick = onReloadClick,
                                    onStopClick = onStopClick,
                                    onShareClick = onShareClick,
                                    onHomeClick = onHomeClick,
                                    onNewTabClick = onNewTabClick,
                                    onTabsClick = onTabsClick,
                                    onMenuClick = onMenuClick,
                                    onSearchClick = onSearchClick,
                                    onDownloadsClick = onDownloadsClick,
                                    onFindInPageClick = onFindInPageClick,
                                    onBookmarkClick = onBookmarkClick,
                                    onHistoryClick = onHistoryClick,
                                    onBookmarksManagerClick = onBookmarksManagerClick,
                                    onAiResearchClick = onAiResearchClick,
                                    onReaderClick = onReaderClick,
                                    onHardRefresh = onHardRefresh,
                                    onCustomizeClick = onCustomizeClick
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPill) {
            PetalMinimizedPill(
                domain = BarCollapseState.currentDomain,
                isHttps = BarCollapseState.isHttps,
                isIncognito = isIncognito,
                isBottom = resolvedIsBottom,
                glassSource = if (isLiquidGlass) glassSource else null,
                isLiquidGlass = isLiquidGlass,
                glassBar = pillGlassBar,
                onExpand = {
                    BarCollapseState.requestExpand()
                },
                modifier = Modifier.graphicsLayer {
                    val f = BarCollapseState.collapsedFraction
                    val s = BarCollapseMath.pillScale(collapseStyle, f)
                    alpha = BarCollapseMath.pillAlpha(collapseStyle, f)
                    scaleX = s
                    scaleY = s
                    translationY = BarCollapseMath.pillTranslationDp(collapseStyle, f, resolvedIsBottom).dp.toPx()
                }
            )
        }
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
    onBackClick: () -> Unit,
    onForwardClick: () -> Unit,
    onReloadClick: () -> Unit,
    onStopClick: () -> Unit,
    onShareClick: () -> Unit,
    onHomeClick: () -> Unit,
    onNewTabClick: () -> Unit,
    onTabsClick: () -> Unit,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onFindInPageClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onAiResearchClick: () -> Unit,
    onReaderClick: () -> Unit,
    onHistoryClick: () -> Unit = {},
    onBookmarksManagerClick: () -> Unit = onBookmarkClick,
    onHardRefresh: () -> Unit = onReloadClick,
    onCustomizeClick: () -> Unit = {}
) {
    val context = LocalContext.current
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
        when (action) {
            BarAction.TABS -> onTabsClick()
            BarAction.NEW_TAB -> onNewTabClick()
            BarAction.HOME -> onHomeClick()
            BarAction.SEARCH -> onSearchClick()
            BarAction.BACK -> onBackClick()
            BarAction.FORWARD -> onForwardClick()
            BarAction.RELOAD -> if (isLoading) onStopClick() else onReloadClick()
            BarAction.SHARE -> onShareClick()
            BarAction.BOOKMARK -> onBookmarkClick()
            BarAction.AI_RESEARCH, BarAction.AI -> onAiResearchClick()
            BarAction.READER -> onReaderClick()
            BarAction.DOWNLOADS -> onDownloadsClick()
            BarAction.FIND_IN_PAGE -> onFindInPageClick()
            BarAction.HISTORY -> onHistoryClick()
            BarAction.MENU -> onMenuClick()
        }
    }

    val longClickHandler: (() -> Unit)? = if (action.supportsLongPress) {
        {
            when (action) {
                // Long-press Back: this tab's back/forward popup (falls back to full history).
                BarAction.BACK -> if (canGoBack) {
                    com.petal.browser.browser.PetalTabHistoryState.requestOpen()
                } else {
                    onHistoryClick()
                }
                BarAction.BOOKMARK -> onBookmarksManagerClick()
                BarAction.RELOAD -> if (!isLoading) onHardRefresh()
                BarAction.TABS -> onNewTabClick()
                BarAction.NEW_TAB -> onNewTabClick()
                BarAction.MENU -> onCustomizeClick()
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
            .size(48.dp)
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
