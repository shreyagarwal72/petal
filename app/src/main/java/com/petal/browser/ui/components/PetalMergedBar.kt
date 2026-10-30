package com.petal.browser.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    onAiResearchClick: () -> Unit = {},
    onMediaClick: () -> Unit = {},
    onSwipeNextTab: () -> Unit = {},
    onSwipePrevTab: () -> Unit = {},
    onPasteAndGo: (String) -> Unit = {},
    onHardRefresh: () -> Unit = {},
    isReaderEligible: Boolean = false,
    onReaderClick: () -> Unit = {},
    onCustomizeClick: () -> Unit = {},
    isBottom: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    var mergeLayout by remember { mutableStateOf(sp.getString(BarPrefs.KEY_MERGE_LAYOUT, BarPrefs.LAYOUT_SINGLE) ?: BarPrefs.LAYOUT_SINGLE) }
    var collapseStyle by remember { mutableStateOf(sp.getString(BarPrefs.KEY_BAR_COLLAPSE_STYLE, BarPrefs.COLLAPSE_PILL) ?: BarPrefs.COLLAPSE_PILL) }
    var addressBarHeight by remember { mutableStateOf(sp.getString("sp_address_bar_height", "COMPACT") ?: "COMPACT") }

    val (leadingActions, trailingActions) = remember(
        sp.getString(BarPrefs.KEY_MERGE_LEADING, BarPrefs.DEFAULT_MERGE_LEADING),
        sp.getString(BarPrefs.KEY_MERGE_TRAILING, BarPrefs.DEFAULT_MERGE_TRAILING)
    ) {
        BarPrefs.getSanitizedMergeActions(sp)
    }

    DisposableEffect(sp) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                BarPrefs.KEY_MERGE_LAYOUT -> mergeLayout = sp.getString(BarPrefs.KEY_MERGE_LAYOUT, BarPrefs.LAYOUT_SINGLE) ?: BarPrefs.LAYOUT_SINGLE
                BarPrefs.KEY_BAR_COLLAPSE_STYLE -> collapseStyle = sp.getString(BarPrefs.KEY_BAR_COLLAPSE_STYLE, BarPrefs.COLLAPSE_PILL) ?: BarPrefs.COLLAPSE_PILL
                "sp_address_bar_height" -> addressBarHeight = sp.getString("sp_address_bar_height", "COMPACT") ?: "COMPACT"
            }
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val resolvedIsBottom = isBottom || "BOTTOM".equals(sp.getString("sp_address_bar_position", "TOP"), ignoreCase = true)
    val isPillCollapse = BarPrefs.COLLAPSE_PILL.equals(collapseStyle, ignoreCase = true)
    val collapsedFraction = BarCollapseState.collapsedFraction
    val isFullyCollapsed = isPillCollapse && (collapsedFraction >= 0.95f || BarCollapseState.isCollapsed)

    val containerShape = if (resolvedIsBottom) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
    } else {
        RoundedCornerShape(if (addressBarHeight.equals("COMPACT", true)) 24.dp else 28.dp)
    }

    LocalPetalGlassRevision.current
    val isLiquidGlass = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_address_bar") && !isIncognito
    val samplesWallpaper = BarInsets.glassSamplesWallpaper(sp, resolvedIsBottom, sp.getBoolean("sp_floating_tab_bar", true), false)
    val glassSource = LocalView.current.rootView.findViewById<android.view.View>(
        if (samplesWallpaper) com.petal.browser.R.id.glass_wallpaper_backdrop else com.petal.browser.R.id.main_content
    )
    val glassBar = rememberGlassBar(if (isLiquidGlass) glassSource else null)
    val baseGlassColor = MaterialTheme.colorScheme.surfaceContainerHigh

    val containerColor = when {
        isIncognito -> com.petal.browser.ui.theme.IncognitoSurfaceContainer
        isLiquidGlass -> Color.Transparent
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val topBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    Crossfade(
        targetState = isFullyCollapsed,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "merge_pill_morph"
    ) { collapsed ->
        if (collapsed) {
            PetalMinimizedPill(
                domain = BarCollapseState.currentDomain,
                isHttps = BarCollapseState.isHttps,
                isIncognito = isIncognito,
                isBottom = resolvedIsBottom,
                glassSource = if (isLiquidGlass) glassSource else null,
                onExpand = {
                    BarCollapseState.requestExpand()
                },
                modifier = modifier
            )
        } else {
            Surface(
                shape = containerShape,
                color = containerColor,
                tonalElevation = if (isLiquidGlass) 0.dp else if (resolvedIsBottom) 2.dp else 4.dp,
                shadowElevation = if (isLiquidGlass) 0.dp else if (resolvedIsBottom) 4.dp else 4.dp,
                border = if (resolvedIsBottom && !isLiquidGlass) BorderStroke(0.65.dp, topBorderColor) else null,
                modifier = modifier
                    .fillMaxWidth()
                    .then(
                        if (resolvedIsBottom) Modifier.padding(horizontal = 0.dp, vertical = 0.dp)
                        else Modifier.padding(horizontal = 12.dp, vertical = if (addressBarHeight.equals("COMPACT", true)) 3.dp else 5.dp)
                    )
                    .then(
                        if (isLiquidGlass) Modifier.liquidGlass(containerShape, baseGlassColor, glassBar)
                        else Modifier
                    )
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.HEAVY_CLICK, 0.8f)
                            onCustomizeClick()
                        }
                    )
            ) {
                if (BarPrefs.LAYOUT_STACKED.equals(mergeLayout, ignoreCase = true)) {
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
                            onPasteAndGo = onPasteAndGo,
                            onHardRefresh = onHardRefresh,
                            isReaderEligible = isReaderEligible,
                            onReaderClick = onReaderClick,
                            isBottom = resolvedIsBottom,
                            inMergedBar = true,
                            isLiquidGlass = isLiquidGlass,
                            modifier = Modifier.fillMaxWidth()
                        )

                        val allActions = (leadingActions + trailingActions).distinct().take(BarPrefs.MAX_MERGE_ACTIONS_TOTAL)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
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
                                    onBackClick = onBackClick,
                                    onForwardClick = onForwardClick,
                                    onReloadClick = onReloadClick,
                                    onShareClick = onShareClick,
                                    onHomeClick = onHomeClick,
                                    onNewTabClick = onNewTabClick,
                                    onTabsClick = onTabsClick,
                                    onMenuClick = onMenuClick,
                                    onSearchClick = onSearchClick,
                                    onDownloadsClick = onDownloadsClick,
                                    onFindInPageClick = onFindInPageClick,
                                    onBookmarkClick = onBookmarkClick,
                                    onAiResearchClick = onAiResearchClick,
                                    onReaderClick = onReaderClick
                                )
                            }
                        }
                    }
                } else {
                    // SINGLE Row Layout: [leading buttons] [AddressPill] [trailing buttons]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (addressBarHeight.equals("COMPACT", true)) 50.dp else 56.dp)
                            .padding(horizontal = if (resolvedIsBottom) 4.dp else 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Leading Buttons
                        leadingActions.forEach { action ->
                            BarActionButton(
                                action = action,
                                isLoading = isLoading,
                                canGoBack = canGoBack,
                                canGoForward = canGoForward,
                                tabCount = tabCount,
                                onBackClick = onBackClick,
                                onForwardClick = onForwardClick,
                                onReloadClick = onReloadClick,
                                onShareClick = onShareClick,
                                onHomeClick = onHomeClick,
                                onNewTabClick = onNewTabClick,
                                onTabsClick = onTabsClick,
                                onMenuClick = onMenuClick,
                                onSearchClick = onSearchClick,
                                onDownloadsClick = onDownloadsClick,
                                onFindInPageClick = onFindInPageClick,
                                onBookmarkClick = onBookmarkClick,
                                onAiResearchClick = onAiResearchClick,
                                onReaderClick = onReaderClick
                            )
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
                        trailingActions.forEach { action ->
                            BarActionButton(
                                action = action,
                                isLoading = isLoading,
                                canGoBack = canGoBack,
                                canGoForward = canGoForward,
                                tabCount = tabCount,
                                onBackClick = onBackClick,
                                onForwardClick = onForwardClick,
                                onReloadClick = onReloadClick,
                                onShareClick = onShareClick,
                                onHomeClick = onHomeClick,
                                onNewTabClick = onNewTabClick,
                                onTabsClick = onTabsClick,
                                onMenuClick = onMenuClick,
                                onSearchClick = onSearchClick,
                                onDownloadsClick = onDownloadsClick,
                                onFindInPageClick = onFindInPageClick,
                                onBookmarkClick = onBookmarkClick,
                                onAiResearchClick = onAiResearchClick,
                                onReaderClick = onReaderClick
                            )
                        }
                    }
                }
            }
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
    onBackClick: () -> Unit,
    onForwardClick: () -> Unit,
    onReloadClick: () -> Unit,
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
    onReaderClick: () -> Unit
) {
    val isEnabled = when (action) {
        BarAction.BACK -> isLoading || canGoBack
        BarAction.FORWARD -> canGoForward
        else -> true
    }

    val icon = when (action) {
        BarAction.BACK -> if (isLoading) Icons.Rounded.Close else action.icon
        BarAction.RELOAD -> if (isLoading) Icons.Rounded.Close else action.icon
        else -> action.icon
    }

    val clickHandler: () -> Unit = {
        when (action) {
            BarAction.TABS -> onTabsClick()
            BarAction.NEW_TAB -> onNewTabClick()
            BarAction.HOME -> onHomeClick()
            BarAction.SEARCH -> onSearchClick()
            BarAction.BACK -> onBackClick()
            BarAction.FORWARD -> onForwardClick()
            BarAction.RELOAD -> onReloadClick()
            BarAction.SHARE -> onShareClick()
            BarAction.BOOKMARK -> onBookmarkClick()
            BarAction.AI_RESEARCH -> onAiResearchClick()
            BarAction.READER -> onReaderClick()
            BarAction.DOWNLOADS -> onDownloadsClick()
            BarAction.FIND_IN_PAGE -> onFindInPageClick()
            BarAction.MENU -> onMenuClick()
        }
    }

    val tint = if (isEnabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    IconButton(
        onClick = clickHandler,
        enabled = isEnabled,
        modifier = Modifier.size(44.dp)
    ) {
        if (action == BarAction.TABS) {
            TabCountBadge(color = tint, count = tabCount, scale = 0.95f)
        } else {
            Icon(
                imageVector = icon,
                contentDescription = action.label,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
