package com.petal.browser.compose.settings.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.unit.BackupUnit
import com.petal.browser.unit.BookmarkHtmlImporterExporter

@Composable
fun DataBackupSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    var backupBookmarks by remember { mutableStateOf(true) }
    var backupHistory by remember { mutableStateOf(true) }
    var backupStartSites by remember { mutableStateOf(true) }
    var backupTabSessions by remember { mutableStateOf(true) }
    var backupSavedSites by remember { mutableStateOf(true) }
    var backupSettings by remember { mutableStateOf(true) }

    var restoreBookmarks by remember { mutableStateOf(true) }
    var restoreHistory by remember { mutableStateOf(true) }
    var restoreStartSites by remember { mutableStateOf(true) }
    var restoreTabSessions by remember { mutableStateOf(true) }
    var restoreSavedSites by remember { mutableStateOf(true) }
    var restoreSettings by remember { mutableStateOf(true) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            BackupUnit.backupToUri(
                context,
                uri,
                backupBookmarks,
                backupHistory,
                backupStartSites,
                backupTabSessions,
                backupSavedSites,
                backupSettings
            )
        }
    }

    val openRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            BackupUnit.restoreFromUri(
                context,
                uri,
                restoreBookmarks,
                restoreHistory,
                restoreStartSites,
                restoreTabSessions,
                restoreSavedSites,
                restoreSettings
            )
        }
    }

    val exportBookmarksHtmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/html")
    ) { uri: Uri? ->
        if (uri != null) {
            BookmarkHtmlImporterExporter.exportToUri(context, uri)
        }
    }

    val importBookmarksHtmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            BookmarkHtmlImporterExporter.importFromUri(context, uri)
        }
    }

    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text("Backup Options (JSON)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select items to include in backup file:")
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { backupBookmarks = !backupBookmarks }) {
                        Checkbox(checked = backupBookmarks, onCheckedChange = { backupBookmarks = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Bookmarks")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { backupHistory = !backupHistory }) {
                        Checkbox(checked = backupHistory, onCheckedChange = { backupHistory = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Browsing History")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { backupStartSites = !backupStartSites }) {
                        Checkbox(checked = backupStartSites, onCheckedChange = { backupStartSites = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Home Screen Top Sites & Shortcuts")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { backupTabSessions = !backupTabSessions }) {
                        Checkbox(checked = backupTabSessions, onCheckedChange = { backupTabSessions = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Open Tabs & Tab Groups")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { backupSavedSites = !backupSavedSites }) {
                        Checkbox(checked = backupSavedSites, onCheckedChange = { backupSavedSites = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Site Whitelists & Profiles")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { backupSettings = !backupSettings }) {
                        Checkbox(checked = backupSettings, onCheckedChange = { backupSettings = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Browser, Themes & Accessibility Settings")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showBackupDialog = false
                    createBackupLauncher.launch("petal_browser_backup.json")
                }) {
                    Text("Choose Save Folder")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Restore Options (JSON)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select items to restore from JSON file:")
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { restoreBookmarks = !restoreBookmarks }) {
                        Checkbox(checked = restoreBookmarks, onCheckedChange = { restoreBookmarks = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Bookmarks")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { restoreHistory = !restoreHistory }) {
                        Checkbox(checked = restoreHistory, onCheckedChange = { restoreHistory = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Browsing History")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { restoreStartSites = !restoreStartSites }) {
                        Checkbox(checked = restoreStartSites, onCheckedChange = { restoreStartSites = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Home Screen Top Sites & Shortcuts")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { restoreTabSessions = !restoreTabSessions }) {
                        Checkbox(checked = restoreTabSessions, onCheckedChange = { restoreTabSessions = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Open Tabs & Tab Groups")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { restoreSavedSites = !restoreSavedSites }) {
                        Checkbox(checked = restoreSavedSites, onCheckedChange = { restoreSavedSites = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Site Whitelists & Profiles")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { restoreSettings = !restoreSettings }) {
                        Checkbox(checked = restoreSettings, onCheckedChange = { restoreSettings = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Browser, Themes & Accessibility Settings")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showRestoreDialog = false
                    openRestoreLauncher.launch(arrayOf("application/json", "*/*"))
                }) {
                    Text("Choose Backup File")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        M3ExpressiveVariableBackground(pageSeed = "data_storage_settings")

        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveHeader(
                title = "Data & Backup",
                subtitle = "Backup and restore history, bookmarks & settings",
                onBack = onNavigateBack
            )

            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Backup & Restore (JSON) Card
                SettingsCategoryCard(title = "Backup & Restore (JSON)", iconRes = com.petal.browser.R.drawable.backup_filled) {
                    Text(
                        "Export or restore specific items to/from a single JSON file (Documents/browser_backup/petal_browser_backup.json):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { showBackupDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Backup JSON", maxLines = 1)
                            }
                        }

                        Button(
                            onClick = { showRestoreDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text("Restore JSON", maxLines = 1)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Text(
                        "HTML Bookmarks (Standard Netscape Format — Chrome, Firefox, Safari):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { exportBookmarksHtmlLauncher.launch("bookmarks.html") },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Export HTML", maxLines = 1)
                            }
                        }

                        OutlinedButton(
                            onClick = { importBookmarksHtmlLauncher.launch(arrayOf("text/html", "text/plain", "*/*")) },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Import HTML", maxLines = 1)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
