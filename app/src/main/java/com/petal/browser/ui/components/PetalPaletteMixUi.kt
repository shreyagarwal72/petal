package com.petal.browser.ui.components

import com.petal.browser.ui.theme.ExpressiveButtonShape
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ColorLens
import com.petal.browser.ui.kit.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.petal.browser.ui.kit.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.petal.browser.ui.theme.PetalPaletteMix
import com.petal.browser.ui.theme.PetalPaletteMix.Source
import com.petal.browser.ui.theme.PetalPalettes

/** Human-readable name of a mix source, for settings rows. */
fun mixSourceLabel(source: Source, customArgb: Int): String = when (source) {
    Source.Auto -> "Auto (derived from primary)"
    Source.Dynamic -> "Material You (wallpaper)"
    Source.Custom -> if (customArgb != 0) "Custom colour #%06X".format(customArgb and 0xFFFFFF) else "Custom colour (not picked yet)"
    is Source.Preset -> com.petal.browser.ui.theme.paletteById(source.id).label
}

/** Three live circles showing the colours the app is using right now. */
@Composable
fun PetalMixPreviewStrip(modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("Primary" to c.primary, "Secondary" to c.secondary, "Tertiary" to c.tertiary).forEach { (name, color) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Box(Modifier.size(44.dp).clip(ExpressiveButtonShape).background(color))
                Text(name, style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Chooser for where secondary or tertiary gets its colour. */
@Composable
fun PetalMixSourceDialog(
    title: String,
    current: Source,
    customArgb: Int,
    onSelect: (Source) -> Unit,
    onPickCustom: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(vertical = 20.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    SourceRow("Auto", "Derived from your primary colour", current == Source.Auto,
                        swatch = { Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }) {
                        onSelect(Source.Auto)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SourceRow("Material You", "Colours from your wallpaper", current == Source.Dynamic,
                            swatch = { Icon(Icons.Rounded.ColorLens, null, tint = MaterialTheme.colorScheme.primary) }) {
                            onSelect(Source.Dynamic)
                        }
                    }
                    PetalPalettes.forEach { pal ->
                        SourceRow(pal.label, "Preset palette", current == Source.Preset(pal.id),
                            swatch = { Box(Modifier.size(28.dp).clip(ExpressiveButtonShape).background(pal.seed)) }) {
                            onSelect(Source.Preset(pal.id))
                        }
                    }
                    SourceRow("Pick a colour", mixSourceLabel(Source.Custom, customArgb), current == Source.Custom,
                        swatch = {
                            Box(
                                Modifier.size(28.dp).clip(ExpressiveButtonShape).background(
                                    if (customArgb != 0) Brush.linearGradient(listOf(Color(customArgb), Color(customArgb)))
                                    else Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))
                                )
                            )
                        }) { onPickCustom() }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(label: String, sub: String, selected: Boolean, swatch: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            Modifier.size(40.dp).clip(ExpressiveButtonShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) { swatch() }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
    }
}

/** Interactive sandbox displaying real-time live mixed colors on buttons, chips, and pills. */
@Composable
fun PetalMixComponentPreview(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = cs.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Live Component Sandbox",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = cs.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Primary Action Button
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = cs.primary,
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Primary", color = cs.onPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
                // Secondary Container Chip
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = cs.secondaryContainer,
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Secondary", color = cs.onSecondaryContainer, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
                // Tertiary Highlight Pill
                Surface(
                    shape = ExpressiveButtonShape,
                    color = cs.tertiary,
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Tertiary", color = cs.onTertiary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** One-tap curated harmonious recipes strip. */
@Composable
fun PetalMixRecipesStrip(
    activeSecondary: Source,
    activeTertiary: Source,
    onSelectRecipe: (PetalPaletteMix.Recipe) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Harmonious Quick Recipes",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PetalPaletteMix.RECIPES.forEach { recipe ->
                val isSelected = activeSecondary == recipe.secondary && activeTertiary == recipe.tertiary
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectRecipe(recipe) },
                    label = { Text(recipe.name) },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    border = null
                )
            }
        }
    }
}
