/*
 * PetalAdBlockMode.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Shields & Content Protection Modes for Petal Browser (Brave/Cromite/Firefox hybrid).
 */

package com.petal.browser.browser

enum class PetalAdBlockMode(val id: String, val title: String, val description: String) {
    DISABLED(
        id = "disabled",
        title = "Shields Down",
        description = "Content blocking and tracking protection disabled. Full page scripts and network requests are allowed."
    ),
    STANDARD(
        id = "standard",
        title = "Standard Protection",
        description = "Blocks third-party ads, analytics, social trackers, and cryptominers. High compatibility with websites."
    ),
    AGGRESSIVE(
        id = "aggressive",
        title = "Aggressive Protection",
        description = "Brave-style strict blocking: blocks first & third-party trackers, cookie notices, annoying popups, and applies strict cosmetic DOM hiding."
    );

    companion object {
        fun fromId(id: String?): PetalAdBlockMode = when (id?.lowercase()) {
            "disabled", "off", "false" -> DISABLED
            "aggressive", "strict" -> AGGRESSIVE
            else -> STANDARD
        }
    }
}
