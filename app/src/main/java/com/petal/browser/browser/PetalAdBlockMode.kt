/*
 * PetalAdBlockMode.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Shields & Content Protection Modes for Petal Browser (Brave/Cromite/Firefox hybrid).
 */

package com.petal.browser.browser

enum class PetalAdBlockMode(val id: String, val title: String, val description: String) {
    DISABLED(
        id = "disabled",
        title = "Off",
        description = "Ad and tracker blocking is off. All page scripts and network requests are allowed."
    ),
    STANDARD(
        id = "standard",
        title = "On",
        description = "Blocks ads, trackers, fingerprinters and cryptominers using Gecko Strict tracking protection plus Petal filter lists."
    );

    companion object {
        /** Legacy "aggressive"/"strict" values from older versions are folded into the single ON state. */
        fun fromId(id: String?): PetalAdBlockMode = when (id?.lowercase()) {
            "disabled", "off", "false" -> DISABLED
            else -> STANDARD
        }
    }
}
