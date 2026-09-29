package com.petal.browser.ui.components;

import androidx.compose.animation.AnimatedVisibility;
import androidx.compose.animation.core.Spring;
import androidx.compose.animation.core.spring;
import androidx.compose.animation.slideInVertically;
import androidx.compose.animation.slideOutVertically;
import androidx.compose.foundation.background;
import androidx.compose.foundation.clickable;
import androidx.compose.foundation.layout.*;
import androidx.compose.foundation.shape.RoundedCornerShape;
import androidx.compose.material.icons.Icons;
import androidx.compose.material.icons.rounded.*;
import androidx.compose.material3.*;
import androidx.compose.runtime.*;
import androidx.compose.ui.Alignment;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.draw.clip;
import androidx.compose.ui.graphics.Color;
import androidx.compose.ui.unit.dp;

/**
 * ExpressiveQuickActionsPill renders a floating quick-settings bottom pill bar for web toggles.
 */
@Composable
fun ExpressiveQuickActionsPill(
    isDesktopMode: Boolean,
    isAdBlock: Boolean,
    isDarkMode: Boolean,
    onToggleDesktopMode: () -> Unit,
    onToggleAdBlock: () -> Unit,
    onToggleDarkMode: () -> Unit,
    onTranslate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        shadowElevation = 8.dp,
        modifier = modifier.height(56.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            FilterChip(
                selected = isDesktopMode,
                onClick = onToggleDesktopMode,
                label = { Text("Desktop") },
                leadingIcon = { Icon(Icons.Rounded.Computer, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )

            FilterChip(
                selected = isAdBlock,
                onClick = onToggleAdBlock,
                label = { Text("AdBlock") },
                leadingIcon = { Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )

            FilterChip(
                selected = isDarkMode,
                onClick = onToggleDarkMode,
                label = { Text("Dark") },
                leadingIcon = { Icon(Icons.Rounded.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )

            IconButton(onClick = onTranslate) {
                Icon(Icons.Rounded.Translate, contentDescription = "Translate")
            }
        }
    }
}
