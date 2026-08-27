package com.petal.browser.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController

/**
 * Thin content container for screens hosted outside the predictive-back surfaces in
 * `com.petal.browser.predictive` (currently just the Settings overview/category split, which
 * isn't driven by a NavController back stack). Predictive-back visuals - slide + scale, ported
 * from RvSystem-Monitor's aospSharedAxisPopExit - live in `PetalScreenWrapper`
 * (predictive/PetalPredictiveJunction.kt); this wrapper no longer applies any of its own, since
 * RvSystem-Monitor's equivalent (Navigation3's plain `NavDisplay`) doesn't either.
 */
@UnstableApi
@Composable
fun ScreenWrapper(
    navController: NavController? = null,
    modifier: Modifier = Modifier,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        content()
    }
}
