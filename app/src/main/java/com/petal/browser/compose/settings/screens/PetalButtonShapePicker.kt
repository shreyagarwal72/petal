package com.petal.browser.compose.settings.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.petal.browser.ui.theme.ExpressiveButtonPreset
import com.petal.browser.ui.theme.ExpressiveIconButton
import com.petal.browser.ui.theme.ExpressiveShapeAmount
import com.petal.browser.ui.theme.PetalShapePrefs
import com.petal.browser.ui.theme.PetalShapeState

/**
 * Lets the user choose the expressive shape used for every button and how widely it is applied.
 * Same three modes as the Expressive Keyboard: everywhere, accents only, or plain circles.
 */
@Composable
fun PetalButtonShapePicker() {
    val context = LocalContext.current
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var preset by remember { mutableStateOf(PetalShapeState.preset) }
    var amount by remember { mutableStateOf(PetalShapeState.amount) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "Button shape",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ExpressiveButtonPreset.entries.forEach { option ->
                val selected = option == preset
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ExpressiveIconButton(
                        icon = if (selected) Icons.Rounded.Check else Icons.Rounded.Settings,
                        contentDescription = option.title,
                        onClick = {
                            preset = option
                            PetalShapeState.preset = option
                            sp.edit().putString(PetalShapePrefs.KEY_BUTTON_PRESET, option.id).apply()
                        },
                        size = 52.dp,
                        iconSize = 22.dp,
                        preset = option,
                        containerColor = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        option.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Text(
            "Where to use shapes",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                ExpressiveShapeAmount.FULL to "Everywhere",
                ExpressiveShapeAmount.ACCENTS to "Main buttons",
                ExpressiveShapeAmount.NONE to "Plain circles",
            ).forEach { (value, label) ->
                FilterChip(
                    selected = amount == value,
                    onClick = {
                        amount = value
                        PetalShapeState.amount = value
                        sp.edit().putString(PetalShapePrefs.KEY_SHAPE_AMOUNT, value.name).apply()
                    },
                    label = { Text(label) },
                )
            }
        }

        // Live preview row: header-style buttons using the icons from the home and tab screens.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ExpressiveIconButton(Icons.Rounded.Palette, "Preview palette", onClick = {})
            ExpressiveIconButton(Icons.Rounded.Search, "Preview search", onClick = {})
        }
    }
}
