package com.petal.browser.ui.components

import android.view.View
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.ui.containment.PetalBackdrop
import com.petal.browser.ui.miuix.glass.miuixAcrylicGlass
import com.petal.browser.ui.miuix.theme.MiuiXMotion
import com.petal.browser.ui.miuix.theme.MiuiXShapes
import com.petal.browser.ui.miuix.theme.MiuiXSquircleShape
import com.petal.browser.ui.miuix.theme.MiuiXTheme

/**
 * Xiaomi HyperOS / MIUIX UI4 Signature Floating Glass Navigation Bar.
 *
 * Implements the flagship floating pill navigation bar featuring:
 * - Continuous squircle pill shape (`smoothing = 0.68f`)
 * - Acrylic refractive blur material with 0.5dp specular border
 * - Xiaomi spring physics on tab interaction and swipe damping
 * - Squircle tab count badge
 */
@Composable
internal fun PetalMiuixNavBar(
    selectedTab: PetalNavTab,
    tabCount: Int,
    badgeScale: Float,
    backdrop: PetalBackdrop? = null,
    onHomeClick: () -> Unit,
    onNewTabClick: () -> Unit,
    onTabsClick: () -> Unit,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSwipeTabLeft: () -> Unit = {},
    onSwipeTabRight: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = MiuiXTheme.colors
    val pillShape = MiuiXSquircleShape(9999.dp, smoothing = 0.68f)

    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val maxDragPx = with(LocalDensity.current) { 24.dp.toPx() }
    val dragOffsetPx by animateFloatAsState(
        targetValue = if (isDragging) (dragAccumulator * 0.32f).coerceIn(-maxDragPx, maxDragPx) else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
        label = "miuix_nav_drag"
    )

    Box(
        modifier = modifier
            .graphicsLayer { translationX = dragOffsetPx }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = {
                        isDragging = false
                        if (dragAccumulator > 60f) onSwipeTabLeft()
                        else if (dragAccumulator < -60f) onSwipeTabRight()
                        dragAccumulator = 0f
                    },
                    onDragCancel = {
                        isDragging = false
                        dragAccumulator = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumulator += dragAmount
                    }
                )
            }
            .miuixAcrylicGlass(
                shape = pillShape,
                isDark = colors.isDark,
                backdrop = backdrop,
                enabled = true,
                drawBorder = true
            )
            .height(58.dp)
            .padding(horizontal = 6.dp, vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiuixNavTabItem(
                icon = Icons.Rounded.Home,
                label = "Home",
                selected = selectedTab == PetalNavTab.HOME,
                onClick = onHomeClick
            )

            MiuixNavTabItem(
                icon = Icons.Rounded.Search,
                label = "Search",
                selected = false,
                onClick = onSearchClick
            )

            MiuixNavTabItem(
                icon = Icons.Rounded.Add,
                label = "New",
                selected = false,
                onClick = onNewTabClick
            )

            MiuixNavTabsCounterItem(
                count = tabCount,
                badgeScale = badgeScale,
                selected = selectedTab == PetalNavTab.TABS,
                onClick = onTabsClick
            )

            MiuixNavTabItem(
                icon = Icons.Rounded.MoreVert,
                label = "Menu",
                selected = false,
                onClick = onMenuClick
            )
        }
    }
}

@Composable
private fun MiuixNavTabItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuiXTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) MiuiXMotion.PRESS_SCALE else 1f,
        animationSpec = MiuiXMotion.standard(),
        label = "miuix_nav_item_press"
    )

    val itemPill = MiuiXSquircleShape(9999.dp, smoothing = 0.65f)
    val indicatorColor = if (selected) colors.primaryContainer else Color.Transparent
    val iconTint = if (selected) colors.primary else colors.onSurfaceVariant

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(itemPill)
            .background(indicatorColor, itemPill)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun MiuixNavTabsCounterItem(
    count: Int,
    badgeScale: Float,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuiXTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) MiuiXMotion.PRESS_SCALE else 1f,
        animationSpec = MiuiXMotion.standard(),
        label = "miuix_tabs_badge_press"
    )

    val itemPill = MiuiXSquircleShape(9999.dp, smoothing = 0.65f)
    val badgeShape = MiuiXSquircleShape(7.dp, smoothing = 0.68f)
    val indicatorColor = if (selected) colors.primaryContainer else Color.Transparent
    val contentTint = if (selected) colors.primary else colors.onSurfaceVariant

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(itemPill)
            .background(indicatorColor, itemPill)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    scaleX = badgeScale
                    scaleY = badgeScale
                }
                .border(BorderStroke(1.8.dp, contentTint), badgeShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (count > 99) "99+" else count.toString(),
                color = contentTint,
                fontWeight = FontWeight.Bold,
                fontSize = if (count > 99) 9.sp else 11.sp,
                lineHeight = 11.sp
            )
        }
    }
}
