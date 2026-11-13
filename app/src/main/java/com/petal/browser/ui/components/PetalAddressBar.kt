package com.petal.browser.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import com.petal.browser.ui.kit.IconButton
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
    onShareClick: () -> Unit,
    onAddressClick: () -> Unit,
    onAiResearchClick: () -> Unit = {},
    onMediaClick: () -> Unit = {},
    onSwipeNextTab: () -> Unit = {},
    onSwipePrevTab: () -> Unit = {},
    onPasteAndGo: (String) -> Unit = {},
    onHardRefresh: () -> Unit = {},
    isReaderEligible: Boolean = false,
    onReaderClick: () -> Unit = {},
    isBottom: Boolean = false,
    hasBottomNav: Boolean = false,
    isPreview: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var addressBarHeight by remember { mutableStateOf(sp.getString("sp_address_bar_height", "COMPACT") ?: "COMPACT") }
    var collapseStyle by remember { mutableStateOf(sp.getString(BarPrefs.KEY_BAR_COLLAPSE_STYLE, BarPrefs.COLLAPSE_PILL) ?: BarPrefs.COLLAPSE_PILL) }
    var floatingTabBar by remember { mutableStateOf(sp.getBoolean("sp_floating_tab_bar", true)) }

    DisposableEffect(sp) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "sp_address_bar_height" -> addressBarHeight = sp.getString("sp_address_bar_height", "COMPACT") ?: "COMPACT"
                "sp_floating_tab_bar" -> floatingTabBar = sp.getBoolean("sp_floating_tab_bar", true)
                BarPrefs.KEY_BAR_COLLAPSE_STYLE -> collapseStyle = sp.getString(BarPrefs.KEY_BAR_COLLAPSE_STYLE, BarPrefs.COLLAPSE_PILL) ?: BarPrefs.COLLAPSE_PILL
            }
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val resolvedIsBottom = if (isPreview) isBottom else (isBottom || "BOTTOM".equals(sp.getString("sp_address_bar_position", "TOP"), ignoreCase = true))
    val isFloatingBar = resolvedIsBottom && floatingTabBar
    val isPillCollapse = if (isPreview) false else BarPrefs.COLLAPSE_PILL.equals(collapseStyle, ignoreCase = true)
    val collapsedFraction = if (isPreview) 0f else BarCollapseState.collapsedFraction
    val isFullyCollapsed = isPillCollapse && (collapsedFraction >= 0.95f || BarCollapseState.isCollapsed)

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
    val isLiquidGlass = !isPreview && com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_address_bar") && !isIncognito
    val samplesWallpaper = BarInsets.glassSamplesWallpaper(
        sp, resolvedIsBottom, floatingTabBar, false
    )
    val ambientGlassSource = com.petal.browser.ui.glass.LocalPetalGlassSource.current
    val activity = LocalContext.current as? android.app.Activity
    val glassSource = if (isPreview || samplesWallpaper) null else (ambientGlassSource ?: activity?.findViewById<android.view.View>(com.petal.browser.R.id.main_content))
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
                                Modifier.padding(start = 12.dp, end = 12.dp, bottom = if (hasBottomNav) 0.dp else 4.dp)
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
                        if (isLiquidGlass && outerBgAlpha > 0.05f) {
                            Modifier.liquidGlass(containerShape, baseGlassColor, glassBar)
                        } else Modifier
                    )
                    .graphicsLayer {
                        alpha = fullBarAlpha
                        scaleX = fullBarScaleX
                        scaleY = fullBarScaleY
                        translationY = fullBarTranslationY.toPx()
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (addressBarHeight.equals("COMPACT", true)) 50.dp else 56.dp)
                            .padding(horizontal = if (resolvedIsBottom) 6.dp else 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Far Left: Back Navigation / Stop Loading Icon Button
                        val leftIcon = if (isLoading) Icons.Rounded.Close else Icons.Rounded.ArrowBack
                        val leftContentDesc = if (isLoading) "Stop Loading" else "Back"
                        val isLeftEnabled = isLoading || canGoBack

                        IconButton(
                            onClick = onBackClick,
                            enabled = isLeftEnabled,
                            modifier = Modifier
                                .size(48.dp)
                                .graphicsLayer {
                                    alpha = sideButtonAlpha
                                    scaleX = sideButtonScale
                                    scaleY = sideButtonScale
                                    translationX = leftButtonOffset.toPx()
                                }
                        ) {
                            Icon(
                                imageVector = leftIcon,
                                contentDescription = leftContentDesc,
                                tint = if (isLeftEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(if (resolvedIsBottom) 4.dp else 2.dp))

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

                        // Far Right: Share Icon Button
                        IconButton(
                            onClick = onShareClick,
                            modifier = Modifier
                                .size(48.dp)
                                .graphicsLayer {
                                    alpha = sideButtonAlpha
                                    scaleX = sideButtonScale
                                    scaleY = sideButtonScale
                                    translationX = rightButtonOffset.toPx()
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
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
