package com.petal.browser.haptics

import android.content.Context
import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role

/**
 * Performs custom haptic feedback using PetalHapticEngine, mapped from RvSystem-Monitor.
 */
private fun performRvHapticFeedback(context: Context, intensity: VibrationIntensity) {
    val pattern = when (intensity) {
        VibrationIntensity.LIGHT -> PetalHapticEngine.Pattern.TICK
        VibrationIntensity.MEDIUM -> PetalHapticEngine.Pattern.CLICK
        VibrationIntensity.STRONG -> PetalHapticEngine.Pattern.HEAVY_CLICK
    }
    PetalHapticEngine.getInstance(context).playIfEnabled(context, pattern, 0.75f)
}

/**
 * A helper to provide consistent haptic feedback when an onClick event occurs.
 */
@Composable
fun rememberHapticOnClick(onClick: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val hapticEnabled = LocalHapticEnabled.current
    val intensity = LocalVibrationIntensity.current
    return remember(onClick, hapticEnabled, intensity, context) {
        {
            if (hapticEnabled) {
                performRvHapticFeedback(context, intensity)
            }
            onClick()
        }
    }
}

/**
 * A helper to provide haptic feedback when a value changes (e.g., Slider steps).
 */
@Composable
fun <T> rememberHapticOnValueChange(onValueChange: (T) -> Unit): (T) -> Unit {
    val context = LocalContext.current
    val hapticEnabled = LocalHapticEnabled.current
    val intensity = LocalVibrationIntensity.current
    return remember(onValueChange, hapticEnabled, intensity, context) {
        { newValue ->
            if (hapticEnabled) {
                performRvHapticFeedback(context, intensity)
            }
            onValueChange(newValue)
        }
    }
}

/**
 * A custom modifier that provides haptic feedback along with standard clickable behavior,
 * ported seamlessly from RvSystem-Monitor.
 */
fun Modifier.hapticClickable(
    enabled: Boolean = true,
    ripple: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    indication: Indication? = null,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val context = LocalContext.current
    val hapticEnabled = LocalHapticEnabled.current
    val intensity = LocalVibrationIntensity.current
    val hapticOnClick = remember(onClick, hapticEnabled, intensity, context) {
        {
            if (hapticEnabled) {
                performRvHapticFeedback(context, intensity)
            }
            onClick()
        }
    }

    this.clickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        indication = indication ?: if (ripple) ripple() else null,
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
        onClick = hapticOnClick,
    )
}
