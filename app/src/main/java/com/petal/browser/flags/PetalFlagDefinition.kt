package com.petal.browser.flags

/**
 * PetalFlagDefinition
 * ─────────────────────────────────────────────────────────────────────────
 * Metadata model defining a curated experimental flag in Petal Browser.
 */
data class PetalFlagDefinition(
    val id: String,
    val title: String,
    val description: String,
    val category: FlagCategory,
    val prefKey: String,
    val type: FlagType = FlagType.BOOLEAN,
    val defaultState: FlagState = FlagState.DEFAULT,
    val enabledValue: String = "true",
    val disabledValue: String = "false"
)

enum class FlagCategory(val displayName: String) {
    ALL("All"),
    PERFORMANCE("Performance"),
    PRIVACY("Privacy & Security"),
    RENDERING("Rendering & GPU"),
    NETWORK("Networking"),
    EXTENSIONS("Add-ons & Scripts")
}

enum class FlagType {
    BOOLEAN,
    INT,
    STRING
}

enum class FlagState(val label: String) {
    DEFAULT("Default"),
    ENABLED("Enabled"),
    DISABLED("Disabled")
}
