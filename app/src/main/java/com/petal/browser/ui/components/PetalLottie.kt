package com.petal.browser.ui.components

import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.petal.browser.R

/**
 * Reusable Lottie animation wrapper for Petal Browser.
 * Loads a raw resource JSON Lottie file and plays it with optional looping.
 */
@Composable
fun PetalLottieAnimation(
    @RawRes resId: Int,
    modifier: Modifier = Modifier,
    iterations: Int = LottieConstants.IterateForever,
    speed: Float = 1f,
    restartOnPlay: Boolean = false,
    isPlaying: Boolean = true
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(resId))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = iterations,
        speed = speed,
        restartOnPlay = restartOnPlay,
        isPlaying = isPlaying
    )
    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier
    )
}

/** Infinite-loop orbital loading animation. */
@Composable
fun PetalLoadingLottie(modifier: Modifier = Modifier) {
    PetalLottieAnimation(
        resId = R.raw.lottie_loading,
        modifier = modifier,
        iterations = LottieConstants.IterateForever
    )
}

/** Gentle floating empty-tabs illustration (loops). */
@Composable
fun PetalEmptyTabsLottie(modifier: Modifier = Modifier) {
    PetalLottieAnimation(
        resId = R.raw.lottie_empty_tabs,
        modifier = modifier,
        iterations = LottieConstants.IterateForever
    )
}

/** Bouncy shield entrance for welcome screen (plays once). */
@Composable
fun PetalWelcomeLottie(modifier: Modifier = Modifier) {
    PetalLottieAnimation(
        resId = R.raw.lottie_welcome,
        modifier = modifier,
        iterations = 1
    )
}

/** Check success animation (plays once). */
@Composable
fun PetalSuccessLottie(modifier: Modifier = Modifier) {
    PetalLottieAnimation(
        resId = R.raw.lottie_success,
        modifier = modifier,
        iterations = 1
    )
}

/** Pulsing search magnifying glass (loops). */
@Composable
fun PetalSearchLottie(modifier: Modifier = Modifier) {
    PetalLottieAnimation(
        resId = R.raw.lottie_search,
        modifier = modifier,
        iterations = LottieConstants.IterateForever
    )
}
