package com.petal.browser.haptics

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Vibration Intensity levels matching RvSystem-Monitor specifications.
 */
enum class VibrationIntensity {
    LIGHT,
    MEDIUM,
    STRONG
}

/**
 * CompositionLocal providing a boolean value to enable or disable haptic feedback globally.
 */
val LocalHapticEnabled = staticCompositionLocalOf { true }

/**
 * CompositionLocal providing the current [VibrationIntensity] for haptic feedback.
 */
val LocalVibrationIntensity = staticCompositionLocalOf { VibrationIntensity.LIGHT }
