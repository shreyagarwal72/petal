// SPDX-License-Identifier: GPL-3.0-only
package com.petal.browser.animation.predictiveback

/**
 * Defines the direction of the page exit animation.
 */
enum class PredictiveBackExitDirection(val value: String, val label: String) {
    /** Follows the user's swipe gesture direction (e.g., swipe left -> exit right). */
    FOLLOW_GESTURE("follow_gesture", "Follow Gesture"),

    /** Always translates to the right, regardless of swipe edge. */
    ALWAYS_RIGHT("always_right", "Always Right"),

    /** Always translates to the left, regardless of swipe edge. */
    ALWAYS_LEFT("always_left", "Always Left");

    companion object {
        fun fromValueOrDefault(value: String): PredictiveBackExitDirection =
            entries.find { it.value == value || it.name.equals(value, ignoreCase = true) } ?: ALWAYS_RIGHT
    }
}
