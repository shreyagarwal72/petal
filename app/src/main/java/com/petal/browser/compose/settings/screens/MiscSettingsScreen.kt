package com.petal.browser.compose.settings.screens

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
import com.petal.browser.compose.downloads.LiveUpdateNotificationManager
import com.petal.browser.compose.settings.viewmodel.MiscSettingsViewModel
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.unit.ExternalDownloadManagerHelper

@Composable
fun MiscSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetHighlightItemId: String? = null,
    viewModel: MiscSettingsViewModel = hiltViewModel()
) {
    val autoOpenApps by viewModel.autoOpenApps.collectAsStateWithLifecycle()
    val checkUpdateOnLaunch by viewModel.checkUpdateOnLaunch.collectAsStateWithLifecycle()
    val downloadManagerMode by viewModel.downloadManagerMode.collectAsStateWithLifecycle()
    val autoPreviewDownloadedImages by viewModel.autoPreviewDownloadedImages.collectAsStateWithLifecycle()
    val liveUpdates by viewModel.liveUpdates.collectAsStateWithLifecycle()

    MiscSettingsScreenContent(
        autoOpenApps = autoOpenApps,
        checkUpdateOnLaunch = checkUpdateOnLaunch,
        downloadManagerMode = downloadManagerMode,
        autoPreviewDownloadedImages = autoPreviewDownloadedImages,
        liveUpdates = liveUpdates,
        onAutoOpenAppsChange = viewModel::setAutoOpenApps,
        onCheckUpdateOnLaunchChange = viewModel::setCheckUpdateOnLaunch,
        onDownloadManagerModeChange = viewModel::setDownloadManagerMode,
        onAutoPreviewDownloadedImagesChange = viewModel::setAutoPreviewDownloadedImages,
        onLiveUpdatesChange = viewModel::setLiveUpdates,
        onNavigateBack = onNavigateBack,
        targetHighlightItemId = targetHighlightItemId,
        modifier = modifier
    )
}

@Composable
fun MiscSettingsScreenContent(
    autoOpenApps: Boolean,
    checkUpdateOnLaunch: Boolean,
    downloadManagerMode: String,
    autoPreviewDownloadedImages: Boolean,
    liveUpdates: Boolean,
    onAutoOpenAppsChange: (Boolean) -> Unit,
    onCheckUpdateOnLaunchChange: (Boolean) -> Unit,
    onDownloadManagerModeChange: (String) -> Unit,
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
        M3ExpressiveVariableBackground(pageSeed = "misc_settings")

        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveHeader(
                title = "Miscellaneous",
                subtitle = "Download preferences, external apps and tools",
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
                // Default Download Manager Card
                SettingsCategoryCard(
                    title = "Default Download Manager",
                    icon = Icons.Rounded.Download,
                    cardId = "misc_download",
                    targetHighlightId = targetHighlightItemId
                ) {
                    Text(
                        text = "Choose whether downloads are handled by Petal's high-speed in-app downloader or redirected to an external download manager.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    ToggleRow(
                        title = "Auto-preview downloaded images",
                        subtitle = "Show downloaded photos in the manager like Chrome",
                        icon = Icons.Rounded.Image,
                        checked = autoPreviewDownloadedImages,
                        onCheckedChange = onAutoPreviewDownloadedImagesChange
                    )

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

                    ToggleRow(
                        title = "Live updates & alerts",
                        subtitle = "Show live progress chip in status bar with animated doll runner for active downloads",
                        icon = Icons.Rounded.NotificationsActive,
                        checked = liveUpdates,
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

                    if (showPermissionDialog) {
                        AlertDialog(
                            onDismissRequest = { showPermissionDialog = false },
                            icon = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null) },
                            title = { Text(text = "Enable Live Notifications") },
                            text = {
                                Text(
                                    text = "To display real-time download progress, velocity, and the animated running doll in your status bar, Petal requires notification permission."
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
                                    Text("Grant Permission")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showPermissionDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }

                    if (showPromotedSettingsDialog) {
                        AlertDialog(
                            onDismissRequest = { showPromotedSettingsDialog = false },
                            icon = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null) },
                            title = { Text(text = "Promoted Live Updates") },
                            text = {
                                Text(
                                    text = "Your device supports promoted status bar live chips (Android 16+). To display the running doll chip persistently in your status bar, verify live updates are permitted in notification settings."
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
                                    Text("Open Settings")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showPromotedSettingsDialog = false }) {
                                    Text("Dismiss")
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Option 1: In-App Downloader (Default)
                    val isInApp = downloadManagerMode == ExternalDownloadManagerHelper.MODE_IN_APP
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isInApp) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDownloadManagerModeChange(ExternalDownloadManagerHelper.MODE_IN_APP) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = if (isInApp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Speed,
                                        contentDescription = null,
                                        tint = if (isInApp) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "In-App Downloader (Fast, Multi-Threaded)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isInApp) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Native Petal accelerated downloader with background notifications",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            RadioButton(
                                selected = isInApp,
                                onClick = { onDownloadManagerModeChange(ExternalDownloadManagerHelper.MODE_IN_APP) }
                            )
                        }
                    }

                    // Option 2: Detected installed download managers
                    installedDownloaders.forEach { downloader ->
                        val isSelected = downloadManagerMode.equals(downloader.key, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDownloadManagerModeChange(downloader.key) }
                        ) {
                            Row(
                                modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.OpenInNew,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = downloader.displayName,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Installed external download manager with auto-redirect",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onDownloadManagerModeChange(downloader.key) }
                                )
                            }
                        }
                    }

                    // Option 3: External App (Chooser)
                    val isExternalAuto = downloadManagerMode == ExternalDownloadManagerHelper.MODE_EXTERNAL_AUTO
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isExternalAuto) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDownloadManagerModeChange(ExternalDownloadManagerHelper.MODE_EXTERNAL_AUTO) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = if (isExternalAuto) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.OpenInNew,
                                        contentDescription = null,
                                        tint = if (isExternalAuto) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "External App (Auto Chooser)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Prompt system chooser or dispatch directly to any available external downloader",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            RadioButton(
                                selected = isExternalAuto,
                                onClick = { onDownloadManagerModeChange(ExternalDownloadManagerHelper.MODE_EXTERNAL_AUTO) }
                            )
                        }
                    }
                }

                // External Applications & Tools Card
                SettingsCategoryCard(
                    title = "External Applications & Links",
                    iconRes = com.petal.browser.R.drawable.download_2_filled,
                    cardId = "misc_apps",
                    targetHighlightId = targetHighlightItemId
                ) {
                    ToggleRow(
                        title = "Auto Open External Apps",
                        subtitle = "Allow YouTube, Maps & Play Store links to open in external native apps instead of Petal",
                        icon = Icons.Rounded.Launch,
                        checked = autoOpenApps,
                        onCheckedChange = onAutoOpenAppsChange
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

