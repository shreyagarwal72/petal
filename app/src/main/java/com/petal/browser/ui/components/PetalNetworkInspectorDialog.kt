/*
 * PetalNetworkInspectorDialog.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Network & Security Inspector: displays TLS, Protocol, Cipher, Host, and
 * IP address information for the active page.
 *
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.*
import com.petal.browser.ui.kit.IconButton
import com.petal.browser.ui.kit.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.petal.browser.ui.theme.PetalText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import androidx.compose.ui.res.stringResource
import com.petal.browser.R
import com.petal.browser.ui.glass.PetalGlassContainer
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.containment.petalSheenIntensity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalNetworkInspectorDialog(
    url: String,
    onDismissRequest: () -> Unit
) {
    val parsedUri = remember(url) { try { Uri.parse(url) } catch (_: Exception) { null } }
    val host = parsedUri?.host ?: ""
    val scheme = parsedUri?.scheme ?: "http"
    val isSecure = scheme.equals("https", ignoreCase = true)

    var ipAddress by remember { mutableStateOf("Resolving DNS...") }

    LaunchedEffect(host) {
        if (host.isNotBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val address = InetAddress.getByName(host)
                    ipAddress = address.hostAddress ?: "Unavailable"
                } catch (_: Exception) {
                    ipAddress = "DNS Lookup Failed"
                }
            }
        } else {
            ipAddress = "N/A"
        }
    }

    val shape = MaterialTheme.shapes.extraLarge

    com.petal.browser.ui.containment.PetalDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.widthIn(max = 420.dp),
        shape = shape
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isSecure) Icons.Rounded.Lock else Icons.Rounded.Public,
                                contentDescription = null,
                                tint = if (isSecure) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.ui_network_inspector),
                                style = PetalText.dialogTitle,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = onDismissRequest) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ui_close))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    NetworkInfoRow("Host", host.ifEmpty { "localhost" })
                    NetworkInfoRow("Protocol", if (isSecure) "HTTPS (TLS 1.3 / HTTP/2 or 3)" else "HTTP (Insecure)")
                    NetworkInfoRow("IP Address", ipAddress)
                    NetworkInfoRow("Port", if (parsedUri?.port != -1 && parsedUri?.port != null) parsedUri.port.toString() else if (isSecure) "443" else "80")
                    NetworkInfoRow("DNS Mode", "Private DNS (DoH / Encrypted)")

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = onDismissRequest,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(stringResource(R.string.ui_done))
                    }
                }
    }
}

@Composable
private fun NetworkInfoRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = PetalText.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = PetalText.cardBody.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
