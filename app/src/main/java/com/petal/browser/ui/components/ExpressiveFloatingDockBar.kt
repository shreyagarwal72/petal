package com.petal.browser.ui.components;

import androidx.compose.animation.AnimatedVisibility;
import androidx.compose.animation.core.Spring;
import androidx.compose.animation.core.spring;
import androidx.compose.animation.slideInVertically;
import androidx.compose.animation.slideOutVertically;
import androidx.compose.foundation.background;
import androidx.compose.foundation.clickable;
import androidx.compose.foundation.layout.*;
import androidx.compose.foundation.shape.CircleShape;
import androidx.compose.foundation.shape.RoundedCornerShape;
import androidx.compose.material.icons.Icons;
import androidx.compose.material.icons.rounded.*;
import androidx.compose.material3.*;
import androidx.compose.runtime.Composable;
import androidx.compose.ui.Alignment;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.draw.clip;
import androidx.compose.ui.graphics.Color;
import androidx.compose.ui.unit.dp;

/**
 * ExpressiveFloatingDockBar renders a floating bottom dock bar for primary navigation.
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
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
            shadowElevation = 8.dp,
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .height(60.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                }
                IconButton(onClick = onForward) {
                    Icon(Icons.Rounded.ArrowForward, contentDescription = "Forward")
                }
                IconButton(onClick = onHome) {
                    Icon(Icons.Rounded.Home, contentDescription = "Home")
                }
                IconButton(onClick = onTabs) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Layers, contentDescription = "Tabs")
                        AnimatedCounterBadge(count = tabCount, modifier = Modifier.padding(start = 14.dp, bottom = 14.dp))
                    }
                }
                IconButton(onClick = onMenu) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Menu")
                }
            }
        }
    }
}
