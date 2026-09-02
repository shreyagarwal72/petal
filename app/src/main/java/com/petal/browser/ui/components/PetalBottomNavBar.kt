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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.ui.theme.ExperimentalMaterial3ExpressiveApi

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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PetalBottomNavBar(
    selectedTab: PetalNavTab,
    tabCount: Int,
    isIncognito: Boolean = false,
    isFloatingStyle: Boolean = true,
    onHomeClick: () -> Unit,
    onNewTabClick: () -> Unit,
    onTabsClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animatedCount by animateIntAsState(
        targetValue = tabCount,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tabCountAnimation"
    )

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

    val tabsLabel = "Tabs ($animatedCount)"
    val newTabLabel = "New"

    if (isFloatingStyle) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Material 3 Expressive Floating Toolbar with vibrant primary colors for both normal and incognito
            val toolbarColors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(
                toolbarContainerColor = MaterialTheme.colorScheme.primary,
                toolbarContentColor = MaterialTheme.colorScheme.onPrimary
            )

            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier
                    .wrapContentWidth()
                    .height(64.dp)
                    .shadow(12.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                    .clip(CircleShape),
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
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(if (isSelected) com.petal.browser.R.drawable.home_filled else com.petal.browser.R.drawable.home),
                            contentDescription = "Home",
                            tint = tint,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                        )
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
                            count = animatedCount,
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
        // Flat Bottom Navigation Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            shadowElevation = 4.dp,
            modifier = modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onHomeClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(if (selectedTab == PetalNavTab.HOME) com.petal.browser.R.drawable.home_filled else com.petal.browser.R.drawable.home),
                            contentDescription = "Home",
                            tint = if (selectedTab == PetalNavTab.HOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = onNewTabClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "New Tab",
                            tint = if (selectedTab == PetalNavTab.NEW_TAB) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = onTabsClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        TabCountBadge(
                            color = if (selectedTab == PetalNavTab.TABS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            count = animatedCount,
                            scale = badgeScale.value
                        )
                    }

                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Menu",
                            tint = if (selectedTab == PetalNavTab.MENU) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
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

    val activeContainerColor = MaterialTheme.colorScheme.background
    val activeContentColor = MaterialTheme.colorScheme.primary
    val inactiveContentColor = MaterialTheme.colorScheme.onPrimary

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
        onClick = onClick,
        interactionSource = interactionSource,
        shape = CircleShape,
        color = currentBgColor,
        modifier = modifier
            .height(48.dp)
            .width(48.dp + labelWidth)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(CircleShape)
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
                    softWrap = false
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
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
            ),
            color = color,
            textAlign = TextAlign.Center
        )
    }
}
