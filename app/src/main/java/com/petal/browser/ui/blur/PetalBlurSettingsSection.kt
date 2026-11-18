/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.petal.browser.ui.blur

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurCircular
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.petal.browser.ui.components.ExpressiveButtonGroup
import com.petal.browser.ui.components.ExpressiveSegmentItem
import com.petal.browser.ui.components.PetalSlider
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.PetalSettingsSection
import com.petal.browser.ui.containment.PetalSettingsToggleRow

private class BlurSurfaceRow(
    val surface: PetalBlurSurface,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

private val SURFACE_ROWS = listOf(
    BlurSurfaceRow(PetalBlurSurface.OVERFLOW_MENU, "Overflow menu", "Blur the page behind the three-dot menu", Icons.Rounded.MoreVert),
    BlurSurfaceRow(PetalBlurSurface.SHEETS, "Bottom sheets", "Blur behind every bottom sheet in the app", Icons.Rounded.VerticalAlignBottom),
    BlurSurfaceRow(PetalBlurSurface.DIALOGS, "Dialogs", "Blur behind permission, confirmation and info dialogs", Icons.Rounded.Layers),
    BlurSurfaceRow(PetalBlurSurface.TAB_MANAGER, "Tab manager sheets", "Blur behind the tab glance and workspace sheets", Icons.Rounded.Tab),
    BlurSurfaceRow(PetalBlurSurface.MEDIA, "Media player", "Blur behind queue, lyrics and player sheets", Icons.Rounded.PlayArrow),
)

/**
 * Appearance → "Background blur". Independent of Liquid Glass: it blurs what is behind a surface in
 * Material 3 Expressive and in Liquid Glass alike. Reads and writes SharedPreferences directly.
 */
@Composable
fun PetalBlurSettingsSection(targetHighlightId: String? = null) {
    val context = LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }

    var masterOn by remember(sp) { mutableStateOf(sp.getBoolean(PetalBlurPrefs.KEY_ENABLED, true)) }
    var strength by remember(sp) { mutableFloatStateOf(PetalBlurPrefs.strengthDp(sp)) }
    var dim by remember(sp) { mutableFloatStateOf(PetalBlurPrefs.dim(sp)) }
    var method by remember(sp) { mutableStateOf(PetalBlurPrefs.method(sp)) }
    // Bumped on any blur pref change so the per-surface rows re-read (e.g. after a backup restore).
    var revision by remember(sp) { mutableStateOf(0) }

    DisposableEffect(sp) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key.startsWith(PetalBlurPrefs.PREFIX)) {
                masterOn = sp.getBoolean(PetalBlurPrefs.KEY_ENABLED, true)
                strength = PetalBlurPrefs.strengthDp(sp)
                dim = PetalBlurPrefs.dim(sp)
                method = PetalBlurPrefs.method(sp)
                revision++
            }
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    // Live: battery saver or an OEM policy can switch the OS blur off while this screen is open.
    val state = rememberPetalBlurState(PetalBlurSurface.SHEETS)
    val osAvailable = PetalBlurPrefs.isOsBlurAvailable(context)

    PetalSettingsSection(
        title = "Background blur",
        icon = Icons.Rounded.BlurOn,
        cardId = "appearance_background_blur",
        targetHighlightId = targetHighlightId
    ) {
        Text(
            text = "Blurs the live page behind menus, sheets and dialogs. Works the same with Material 3 Expressive and Liquid Glass, and never changes the surface itself.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PetalSettingsToggleRow(
            title = "Background blur",
            subtitle = "Live blur behind menus, sheets and dialogs",
            icon = Icons.Rounded.BlurCircular,
            checked = masterOn,
            position = PetalGroupPosition.SINGLE,
            onCheckedChange = {
                masterOn = it
                sp.edit().putBoolean(PetalBlurPrefs.KEY_ENABLED, it).apply()
            }
        )
        if (masterOn) {
            Text(
                text = "Blur method",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            ExpressiveButtonGroup(
                items = listOf(
                    ExpressiveSegmentItem(id = PetalBlurMethod.AUTO.id, label = "Auto", icon = Icons.Rounded.AutoAwesome),
                    ExpressiveSegmentItem(id = PetalBlurMethod.SYSTEM.id, label = "System", icon = Icons.Rounded.BlurOn),
                    ExpressiveSegmentItem(id = PetalBlurMethod.IN_APP.id, label = "In-app", icon = Icons.Rounded.Layers)
                ),
                selectedId = method.id,
                onItemSelected = {
                    method = PetalBlurMethod.fromId(it)
                    sp.edit().putString(PetalBlurPrefs.KEY_METHOD, it).apply()
                },
                modifier = Modifier.fillMaxWidth()
            )
            val running = PetalBlurPrefs.resolveMode(method, osAvailable)
            PetalSettingsToggleRowNote(
                when (running) {
                    PetalBlurMode.SYSTEM -> "Using system blur."
                    PetalBlurMode.IN_APP ->
                        if (method == PetalBlurMethod.AUTO && !osAvailable)
                            "System blur is switched off on this device, so Petal uses its own in-app blur."
                        else "Using in-app blur."
                    PetalBlurMode.NONE ->
                        "System blur isn't available on this device or in battery saver; a dim is used instead. Try Auto or In-app."
                }
            )
        }
        if (masterOn) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SliderHeader("Blur strength", "${Math.round(strength)} dp")
                PetalSlider(
                    value = strength,
                    onValueChange = {
                        strength = it
                        sp.edit().putFloat(PetalBlurPrefs.KEY_STRENGTH, it).apply()
                    },
                    valueRange = 0f..PetalBlurPrefs.MAX_STRENGTH_DP,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SliderHeader("Dim over blur", "${Math.round(dim * 100)}%")
                PetalSlider(
                    value = dim,
                    onValueChange = {
                        dim = it
                        sp.edit().putFloat(PetalBlurPrefs.KEY_DIM, it).apply()
                    },
                    valueRange = 0f..PetalBlurPrefs.MAX_DIM,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(
                text = "Where to blur",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            SURFACE_ROWS.forEachIndexed { index, row ->
                // `revision` is read so the toggle re-reads the pref after external changes.
                val checked = revision.let { PetalBlurPrefs.surfaceToggle(sp, row.surface) }
                PetalSettingsToggleRow(
                    title = row.title,
                    subtitle = row.subtitle,
                    icon = row.icon,
                    checked = checked,
                    position = when (index) {
                        0 -> PetalGroupPosition.TOP
                        SURFACE_ROWS.lastIndex -> PetalGroupPosition.BOTTOM
                        else -> PetalGroupPosition.MIDDLE
                    },
                    onCheckedChange = {
                        sp.edit().putBoolean(row.surface.key, it).apply()
                    }
                )
            }
        }
    }
}

@Composable
private fun SliderHeader(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun PetalSettingsToggleRowNote(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Icon(
            imageVector = Icons.Rounded.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
