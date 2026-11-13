package com.petal.browser.account.mozilla

import android.text.format.DateUtils
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petal.browser.compose.home.PetalShortcut
import com.petal.browser.ui.components.bouncyClickable
import kotlinx.coroutines.launch
import mozilla.components.concept.sync.SyncEngine

/** Firefox Account & Sync settings, driven by the official FxaAccountManager through [PetalFxa]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirefoxAccountSyncScreen(
    backgroundSnapshot: androidx.compose.ui.graphics.ImageBitmap? = null,
    onBack: () -> Unit = {},
    onOpenOAuth: (PetalShortcut) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val state by PetalFxa.state.collectAsStateWithLifecycle()
    val syncing by PetalFxa.syncing.collectAsStateWithLifecycle()
    val lastSync by PetalFxa.lastSync.collectAsStateWithLifecycle()
    val devices by PetalFxa.devices.collectAsStateWithLifecycle()
    val received by PetalFxa.receivedTabs.collectAsStateWithLifecycle()
    val error by PetalFxa.error.collectAsStateWithLifecycle()

    var bookmarksOn by remember { mutableStateOf(PetalFxaSettings.bookmarks(context)) }
    var historyOn by remember { mutableStateOf(PetalFxaSettings.history(context)) }
    var tabsOn by remember { mutableStateOf(PetalFxaSettings.tabs(context)) }
    var passwordsOn by remember { mutableStateOf(PetalFxaSettings.passwords(context)) }
    val remoteTabs by PetalFxa.remoteTabs.collectAsStateWithLifecycle()
    var deviceName by remember { mutableStateOf(PetalFxaSettings.deviceName(context)) }
    var showXpBackup by remember { mutableStateOf(false) }
    var showRewards by remember { mutableStateOf(false) }

    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); PetalFxa.clearError() } }
    LaunchedEffect(state) { if (state is FxaUi.SignedIn) PetalFxa.refreshDevices() }
    LaunchedEffect(Unit) { com.petal.browser.account.progress.PetalProgressStore.init(context) }
    if (showRewards) com.petal.browser.account.progress.PetalRewardsSheet(onDismiss = { showRewards = false })
    if (showXpBackup) com.petal.browser.account.progress.PetalXpBackupRestoreDialog(
        onDismiss = { showXpBackup = false },
        onMessage = { m -> scope.launch { snackbar.showSnackbar(m) } }
    )

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Firefox Account & Sync", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── account ──
            item {
                Section {
                    when (val s = state) {
                        is FxaUi.SignedIn -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.AccountCircle, null, Modifier.size(44.dp), tint = Color(0xFFFF9400))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(s.displayName ?: s.email, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (s.displayName != null) Text(s.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        if (syncing) "Syncing…" else if (lastSync > 0) "Last synced ${DateUtils.getRelativeTimeSpanString(lastSync)}" else "Not synced yet",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { PetalFxa.syncNow() }, enabled = !syncing, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.Sync, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Sync now")
                                }
                                OutlinedButton(onClick = { PetalFxa.signOut() }, modifier = Modifier.weight(1f)) { Text("Sign out") }
                            }
                        }
                        FxaUi.Connecting -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Waiting for sign-in…", fontWeight = FontWeight.SemiBold)
                                Text("Finish signing in on the page that opened.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { PetalFxa.cancelSignIn() }) { Text("Cancel") }
                        }
                        else -> {
                            val needsReauth = s == FxaUi.NeedsReauth
                            Text(if (needsReauth) "Reconnect your account" else "Sign in to Firefox", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                if (needsReauth) "Your Firefox Account needs you to sign in again to keep syncing."
                                else "Sync bookmarks and history, and send tabs between your devices. Data is end-to-end encrypted by Firefox Sync.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = {
                                scope.launch {
                                    PetalFxa.beginSignIn()?.let { url ->
                                        onOpenOAuth(PetalShortcut("Firefox sign in", url, "firefox-account", Color(0xFFFF9400)))
                                    }
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text(if (needsReauth) "Reconnect" else "Sign in") }
                        }
                    }
                }
            }

            // ── received tabs ──
            if (received.isNotEmpty()) {
                item { Text("Tabs sent to this device", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                items(received) { t ->
                    Section(Modifier.bouncyClickable {
                        PetalFxa.dismissReceived(t)
                        onOpenOAuth(PetalShortcut(t.title, t.url, "received-tab", Color(0xFFFF9400)))
                    }) {
                        Text(t.title.ifBlank { t.url }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("From ${t.from} · ${t.url}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // ── what to sync + device ──
            if (state is FxaUi.SignedIn) {
                item {
                    Section {
                        Text("What to sync", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        ToggleRow("Bookmarks", bookmarksOn) { bookmarksOn = it; PetalFxa.setEngine(SyncEngine.Bookmarks, it) }
                        ToggleRow("History", historyOn) { historyOn = it; PetalFxa.setEngine(SyncEngine.History, it) }
                        ToggleRow("Open tabs", tabsOn) { tabsOn = it; PetalFxa.setEngine(SyncEngine.Tabs, it) }
                        ToggleRow("Passwords", passwordsOn) { passwordsOn = it; PetalFxa.setEngine(SyncEngine.Passwords, it) }
                        if (passwordsOn) Text(
                            "Passwords are end-to-end encrypted by Firefox Sync and merged with your Petal password vault.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = deviceName, onValueChange = { deviceName = it }, singleLine = true,
                            label = { Text("Device name") }, modifier = Modifier.fillMaxWidth(),
                            trailingIcon = { TextButton(onClick = { PetalFxa.renameDevice(deviceName) }) { Text("Save") } }
                        )
                    }
                }
                item {
                    Section {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Your other devices", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { PetalFxa.refreshDevices() }) { Icon(Icons.Rounded.Refresh, "Refresh") }
                        }
                        if (devices.isEmpty()) Text("No other devices yet. Sign in to Firefox on another device to see it here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        devices.forEach { d ->
                            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Devices, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp))
                                Text(d.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (d.canReceiveTabs) Text("Send tab", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            if (state is FxaUi.SignedIn && tabsOn && remoteTabs.isNotEmpty()) {
                item { Text("Tabs on your other devices", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                items(remoteTabs.take(30)) { t ->
                    Section(Modifier.bouncyClickable { onOpenOAuth(PetalShortcut(t.title.ifBlank { t.url }, t.url, "synced-tab", Color(0xFFFF9400))) }) {
                        Text(t.title.ifBlank { t.url }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${t.device} · ${t.url}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // ── backup & restore ──
            item {
                Section {
                    Text("Backup & restore", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Petal backups include your sync choices and your bookmarks and history, so restoring on a new phone brings them back. " +
                            "Your Firefox sign-in is never saved in a backup: after restoring, sign in again and Petal merges everything with your account.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                Section(Modifier.bouncyClickable { showXpBackup = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.EnhancedEncryption, null); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Encrypted backup & recovery", fontWeight = FontWeight.Bold)
                            Text("Export and restore XP, levels and stats (.pt)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { com.petal.browser.account.progress.PetalRewardsCatalogCard(onOpen = { showRewards = true }) }
            item { com.petal.browser.account.progress.PetalSupportCard(onMessage = { m -> scope.launch { snackbar.showSnackbar(m) } }) }
        }
    }
}

@Composable
private fun Section(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked = checked, onCheckedChange = onChange)
    }
}
