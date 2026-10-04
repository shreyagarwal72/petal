package com.petal.browser.compose.settings.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import com.petal.browser.ui.components.petalTouchFeedback
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.containment.PetalHeroCard
import com.petal.browser.ui.containment.PetalSettingsSection
import com.petal.browser.unit.BackupUnit
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

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

    var showRestorePicker by remember { mutableStateOf(false) }
    val systemRestoreLauncher = rememberLauncherForActivityResult(
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


    if (showBackupDialog) {
        val allSelected = backupBookmarks && backupHistory && backupStartSites && backupTabSessions && backupSavedSites && backupSettings
        com.petal.browser.ui.containment.PetalMaterialAlertDialog(
            onDismissRequest = { showBackupDialog = false },
            shape = RoundedCornerShape(32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            icon = {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Backup,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = stringResource(R.string.ui_backup_options_json),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.ui_data_to_include),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(
                            onClick = {
                                val target = !allSelected
                                backupBookmarks = target
                                backupHistory = target
                                backupStartSites = target
                                backupTabSessions = target
                                backupSavedSites = target
                                backupSettings = target
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(if (allSelected) R.string.ui_deselect_all else R.string.ui_select_all),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            BackupOptionRow(
                                icon = Icons.Rounded.Bookmarks,
                                label = stringResource(R.string.ui_bookmarks),
                                checked = backupBookmarks,
                                onCheckedChange = { backupBookmarks = it }
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            BackupOptionRow(
                                icon = Icons.Rounded.History,
                                label = stringResource(R.string.ui_browsing_history),
                                checked = backupHistory,
                                onCheckedChange = { backupHistory = it }
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            BackupOptionRow(
                                icon = Icons.Rounded.Home,
                                label = stringResource(R.string.ui_home_screen_top_sites_shortcuts),
                                checked = backupStartSites,
                                onCheckedChange = { backupStartSites = it }
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            BackupOptionRow(
                                icon = Icons.Rounded.Tab,
                                label = stringResource(R.string.ui_open_tabs_tab_groups),
                                checked = backupTabSessions,
                                onCheckedChange = { backupTabSessions = it }
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            BackupOptionRow(
                                icon = Icons.Rounded.VpnLock,
                                label = stringResource(R.string.ui_site_whitelists_profiles),
                                checked = backupSavedSites,
                                onCheckedChange = { backupSavedSites = it }
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            BackupOptionRow(
                                icon = Icons.Rounded.Tune,
                                label = stringResource(R.string.ui_browser_themes_accessibility_settings),
                                checked = backupSettings,
                                onCheckedChange = { backupSettings = it }
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Secondary action buttons (Export Passwords & Custom Folder)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                showBackupDialog = false
                                com.petal.browser.passwords.PetalCredentialVault.init(context)
                                val json = com.petal.browser.passwords.PetalCredentialVault.exportToJson()
                                val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                                val fileName = "petal_passwords_backup_$timeStamp.json"
                                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                                val destFile = java.io.File(downloadsDir, fileName)
                                destFile.writeText(json, Charsets.UTF_8)
                                com.petal.browser.view.PetalToast.show(context, "Exported passwords to Downloads/$fileName")
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.ui_export_passwords),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                showBackupDialog = false
                                createBackupLauncher.launch("petal_browser_backup.json")
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.ui_custom_folder),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBackupDialog = false
                        BackupUnit.backupToDownloadManager(
                            context,
                            backupBookmarks,
                            backupHistory,
                            backupStartSites,
                            backupTabSessions,
                            backupSavedSites,
                            backupSettings
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.ui_download),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBackupDialog = false },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text(
                        stringResource(R.string.ui_cancel),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        )
    }

    if (showRestoreDialog) {
        val allRestoreSelected = restoreBookmarks && restoreHistory && restoreStartSites && restoreTabSessions && restoreSavedSites && restoreSettings
        com.petal.browser.ui.containment.PetalMaterialAlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            shape = RoundedCornerShape(32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            icon = {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Restore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = stringResource(R.string.ui_restore_options_json),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.ui_data_to_restore),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        TextButton(
                            onClick = {
                                val target = !allRestoreSelected
                                restoreBookmarks = target
                                restoreHistory = target
                                restoreStartSites = target
                                restoreTabSessions = target
                                restoreSavedSites = target
                                restoreSettings = target
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(if (allRestoreSelected) R.string.ui_deselect_all else R.string.ui_select_all),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            BackupOptionRow(
                                icon = Icons.Rounded.Bookmarks,
                                label = stringResource(R.string.ui_bookmarks),
                                checked = restoreBookmarks,
                                onCheckedChange = { restoreBookmarks = it }
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            BackupOptionRow(
                                icon = Icons.Rounded.History,
                                label = stringResource(R.string.ui_browsing_history),
                                checked = restoreHistory,
                                onCheckedChange = { restoreHistory = it }
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            BackupOptionRow(
                                icon = Icons.Rounded.Home,
                                label = stringResource(R.string.ui_home_screen_top_sites_shortcuts),
                                checked = restoreStartSites,
                                onCheckedChange = { restoreStartSites = it }
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            BackupOptionRow(
                                icon = Icons.Rounded.Tab,
                                label = stringResource(R.string.ui_open_tabs_tab_groups),
                                checked = restoreTabSessions,
                                onCheckedChange = { restoreTabSessions = it }
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            BackupOptionRow(
                                icon = Icons.Rounded.VpnLock,
                                label = stringResource(R.string.ui_site_whitelists_profiles),
                                checked = restoreSavedSites,
                                onCheckedChange = { restoreSavedSites = it }
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            BackupOptionRow(
                                icon = Icons.Rounded.Tune,
                                label = stringResource(R.string.ui_browser_themes_accessibility_settings),
                                checked = restoreSettings,
                                onCheckedChange = { restoreSettings = it }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreDialog = false
                        showRestorePicker = true
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.ui_choose_backup_file),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRestoreDialog = false },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text(
                        stringResource(R.string.ui_cancel),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
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
                PetalSettingsSection(
                    title = stringResource(R.string.ui_backup_restore_json),
                    iconRes = com.petal.browser.R.drawable.backup_filled
                ) {
                    Text(
                        stringResource(R.string.ui_export_backups_directly_to_downloads),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )

                    com.petal.browser.ui.containment.PetalGroup(rowCount = 2) { index, position ->
                        when (index) {
                            0 -> {
                                com.petal.browser.ui.containment.PetalGroupRow(
                                    icon = Icons.Filled.CloudUpload,
                                    title = stringResource(R.string.ui_backup_json),
                                    subtitle = "Export bookmarks, history, settings and vault",
                                    position = position,
                                    onClick = { showBackupDialog = true },
                                    trailing = {
                                        Button(
                                            onClick = { showBackupDialog = true },
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Text(stringResource(R.string.ui_backup_json))
                                        }
                                    }
                                )
                            }
                            1 -> {
                                com.petal.browser.ui.containment.PetalGroupRow(
                                    icon = Icons.Rounded.Restore,
                                    title = stringResource(R.string.ui_restore_json),
                                    subtitle = "Restore browser profile from local JSON file",
                                    position = position,
                                    onClick = { showRestoreDialog = true },
                                    trailing = {
                                        OutlinedButton(
                                            onClick = { showRestoreDialog = true },
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Text(stringResource(R.string.ui_restore_json))
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }

        if (showRestorePicker) {
            com.petal.browser.compose.file.PetalFilePickerScreen(
                mimeTypes = arrayOf("application/json", "*/*"),
                onDismissRequest = { showRestorePicker = false },
                onFileSelected = { file ->
                    showRestorePicker = false
                    val uri = android.net.Uri.fromFile(file)
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
                },
                onBrowseSystemFallback = {
                    showRestorePicker = false
                    systemRestoreLauncher.launch(arrayOf("application/json", "*/*"))
                }
            )
        }
    }
}

@Composable
private fun BackupOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "BackupOptionRowScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = { onCheckedChange(!checked) }
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .petalTouchFeedback(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            com.petal.browser.ui.containment.PetalGroupIconBadge(
                icon = icon,
                variant = if (checked) com.petal.browser.ui.containment.PetalBadgeVariant.PRIMARY else com.petal.browser.ui.containment.PetalBadgeVariant.SURFACE_TONAL,
                size = 36.dp,
                iconSize = 18.dp,
                shape = RoundedCornerShape(10.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                checkmarkColor = MaterialTheme.colorScheme.onPrimary
            )
        )
    }
}

