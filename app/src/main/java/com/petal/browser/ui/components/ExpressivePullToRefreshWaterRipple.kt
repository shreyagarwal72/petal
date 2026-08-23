package com.petal.browser.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * ExpressivePullToRefreshWaterRipple renders an elastic M3 Expressive water-ripple animation.
 */
@Composable
fun ExpressivePullToRefreshWaterRipple(
    isRefreshing: Boolean,
    pullFraction: Float,
    modifier: Modifier = Modifier
) {
    val isVisible = isRefreshing || pullFraction > 0.01f

    val alphaAnim by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "waterRippleAlpha"
    )

    val scaleAnim by animateFloatAsState(
        targetValue = if (isRefreshing) 1.15f else (0.4f + (pullFraction.coerceIn(0f, 1f) * 0.6f)),
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "waterRippleScale"
    )

    val offsetY = if (isRefreshing) 16.dp else (pullFraction.coerceIn(0f, 1f) * 48.dp.value).dp

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 10.dp,
            shadowElevation = 10.dp,
            modifier = Modifier
                .graphicsLayer {
                    alpha = alphaAnim
                    scaleX = scaleAnim
                    scaleY = scaleAnim
                    translationY = offsetY.toPx()
                }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .padding(4.dp)
            ) {
                // Outer ripple ring
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f))
                )

                // Inner core with icon or progress indicator
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        val rotationAnim by animateFloatAsState(
                            targetValue = if (pullFraction >= 0.75f) 180f else 0f,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "arrowRotation"
                        )
                        Icon(
                            imageVector = Icons.Rounded.ArrowDownward,
                            contentDescription = "Pull to refresh",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer {
                                    rotationZ = rotationAnim
                                }
                        )
                    }
                }
            }
        }
    }
}

