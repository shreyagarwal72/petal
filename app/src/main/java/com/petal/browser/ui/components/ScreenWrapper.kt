package com.petal.browser.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.petal.browser.predictive.PetalDepthScreenWrapper

@Composable
fun ScreenWrapper(
    navController: NavController? = null,
    modifier: Modifier = Modifier,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    content: @Composable () -> Unit
) {
    PetalDepthScreenWrapper(
        navController = navController,
        modifier = modifier,
        animatedVisibilityScope = animatedVisibilityScope,
        content = content
    )
}

