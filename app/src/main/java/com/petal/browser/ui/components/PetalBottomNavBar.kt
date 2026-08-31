package com.petal.browser.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
 * Modern Floating Navigation Bar with Progressive Frosted-Glass Blur
 * (inspired by bikram-agarwal/FilePipe and bikram-agarwal/Remember).
 * Features:
 * - Floating pill shape with tactile spring indicator
 * - Edge-to-edge progressive blur effect behind navigation bar
 * - Haptic click-feedback ready and fluid scale bounce transitions
 * - Live Tab Count badge with animated badge count
 */
@Composable
fun PetalBottomNavBar(
    selectedTab: PetalNavTab,
    tabCount: Int,
    isIncognito: Boolean = false,
    isFloatingStyle: Boolean = true,
    isProgressiveBlurEnabled: Boolean = true,
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
                .padding(bottom = 20.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Progressive blur underlay (FilePipe / Remember style)
            if (isProgressiveBlurEnabled) {
                ProgressiveBlurBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .align(Alignment.BottomCenter)
                )
            }

            // Floating Navigation Capsule
            val containerColor = if (isIncognito) {
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = if (isProgressiveBlurEnabled) 0.88f else 0.98f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (isProgressiveBlurEnabled) 0.88f else 0.98f)
            }

            val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)

            Surface(
                shape = CircleShape,
                color = containerColor,
                border = BorderStroke(1.dp, borderColor),
                shadowElevation = 8.dp,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .wrapContentWidth()
                    .height(64.dp)
                    .clip(CircleShape)
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .wrapContentWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilePipeFloatingNavItem(
                        selected = selectedTab == PetalNavTab.HOME,
                        label = "Home",
                        icon = { isSelected, tint ->
                            Icon(
                                imageVector = Icons.Rounded.Home,
                                contentDescription = "Home",
                                tint = tint,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        onClick = onHomeClick
                    )

                    FilePipeFloatingNavItem(
                        selected = selectedTab == PetalNavTab.NEW_TAB,
                        label = newTabLabel,
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

                    FilePipeFloatingNavItem(
                        selected = selectedTab == PetalNavTab.TABS,
                        label = tabsLabel,
                        icon = { isSelected, tint ->
                            TabCountBadge(
                                color = tint,
                                count = animatedCount,
                                scale = badgeScale.value
                            )
                        },
                        onClick = onTabsClick
                    )

                    FilePipeFloatingNavItem(
                        selected = selectedTab == PetalNavTab.MENU,
                        label = "Menu",
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
        }
    } else {
        // Flat Bottom Navigation Bar with Optional Progressive Blur
        Box(modifier = modifier.fillMaxWidth()) {
            if (isProgressiveBlurEnabled) {
                ProgressiveBlurBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .align(Alignment.BottomCenter)
                )
            }

            Surface(
                color = if (isIncognito) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
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
                                imageVector = Icons.Rounded.Home,
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
}

/**
 * FilePipe / Remember Style Floating Pill Navigation Item.
 * Smoothly morphs active background capsule and expands label with spring motion.
 */
@Composable
private fun FilePipeFloatingNavItem(
    selected: Boolean,
    label: String,
    icon: @Composable (isSelected: Boolean, tint: Color) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    val indicatorColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "indicatorColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "contentColor"
    )

    Surface(
        shape = CircleShape,
        color = indicatorColor,
        modifier = modifier
            .height(52.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = MaterialTheme.colorScheme.primary),
                onClick = onClick
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(horizontal = if (selected) 16.dp else 12.dp, vertical = 8.dp)
                .animateContentSize(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
        ) {
            Box(contentAlignment = Alignment.Center) {
                icon(selected, contentColor)
            }

            AnimatedVisibility(
                visible = selected,
                enter = fadeIn(tween(180)) + slideInHorizontally(
                    initialOffsetX = { -15 },
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)
                ) + expandHorizontally(expandFrom = Alignment.Start),
                exit = fadeOut(tween(120)) + slideOutHorizontally(
                    targetOffsetX = { -15 }
                ) + shrinkHorizontally(shrinkTowards = Alignment.Start)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp
                    ),
                    color = contentColor,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp, end = 2.dp)
                )
            }
        }
    }
}

/**
 * Progressive Frosted-Glass Blur Layer (inspired by FilePipe and Remember).
 * Leverages RenderEffect / blur on Android 12+ (API 31+) and graded opacity gradient fallback.
 */
@Composable
fun ProgressiveBlurBar(
    modifier: Modifier = Modifier,
    blurRadius: Int = 20
) {
    val isBlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val backgroundColor = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .then(
                if (isBlurSupported) {
                    Modifier.blur(blurRadius.dp)
                } else Modifier
            )
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        backgroundColor.copy(alpha = 0.35f),
                        backgroundColor.copy(alpha = 0.75f),
                        backgroundColor.copy(alpha = 0.95f)
                    )
                )
            )
    )
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
