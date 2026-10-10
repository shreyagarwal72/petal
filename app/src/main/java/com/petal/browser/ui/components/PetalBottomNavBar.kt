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
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.theme.ExperimentalMaterial3ExpressiveApi
import kotlinx.coroutines.delay

enum class PetalNavTab {
    HOME, NEW_TAB, TABS, MENU
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
    onHomeClick: () -> Unit,
    onNewTabClick: () -> Unit,
    onTabsClick: () -> Unit,
    onMenuClick: () -> Unit,
    onSwipeTabLeft: () -> Unit = {},
    onSwipeTabRight: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Whole-bar entrance: slides up and fades in with a soft spring when first shown
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 260f))
    }
    val entranceOffsetPx = with(LocalDensity.current) { 32.dp.toPx() }

    val badgeScale = remember { Animatable(1f) }
    LaunchedEffect(tabCount) {
        badgeScale.snapTo(1.35f)
        badgeScale.animateTo(
            1f,
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }

    val tabsLabel = "Tabs"
    val newTabLabel = "New"

    val effectiveFloating = isFloatingStyle && !isBottomAddressBar
    if (effectiveFloating) {
        Box(
            modifier = modifier
                .graphicsLayer {
                    translationY = (1f - entrance.value) * entranceOffsetPx
                    alpha = entrance.value.coerceIn(0f, 1f)
                }
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            val context = LocalContext.current
            val sp = remember(context) { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
            val isLiquidGlass = sp.getBoolean("sp_liquid_glass_enabled", false)
            val glassAlpha = if (isLiquidGlass) sp.getFloat("sp_liquid_glass_alpha", 0.70f).coerceIn(0.20f, 0.95f) else 1f
            val glassSheen = if (isLiquidGlass) sp.getFloat("sp_liquid_glass_sheen", 0.60f).coerceIn(0f, 1f) else 0.60f
            val glassTint = if (isLiquidGlass) sp.getString("sp_liquid_glass_tint", "FROSTED") ?: "FROSTED" else "FROSTED"

            val baseContainer = when (glassTint) {
                "ACCENT" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = glassAlpha * 0.85f)
                "DEEP" -> MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = glassAlpha)
                else -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = glassAlpha)
            }

            // Material 3 Expressive Floating Toolbar with styled surfaceContainer for proper theme presentation
            val toolbarColors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(
                toolbarContainerColor = if (isLiquidGlass) baseContainer else MaterialTheme.colorScheme.surfaceContainer,
                toolbarContentColor = MaterialTheme.colorScheme.onSurface
            )

            var dragAccumulator by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
            var isDragging by remember { mutableStateOf(false) }
            val maxDragPx = with(LocalDensity.current) { 28.dp.toPx() }
            // Bar follows the finger with resistance while swiping, then springs back on release
            val dragOffsetPx by animateFloatAsState(
                targetValue = if (isDragging) (dragAccumulator * 0.35f).coerceIn(-maxDragPx, maxDragPx) else 0f,
                animationSpec = if (isDragging) snap() else spring(dampingRatio = 0.50f, stiffness = 320f),
                label = "swipe_rubber_band"
            )

            val backdrop = com.petal.browser.ui.containment.LocalPetalBackdrop.current
            val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()

            if (isLiquidGlass) {
                // Real Material 3 Expressive Liquid Glass Floating Tab Bar (SimpMusic port)
                val barInteraction = com.petal.browser.ui.containment.rememberGlassInteraction()
                val glassLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
                val luminance = com.petal.browser.ui.containment.rememberGlassLuminance(glassLayer, enabled = backdrop != null && com.petal.browser.ui.containment.petalRealGlassSupported)

                Box(
                    modifier = Modifier
                        .wrapContentWidth()
                        .height(64.dp)
                        .graphicsLayer { translationX = dragOffsetPx }
                        .pointerInput(barInteraction) { barInteraction.detectPress(this) }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { isDragging = true },
                                onDragEnd = {
                                    isDragging = false
                                    if (dragAccumulator > 70f) {
                                        onSwipeTabLeft()
                                    } else if (dragAccumulator < -70f) {
                                        onSwipeTabRight()
                                    }
                                    dragAccumulator = 0f
                                },
                                onDragCancel = {
                                    isDragging = false
                                    dragAccumulator = 0f
                                },
                                onHorizontalDrag = { _, dragAmount: Float ->
                                    dragAccumulator += dragAmount
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Outer Liquid Glass interactive capsule
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .com.petal.browser.ui.containment.drawInteractiveGlass(
                                isDark = isDarkTheme,
                                backdrop = backdrop,
                                layer = glassLayer,
                                luminance = { luminance.value },
                                shape = CircleShape,
                                interaction = barInteraction
                            )
                            .border(
                                0.75.dp,
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f * glassSheen),
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f * glassSheen)
                                    )
                                ),
                                CircleShape
                            )
                    )

                    Row(
                        modifier = Modifier
                            .wrapContentWidth()
                            .fillMaxHeight()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FloatingNavTabItem(
                            selected = selectedTab == PetalNavTab.HOME,
                            label = "Home",
                            index = 0,
                            icon = { isSelected, tint ->
                                val iconScale by animateFloatAsState(
                                    targetValue = if (isSelected) 1.15f else 1.0f,
                                    animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
                                    label = "home_scale"
                                )
                                Crossfade(
                                    targetState = isSelected,
                                    animationSpec = tween(180),
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
                                        contentDescription = "Home",
                                        tint = tint,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            },
                            onClick = onHomeClick
                        )

                        FloatingNavTabItem(
                            selected = selectedTab == PetalNavTab.NEW_TAB,
                            label = newTabLabel,
                            index = 1,
                            icon = { isSelected, tint ->
                                val rotationAngle by animateFloatAsState(
                                    targetValue = if (isSelected) 90f else 0f,
                                    animationSpec = spring(dampingRatio = 0.68f, stiffness = 450f),
                                    label = "add_rotation"
                                )
                                val iconScale by animateFloatAsState(
                                    targetValue = if (isSelected) 1.15f else 1.0f,
                                    animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
                                    label = "add_scale"
                                )
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "New Tab",
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
                            onClick = onNewTabClick
                        )

                        FloatingNavTabItem(
                            selected = selectedTab == PetalNavTab.TABS,
                            label = tabsLabel,
                            index = 2,
                            icon = { isSelected, tint ->
                                val iconScale by animateFloatAsState(
                                    targetValue = if (isSelected) 1.12f else 1.0f,
                                    animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
                                    label = "tabs_scale"
                                )
                                TabCountBadge(
                                    color = tint,
                                    count = tabCount,
                                    scale = badgeScale.value * iconScale
                                )
                            },
                            onClick = onTabsClick
                        )

                        FloatingNavTabItem(
                            selected = selectedTab == PetalNavTab.MENU,
                            label = "Menu",
                            index = 3,
                            icon = { isSelected, tint ->
                                val rotationAngle by animateFloatAsState(
                                    targetValue = if (isSelected) 180f else 0f,
                                    animationSpec = spring(dampingRatio = 0.70f, stiffness = 420f),
                                    label = "menu_rotation"
                                )
                                val iconScale by animateFloatAsState(
                                    targetValue = if (isSelected) 1.15f else 1.0f,
                                    animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
                                    label = "menu_scale"
                                )
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "Menu",
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
                            onClick = onMenuClick
                        )
                    }
                }
            } else {
                HorizontalFloatingToolbar(
                    expanded = true,
                    modifier = Modifier
                        .wrapContentWidth()
                        .height(64.dp)
                        .graphicsLayer { translationX = dragOffsetPx }
                        .shadow(16.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
                        .border(
                            0.75.dp,
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                                )
                            ),
                            CircleShape
                        )
                        .clip(CircleShape)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { isDragging = true },
                                onDragEnd = {
                                    isDragging = false
                                    if (dragAccumulator > 70f) {
                                        onSwipeTabLeft()
                                    } else if (dragAccumulator < -70f) {
                                        onSwipeTabRight()
                                    }
                                    dragAccumulator = 0f
                                },
                                onDragCancel = {
                                    isDragging = false
                                    dragAccumulator = 0f
                                },
                                onHorizontalDrag = { _, dragAmount: Float ->
                                    dragAccumulator += dragAmount
                                }
                            )
                        },
                    colors = toolbarColors
                ) {
                    FloatingNavTabItem(
                        selected = selectedTab == PetalNavTab.HOME,
                        label = "Home",
                        index = 0,
                        icon = { isSelected, tint ->
                            val iconScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.15f else 1.0f,
                                animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
                                label = "home_scale"
                            )
                        Crossfade(
                            targetState = isSelected,
                            animationSpec = tween(180),
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
                                contentDescription = "Home",
                                tint = tint,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    onClick = onHomeClick
                )

                FloatingNavTabItem(
                    selected = selectedTab == PetalNavTab.NEW_TAB,
                    label = newTabLabel,
                    index = 1,
                    icon = { isSelected, tint ->
                        val rotationAngle by animateFloatAsState(
                            targetValue = if (isSelected) 90f else 0f,
                            animationSpec = spring(dampingRatio = 0.68f, stiffness = 450f),
                            label = "add_rotation"
                        )
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.15f else 1.0f,
                            animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
                            label = "add_scale"
                        )
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "New Tab",
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
                    onClick = onNewTabClick
                )

                FloatingNavTabItem(
                    selected = selectedTab == PetalNavTab.TABS,
                    label = tabsLabel,
                    index = 2,
                    icon = { isSelected, tint ->
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.12f else 1.0f,
                            animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
                            label = "tabs_scale"
                        )
                        TabCountBadge(
                            color = tint,
                            count = tabCount,
                            scale = badgeScale.value * iconScale
                        )
                    },
                    onClick = onTabsClick
                )

                FloatingNavTabItem(
                    selected = selectedTab == PetalNavTab.MENU,
                    label = "Menu",
                    index = 3,
                    icon = { isSelected, tint ->
                        val rotationAngle by animateFloatAsState(
                            targetValue = if (isSelected) 180f else 0f,
                            animationSpec = spring(dampingRatio = 0.70f, stiffness = 420f),
                            label = "menu_rotation"
                        )
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.15f else 1.0f,
                            animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
                            label = "menu_scale"
                        )
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Menu",
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
                    onClick = onMenuClick
                )
            }
        }
    } else {
        // Material 3 Expressive Non-Floating Bottom Navigation Bar
        Box(
            modifier = modifier
                .graphicsLayer {
                    translationY = (1f - entrance.value) * entranceOffsetPx
                    alpha = entrance.value.coerceIn(0f, 1f)
                }
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentAlignment = Alignment.BottomCenter
        ) {
            val navBarShape = if (isBottomAddressBar) {
                RoundedCornerShape(0.dp)
            } else {
                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            }
            val context = LocalContext.current
            val sp = remember(context) { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
            val isLiquidGlass = sp.getBoolean("sp_liquid_glass_enabled", false)
            val glassAlpha = if (isLiquidGlass) sp.getFloat("sp_liquid_glass_alpha", 0.70f).coerceIn(0.20f, 0.95f) else 1f
            val glassSheen = if (isLiquidGlass) sp.getFloat("sp_liquid_glass_sheen", 0.60f).coerceIn(0f, 1f) else 0.60f
            val glassTint = if (isLiquidGlass) sp.getString("sp_liquid_glass_tint", "FROSTED") ?: "FROSTED" else "FROSTED"

            val baseContainer = when (glassTint) {
                "ACCENT" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = glassAlpha * 0.85f)
                "DEEP" -> MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = glassAlpha)
                else -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = glassAlpha)
            }

            Surface(
                shape = navBarShape,
                color = if (isLiquidGlass) baseContainer else MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = if (isBottomAddressBar) 0.dp else 3.dp,
                shadowElevation = if (isBottomAddressBar) 0.dp else 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .then(
                        if (isLiquidGlass) {
                            Modifier.liquidGlassChrome(navBarShape, true, glassSheen)
                        } else Modifier
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExpressiveNavTabItem(
                        selected = selectedTab == PetalNavTab.HOME,
                        label = "Home",
                        index = 0,
                        modifier = Modifier.weight(1f),
                        icon = { isSelected, tint ->
                            val iconScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.18f else 1.0f,
                                animationSpec = spring(dampingRatio = 0.62f, stiffness = 380f),
                                label = "home_expressive_scale"
                            )
                            Crossfade(
                                targetState = isSelected,
                                animationSpec = tween(180),
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
                                    contentDescription = "Home",
                                    tint = tint,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        },
                        onClick = onHomeClick
                    )

                    ExpressiveNavTabItem(
                        selected = selectedTab == PetalNavTab.NEW_TAB,
                        label = newTabLabel,
                        index = 1,
                        modifier = Modifier.weight(1f),
                        icon = { isSelected, tint ->
                            val rotationAngle by animateFloatAsState(
                                targetValue = if (isSelected) 90f else 0f,
                                animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
                                label = "add_expressive_rotation"
                            )
                            val iconScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.18f else 1.0f,
                                animationSpec = spring(dampingRatio = 0.62f, stiffness = 380f),
                                label = "add_expressive_scale"
                            )
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "New Tab",
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
                        onClick = onNewTabClick
                    )

                    ExpressiveNavTabItem(
                        selected = selectedTab == PetalNavTab.TABS,
                        label = "Tabs",
                        badgeText = if (tabCount > 99) "99+" else tabCount.toString(),
                        index = 2,
                        modifier = Modifier.weight(1f),
                        icon = { isSelected, tint ->
                            val iconScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.15f else 1.0f,
                                animationSpec = spring(dampingRatio = 0.62f, stiffness = 380f),
                                label = "tabs_expressive_scale"
                            )
                            TabCountBadge(
                                color = tint,
                                count = tabCount,
                                scale = badgeScale.value * iconScale
                            )
                        },
                        onClick = onTabsClick
                    )

                    ExpressiveNavTabItem(
                        selected = selectedTab == PetalNavTab.MENU,
                        label = "Menu",
                        index = 3,
                        modifier = Modifier.weight(1f),
                        icon = { isSelected, tint ->
                            val rotationAngle by animateFloatAsState(
                                targetValue = if (isSelected) 180f else 0f,
                                animationSpec = spring(dampingRatio = 0.68f, stiffness = 400f),
                                label = "menu_expressive_rotation"
                            )
                            val iconScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.18f else 1.0f,
                                animationSpec = spring(dampingRatio = 0.62f, stiffness = 380f),
                                label = "menu_expressive_scale"
                            )
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Menu",
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
                        onClick = onMenuClick
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingNavTabItem(
    selected: Boolean,
    label: String,
    index: Int,
    icon: @Composable (isSelected: Boolean, tint: Color) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Bouncy touch feedback press scale
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "press_scale_$index"
    )

    val labelWidth by animateDpAsState(
        targetValue = if (selected) 72.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = 0.76f,
            stiffness = 360f
        ),
        label = "nav_label_$index"
    )

    // Label fades/slides in slightly after the pill starts expanding
    val labelAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (selected) 220 else 90,
            delayMillis = if (selected) 90 else 0
        ),
        label = "nav_label_alpha_$index"
    )

    // Staggered pop-in on first appearance
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 55L)
        enter.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 320f))
    }

    val activeContainerColor = MaterialTheme.colorScheme.primaryContainer
    val activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
    val inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant

    val currentContentColor by animateColorAsState(
        targetValue = if (selected) activeContentColor else inactiveContentColor,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
        label = "nav_color_$index"
    )

    val currentBgColor by animateColorAsState(
        targetValue = if (selected) activeContainerColor else Color.Transparent,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
        label = "nav_bg_$index"
    )

    Surface(
        shape = CircleShape,
        color = currentBgColor,
        modifier = modifier
            .height(48.dp)
            .width(48.dp + labelWidth)
            .graphicsLayer {
                scaleX = pressScale * enter.value.coerceAtLeast(0f)
                scaleY = pressScale * enter.value.coerceAtLeast(0f)
                alpha = enter.value.coerceIn(0f, 1f)
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            )
            .semantics { contentDescription = label }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(horizontal = if (selected) 8.dp else 0.dp)
                .fillMaxHeight()
        ) {
            Box(contentAlignment = Alignment.Center) {
                icon(selected, currentContentColor)
            }

            if (labelWidth > 4.dp) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp
                    ),
                    color = currentContentColor,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.graphicsLayer {
                        alpha = labelAlpha
                        translationX = (1f - labelAlpha) * -8.dp.toPx()
                    }
                )
            }
        }
    }
}

