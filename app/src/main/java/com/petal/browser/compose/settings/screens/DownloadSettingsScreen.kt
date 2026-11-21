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
    val islandOverlay by viewModel.islandOverlay.collectAsStateWithLifecycle()

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
        islandOverlay = islandOverlay,
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
        onIslandOverlayChange = viewModel::setIslandOverlay,
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
    islandOverlay: Boolean,
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
    onIslandOverlayChange: (Boolean) -> Unit,
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

        com.petal.browser.ui.components.PetalGlassHeaderLayout(
            modifier = Modifier.fillMaxSize(),
            header = { headerModifier, headerBackdrop ->
                ExpressiveHeader(
                    modifier = headerModifier,
                    backdrop = headerBackdrop,
                    title = "Downloads",
                    subtitle = "Download engine, live progress chips and file management",
                    onBack = onNavigateBack
                )
            }
        ) { topInset ->

            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize()
                    .scrollMotionBlur(scrollState)
                    .verticalScroll(scrollState, flingBehavior = com.petal.browser.ui.theme.layout.petalFlingBehavior())
                    .padding(start = 16.dp, end = 16.dp, top = topInset + 12.dp, bottom = 12.dp),
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

                    com.petal.browser.ui.containment.PetalGroup(rowCount = 2) { index, rowPosition ->
                        when (index) {
                            0 -> com.petal.browser.ui.containment.PetalSettingsToggleRow(
                                title = stringResource(R.string.ui_auto_preview_downloaded_images),
                                subtitle = stringResource(R.string.ui_show_downloaded_photos_in_the),
                                icon = Icons.Rounded.Image,
                                checked = autoPreviewDownloadedImages,
                                position = rowPosition,
                                onCheckedChange = onAutoPreviewDownloadedImagesChange
                            )
                            1 -> {
                                val smartRouting by viewModel.smartDownloadRouting.collectAsStateWithLifecycle()
                                com.petal.browser.ui.containment.PetalSettingsToggleRow(
                                    title = "Smart download routing",
                                    subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                                        "Sort finished downloads into Petal subfolders by type (Images, Video, Audio, Documents, Apps). Files are moved right after they complete."
                                    else
                                        "Automatically sort downloaded files into subfolders by type (Images, Video, Audio, Documents, Apps)",
                                    icon = Icons.Rounded.FolderSpecial,
                                    checked = smartRouting,
                                    position = rowPosition,
                                    onCheckedChange = { viewModel.setSmartDownloadRouting(it) }
                                )
                            }
                        }
                    }
                }

                // Speed & background reliability (parallel connections, battery exemption)
                PetalSettingsSection(
                    title = "Speed & reliability",
                    icon = Icons.Rounded.Speed,
                    cardId = "misc_download_speed",
                    targetHighlightId = targetHighlightItemId
                ) {
                    val parallelEnabled by viewModel.parallelDownloads.collectAsStateWithLifecycle()
                    val connections by viewModel.parallelConnections.collectAsStateWithLifecycle()
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
                                onCheckedChange = { viewModel.setParallelDownloads(it) }
                            )
                            1 -> com.petal.browser.ui.containment.PetalGroupListRow(
                                position = rowPosition,
                                onClick = {
                                    if (parallelEnabled) {
                                        val options = listOf(2, 4, 6, 8)
                                        viewModel.setParallelConnections(options[(options.indexOf(connections) + 1).mod(options.size)])
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

                // Queue, limits & post-download actions
                PetalSettingsSection(
                    title = "Queue & limits",
                    icon = Icons.Rounded.Tune,
                    cardId = "misc_download_queue",
                    targetHighlightId = targetHighlightItemId
                ) {
                    val maxConcurrent by viewModel.maxConcurrentDownloads.collectAsStateWithLifecycle()
                    val wifiOnly by viewModel.wifiOnlyDownloads.collectAsStateWithLifecycle()
                    val speedLimit by viewModel.downloadSpeedLimitKBps.collectAsStateWithLifecycle()
                    val verify by viewModel.verifyDownloadChecksums.collectAsStateWithLifecycle()
                    val offerClipboard by viewModel.offerClipboardImport.collectAsStateWithLifecycle()
                    com.petal.browser.ui.containment.PetalGroup(rowCount = 5) { rowIndex, rowPosition ->
                        when (rowIndex) {
                            0 -> com.petal.browser.ui.containment.PetalGroupListRow(
                                position = rowPosition,
                                onClick = {
                                    viewModel.setMaxConcurrentDownloads(if (maxConcurrent >= 6) 1 else maxConcurrent + 1)
                                },
                                leading = {
                                    com.petal.browser.ui.containment.PetalGroupIconBadge(
                                        icon = Icons.Rounded.Tune,
                                        variant = com.petal.browser.ui.containment.PetalBadgeVariant.SURFACE_TONAL
                                    )
                                },
                                content = {
                                    Text("Simultaneous downloads", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                                    Text("Extra downloads wait in the queue. Tap to change.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                },
                                trailing = {
                                    Text("$maxConcurrent", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                }
                            )
                            1 -> com.petal.browser.ui.containment.PetalSettingsToggleRow(
                                title = "Wi-Fi only",
                                subtitle = "Pause downloads on mobile data and resume on Wi-Fi.",
                                icon = Icons.Rounded.Speed,
                                checked = wifiOnly,
                                position = rowPosition,
                                onCheckedChange = { viewModel.setWifiOnlyDownloads(it) }
                            )
                            2 -> com.petal.browser.ui.containment.PetalGroupListRow(
                                position = rowPosition,
                                onClick = {
                                    val steps = listOf(0, 256, 512, 1024, 2048, 5120)
                                    viewModel.setDownloadSpeedLimitKBps(steps[(steps.indexOf(speedLimit).takeIf { it >= 0 } ?: -1).plus(1).mod(steps.size)])
                                },
                                leading = {
                                    com.petal.browser.ui.containment.PetalGroupIconBadge(
                                        icon = Icons.Rounded.Speed,
                                        variant = com.petal.browser.ui.containment.PetalBadgeVariant.SURFACE_TONAL
                                    )
                                },
                                content = {
                                    Text("Speed limit", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                                    Text("Caps total download speed. Tap to change. Applies to downloads Petal fetches itself.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                },
                                trailing = {
                                    Text(if (speedLimit == 0) "Off" else if (speedLimit >= 1024) "${speedLimit / 1024} MB/s" else "$speedLimit KB/s", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                }
                            )
                            3 -> com.petal.browser.ui.containment.PetalSettingsToggleRow(
                                title = "Verify checksums",
                                subtitle = "Check the file against the hash the server provides (Digest / Content-MD5) and show the result.",
                                icon = Icons.Rounded.FolderSpecial,
                                checked = verify,
                                position = rowPosition,
                                onCheckedChange = { viewModel.setVerifyDownloadChecksums(it) }
                            )
                            else -> com.petal.browser.ui.containment.PetalSettingsToggleRow(
                                title = "Offer to import copied links",
                                subtitle = "When Petal opens with links on the clipboard, offer to add them. Off by default; the clipboard is only read when Petal opens.",
                                icon = Icons.Rounded.FolderSpecial,
                                checked = offerClipboard,
                                position = rowPosition,
                                onCheckedChange = { viewModel.setOfferClipboardImport(it) }
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

