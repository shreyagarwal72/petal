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
    isPreview: Boolean = false,
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

    val resolvedIsBottom = if (isPreview) isBottom else (isBottom || "BOTTOM".equals(sp.getString("sp_address_bar_position", "TOP"), ignoreCase = true))
    val isPillCollapse = if (isPreview) false else BarPrefs.COLLAPSE_PILL.equals(collapseStyle, ignoreCase = true)
    val collapsedFraction = if (isPreview) 0f else BarCollapseState.collapsedFraction
    val isFullyCollapsed = isPillCollapse && (collapsedFraction >= 0.95f || BarCollapseState.isCollapsed)

    val isFloatingBar = resolvedIsBottom && sp.getBoolean("sp_floating_tab_bar", true)

    val containerShape = if (resolvedIsBottom) {
        if (isFloatingBar) {
            RoundedCornerShape(if (addressBarHeight.equals("COMPACT", true)) 24.dp else 28.dp)
        } else {
            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
        }
    } else {
        RoundedCornerShape(if (addressBarHeight.equals("COMPACT", true)) 24.dp else 28.dp)
    }

    LocalPetalGlassRevision.current
    val isGlassEnabledPref = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_address_bar") ||
        (resolvedIsBottom && com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_bottom_nav")) ||
        (isPreview && com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_containments"))
    val isLiquidGlass = isGlassEnabledPref && !isIncognito
    val samplesWallpaper = BarInsets.glassSamplesWallpaper(sp, resolvedIsBottom, isFloatingBar, false)
    val activity = LocalContext.current as? android.app.Activity
    val glassSource = if (isPreview) null else activity?.findViewById<android.view.View>(
        if (samplesWallpaper) com.petal.browser.R.id.glass_wallpaper_backdrop else com.petal.browser.R.id.main_content
    ) ?: LocalView.current.rootView.findViewById<android.view.View>(
        if (samplesWallpaper) com.petal.browser.R.id.glass_wallpaper_backdrop else com.petal.browser.R.id.main_content
    )
    val glassBar = rememberGlassBar(if (isLiquidGlass) glassSource else null)
    val pillGlassBar = rememberGlassBar(if (isLiquidGlass) glassSource else null)
    val baseGlassColor = MaterialTheme.colorScheme.surfaceContainerHigh

    val containerColor = when {
        isIncognito -> com.petal.browser.ui.theme.IncognitoSurfaceContainer
        isLiquidGlass -> Color.Transparent
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val rawStatusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val isImmersive = rawStatusTop == 0.dp
    val statusBarTopInset = if (isPreview || isImmersive) 0.dp else WindowInsets.statusBars.union(WindowInsets.displayCutout)
        .asPaddingValues().calculateTopPadding()
    val navBarBottomInset = if (isPreview) 0.dp else WindowInsets.navigationBars
        .asPaddingValues().calculateBottomPadding()

    val collapseProgress = if (isPillCollapse) collapsedFraction.coerceIn(0f, 1f) else 0f
    val fullBarAlpha = if (isPillCollapse) ((1f - collapseProgress) / 0.65f).coerceIn(0f, 1f) else 1f
    val fullBarScaleX = if (isPillCollapse) (0.94f + 0.06f * (1f - collapseProgress)) else 1f
    val fullBarScaleY = if (isPillCollapse) (0.90f + 0.10f * (1f - collapseProgress)) else 1f
    val fullBarTranslationY = if (isPillCollapse) ((if (resolvedIsBottom) 10.dp else (-10).dp) * collapseProgress) else 0.dp

    val sideButtonAlpha = if (isPillCollapse) (1f - collapseProgress * 1.8f).coerceIn(0f, 1f) else 1f
    val sideButtonScale = if (isPillCollapse) (0.80f + 0.20f * sideButtonAlpha) else 1f
    val leftButtonOffset = if (isPillCollapse) ((-12).dp * collapseProgress) else 0.dp
    val rightButtonOffset = if (isPillCollapse) (12.dp * collapseProgress) else 0.dp

    val pillAlpha = if (isPillCollapse) ((collapseProgress - 0.20f) / 0.80f).coerceIn(0f, 1f) else 0f
    val pillScale = if (isPillCollapse) (0.88f + 0.12f * pillAlpha) else 1f
    val pillTranslationY = if (isPillCollapse) ((if (resolvedIsBottom) (-10).dp else 10.dp) * (1f - pillAlpha)) else 0.dp
    val outerBgAlpha = if (isPillCollapse) (1f - collapseProgress * 1.5f).coerceIn(0f, 1f) else 1f

    val animatedContainerColor = if (isLiquidGlass) Color.Transparent else containerColor.copy(alpha = containerColor.alpha * outerBgAlpha)
    val animatedElevation = (if (isLiquidGlass) 0.dp else if (resolvedIsBottom) 2.dp else 4.dp) * outerBgAlpha

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        if (fullBarAlpha > 0.01f) {
            Surface(
                shape = containerShape,
                color = animatedContainerColor,
                tonalElevation = animatedElevation,
                shadowElevation = animatedElevation,
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
                                top = statusBarTopInset + (if (addressBarHeight.equals("COMPACT", true)) 3.dp else 5.dp),
                                bottom = if (addressBarHeight.equals("COMPACT", true)) 3.dp else 5.dp
                            )
                        }
                    )
                    .then(
                        if (isLiquidGlass && outerBgAlpha > 0.05f) Modifier.liquidGlass(containerShape, baseGlassColor, glassBar)
                        else Modifier
                    )
                    .graphicsLayer {
                        alpha = fullBarAlpha
                        scaleX = fullBarScaleX
                        scaleY = fullBarScaleY
                        translationY = fullBarTranslationY.toPx()
                    }
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.HEAVY_CLICK, 0.8f)
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
                                .height(44.dp)
                                .then(
                                    if (isLiquidGlass) {
                                        Modifier
                                            .clip(RoundedCornerShape(22.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.12f))
                                    } else Modifier
                                )
                                .graphicsLayer {
                                    alpha = sideButtonAlpha
                                    scaleX = sideButtonScale
                                    scaleY = sideButtonScale
                                    translationY = (if (resolvedIsBottom) 12.dp else (-12).dp).toPx() * collapseProgress
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
                        Row(
                            modifier = Modifier.graphicsLayer {
                                alpha = sideButtonAlpha
                                scaleX = sideButtonScale
                                scaleY = sideButtonScale
                                translationX = leftButtonOffset.toPx()
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
                        Row(
                            modifier = Modifier.graphicsLayer {
                                alpha = sideButtonAlpha
                                scaleX = sideButtonScale
                                scaleY = sideButtonScale
                                translationX = rightButtonOffset.toPx()
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

    if (!isPreview && isPillCollapse && pillAlpha > 0.005f) {
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
                    alpha = pillAlpha
                    scaleX = pillScale
                    scaleY = pillScale
                    translationY = pillTranslationY.toPx()
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
    val context = LocalContext.current
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

    // Real bounds of this button, so a SEARCH action grows the omnibox out of the tapped icon.
    val buttonBounds = remember { arrayOfNulls<androidx.compose.ui.geometry.Rect>(1) }
    val clickHandler: () -> Unit = {
        PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.6f)
        if (action == BarAction.SEARCH) PetalOmniboxOrigin.mark(buttonBounds[0], -1f)
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

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "merge_btn_press_scale"
    )

    Surface(
        shape = ExpressiveButtonShape,
        color = if (isLiquidGlass && isPressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
        modifier = Modifier
            .size(44.dp)
            .onGloballyPositioned { buttonBounds[0] = it.boundsInWindow() }
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(ExpressiveButtonShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = clickHandler
            )
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
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
}
