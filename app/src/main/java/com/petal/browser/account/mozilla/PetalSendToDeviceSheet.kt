/*
 * PetalSendToDeviceSheet.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive bottom sheet for sending tabs directly to paired
 * Firefox & Petal devices via Mozilla Sync commands.
 *
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.account.mozilla

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.petal.browser.ui.theme.ExpressiveButtonShape
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.components.PetalButtonVariant
import com.petal.browser.ui.components.PetalLiquidButton
import com.petal.browser.ui.containment.PetalSheet
import com.petal.browser.view.PetalToast
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalSendToDeviceSheet(
    url: String,
    title: String,
    onDismissRequest: () -> Unit,
    onOpenSyncSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val syncManager = remember { PetalMozillaSyncManager.getInstance() }
    val fxaManager = remember { FxAccountManager.getInstance() }

    val accountState by fxaManager.accountState.collectAsStateWithLifecycle()
    val remoteDevices by syncManager.tabBridge.remoteTabsFlow.collectAsStateWithLifecycle()

    var isSending by remember { mutableStateOf(false) }
    var sentTarget by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    PetalSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Title & Target Tab Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(ExpressiveButtonShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SendToMobile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Send Tab to Device",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = title.ifBlank { url },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 1.dp
            )

            if (accountState !is FxaState.SignedIn) {
                // Not Signed In to Firefox Accounts
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Rounded.SyncDisabled,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Sign in with Firefox Account to send tabs across your phone, tablet, and computer.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    PetalLiquidButton(
                        onClick = {
                            onDismissRequest()
                            onOpenSyncSettings()
                        },
                        variant = PetalButtonVariant.Filled,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sign In to Firefox Account")
                    }
                }
            } else if (remoteDevices.isEmpty()) {
                // Signed in, but no other connected devices found
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Rounded.DevicesOther,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = "No other devices connected",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Sign into Firefox on your computer, laptop, or other phone to send tabs instantly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    PetalLiquidButton(
                        onClick = {
                            // Trigger sync cycle to refresh device list
                            syncManager.syncNow(context)
                            PetalToast.show(context, "Checking for paired Firefox devices...")
                        },
                        variant = PetalButtonVariant.Tonal,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Refresh Devices")
                    }
                }
            } else {
                // List of Paired Remote Devices
                Text(
                    text = "Connected Devices (${remoteDevices.size})",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(remoteDevices, key = { it.deviceId }) { device ->
                        val deviceIcon = resolveDeviceIcon(device.deviceType, device.deviceName)
                        val isTargetSent = sentTarget == device.deviceId

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isTargetSent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(enabled = !isSending) {
                                    isSending = true
                                    PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.6f)
                                    syncManager.sendTabToDevice(
                                        context = context,
                                        targetDeviceId = device.deviceId,
                                        targetDeviceName = device.deviceName,
                                        url = url,
                                        title = title
                                    ) { success ->
                                        isSending = false
                                        if (success) {
                                            sentTarget = device.deviceId
                                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.HEAVY_CLICK, 0.9f)
                                            PetalToast.show(context, "Tab sent to ${device.deviceName.ifBlank { "device" }}!")
                                            onDismissRequest()
                                        } else {
                                            PetalToast.show(context, "Failed to send tab. Please try again.")
                                        }
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Icon(
                                    imageVector = if (isTargetSent) Icons.Rounded.CheckCircle else deviceIcon,
                                    contentDescription = null,
                                    tint = if (isTargetSent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = device.deviceName.ifBlank { "Firefox Device" },
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${device.tabs.size} open tabs",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Rounded.ArrowForwardIos,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Quick Action: Send to All Devices
                PetalLiquidButton(
                    onClick = {
                        isSending = true
                        PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.HEAVY_CLICK, 0.8f)
                        syncManager.sendTabToDevice(
                            context = context,
                            targetDeviceId = "*",
                            targetDeviceName = "All Devices",
                            url = url,
                            title = title
                        ) { success ->
                            isSending = false
                            if (success) {
                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.HEAVY_CLICK, 1.0f)
                                PetalToast.show(context, "Tab sent to all connected Firefox devices!")
                                onDismissRequest()
                            } else {
                                PetalToast.show(context, "Failed to send tab.")
                            }
                        }
                    },
                    variant = PetalButtonVariant.Tonal,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Send to All Devices")
                }
            }
        }
    }
}

private fun resolveDeviceIcon(type: String, name: String): ImageVector {
    val lower = "$type $name".lowercase()
    return when {
        lower.contains("phone") || lower.contains("mobile") || lower.contains("android") -> Icons.Rounded.PhoneAndroid
        lower.contains("tablet") || lower.contains("ipad") -> Icons.Rounded.TabletMac
        lower.contains("laptop") || lower.contains("macbook") -> Icons.Rounded.LaptopMac
        else -> Icons.Rounded.DesktopWindows
    }
}
