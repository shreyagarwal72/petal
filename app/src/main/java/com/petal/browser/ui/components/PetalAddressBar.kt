package com.petal.browser.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import com.petal.browser.R
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.petal.browser.ui.glass.LocalPetalGlassRevision
import com.petal.browser.ui.glass.liquidGlass
import com.petal.browser.ui.glass.rememberGlassBar

/**
 * Material 3 Expressive Collapsed/Scrolled Address Bar.
 *
 * Single pill squircle container row matching modern Android browser design:
 * - Far left: Back/Navigation arrow icon button or Stop button when loading
 * - Center: [AddressPill] (Favicon / Security / URL text / gestures / quick actions)
 * - Far right: Share icon button
 * - Supports Chrome-style scroll morphing into [PetalMinimizedPill]
 */
@OptIn(ExperimentalMaterial3Api::class, com.petal.browser.ui.theme.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PetalAddressBar(
    url: String,
    title: String,
    favicon: Bitmap? = null,
    progress: Float = 0f,
    isIncognito: Boolean = false,
    isLoading: Boolean = false,
    canGoBack: Boolean = true,
    onBackClick: () -> Unit,
    onReloadClick: () -> Unit = {},
    onStopClick: () -> Unit = {},
    onShareClick: () -> Unit,
    onAddressClick: () -> Unit,
    onAiResearchClick: () -> Unit = {},
    onMediaClick: () -> Unit = {},
    onSwipeNextTab: () -> Unit = {},
    onSwipePrevTab: () -> Unit = {},
    onSwipeUp: () -> Unit = {},
    onPasteAndGo: (String) -> Unit = {},
    onHardRefresh: () -> Unit = {},
    isReaderEligible: Boolean = false,
    onReaderClick: () -> Unit = {},
    isBottom: Boolean = false,
    hasBottomNav: Boolean = false,
    extraBottomSlidePx: Int = 0,
    isPreview: Boolean = false,
    suppressStatusBarTopInset: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = rememberBarSettings()
    val floatingTabBar = settings.floatingNav

    val addressBarGlassEnabled = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_address_bar")
    val chrome = rememberBarChrome(
        isBottomParam = isBottom,
        isPreview = isPreview,
        isIncognito = isIncognito,
        suppressStatusBarTopInset = suppressStatusBarTopInset,
        floatingForShape = { bottom -> bottom && floatingTabBar },
        floatingForWallpaper = { floatingTabBar },
        glassPref = { addressBarGlassEnabled }
    )
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
                // HIDE (Full Slide): the whole bar slides off its own edge using the settled full bar height.
                val v = chrome.currentVisuals()
                val slideDist = fullBarHeightPx + if (resolvedIsBottom) extraBottomSlidePx else 0
                translationY = (if (resolvedIsBottom) 1f else -1f) * slideDist * v.slideFraction
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
                                Modifier.padding(start = 12.dp, end = 12.dp, bottom = if (hasBottomNav) 0.dp else 4.dp)
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
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = 2.dp,
                            bottom = if (resolvedIsBottom && !hasBottomNav && !isPreview) navBarBottomInset + 4.dp else 2.dp
                        )
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val isTightBar = maxWidth < 360.dp
                        val btnWidth = if (isTightBar) 40.dp else 48.dp
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(addressBarHeight.barDp.dp)
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Far left: Back. Long-press opens this tab's back/forward history popup.
                            val backDesc = stringResource(R.string.bar_back)
                            BarIconButton(
                                icon = Icons.Rounded.ArrowBack,
                                contentDescription = backDesc,
                                enabled = canGoBack,
                                visualWidth = btnWidth,
                                onClick = onBackClick,
                                onLongClick = {
                                    com.petal.browser.haptics.PetalHapticEngine.getInstance(context).thresholdCrossed(context)
                                    com.petal.browser.browser.PetalTabHistoryState.requestOpen()
                                },
                                modifier = Modifier.graphicsLayer {
                                    val v = chrome.currentVisuals()
                                    alpha = v.sideButtonAlpha
                                    scaleX = v.sideButtonScale
                                    scaleY = v.sideButtonScale
                                    translationX = v.leftButtonOffsetDp.dp.toPx()
                                }
                            )

                            Spacer(modifier = Modifier.width(2.dp))

                            // Center: AddressPill
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
                                inMergedBar = false,
                                isLiquidGlass = isLiquidGlass,
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(2.dp))

                            // Reload, which swaps to Stop while the page loads.
                            val reloadDesc = stringResource(if (isLoading) R.string.bar_stop_loading else R.string.bar_reload)
                            BarIconButton(
                                icon = if (isLoading) Icons.Rounded.Close else Icons.Rounded.Refresh,
                                contentDescription = reloadDesc,
                                visualWidth = btnWidth,
                                onClick = if (isLoading) onStopClick else onReloadClick,
                                onLongClick = if (isLoading) null else onHardRefresh,
                                modifier = Modifier.graphicsLayer {
                                    val v = chrome.currentVisuals()
                                    alpha = v.sideButtonAlpha
                                    scaleX = v.sideButtonScale
                                    scaleY = v.sideButtonScale
                                    translationX = v.rightButtonOffsetDp.dp.toPx()
                                }
                            )

                            // Far right: Share
                            BarIconButton(
                                icon = Icons.Rounded.Share,
                                contentDescription = stringResource(R.string.bar_share),
                                visualWidth = btnWidth,
                                onClick = onShareClick,
                                modifier = Modifier.graphicsLayer {
                                    val v = chrome.currentVisuals()
                                    alpha = v.sideButtonAlpha
                                    scaleX = v.sideButtonScale
                                    scaleY = v.sideButtonScale
                                    translationX = v.rightButtonOffsetDp.dp.toPx()
                                }
                            )
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
