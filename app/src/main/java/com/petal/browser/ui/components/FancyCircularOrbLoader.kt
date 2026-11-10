package com.petal.browser.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Loading Indicator.
 * Delegates to official [androidx.compose.material3.LoadingIndicator].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FancyCircularOrbLoader(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    strokeWidth: Dp = 4.dp,
) {
    LoadingIndicator(
        modifier = modifier.size(size)
    )
}
