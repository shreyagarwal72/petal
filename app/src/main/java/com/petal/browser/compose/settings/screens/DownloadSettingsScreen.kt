package com.petal.browser.compose.settings.screens

import com.petal.browser.ui.containment.PetalSettingsSection

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.preference.PreferenceManager
import com.petal.browser.compose.downloads.LiveUpdateNotificationManager
import com.petal.browser.compose.settings.viewmodel.DownloadSettingsViewModel
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.unit.ExternalDownloadManagerHelper
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

@Composable
fun DownloadSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetHighlightItemId: String? = null,
    viewModel: DownloadSettingsViewModel = hiltViewModel()
) {
    val downloadManagerMode by viewModel.downloadManagerMode.collectAsStateWithLifecycle()
    val confirmDownloadDelete by viewModel.confirmDownloadDelete.collectAsStateWithLifecycle()
    val deleteDownloadFile by viewModel.deleteDownloadFile.collectAsStateWithLifecycle()
    val autoPreviewDownloadedImages by viewModel.autoPreviewDownloadedImages.collectAsStateWithLifecycle()
    val liveUpdates by viewModel.liveUpdates.collectAsStateWithLifecycle()

    DownloadSettingsScreenContent(
        downloadManagerMode = downloadManagerMode,
        confirmFileDelete = confirmDownloadDelete,
        deleteFromStorage = deleteDownloadFile,
        autoPreviewDownloadedImages = autoPreviewDownloadedImages,
        liveUpdates = liveUpdates,
        onDownloadManagerModeChange = viewModel::setDownloadManagerMode,
        onConfirmFileDeleteChange = viewModel::setConfirmDownloadDelete,
        onDeleteFromStorageChange = viewModel::setDeleteDownloadFile,
        onAutoPreviewDownloadedImagesChange = viewModel::setAutoPreviewDownloadedImages,
        onLiveUpdatesChange = viewModel::setLiveUpdates,
        onNavigateBack = onNavigateBack,
        targetHighlightItemId = targetHighlightItemId,
        modifier = modifier
    )
}

