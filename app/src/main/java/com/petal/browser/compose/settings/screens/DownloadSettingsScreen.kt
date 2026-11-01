package com.petal.browser.compose.settings.screens
import com.petal.browser.ui.components.scrollMotionBlur

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.preference.PreferenceManager
import com.petal.browser.compose.downloads.LiveUpdateNotificationManager
import com.petal.browser.compose.settings.viewmodel.DownloadSettingsViewModel
import com.petal.browser.download.PetalBatteryOptimization
import com.petal.browser.download.PetalParallelFetchClient
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.unit.ExternalDownloadManagerHelper
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import com.petal.browser.R
import com.petal.browser.island.IslandAnimationStyle
import com.petal.browser.island.IslandAnimationSpeed
import com.petal.browser.island.IslandAnimationBounce
import com.petal.browser.island.IslandMotion
import com.petal.browser.island.PetalWavyProgressBar
import kotlinx.coroutines.launch
import java.util.Locale

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
    val islandMode by viewModel.islandMode.collectAsStateWithLifecycle()
    val islandAnimationStyle by viewModel.islandAnimationStyle.collectAsStateWithLifecycle()
    val islandAnimationSpeed by viewModel.islandAnimationSpeed.collectAsStateWithLifecycle()
    val islandAnimationBounce by viewModel.islandAnimationBounce.collectAsStateWithLifecycle()
    val islandWavyProgress by viewModel.islandWavyProgress.collectAsStateWithLifecycle()

    DownloadSettingsScreenContent(
        downloadManagerMode = downloadManagerMode,
        confirmFileDelete = confirmDownloadDelete,
        deleteFromStorage = deleteDownloadFile,
        autoPreviewDownloadedImages = autoPreviewDownloadedImages,
        liveUpdates = liveUpdates,
        islandMode = islandMode,
        islandAnimationStyle = islandAnimationStyle,
        islandAnimationSpeed = islandAnimationSpeed,
        islandAnimationBounce = islandAnimationBounce,
        islandWavyProgress = islandWavyProgress,
        onDownloadManagerModeChange = viewModel::setDownloadManagerMode,
        onConfirmFileDeleteChange = viewModel::setConfirmDownloadDelete,
        onDeleteFromStorageChange = viewModel::setDeleteDownloadFile,
        onAutoPreviewDownloadedImagesChange = viewModel::setAutoPreviewDownloadedImages,
        onLiveUpdatesChange = viewModel::setLiveUpdates,
        onIslandModeChange = viewModel::setIslandMode,
        onIslandAnimationStyleChange = viewModel::setIslandAnimationStyle,
        onIslandAnimationSpeedChange = viewModel::setIslandAnimationSpeed,
        onIslandAnimationBounceChange = viewModel::setIslandAnimationBounce,
        onIslandWavyProgressChange = viewModel::setIslandWavyProgress,
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
    islandMode: String,
    islandAnimationStyle: String,
    islandAnimationSpeed: String,
    islandAnimationBounce: String,
    islandWavyProgress: Boolean,
    onDownloadManagerModeChange: (String) -> Unit,
    onConfirmFileDeleteChange: (Boolean) -> Unit,
    onDeleteFromStorageChange: (Boolean) -> Unit,
    onAutoPreviewDownloadedImagesChange: (Boolean) -> Unit,
    onLiveUpdatesChange: (Boolean) -> Unit,
    onIslandModeChange: (String) -> Unit,
    onIslandAnimationStyleChange: (String) -> Unit,
    onIslandAnimationSpeedChange: (String) -> Unit,
    onIslandAnimationBounceChange: (String) -> Unit,
    onIslandWavyProgressChange: (Boolean) -> Unit,
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
                    .scrollMotionBlur(scrollState)
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

                    com.petal.browser.ui.containment.PetalGroup(rowCount = 1) { _, rowPosition ->
                        com.petal.browser.ui.containment.PetalSettingsToggleRow(
                            title = stringResource(R.string.ui_auto_preview_downloaded_images),
                            subtitle = stringResource(R.string.ui_show_downloaded_photos_in_the),
                            icon = Icons.Rounded.Image,
                            checked = autoPreviewDownloadedImages,
                            position = rowPosition,
                            onCheckedChange = onAutoPreviewDownloadedImagesChange
                        )
                    }
                }

                // Live Actions & Expressive Cutout Island (Android 16 & Non-Supported Fallback)
                PetalSettingsSection(
                    title = "Live Actions & Cutout Island",
                    icon = Icons.Rounded.DynamicFeed,
                    cardId = "live_actions_cutout_island",
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

                    // Master Toggle
                    com.petal.browser.ui.containment.PetalGroup(rowCount = 1) { _, rowPosition ->
                        com.petal.browser.ui.containment.PetalSettingsToggleRow(
                            title = stringResource(R.string.ui_live_updates_alerts),
                            subtitle = "Promoted status bar chip on Android 16 & expressive dynamic island on non-supported devices",
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

                    if (liveUpdates && notificationsAllowed) {
                        Spacer(modifier = Modifier.height(10.dp))

                        // Interactive Motion Preview Card
                        IslandAnimationPreviewCard(
                            style = islandAnimationStyle,
                            speed = islandAnimationSpeed,
                            bounce = islandAnimationBounce,
                            wavy = islandWavyProgress
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Display Mode Group
                        Text(
                            text = "Presentation Mode",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )

                        val modes = listOf(
                            Triple("AUTO", "Auto (Recommended)", "Android 16 status bar chip on supported, Cutout Island on others"),
                            Triple("ALWAYS_ISLAND", "Always Cutout Island", "Force the Material 3 Expressive dynamic island on all devices"),
                            Triple("NOTIFICATION_ONLY", "Notification Bar Only", "Standard notification shade alerts without cutout island")
                        )

                        com.petal.browser.ui.containment.PetalGroup(rowCount = modes.size) { index, position ->
                            val (key, title, subtitle) = modes[index]
                            val isSelected = islandMode.equals(key, ignoreCase = true)
                            com.petal.browser.ui.containment.PetalGroupListRow(
                                position = position,
                                selected = isSelected,
                                onClick = { onIslandModeChange(key) },
                                leading = {
                                    com.petal.browser.ui.containment.PetalGroupIconBadge(
                                        icon = if (key == "ALWAYS_ISLAND") Icons.Rounded.SmartDisplay else if (key == "AUTO") Icons.Rounded.AutoAwesome else Icons.Rounded.Notifications,
                                        variant = if (isSelected) com.petal.browser.ui.containment.PetalBadgeVariant.PRIMARY else com.petal.browser.ui.containment.PetalBadgeVariant.SURFACE_TONAL
                                    )
                                },
                                content = {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailing = {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { onIslandModeChange(key) }
                                    )
                                }
                            )
                        }

                        if (!islandMode.equals("NOTIFICATION_ONLY", ignoreCase = true)) {
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Motion & Spring Physics",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )

                            // Animation Style Group
                            val styles = listOf(
                                Pair("EXPRESSIVE", "Expressive (Spatial Springs & Bounce)"),
                                Pair("EASE_IN_OUT", "Standard (Ease In/Out)")
                            )
                            com.petal.browser.ui.containment.PetalGroup(rowCount = styles.size) { index, position ->
                                val (key, label) = styles[index]
                                val isSelected = islandAnimationStyle.equals(key, ignoreCase = true)
                                com.petal.browser.ui.containment.PetalGroupListRow(
                                    position = position,
                                    selected = isSelected,
                                    onClick = { onIslandAnimationStyleChange(key) },
                                    leading = {
                                        com.petal.browser.ui.containment.PetalGroupIconBadge(
                                            icon = Icons.Rounded.Animation,
                                            variant = if (isSelected) com.petal.browser.ui.containment.PetalBadgeVariant.PRIMARY else com.petal.browser.ui.containment.PetalBadgeVariant.SURFACE_TONAL
                                        )
                                    },
                                    content = {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                    },
                                    trailing = {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { onIslandAnimationStyleChange(key) }
                                        )
                                    }
                                )
                            }

                            if (islandAnimationStyle.equals("EXPRESSIVE", ignoreCase = true)) {
                                Spacer(modifier = Modifier.height(8.dp))

                                com.petal.browser.ui.containment.PetalGroup(rowCount = 2) { index, position ->
                                    if (index == 0) {
                                        val speeds = listOf("SLOW", "DEFAULT", "FAST")
                                        val currentSpeed = islandAnimationSpeed.uppercase(Locale.US)
                                        com.petal.browser.ui.containment.PetalGroupListRow(
                                            position = position,
                                            onClick = {
                                                val nextIdx = (speeds.indexOf(currentSpeed) + 1) % speeds.size
                                                onIslandAnimationSpeedChange(speeds[nextIdx])
                                            },
                                            leading = {
                                                com.petal.browser.ui.containment.PetalGroupIconBadge(
                                                    icon = Icons.Rounded.Speed,
                                                    variant = com.petal.browser.ui.containment.PetalBadgeVariant.SURFACE_TONAL
                                                )
                                            },
                                            content = {
                                                Text("Spring Stiffness & Pace", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                                Text(
                                                    when (currentSpeed) {
                                                        "SLOW" -> "Slow (Gentle 170f stiffness)"
                                                        "FAST" -> "Fast (Snappy 800f stiffness)"
                                                        else -> "Default (Balanced 380f stiffness)"
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            },
                                            trailing = {
                                                AssistChip(
                                                    onClick = {
                                                        val nextIdx = (speeds.indexOf(currentSpeed) + 1) % speeds.size
                                                        onIslandAnimationSpeedChange(speeds[nextIdx])
                                                    },
                                                    label = { Text(currentSpeed) }
                                                )
                                            }
                                        )
                                    } else {
                                        val bounces = listOf("SMALL", "NORMAL", "BIG")
                                        val currentBounce = islandAnimationBounce.uppercase(Locale.US)
                                        com.petal.browser.ui.containment.PetalGroupListRow(
                                            position = position,
                                            onClick = {
                                                val nextIdx = (bounces.indexOf(currentBounce) + 1) % bounces.size
                                                onIslandAnimationBounceChange(bounces[nextIdx])
                                            },
                                            leading = {
                                                com.petal.browser.ui.containment.PetalGroupIconBadge(
                                                    icon = Icons.Rounded.Gesture,
                                                    variant = com.petal.browser.ui.containment.PetalBadgeVariant.SURFACE_TONAL
                                                )
                                            },
                                            content = {
                                                Text("Spring Damping & Overshoot", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                                Text(
                                                    when (currentBounce) {
                                                        "SMALL" -> "Small (~1.5% overshoot)"
                                                        "BIG" -> "Big (~20% distinct springy bounce)"
                                                        else -> "Normal (~9% overshoot)"
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            },
                                            trailing = {
                                                AssistChip(
                                                    onClick = {
                                                        val nextIdx = (bounces.indexOf(currentBounce) + 1) % bounces.size
                                                        onIslandAnimationBounceChange(bounces[nextIdx])
                                                    },
                                                    label = { Text(currentBounce) }
                                                )
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            com.petal.browser.ui.containment.PetalGroup(rowCount = 1) { _, position ->
                                com.petal.browser.ui.containment.PetalSettingsToggleRow(
                                    title = "Liquid Wavy Progress Bar",
                                    subtitle = "Animated travelling sinusoidal wave along active download progress",
                                    icon = Icons.Rounded.Waves,
                                    checked = islandWavyProgress,
                                    position = position,
                                    onCheckedChange = onIslandWavyProgressChange
                                )
                            }
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

                // Speed & background reliability (parallel connections, battery exemption)
                PetalSettingsSection(
                    title = "Speed & reliability",
                    icon = Icons.Rounded.Speed,
                    cardId = "misc_download_speed",
                    targetHighlightId = targetHighlightItemId
                ) {
                    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
                    var parallelEnabled by remember {
                        mutableStateOf(sp.getBoolean(PetalParallelFetchClient.Settings.KEY_ENABLED, true))
                    }
                    var connections by remember {
                        mutableStateOf(
                            PetalParallelFetchClient.Settings.load(context).connections
                                .coerceAtLeast(2)
                        )
                    }
                    var batteryExempt by remember { mutableStateOf(PetalBatteryOptimization.isExempt(context)) }
                    DisposableEffect(context) {
                        val owner = context as? androidx.lifecycle.LifecycleOwner
                        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                batteryExempt = PetalBatteryOptimization.isExempt(context)
                            }
                        }
                        owner?.lifecycle?.addObserver(observer)
                        onDispose { owner?.lifecycle?.removeObserver(observer) }
                    }

                    com.petal.browser.ui.containment.PetalGroup(rowCount = 3) { rowIndex, rowPosition ->
                        when (rowIndex) {
                            0 -> com.petal.browser.ui.containment.PetalSettingsToggleRow(
                                title = "Parallel downloads",
                                subtitle = "Fetch big files over several connections at once. Falls back to a single stream on servers that don't support it.",
                                icon = Icons.Rounded.Speed,
                                checked = parallelEnabled,
                                position = rowPosition,
                                onCheckedChange = {
                                    parallelEnabled = it
                                    sp.edit().putBoolean(PetalParallelFetchClient.Settings.KEY_ENABLED, it).apply()
                                }
                            )
                            1 -> com.petal.browser.ui.containment.PetalGroupListRow(
                                position = rowPosition,
                                onClick = {
                                    if (parallelEnabled) {
                                        val options = listOf(2, 4, 6, 8)
                                        connections = options[(options.indexOf(connections) + 1).mod(options.size)]
                                        sp.edit().putInt(PetalParallelFetchClient.Settings.KEY_CONNECTIONS, connections).apply()
                                    }
                                },
                                leading = {
                                    com.petal.browser.ui.containment.PetalGroupIconBadge(
                                        icon = Icons.Rounded.Tune,
                                        variant = com.petal.browser.ui.containment.PetalBadgeVariant.SURFACE_TONAL
                                    )
                                },
                                content = {
                                    Text(
                                        text = "Connections per download",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Tap to change. More is faster on slow servers, but some hosts limit it (Petal backs off automatically). Applies to new downloads.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailing = {
                                    Text(
                                        text = if (parallelEnabled) "$connections" else "Off",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                            else -> com.petal.browser.ui.containment.PetalGroupListRow(
                                position = rowPosition,
                                onClick = { if (!batteryExempt) PetalBatteryOptimization.requestExemption(context) },
                                leading = {
                                    com.petal.browser.ui.containment.PetalGroupIconBadge(
                                        icon = Icons.Rounded.BatteryAlert,
                                        variant = if (batteryExempt) com.petal.browser.ui.containment.PetalBadgeVariant.PRIMARY
                                        else com.petal.browser.ui.containment.PetalBadgeVariant.SURFACE_TONAL
                                    )
                                },
                                content = {
                                    Text(
                                        text = "Background downloads",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (batteryExempt) "Unrestricted: downloads keep running with the screen off."
                                        else "Restricted: Android may pause downloads when the screen locks. Tap to allow.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        }
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

@Composable
private fun IslandAnimationPreviewCard(
    style: String,
    speed: String,
    bounce: String,
    wavy: Boolean
) {
    var isExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }

    val motion = remember(style, speed, bounce) {
        val s = if ("EASE_IN_OUT".equals(style, ignoreCase = true)) IslandAnimationStyle.EASE_IN_OUT else IslandAnimationStyle.EXPRESSIVE
        val spd = when (speed.uppercase(Locale.US)) {
            "SLOW" -> IslandAnimationSpeed.SLOW
            "FAST" -> IslandAnimationSpeed.FAST
            else -> IslandAnimationSpeed.DEFAULT
        }
        val bnc = when (bounce.uppercase(Locale.US)) {
            "SMALL" -> IslandAnimationBounce.SMALL
            "BIG" -> IslandAnimationBounce.BIG
            else -> IslandAnimationBounce.NORMAL
        }
        IslandMotion(style = s, speed = spd, bounce = bnc)
    }

    val width by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isExpanded) 280.dp else 160.dp,
        animationSpec = motion.dp(),
        label = "previewWidth"
    )
    val height by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isExpanded) 96.dp else 38.dp,
        animationSpec = motion.dp(),
        label = "previewHeight"
    )
    val cornerRadius by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isExpanded) 24.dp else 19.dp,
        animationSpec = motion.dp(),
        label = "previewCorner"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Interactive Motion Preview (Tap to toggle)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .size(width, height)
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                        }
                        .clip(RoundedCornerShape(cornerRadius))
                        .clickable {
                            scope.launch {
                                scale.animateTo(0.93f, motion.boop())
                                scale.animateTo(1f, motion.boop())
                            }
                            isExpanded = !isExpanded
                        },
                    shape = RoundedCornerShape(cornerRadius),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    tonalElevation = 6.dp,
                    shadowElevation = 4.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!isExpanded) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.ArrowDownward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text("68%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("8.4 MB/s", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("ubuntu-24.04.iso", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text("8.4 MB/s", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                PetalWavyProgressBar(
                                    progress = 0.68f,
                                    waveColor = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    isWavy = wavy,
                                    animated = wavy
                                )
                                Text("2.4 GB / 3.8 GB • 00:48 remaining", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
