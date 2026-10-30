package com.petal.browser.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.petal.browser.R
import com.petal.browser.ui.containment.PetalFilterChip
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor

/**
 * ExpressiveQuickActionsPill renders a floating quick-settings bottom pill bar for web toggles
 * with authentic liquid-glass refraction, single-rim chips, and haptic feedback.
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
    val pillShape = RoundedCornerShape(28.dp)
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_bottom_nav") || isLiquidGlassEnabled("sp_liquid_glass_containments")
    val sheen = com.petal.browser.ui.glass.rememberGlassSheen(isGlass)

    Surface(
        shape = pillShape,
        color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = if (isGlass) 0.dp else 8.dp,
        tonalElevation = if (isGlass) 0.dp else 3.dp,
        border = if (isGlass) BorderStroke(0.75.dp, Color.White.copy(alpha = 0.22f)) else null,
        modifier = modifier
            .height(56.dp)
            .then(if (isGlass) Modifier.liquidGlassChrome(pillShape, enabled = true, sheenIntensity = sheen) else Modifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            PetalFilterChip(
                selected = isDesktopMode,
                onClick = onToggleDesktopMode,
                label = { Text(stringResource(R.string.ui_desktop)) },
                leadingIcon = { Icon(Icons.Rounded.Computer, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )

            PetalFilterChip(
                selected = isAdBlock,
                onClick = onToggleAdBlock,
                label = { Text(stringResource(R.string.ui_adblock)) },
                leadingIcon = { Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )

            PetalFilterChip(
                selected = isDarkMode,
                onClick = onToggleDarkMode,
                label = { Text(stringResource(R.string.ui_dark)) },
                leadingIcon = { Icon(Icons.Rounded.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )

            PetalLiquidIconButton(
                onClick = onTranslate,
                size = 36.dp,
                contentDescription = stringResource(R.string.ui_translate)
            ) {
                Icon(Icons.Rounded.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}
