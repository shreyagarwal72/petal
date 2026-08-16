// SPDX-License-Identifier: GPL-3.0-only
package com.petal.browser.animation.predictiveback

/**
 * Define Predictive Back Animation types imported from InstallerX-Revived.
 */
enum class PredictiveBackAnimation(val value: String, val label: String) {
    NONE("none", "None"),
    AOSP("aosp", "AOSP"),
    MIUIX("miuix", "MIUIX"),
    SCALE("scale", "Scale"),
    CLASSIC("ksu_classic", "Classic");

    companion object {
        fun fromValueOrDefault(value: String): PredictiveBackAnimation =
            entries.find { it.value == value || it.name.equals(value, ignoreCase = true) } ?: CLASSIC
    }
}
