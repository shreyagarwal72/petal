/*
 * PetalContainerTabManager.kt
 * ──────────────────────────────────────────────────────────────────────────
 * Firefox-style Contextual Identity Containers with Neon Glass accents.
 * Isolates cookies, storage, and sessions by container ID.
 */
package com.petal.browser.containers

import androidx.compose.ui.graphics.Color

data class PetalContainer(
    val id: String,
    val name: String,
    val color: Color,
    val hexColor: Long,
    val iconName: String
)

object PetalContainerManager {
    val CONTAINERS = listOf(
        PetalContainer("default", "Personal", Color(0xFF388AF6), 0xFF388AF6, "person"),
        PetalContainer("work", "Work", Color(0xFFFF9800), 0xFFFF9800, "work"),
        PetalContainer("banking", "Banking", Color(0xFF00E676), 0xFF00E676, "account_balance"),
        PetalContainer("shopping", "Shopping", Color(0xFFE91E63), 0xFFE91E63, "shopping_cart"),
        PetalContainer("crypto", "Crypto / Web3", Color(0xFF9C27B0), 0xFF9C27B0, "currency_bitcoin")
    )

    fun getContainer(id: String?): PetalContainer {
        return CONTAINERS.firstOrNull { it.id == id } ?: CONTAINERS.first()
    }
}
