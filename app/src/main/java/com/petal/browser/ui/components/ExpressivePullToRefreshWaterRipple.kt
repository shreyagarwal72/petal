package com.petal.browser.ui.components;

import androidx.compose.animation.core.Spring;
import androidx.compose.animation.core.animateFloatAsState;
import androidx.compose.animation.core.spring;
import androidx.compose.foundation.background;
import androidx.compose.foundation.layout.*;
import androidx.compose.foundation.shape.CircleShape;
import androidx.compose.material3.MaterialTheme;
import androidx.compose.runtime.Composable;
import androidx.compose.runtime.getValue;
import androidx.compose.ui.Alignment;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.draw.clip;
import androidx.compose.ui.graphics.Color;
import androidx.compose.ui.graphics.graphicsLayer;
import androidx.compose.ui.unit.dp;

/**
 * ExpressivePullToRefreshWaterRipple renders an elastic M3 Expressive water-ripple animation.
 */
@Composable
fun ExpressivePullToRefreshWaterRipple(
    isRefreshing: Boolean,
    pullFraction: Float,
    modifier: Modifier = Modifier
) {
    val alphaAnim by animateFloatAsState(
        targetValue = if (isRefreshing || pullFraction > 0.1f) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "waterRippleAlpha"
    )

    val scaleAnim by animateFloatAsState(
        targetValue = if (isRefreshing) 1.2f else Math.min(1.0f, pullFraction),
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "waterRippleScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .padding(top = 12.dp)
            .graphicsLayer {
                alpha = alphaAnim
                scaleX = scaleAnim
                scaleY = scaleAnim
            }
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f))
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            if (isRefreshing) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.5.dp
                )
            } else {
                androidx.compose.material3.CircularProgressIndicator(
                    progress = { pullFraction.coerceIn(0f, 1f) },
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.5.dp
                )
            }
        }
    }
}