@Composable
fun DownloadSettingsScreenContent(
    downloadManagerMode: String,
    confirmFileDelete: Boolean,
    deleteFromStorage: Boolean,
    autoPreviewDownloadedImages: Boolean,
    liveUpdates: Boolean,
    onDownloadManagerModeChange: (String) -> Unit,
    onConfirmFileDeleteChange: (Boolean) -> Unit,
    onDeleteFromStorageChange: (Boolean) -> Unit,
    onAutoPreviewDownloadedImagesChange: (Boolean) -> Unit,
    onLiveUpdatesChange: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    targetHighlightItemId: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val installedDownloaders = remember(context) {
        ExternalDownloadManagerHelper.getInstalledDownloaders(context)
    }

    Box(modifier = modifier.fillMaxSize()) {
        M3ExpressiveVariableBackground(pageSeed = "download_settings")

        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveHeader(
                title = "Downloads",
                subtitle = "Download engine, live progress chips and file management",
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
                // Download Deletion Card
                PetalSettingsSection(
                    title = stringResource(R.string.ui_download_deletion),
                    icon = Icons.Rounded.Delete,
                    cardId = "misc_download_delete",
                    targetHighlightId = targetHighlightItemId
                ) {
                    com.petal.browser.ui.containment.PetalGroup(rowCount = 2) { index, position ->
                        when (index) {
                            0 -> {
                                com.petal.browser.ui.containment.PetalSettingsToggleRow(
                                    title = stringResource(R.string.ui_confirm_file_deletion),
                                    subtitle = stringResource(R.string.ui_ask_before_removing_a_download),
                                    icon = Icons.Rounded.HelpOutline,
                                    checked = confirmFileDelete,
                                    position = position,
                                    onCheckedChange = onConfirmFileDeleteChange
                                )
                            }
                            1 -> {
                                com.petal.browser.ui.containment.PetalSettingsToggleRow(
                                    title = stringResource(R.string.ui_delete_file_from_storage),
                                    subtitle = stringResource(R.string.ui_use_this_as_the_default),
                                    icon = Icons.Rounded.DeleteForever,
                                    checked = deleteFromStorage,
                                    position = position,
                                    onCheckedChange = onDeleteFromStorageChange
                                )
                            }
                        }
                    }
                }

                // Download behavior (preview + live updates)
                PetalSettingsSection(
                    title = stringResource(R.string.ui_download_behavior),
                    icon = Icons.Rounded.Tune,
                    cardId = "misc_download_behavior",
                    targetHighlightId = targetHighlightItemId
                ) {

                    var showPermissionDialog by remember { mutableStateOf(false) }
                    var showPromotedSettingsDialog by remember { mutableStateOf(false) }

                    val notifPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        if (isGranted) {
                            onLiveUpdatesChange(true)
                            if (Build.VERSION.SDK_INT >= 36 && !LiveUpdateNotificationManager.canPostPromotedNotifications(context)) {
                                showPromotedSettingsDialog = true
                            }
                        } else {
                            onLiveUpdatesChange(false)
                        }
                    }

                    val notificationsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

                    com.petal.browser.ui.containment.PetalGroup(rowCount = 2) { rowIndex, rowPosition ->
                    if (rowIndex == 0) {
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_auto_preview_downloaded_images),
                        subtitle = stringResource(R.string.ui_show_downloaded_photos_in_the),
                        icon = Icons.Rounded.Image,
                        checked = autoPreviewDownloadedImages,
                        position = rowPosition,
                        onCheckedChange = onAutoPreviewDownloadedImagesChange
                    )
                    } else {
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_live_updates_alerts),
                        subtitle = stringResource(R.string.ui_show_live_progress_chip_in),
                        icon = Icons.Rounded.NotificationsActive,
                        checked = liveUpdates && notificationsAllowed,
                        position = rowPosition,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) {
                                        onLiveUpdatesChange(true)
                                        if (Build.VERSION.SDK_INT >= 36 && !LiveUpdateNotificationManager.canPostPromotedNotifications(context)) {
                                            showPromotedSettingsDialog = true
                                        }
                                    } else {
                                        showPermissionDialog = true
                                    }
                                } else {
                                    onLiveUpdatesChange(true)
                                }
                            } else {
                                onLiveUpdatesChange(false)
                            }
                        }
                    )
                    }
                    }

                    if (showPermissionDialog) {
                        com.petal.browser.ui.containment.PetalMaterialAlertDialog(
                            onDismissRequest = { showPermissionDialog = false },
                            icon = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null) },
                            title = { Text(text = stringResource(R.string.ui_enable_live_notifications)) },
                            text = {
                                Text(
                                    text = stringResource(R.string.ui_to_display_real_time_download)
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showPermissionDialog = false
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    }
                                ) {
                                    Text(stringResource(R.string.ui_grant_permission))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showPermissionDialog = false }) {
                                    Text(stringResource(R.string.ui_cancel))
                                }
                            }
                        )
                    }

                    if (showPromotedSettingsDialog) {
                        com.petal.browser.ui.containment.PetalMaterialAlertDialog(
                            onDismissRequest = { showPromotedSettingsDialog = false },
                            icon = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null) },
                            title = { Text(text = stringResource(R.string.ui_promoted_live_updates)) },
                            text = {
                                Text(
                                    text = stringResource(R.string.ui_your_device_supports_promoted_status)
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showPromotedSettingsDialog = false
                                        try {
                                            context.startActivity(LiveUpdateNotificationManager.getLiveNotificationSettingsIntent(context))
                                        } catch (e: Exception) {
                                            // Fallback to app settings
                                            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = android.net.Uri.parse("package:${context.packageName}")
                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        }
                                    }
                                ) {
                                    Text(stringResource(R.string.ui_open_settings))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showPromotedSettingsDialog = false }) {
                                    Text(stringResource(R.string.ui_dismiss))
                                }
                            }
                        )
                    }

                }

                // Default Download Manager Card
                PetalSettingsSection(
                    title = stringResource(R.string.ui_default_download_manager),
                    icon = Icons.Rounded.Download,
                    cardId = "misc_download",
                    targetHighlightId = targetHighlightItemId
                ) {
                    Text(
                        text = stringResource(R.string.ui_choose_whether_downloads_are_handled),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val totalOptions = 2 + installedDownloaders.size
                    com.petal.browser.ui.containment.PetalGroup(rowCount = totalOptions) { index, position ->
                        when {
                            index == 0 -> {
                                val isInApp = downloadManagerMode == ExternalDownloadManagerHelper.MODE_IN_APP
                                com.petal.browser.ui.containment.PetalGroupListRow(
                                    position = position,
                                    selected = isInApp,
                                    onClick = { onDownloadManagerModeChange(ExternalDownloadManagerHelper.MODE_IN_APP) },
                                    leading = {
                                        com.petal.browser.ui.containment.PetalGroupIconBadge(
                                            icon = Icons.Rounded.Speed,
                                            variant = if (isInApp) com.petal.browser.ui.containment.PetalBadgeVariant.PRIMARY else com.petal.browser.ui.containment.PetalBadgeVariant.SURFACE_TONAL
                                        )
                                    },
                                    content = {
                                        Text(
                                            text = stringResource(R.string.ui_in_app_downloader_fast_multi),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(R.string.ui_native_petal_accelerated_downloader_wi),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailing = {
                                        RadioButton(
                                            selected = isInApp,
                                            onClick = { onDownloadManagerModeChange(ExternalDownloadManagerHelper.MODE_IN_APP) }
                                        )
                                    }
                                )
                            }
                            index <= installedDownloaders.size -> {
                                val downloader = installedDownloaders[index - 1]
                                val isSelected = downloadManagerMode.equals(downloader.key, ignoreCase = true)
                                com.petal.browser.ui.containment.PetalGroupListRow(
                                    position = position,
                                    selected = isSelected,
                                    onClick = { onDownloadManagerModeChange(downloader.key) },
                                    leading = {
                                        com.petal.browser.ui.containment.PetalGroupIconBadge(
                                            icon = Icons.Rounded.OpenInNew,
                                            variant = if (isSelected) com.petal.browser.ui.containment.PetalBadgeVariant.PRIMARY else com.petal.browser.ui.containment.PetalBadgeVariant.SURFACE_TONAL
                                        )
                                    },
                                    content = {
                                        Text(
                                            text = downloader.displayName,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(R.string.ui_installed_external_download_manager_wi),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailing = {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { onDownloadManagerModeChange(downloader.key) }
                                        )
                                    }
                                )
                            }
                            else -> {
                                val isExternalAuto = downloadManagerMode != ExternalDownloadManagerHelper.MODE_IN_APP &&
                                    installedDownloaders.none { it.key.equals(downloadManagerMode, ignoreCase = true) }
                                com.petal.browser.ui.containment.PetalGroupListRow(
                                    position = position,
                                    selected = isExternalAuto,
                                    onClick = { onDownloadManagerModeChange(ExternalDownloadManagerHelper.MODE_EXTERNAL_AUTO) },
                                    leading = {
                                        com.petal.browser.ui.containment.PetalGroupIconBadge(
                                            icon = Icons.Rounded.OpenInNew,
                                            variant = if (isExternalAuto) com.petal.browser.ui.containment.PetalBadgeVariant.PRIMARY else com.petal.browser.ui.containment.PetalBadgeVariant.SURFACE_TONAL
                                        )
                                    },
                                    content = {
                                        Text(
                                            text = stringResource(R.string.ui_external_app_auto_chooser),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(R.string.ui_prompt_system_chooser_or_dispatch),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailing = {
                                        RadioButton(
                                            selected = isExternalAuto,
                                            onClick = { onDownloadManagerModeChange(ExternalDownloadManagerHelper.MODE_EXTERNAL_AUTO) }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
