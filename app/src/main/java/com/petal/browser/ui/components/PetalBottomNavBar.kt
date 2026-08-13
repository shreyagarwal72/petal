package com.petal.browser.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
 * Expressive Stride Floating Depth Bottom Navigation Bar.
 * Order:
 * 1st: Home Page button
 * 2nd: (+) New Tab button
 * 3rd: Chrome Android Live Tab Counter & Switcher Badge ([1], [2], [3])
 * 4th: Menu / Options button
 */
@Composable
fun PetalBottomNavBar(
    selectedTab: PetalNavTab,
    tabCount: Int,
    onHomeClick: () -> Unit,
    onNewTabClick: () -> Unit,
    onTabsClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(36.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .clip(RoundedCornerShape(36.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .entrance()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 1st Item: Home
            NavItemPill(
                selected = selectedTab == PetalNavTab.HOME,
                label = "Home",
                onClick = onHomeClick
            ) { color ->
                Icon(
                    imageVector = Icons.Rounded.Home,
                    contentDescription = "Home",
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }

            // 2nd Item: (+) New Tab
            NavItemPill(
                selected = selectedTab == PetalNavTab.NEW_TAB,
                label = "New",
                onClick = onNewTabClick
            ) { color ->
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "New Tab",
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }

            // 3rd Item: Live Tab Switcher (Chrome Android Style Badge)
            NavItemPill(
                selected = selectedTab == PetalNavTab.TABS,
                label = "Tabs ($tabCount)",
                onClick = onTabsClick
            ) { color ->
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .border(
                            width = 2.dp,
                            color = color,
                            shape = RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (tabCount > 99) "99+" else tabCount.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = color,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 4th Item: Menu / Options
            NavItemPill(
                selected = selectedTab == PetalNavTab.MENU,
                label = "Menu",
                onClick = onMenuClick
            ) { color ->
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Menu",
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun NavItemPill(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    iconContent: @Composable (Color) -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 220f
        ),
        label = "navPillScale"
    )

    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent

    Surface(
        shape = CircleShape,
        color = containerColor,
        modifier = Modifier
            .bouncyClickable(scaleDown = 0.88f, onClick = onClick)
            .clip(CircleShape)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (selected) 16.dp else 10.dp,
                vertical = 10.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
            ) {
                iconContent(contentColor)
            }

            AnimatedVisibility(
                visible = selected,
                enter = expandHorizontally() + fadeIn(),
                exit = shrinkHorizontally() + fadeOut(),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = contentColor,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
