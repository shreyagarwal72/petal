package com.petal.browser.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Which Material 3 color role a button group / split button uses for its "active" colors.
 * Because Petal's palette mixing can source secondary and tertiary from different palettes,
 * using all three roles makes the mix visible in everyday controls.
 *
 * Suggested use: Primary = main filter or action, Secondary = sort / view options,
 * Tertiary = privacy / incognito / special states.
 */
enum class PetalGroupRole { Primary, Secondary, Tertiary }

@Immutable
data class PetalRoleColors(
    /** Soft fill (selected segment, tonal button). */
    val container: Color,
    val onContainer: Color,
    /** Strong fill (filled button). */
    val solid: Color,
    val onSolid: Color,
)

@Composable
fun petalRoleColors(role: PetalGroupRole): PetalRoleColors {
    val cs = MaterialTheme.colorScheme
    return when (role) {
        PetalGroupRole.Primary -> PetalRoleColors(cs.primaryContainer, cs.onPrimaryContainer, cs.primary, cs.onPrimary)
        PetalGroupRole.Secondary -> PetalRoleColors(cs.secondaryContainer, cs.onSecondaryContainer, cs.secondary, cs.onSecondary)
        PetalGroupRole.Tertiary -> PetalRoleColors(cs.tertiaryContainer, cs.onTertiaryContainer, cs.tertiary, cs.onTertiary)
    }
}
