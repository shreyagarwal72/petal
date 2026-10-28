package com.petal.browser.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor

/**
 * ExpressiveToastPill renders floating toast notifications with slide-up spring entrance
 * animations and live liquid glass refraction.
 */
@Composable
fun ExpressiveToastPill(
    message: String,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val toastShape = RoundedCornerShape(28.dp)
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")

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
            shape = toastShape,
            color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = if (isGlass) 0.dp else 6.dp,
            tonalElevation = if (isGlass) 0.dp else 4.dp,
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .then(if (isGlass) Modifier.liquidGlassChrome(toastShape, enabled = true, sheenIntensity = 0.60f) else Modifier)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                )
            }
        }
    }
}
