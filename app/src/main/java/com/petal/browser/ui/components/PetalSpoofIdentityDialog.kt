/*
 * PetalSpoofIdentityDialog.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Quick User-Agent & Client Hints Identity Spoofing Dialog.
 *
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.containment.PetalDialog
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.PetalSelectableOptionCard

enum class IdentityPreset(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val userAgent: String?
) {
    DEFAULT(
        "Default (Android Mobile)",
        "Official Petal / Firefox Mobile UA",
        Icons.Rounded.PhoneAndroid,
        null
    ),
    DESKTOP_WINDOWS(
        "Desktop Chrome (Windows 11)",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/133.0",
        Icons.Rounded.DesktopWindows,
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
    ),
    IPHONE(
        "Apple iPhone (iOS 18)",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_2 like Mac OS X)",
        Icons.Rounded.PhoneIphone,
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.2 Mobile/15E148 Safari/604.1"
    ),
    MAC_SAFARI(
        "Apple Mac (macOS Safari)",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)",
        Icons.Rounded.LaptopMac,
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.2 Safari/605.1.15"
    ),
    IPAD(
        "Apple iPad (iPadOS 18)",
        "Mozilla/5.0 (iPad; CPU OS 18_2 like Mac OS X)",
        Icons.Rounded.TabletMac,
        "Mozilla/5.0 (iPad; CPU OS 18_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.2 Mobile/15E148 Safari/604.1"
    )
}

@Composable
fun PetalSpoofIdentityDialog(
    currentUa: String?,
    onSelectIdentity: (IdentityPreset) -> Unit,
    onDismissRequest: () -> Unit
) {
    PetalDialog(onDismissRequest = onDismissRequest, modifier = Modifier.widthIn(max = 380.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Spoof Identity (User-Agent)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IdentityPreset.values().forEach { preset ->
                        PetalSelectableOptionCard(
                            title = preset.title,
                            subtitle = preset.subtitle,
                            selected = currentUa == preset.userAgent,
                            onClick = { onSelectIdentity(preset); onDismissRequest() },
                            leading = { PetalGroupIconBadge(preset.icon) },
                        )
                    }
                }
            }
    }
}
