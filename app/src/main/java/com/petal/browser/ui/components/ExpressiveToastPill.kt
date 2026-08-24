package com.petal.browser.ui.components;

import androidx.compose.animation.AnimatedVisibility;
import androidx.compose.animation.core.Spring;
import androidx.compose.animation.core.spring;
import androidx.compose.animation.slideInVertically;
import androidx.compose.animation.slideOutVertically;
import androidx.compose.foundation.background;
import androidx.compose.foundation.layout.*;
import androidx.compose.foundation.shape.RoundedCornerShape;
import androidx.compose.material3.MaterialTheme;
import androidx.compose.material3.Surface;
import androidx.compose.material3.Text;
import androidx.compose.runtime.Composable;
import androidx.compose.ui.Alignment;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.draw.clip;
import androidx.compose.ui.graphics.Color;
import androidx.compose.ui.text.font.FontWeight;
import androidx.compose.ui.unit.dp;
import androidx.compose.ui.unit.sp;

/**
 * ExpressiveToastPill renders floating toast notifications with slide-up spring entrance animations.
 */
@Composable
fun ExpressiveToastPill(
    message: String,
    isVisible: Boolean,
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
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 6.dp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
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