/**
 * Chrome Android style live tab counter badge (bordered box with the current tab
 * count), reused as the icon slot for the Tabs item in both bar styles.
 */
@Composable
private fun TabCountBadge(color: Color, count: Int, scale: Float) {
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
                (slideInVertically(spring(dampingRatio = 0.75f, stiffness = 500f)) { if (up) it else -it } + fadeIn(tween(120)))
                    .togetherWith(
                        slideOutVertically(spring(dampingRatio = 0.75f, stiffness = 500f)) { if (up) -it else it } + fadeOut(tween(90))
                    )
                    .using(SizeTransform(clip = true))
            },
            label = "tab_count_roll"
        ) { value ->
            Text(
                text = if (value > 99) "99+" else value.toString(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
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
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Tactile squash-and-stretch micro-interaction when pressed
    val pressScaleX by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.60f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "expressive_press_scale_x_$index"
    )
    val pressScaleY by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.60f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "expressive_press_scale_y_$index"
    )

    // Active pill background color & indicator lift
    val activeLiftY by animateDpAsState(
        targetValue = if (selected) (-1).dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.70f, stiffness = 400f),
        label = "expressive_lift_$index"
    )

    // Fluid indicator pill geometry: expands from 40dp to 58dp on selection
    val activeIndicatorWidth by animateDpAsState(
        targetValue = if (selected) 58.dp else 40.dp,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 380f
        ),
        label = "expressive_indicator_width_$index"
    )

    val activeIndicatorHeight = 28.dp

    val activeIndicatorColor = MaterialTheme.colorScheme.primaryContainer
    val indicatorBgColor by animateColorAsState(
        targetValue = if (selected) activeIndicatorColor else Color.Transparent,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "expressive_indicator_bg_$index"
    )

    val contentTint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
        label = "expressive_content_tint_$index"
    )

    val labelColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
        label = "expressive_label_color_$index"
    )

    val labelWeight = if (selected) FontWeight.Bold else FontWeight.Medium

    // Staggered pop-in on first appearance
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 55L)
        enter.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 320f))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = pressScaleX * enter.value.coerceAtLeast(0f)
                scaleY = pressScaleY * enter.value.coerceAtLeast(0f)
                alpha = enter.value.coerceIn(0f, 1f)
                translationY = activeLiftY.toPx()
            }
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, radius = 32.dp),
                onClick = onClick
            )
            .padding(horizontal = 2.dp, vertical = 1.dp)
            .semantics { contentDescription = label }
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = indicatorBgColor,
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

        Spacer(Modifier.height(2.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = labelWeight,
                letterSpacing = 0.1.sp,
                fontSize = 10.5.sp
            ),
            color = labelColor,
            maxLines = 1,
            softWrap = false
        )
    }
}
