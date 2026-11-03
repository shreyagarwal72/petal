package com.petal.browser.ui.theme.layout

/**
 * Fling velocity and physics decay styles ported from ImageToolbox.
 */
enum class PetalFlingType(val key: String, val title: String, val subtitle: String) {
    DEFAULT("DEFAULT", "Android Native", "Standard platform physics decay curve"),
    SMOOTH("SMOOTH", "Smooth", "Gentle deceleration with soft momentum roll-off"),
    IOS_STYLE("IOS_STYLE", "iOS Style", "Bouncy high-frequency momentum decay"),
    SNAPPY("SNAPPY", "Snappy", "Crisp deceleration with swift settle time"),
    FLOATY("FLOATY", "Floaty", "Low friction glide for long catalog browsing"),
    REDUCED_MOTION("REDUCED_MOTION", "Reduced Motion", "Immediate stop with minimal inertial roll");

    companion object {
        fun fromKey(key: String?): PetalFlingType =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: DEFAULT
    }
}
