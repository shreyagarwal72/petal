package com.petal.browser.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class PetalNavTab {
    HOME, NEW_TAB, TABS, MENU
}

/**
 * Bottom navigation bar, imported from Zenith's dual-style MainScreen bottom bar
 * (HorizontalFloatingToolbar + ShortNavigationBarItem for the floating pill style,
 * plain NavigationBar + NavigationBarItem for the flat style), wired up to Petal's
 * existing four actions and tab-count badge.
 * Order:
 * 1st: Home Page button
 * 2nd: (+) New Tab button
 * 3rd: Chrome Android Live Tab Counter & Switcher Badge ([1], [2], [3])
 * 4th: Menu / Options button
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
    // Chrome Android style live tab counter badge, shared by both bar styles.
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
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier
                    .animateContentSize()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                    toolbarContainerColor = if (isIncognito) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                FloatingNavItem(
                    selected = selectedTab == PetalNavTab.HOME,
                    label = "Home",
                    onClick = onHomeClick
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Home,
                        contentDescription = "Home",
                        modifier = Modifier.size(24.dp)
                    )
                }

                FloatingNavItem(
                    selected = selectedTab == PetalNavTab.NEW_TAB,
                    label = newTabLabel,
                    onClick = onNewTabClick
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "New Tab",
                        modifier = Modifier.size(24.dp)
                    )
                }

                FloatingNavItem(
                    selected = selectedTab == PetalNavTab.TABS,
                    label = tabsLabel,
                    onClick = onTabsClick
                ) { color ->
                    TabCountBadge(color = color, count = animatedCount, scale = badgeScale.value)
                }

                FloatingNavItem(
                    selected = selectedTab == PetalNavTab.MENU,
                    label = "Menu",
                    onClick = onMenuClick
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Menu",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    } else {
        NavigationBar(
            containerColor = if (isIncognito) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface,
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            NavigationBarItem(
                icon = { Icon(imageVector = Icons.Rounded.Home, contentDescription = "Home", modifier = Modifier.size(24.dp)) },
                label = { Text("Home", style = MaterialTheme.typography.labelMedium) },
                selected = selectedTab == PetalNavTab.HOME,
                onClick = onHomeClick
            )
            NavigationBarItem(
                icon = { Icon(imageVector = Icons.Rounded.Add, contentDescription = "New Tab", modifier = Modifier.size(24.dp)) },
                label = { Text(newTabLabel, style = MaterialTheme.typography.labelMedium) },
                selected = selectedTab == PetalNavTab.NEW_TAB,
                onClick = onNewTabClick
            )
            NavigationBarItem(
                icon = {
                    TabCountBadge(
                        color = if (selectedTab == PetalNavTab.TABS) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        count = animatedCount,
                        scale = badgeScale.value
                    )
                },
                label = { Text(tabsLabel, style = MaterialTheme.typography.labelMedium) },
                selected = selectedTab == PetalNavTab.TABS,
                onClick = onTabsClick
            )
            NavigationBarItem(
                icon = { Icon(imageVector = Icons.Rounded.MoreVert, contentDescription = "Menu", modifier = Modifier.size(24.dp)) },
                label = { Text("Menu", style = MaterialTheme.typography.labelMedium) },
                selected = selectedTab == PetalNavTab.MENU,
                onClick = onMenuClick
            )
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
            .size(22.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(
                width = 2.dp,
                color = color,
                shape = RoundedCornerShape(6.dp)
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

/**
 * Single item inside the floating toolbar: aligned icon and animated label.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FloatingNavItem(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit
) {
    ShortNavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                icon(if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer)
            }
        },
        label = {
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn() + slideInHorizontally(
                    initialOffsetX = { -15 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + expandHorizontally(expandFrom = Alignment.Start),
                exit = fadeOut() + slideOutHorizontally(
                    targetOffsetX = { -15 }
                ) + shrinkHorizontally(shrinkTowards = Alignment.Start)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    modifier = Modifier.padding(start = 2.dp, end = 4.dp)
                )
            }
        },
        iconPosition = NavigationItemIconPosition.Start,
        colors = ShortNavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
            selectedTextColor = MaterialTheme.colorScheme.onPrimary,
            selectedIndicatorColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            unselectedTextColor = Color.Transparent
        ),
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .fillMaxHeight()
    )
}
