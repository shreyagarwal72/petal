/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 *
 * Adapted from Atomicx7/Duo-animation (FoldParameters.kt)
 */

package com.petal.browser.appleduo

/**
 * Physical parameters of the frosted-glass fold (mirrors Swift `FoldParameters` and Atomicx7 Duo-animation).
 *
 * @param eyeDistanceMillimeters Distance from the viewer's eyes to the untilted
 *   screen, looking at it head-on. The eye stays there while the device tilts.
 *   Default 450mm.
 * @param pixelsPerMillimeter Density of layer pixels (≈ xdpi / 25.4). If <= 0,
 *   auto-resolves from display metrics.
 * @param blurSpread Blur radius gained per px of separation between the glass
 *   and the UI plane.
 * @param darkening Fraction of light lost per px of blur radius.
 */
data class FoldParameters(
    val eyeDistanceMillimeters: Float = 450f,
    val pixelsPerMillimeter: Float = 0f, // 0 = auto-resolve from display metrics
    val blurSpread: Float = 0.12f,
    val darkening: Float = 0.015f
)
