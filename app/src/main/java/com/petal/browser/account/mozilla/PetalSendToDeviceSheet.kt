package com.petal.browser.account.mozilla

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petal.browser.ui.components.bouncyClickable
import kotlinx.coroutines.launch

/** Send the current page to another Firefox device using the account's official device commands. */
@Composable
fun PetalSendToDeviceSheet(
    url: String,
    title: String,
    onDismissRequest: () -> Unit,
    onOpenSyncSettings: () -> Unit
) {
    val state by PetalFxa.state.collectAsStateWithLifecycle()
    val devices by PetalFxa.devices.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state) { if (state is FxaUi.SignedIn) PetalFxa.refreshDevices() }

    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Send to device", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(title.ifBlank { url }, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            if (state !is FxaUi.SignedIn) {
                Text("Sign in to your Firefox Account to send tabs.")
                Spacer(Modifier.height(12.dp))
                Button(onClick = onOpenSyncSettings, modifier = Modifier.fillMaxWidth()) { Text("Open Firefox Account settings") }
            } else {
                val targets = devices.filter { it.canReceiveTabs }
                if (targets.isEmpty()) Text("No devices that can receive tabs were found.")
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(targets) { d ->
                        Row(
                            Modifier.fillMaxWidth().bouncyClickable {
                                scope.launch {
                                    val ok = PetalFxa.sendTab(d.id, title, url)
                                    status = if (ok) "Sent to ${d.name}" else "Couldn't send to ${d.name}"
                                    if (ok) onDismissRequest()
                                }
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Devices, null); Spacer(Modifier.width(12.dp)); Text(d.name)
                        }
                    }
                }
                status?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
