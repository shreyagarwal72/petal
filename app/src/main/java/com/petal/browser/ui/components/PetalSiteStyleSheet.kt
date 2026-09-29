/*
 * PetalSiteStyleSheet.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Custom Site Style Injector for Petal Browser.
 * Injects custom CSS styling (Sepia, High-Contrast Dark, Invert, Dyslexic font)
 * directly into GeckoView.
 *
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class SiteStylePreset(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val css: String
) {
    DEFAULT(
        "Default",
        "Original website styling",
        Icons.Rounded.Refresh,
        ""
    ),
    WARM_SEPIA(
        "Warm Sepia",
        "Soft warm paper reading tint",
        Icons.Rounded.MenuBook,
        "html { filter: sepia(60%) !important; background-color: #fbf0d9 !important; }"
    ),
    INVERTED_DARK(
        "Dark Invert",
        "Inverts light background to dark",
        Icons.Rounded.InvertColors,
        "html { filter: invert(90%) hue-rotate(180deg) !important; background: #121212 !important; } img, video, canvas { filter: invert(100%) hue-rotate(180deg) !important; }"
    ),
    HIGH_CONTRAST(
        "OLED High Contrast",
        "Maximum contrast pure black",
        Icons.Rounded.Contrast,
        "html, body { background: #000000 !important; color: #ffffff !important; } * { background-color: transparent !important; color: #f0f0f0 !important; border-color: #444 !important; }"
    ),
    DYSLEXIC_FONT(
        "Dyslexic Friendly",
        "Enhances text spacing & readability",
        Icons.Rounded.TextFields,
        "* { line-height: 1.8 !important; letter-spacing: 0.08em !important; word-spacing: 0.16em !important; }"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalSiteStyleSheet(
    activePreset: SiteStylePreset,
    onSelectPreset: (SiteStylePreset) -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Site Style",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SiteStylePreset.values().forEach { preset ->
                    val isSelected = preset == activePreset
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectPreset(preset)
                                onDismissRequest()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = preset.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.title,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = preset.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isSelected) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
