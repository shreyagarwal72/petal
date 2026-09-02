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
val PetalPopExitSlideEasing = CubicBezierEasing(0.5f, 0.0f, 0.8f, 0.2f)

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

// MD3 Expressive – Emphasized easing (matches Material Motion spec)
private val EmphasizedDecelerateEasing = CubicBezierEasing(0.2f, 0.85f, 0.7f, 1f)
private val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

const val PETAL_EXPRESSIVE_TRANSITION_DURATION = 450

fun enterTransition() = slideInHorizontally(
    animationSpec = tween(PETAL_EXPRESSIVE_TRANSITION_DURATION, easing = EmphasizedDecelerateEasing),
    initialOffsetX = { (it * 0.5f).toInt() }
) + scaleIn(
    animationSpec = tween(PETAL_EXPRESSIVE_TRANSITION_DURATION, easing = EmphasizedDecelerateEasing),
    initialScale = 0.92f,
    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
) + fadeIn(
    animationSpec = tween(PETAL_EXPRESSIVE_TRANSITION_DURATION, easing = EmphasizedAccelerateEasing)
)

fun exitTransition() = slideOutHorizontally(
    animationSpec = tween(PETAL_EXPRESSIVE_TRANSITION_DURATION, easing = EmphasizedAccelerateEasing),
    targetOffsetX = { -(it * 0.25f).toInt() }
) + fadeOut(
    animationSpec = tween(PETAL_EXPRESSIVE_TRANSITION_DURATION / 2, easing = EmphasizedAccelerateEasing)
)

fun popEnterTransition() = slideInHorizontally(
    animationSpec = tween(PETAL_EXPRESSIVE_TRANSITION_DURATION, easing = EmphasizedDecelerateEasing),
    initialOffsetX = { -(it * 0.25f).toInt() }
) + scaleIn(
    animationSpec = tween(PETAL_EXPRESSIVE_TRANSITION_DURATION, easing = EmphasizedDecelerateEasing),
    initialScale = 0.95f
) + fadeIn(
    animationSpec = tween(PETAL_EXPRESSIVE_TRANSITION_DURATION / 2, easing = EmphasizedDecelerateEasing)
)

fun popExitTransition() = slideOutHorizontally(
    animationSpec = tween(PETAL_EXPRESSIVE_TRANSITION_DURATION, easing = EmphasizedAccelerateEasing),
    targetOffsetX = { (it * 0.5f).toInt() }
) + scaleOut(
    animationSpec = tween(PETAL_EXPRESSIVE_TRANSITION_DURATION, easing = EmphasizedAccelerateEasing),
    targetScale = 0.92f,
    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
) + fadeOut(
    animationSpec = tween(PETAL_EXPRESSIVE_TRANSITION_DURATION / 2, easing = EmphasizedAccelerateEasing)
)

enum class PetalMainRootDirection {
    FORWARD,
    BACKWARD,
}

private val MAIN_ROOT_TRANSITION_SPEC =
    tween<androidx.compose.ui.unit.IntOffset>(durationMillis = 400, easing = PetalM3EmphasizedEasing)

private val MAIN_ROOT_FADE_SPEC =
    tween<Float>(durationMillis = 400, easing = PetalM3EmphasizedEasing)

fun mainRootDirection(fromIndex: Int, toIndex: Int): PetalMainRootDirection? {
    if (fromIndex == toIndex) return null
    return if (toIndex > fromIndex) PetalMainRootDirection.FORWARD else PetalMainRootDirection.BACKWARD
}

fun mainRootEnterTransition(direction: PetalMainRootDirection?, fallback: EnterTransition): EnterTransition =
    when (direction) {
        PetalMainRootDirection.FORWARD -> {
            slideInHorizontally(
                animationSpec = MAIN_ROOT_TRANSITION_SPEC,
                initialOffsetX = { it },
            ) + fadeIn(animationSpec = MAIN_ROOT_FADE_SPEC)
        }

        PetalMainRootDirection.BACKWARD -> {
            slideInHorizontally(
                animationSpec = MAIN_ROOT_TRANSITION_SPEC,
                initialOffsetX = { -it },
            ) + fadeIn(animationSpec = MAIN_ROOT_FADE_SPEC)
        }

        null -> fallback
    }

fun mainRootExitTransition(direction: PetalMainRootDirection?, fallback: ExitTransition): ExitTransition =
    when (direction) {
        PetalMainRootDirection.FORWARD -> {
            slideOutHorizontally(
                animationSpec = MAIN_ROOT_TRANSITION_SPEC,
                targetOffsetX = { -it },
            ) + fadeOut(animationSpec = MAIN_ROOT_FADE_SPEC)
        }

        PetalMainRootDirection.BACKWARD -> {
            slideOutHorizontally(
                animationSpec = MAIN_ROOT_TRANSITION_SPEC,
                targetOffsetX = { it },
            ) + fadeOut(animationSpec = MAIN_ROOT_FADE_SPEC)
        }

        null -> fallback
    }

