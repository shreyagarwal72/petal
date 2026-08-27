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
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT/TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.petal.browser.predictive

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/**
 * Ported 1:1 from RvSystem-Monitor & PixelPlayer's `ui/navigation/Transitions.kt`.
 */

// Material 3 "emphasized" easing - cubic-bezier(0.2, 0, 0, 1.0), fast start / smooth settle.
val PetalM3EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

// AOSP Transition duration millis.
const val PETAL_TRANSITION_DURATION = 350

// Pop exit slide easing: cubic slide curve f * f * f.
val PetalPopExitSlideEasing: Easing = Easing { f -> f * f * f }

// Pop exit target scale delta (0.85f final scale).
const val PETAL_POP_EXIT_MAX_SCALE_DELTA = 0.15f

// Ported 1:1 from RvSystem-Monitor & PixelPlayer
fun aospSharedAxisEnter(): EnterTransition {
    return slideInHorizontally(
        initialOffsetX = { it / 3 },
        animationSpec = tween(durationMillis = PETAL_TRANSITION_DURATION, easing = PetalM3EmphasizedEasing)
    ) + fadeIn(
        animationSpec = tween(durationMillis = PETAL_TRANSITION_DURATION, easing = PetalM3EmphasizedEasing)
    )
}

fun aospSharedAxisExit(): ExitTransition {
    return slideOutHorizontally(
        targetOffsetX = { -it / 3 },
        animationSpec = tween(durationMillis = PETAL_TRANSITION_DURATION, easing = PetalM3EmphasizedEasing)
    ) + scaleOut(
        targetScale = 0.92f,
        animationSpec = tween(durationMillis = PETAL_TRANSITION_DURATION, easing = PetalM3EmphasizedEasing)
    )
}

fun aospSharedAxisPopEnter(): EnterTransition {
    return slideInHorizontally(
        initialOffsetX = { -it / 3 },
        animationSpec = tween(durationMillis = PETAL_TRANSITION_DURATION, easing = PetalM3EmphasizedEasing)
    ) + fadeIn(
        animationSpec = tween(durationMillis = PETAL_TRANSITION_DURATION, easing = PetalM3EmphasizedEasing)
    )
}

fun aospSharedAxisPopExit(): ExitTransition {
    return slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = tween(
            durationMillis = PETAL_TRANSITION_DURATION,
            easing = PetalPopExitSlideEasing
        )
    ) + scaleOut(
        targetScale = 0.85f,
        animationSpec = tween(durationMillis = PETAL_TRANSITION_DURATION, easing = PetalM3EmphasizedEasing)
    )
}
