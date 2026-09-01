package com.petal.browser.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
// Modern Floating Bottom Navigation Bar (Remember & FilePipe style HorizontalFloatingToolbar)
// -------------------------------------------------------------------------------------------------

/**
 * Modern Floating Navigation Bar with Material 3 Expressive HorizontalFloatingToolbar.
 *
 * Architecture & Theming:
 * - Toolbar container uses vibrant primary container theme (`colorScheme.primary` container & `colorScheme.onPrimary` content)
 * - Active tab item fills with background color and primary tinted icon & bold label
 * - Inactive tab item stays lightweight
 * - Tactile spring-animated label width expansion (`72.dp` target on active selection)
 * - Live Tab Count badge with animated scale bounce
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

    val tabsLabel = if (isIncognito) "Incognito ($animatedCount)" else "Tabs ($animatedCount)"
    val newTabLabel = if (isIncognito) "Incognito" else "New"

    if (isFloatingStyle) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {

            // Material 3 Expressive Floating Toolbar (matching Remember & FilePipe vibrant colors)
            val toolbarColors = if (isIncognito) {
                FloatingToolbarDefaults.vibrantFloatingToolbarColors(
                    toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    toolbarContentColor = MaterialTheme.colorScheme.onSurface
                )
            } else {
                FloatingToolbarDefaults.vibrantFloatingToolbarColors(
                    toolbarContainerColor = MaterialTheme.colorScheme.primary,
                    toolbarContentColor = MaterialTheme.colorScheme.onPrimary
                )
            }

            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier
                    .wrapContentWidth()
                    .height(64.dp)
                    .shadow(10.dp, CircleShape)
                    .clip(CircleShape),
                colors = toolbarColors
            ) {
                FloatingNavTabItem(
                    selected = selectedTab == PetalNavTab.HOME,
                    label = "Home",
                    index = 0,
                    isIncognito = isIncognito,
                    icon = { isSelected, tint ->
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(if (isSelected) com.petal.browser.R.drawable.home_filled else com.petal.browser.R.drawable.home),
                            contentDescription = "Home",
                            tint = tint,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    onClick = onHomeClick
                )

                FloatingNavTabItem(
                    selected = selectedTab == PetalNavTab.NEW_TAB,
                    label = newTabLabel,
                    index = 1,
                    isIncognito = isIncognito,
                    icon = { isSelected, tint ->
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "New Tab",
                            tint = tint,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    onClick = onNewTabClick
                )

                FloatingNavTabItem(
                    selected = selectedTab == PetalNavTab.TABS,
                    label = tabsLabel,
                    index = 2,
                    isIncognito = isIncognito,
                    icon = { isSelected, tint ->
                        TabCountBadge(
                            color = tint,
                            count = animatedCount,
                            scale = badgeScale.value
                        )
                    },
                    onClick = onTabsClick
                )

                FloatingNavTabItem(
                    selected = selectedTab == PetalNavTab.MENU,
                    label = "Menu",
                    index = 3,
                    isIncognito = isIncognito,
                    icon = { isSelected, tint ->
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Menu",
                            tint = tint,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    onClick = onMenuClick
                )
            }
        }
    } else {
        // Flat Bottom Navigation Bar
        Surface(
            color = if (isIncognito) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer,
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
    isIncognito: Boolean,
    icon: @Composable (isSelected: Boolean, tint: Color) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val labelWidth by animateDpAsState(
        targetValue = if (selected) 72.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = 380f
        ),
        label = "nav_label_$index"
    )

    val activeContainerColor = if (isIncognito) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.background
    }

    val activeContentColor = if (isIncognito) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary
    }

    val inactiveContentColor = if (isIncognito) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onPrimary
    }

    val currentContentColor by animateColorAsState(
        targetValue = if (selected) activeContentColor else inactiveContentColor,
        animationSpec = tween(durationMillis = 200),
        label = "nav_color_$index"
    )

    val currentBgColor by animateColorAsState(
        targetValue = if (selected) activeContainerColor else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "nav_bg_$index"
    )

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = currentBgColor,
        modifier = modifier
            .height(48.dp)
            .width(48.dp + labelWidth)
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

