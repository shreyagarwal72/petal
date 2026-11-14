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
        title = "Standard",
        description = "Blocks third-party ads, trackers, and malicious scripts while preserving first-party page functionality."
    ),
    AGGRESSIVE(
        id = "aggressive",
        title = "Aggressive",
        description = "Aggressively blocks both first-party and third-party ad networks, telemetry pings, and tracking scripts."
    );

    companion object {
        fun fromId(id: String?): PetalAdBlockMode = when (id?.lowercase()) {
            "disabled", "off", "false" -> DISABLED
            "aggressive", "strict", "high" -> AGGRESSIVE
            else -> STANDARD
        }
    }
}
