package com.petal.browser.ui.components;

import androidx.compose.animation.core.Spring;
import androidx.compose.animation.core.animateFloatAsState;
import androidx.compose.animation.core.spring;
import androidx.compose.foundation.background;
import androidx.compose.foundation.layout.*;
import androidx.compose.material3.MaterialTheme;
import androidx.compose.runtime.Composable;
import androidx.compose.runtime.getValue;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.graphics.Color;
import androidx.compose.ui.unit.dp;

/**
 * ExpressiveReadingProgressPill renders a slim, spring-animated reading progress bar for web pages.
 */
@Composable
fun ExpressiveReadingProgressPill(
    progressFraction: Float,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "readingProgress"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedProgress)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}
