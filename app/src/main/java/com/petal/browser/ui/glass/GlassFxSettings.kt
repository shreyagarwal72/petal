package com.petal.browser.ui.glass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Lens
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.PetalSettingsToggleRow

/**
 * Appearance → Liquid Glass → "Liquid Glass Effects": Kyant's interaction effects for the
 * bottom bar, address bar, search button and every glass card, row and button. The keys start
 * with sp_liquid_glass_ so a change reaches every glass surface at once, live.
 */
@Composable
fun GlassEffectsSettings() {
    val fx = rememberGlassFx()
    val sp = PreferenceManager.getDefaultSharedPreferences(LocalContext.current)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Liquid Glass Effects",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            PetalSettingsToggleRow(
                title = "Interactive glass effects",
                subtitle = "Glass reacts when you touch it: bottom bar, address bar, buttons and cards",
                icon = Icons.Rounded.AutoAwesome,
                checked = fx.enabled,
                position = if (fx.enabled) PetalGroupPosition.TOP else PetalGroupPosition.SINGLE,
                onCheckedChange = { sp.edit().putBoolean(GlassFxPrefs.ENABLED, it).apply() }
            )
            if (fx.enabled) {
                PetalSettingsToggleRow(
                    title = "Press highlight",
                    subtitle = "A soft light blooms under your finger and follows it across the glass",
                    icon = Icons.Rounded.TouchApp,
                    checked = fx.highlight,
                    position = PetalGroupPosition.MIDDLE,
                    onCheckedChange = { sp.edit().putBoolean(GlassFxPrefs.HIGHLIGHT, it).apply() }
                )
                PetalSettingsToggleRow(
                    title = "Squish & stretch",
                    subtitle = "Glass swells under your finger; the bar's selection slab stretches as it moves",
                    icon = Icons.Rounded.Animation,
                    checked = fx.squish,
                    position = PetalGroupPosition.MIDDLE,
                    onCheckedChange = { sp.edit().putBoolean(GlassFxPrefs.SQUISH, it).apply() }
                )
                PetalSettingsToggleRow(
                    title = "Lens on press",
                    subtitle = "Edge refraction deepens while the glass is held (Android 13+)",
                    icon = Icons.Rounded.Lens,
                    checked = fx.lens,
                    position = PetalGroupPosition.BOTTOM,
                    onCheckedChange = { sp.edit().putBoolean(GlassFxPrefs.LENS, it).apply() }
                )
            }
        }
    }
}
