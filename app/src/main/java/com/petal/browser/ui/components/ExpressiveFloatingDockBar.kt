package com.petal.browser.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.petal.browser.R
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor

/**
 * ExpressiveFloatingDockBar renders a floating bottom dock bar for primary navigation
 * supporting real-time liquid glass refraction and spring motion.
 */
@Composable
fun ExpressiveFloatingDockBar(
    isVisible: Boolean,
    tabCount: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dockShape = RoundedCornerShape(32.dp)
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_bottom_nav") || isLiquidGlassEnabled("sp_liquid_glass_containments")

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { height -> height },
            animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy)
        ),
        exit = slideOutVertically(
            targetOffsetY = { height -> height },
            animationSpec = spring(stiffness = Spring.StiffnessLow)
        ),
        modifier = modifier
    ) {
        Surface(
            shape = dockShape,
            color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = if (isGlass) 0.dp else 8.dp,
            tonalElevation = if (isGlass) 0.dp else 4.dp,
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .height(60.dp)
                .then(if (isGlass) Modifier.liquidGlassChrome(dockShape, enabled = true, sheenIntensity = 0.65f) else Modifier)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                PetalLiquidIconButton(onClick = onBack, contentDescription = stringResource(R.string.ui_back)) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = null)
                }
                PetalLiquidIconButton(onClick = onForward, contentDescription = stringResource(R.string.ui_forward)) {
                    Icon(Icons.Rounded.ArrowForward, contentDescription = null)
                }
                PetalLiquidIconButton(onClick = onHome, contentDescription = stringResource(R.string.ui_home)) {
                    Icon(Icons.Rounded.Home, contentDescription = null)
                }
                PetalLiquidIconButton(onClick = onTabs, contentDescription = stringResource(R.string.ui_tabs)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Layers, contentDescription = null)
                        AnimatedCounterBadge(count = tabCount, modifier = Modifier.padding(start = 14.dp, bottom = 14.dp))
                    }
                }
                PetalLiquidIconButton(onClick = onMenu, contentDescription = stringResource(R.string.ui_menu)) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = null)
                }
            }
        }
    }
}
