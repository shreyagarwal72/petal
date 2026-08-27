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

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * Ported directly from RvSystem-Monitor's `ui/navigation/Transitions.kt`.
 *
 * RvSystem-Monitor expresses these as declarative [androidx.compose.animation.EnterTransition] /
 * [androidx.compose.animation.ExitTransition] pairs handed to Navigation3's
 * `NavDisplay.predictivePopTransitionSpec`, which plays them automatically as the gesture
 * commits. Petal isn't on Navigation3 - each screen is its own Activity/Composable root driven
 * directly by [androidx.activity.compose.PredictiveBackHandler] - so [PetalPredictiveBackSurface]
 * and [PetalScreenWrapper] evaluate the same curve and values by hand, once per frame, against
 * the live gesture progress instead. The numbers below are unchanged from the source file.
 */

// Material 3 "emphasized" easing - cubic-bezier(0.2, 0, 0, 1.0), fast start / smooth settle.
// Equivalent to RvSystem's M3EmphasizedEasing.
val PetalM3EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

// Equivalent to RvSystem's AOSP_TRANSITION_DURATION.
const val PETAL_TRANSITION_DURATION = 350

// Equivalent to RvSystem's aospSharedAxisPopExit easing: barely moves at the start of the
// drag, then slides fully away as the gesture completes.
val PetalPopExitSlideEasing: Easing = Easing { f -> f * f * f }

// Equivalent to RvSystem's aospSharedAxisPopExit targetScale = 0.85f.
const val PETAL_POP_EXIT_MAX_SCALE_DELTA = 0.15f
