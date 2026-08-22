/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN CONTRACT/TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.petal.browser.predictive

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

val M3EmphasizedEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
const val AOSP_TRANSITION_DURATION = 350

/**
 * Forward push enter transition.
 * Incoming target screen slides in from 1/3 screen-right while fading in with M3EmphasizedEasing.
 */
fun AnimatedContentTransitionScope<NavBackStackEntry>.aospSharedAxisEnter(): EnterTransition {
    return slideInHorizontally(
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing),
        initialOffsetX = { fullWidth -> fullWidth / 3 }
    ) + fadeIn(
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing)
    )
}

/**
 * Forward push exit transition.
 * Outgoing target screen slides left 1/3 screen while scaling down to 0.92 with M3EmphasizedEasing.
 */
fun AnimatedContentTransitionScope<NavBackStackEntry>.aospSharedAxisExit(): ExitTransition {
    return slideOutHorizontally(
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing),
        targetOffsetX = { fullWidth -> -fullWidth / 3 }
    ) + scaleOut(
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing),
        targetScale = 0.92f
    )
}

/**
 * Back pop enter transition.
 * Previous target screen slides in from 1/3-left while scaling up from 0.92 (or reversing previous exit) with M3EmphasizedEasing.
 */
fun AnimatedContentTransitionScope<NavBackStackEntry>.aospSharedAxisPopEnter(): EnterTransition {
    return slideInHorizontally(
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing),
        initialOffsetX = { fullWidth -> -fullWidth / 3 }
    ) + scaleIn(
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing),
        initialScale = 0.92f
    ) + fadeIn(
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = M3EmphasizedEasing)
    )
}

/**
 * Back pop exit transition.
 * Top screen slides fully off to the right with cubic ease-in and scales down to 0.85.
 */
fun AnimatedContentTransitionScope<NavBackStackEntry>.aospSharedAxisPopExit(): ExitTransition {
    return slideOutHorizontally(
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)),
        targetOffsetX = { fullWidth -> fullWidth }
    ) + scaleOut(
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)),
        targetScale = 0.85f
    ) + fadeOut(
        animationSpec = tween(durationMillis = AOSP_TRANSITION_DURATION, easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f))
    )
}
