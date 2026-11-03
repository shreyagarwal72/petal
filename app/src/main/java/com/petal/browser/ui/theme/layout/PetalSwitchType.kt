package com.petal.browser.ui.theme.layout

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.ToggleOff
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.Water
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Switch presentation styles ported from ImageToolbox.
 * Note: When Liquid Glass theme is enabled, switches automatically use Liquid Glass styling.
 */
enum class PetalSwitchType(val key: String, val title: String, val subtitle: String) {
    MATERIAL_YOU("MATERIAL_YOU", "Material You", "Standard Android Material 3 switch with kinetic pill"),
    COMPOSE("COMPOSE", "Compose", "Clean minimalist Jetpack Compose toggle"),
    PIXEL("PIXEL", "Pixel", "Google Pixel flavored rounded capsule switch"),
    FLUENT("FLUENT", "Fluent", "Microsoft Fluent style compact toggle indicator"),
    CUPERTINO("CUPERTINO", "Cupertino", "iOS-style rounded bouncing slider toggle"),
    HYPEROS("HYPEROS", "HyperOS", "HyperOS style high-contrast accent toggle"),
    ONE_UI("ONE_UI", "OneUI", "Samsung OneUI rounded oval toggle");

    companion object {
        fun fromKey(key: String?): PetalSwitchType =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: COMPOSE
    }
}
