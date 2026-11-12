package com.petal.browser.compose.settings.screens
import com.petal.browser.ui.theme.ExpressiveButtonShape
import com.petal.browser.ui.components.scrollMotionBlur
import com.petal.browser.ui.containment.PetalSettingsSection
import com.petal.browser.ui.containment.liquidGlassChrome

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import com.petal.browser.ui.kit.FilledTonalButton
import com.petal.browser.ui.kit.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.preference.PreferenceManager
import com.petal.browser.compose.composable.ContainedLoadingIndicator
import com.petal.browser.compose.downloads.formatBytes
import com.petal.browser.compose.downloads.formatSpeed
import com.petal.browser.compose.settings.viewmodel.MiscSettingsViewModel
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.logger.PetalAppLogger
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.PetalCrashReportingPicker
import com.petal.browser.unit.PetalUpdateManager
import com.petal.browser.unit.PetalUpdateUtils
import com.petal.browser.view.PetalToast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

@Composable
fun UpdaterSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MiscSettingsViewModel = hiltViewModel()
) {
    val checkUpdateOnLaunch by viewModel.checkUpdateOnLaunch.collectAsStateWithLifecycle()

    UpdaterSettingsScreenContent(
        checkUpdateOnLaunch = checkUpdateOnLaunch,
        onCheckUpdateOnLaunchChange = viewModel::setCheckUpdateOnLaunch,
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdaterSettingsScreenContent(
    checkUpdateOnLaunch: Boolean,
    onCheckUpdateOnLaunchChange: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var preReleaseEnabled by remember {
        mutableStateOf(sp.getBoolean(PetalUpdateUtils.PREF_KEY_PRERELEASE_ENABLED, false))
    }
    var pendingPreRelease by remember { mutableStateOf<Boolean?>(null) }
    var crashReportMode by remember {
        mutableStateOf(sp.getString(PetalAppLogger.PREF_CRASH_REPORT_MODE, "off") ?: "off")
    }
    var lastCheckTimestamp by remember {
        mutableLongStateOf(sp.getLong("sp_update_last_check_timestamp", 0L))
    }

    val appVersionName = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) { "1.0.0" }
    }
    val appVersionCode = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (e: Exception) { 100L }
    }

    val formattedLastCheck = remember(lastCheckTimestamp) {
        if (lastCheckTimestamp <= 0L) "Never"
        else SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(lastCheckTimestamp))
    }

    pendingPreRelease?.let { enabling ->
        PetalPreReleaseSheet(
            isEnabling = enabling,
            onDismiss = { pendingPreRelease = null },
            onConfirm = {
                sp.edit()
                    .putBoolean(PetalUpdateUtils.PREF_KEY_PRERELEASE_ENABLED, enabling)
                    .remove("sp_update_skipped_version")
                    .apply()
                preReleaseEnabled = enabling
                pendingPreRelease = null
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        M3ExpressiveVariableBackground(pageSeed = "updater_settings")

        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveHeader(
                title = "Updates & Diagnostics",
                subtitle = "Release tracker, automatic updates & crash reporting",
                onBack = onNavigateBack
            )

            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .scrollMotionBlur(scrollState)
                    .verticalScroll(scrollState, flingBehavior = com.petal.browser.ui.theme.layout.petalFlingBehavior())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Section 1: Inbuilt Update Engine & Tracker ──
                PetalSettingsSection(
                    title = stringResource(R.string.ui_update_tracker_releases),
                    iconRes = com.petal.browser.R.drawable.update_rounded
                ) {
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        position = com.petal.browser.ui.containment.PetalGroupPosition.TOP,
                        title = stringResource(R.string.ui_check_for_updates_on_launch),
                        subtitle = stringResource(R.string.ui_automatically_check_for_new_browser),
                        icon = Icons.Rounded.SystemUpdate,
                        checked = checkUpdateOnLaunch,
                        onCheckedChange = onCheckUpdateOnLaunchChange
                    )
                    if (!com.petal.browser.BuildConfig.IS_PLAY_STORE_BUILD) {
                        com.petal.browser.ui.containment.PetalSettingsToggleRow(
                            position = com.petal.browser.ui.containment.PetalGroupPosition.MIDDLE,
                            title = stringResource(R.string.ui_check_prereleases),
                            subtitle = stringResource(R.string.ui_check_prereleases_desc),
                            icon = Icons.Rounded.Science,
                            checked = preReleaseEnabled,
                            onCheckedChange = { pendingPreRelease = it }
                        )
                    }
                    // Channel & Version Hero Card
                    val isGlass = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_containments")
                    val channelCardShape = com.petal.browser.ui.containment.petalGroupShape(com.petal.browser.ui.containment.PetalGroupPosition.BOTTOM)
                    Surface(
                        shape = channelCardShape,
                        color = com.petal.browser.ui.containment.petalGroupSurfaceColor(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isGlass) Modifier.liquidGlassChrome(channelCardShape, enabled = true) else Modifier)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = ExpressiveButtonShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Rounded.RocketLaunch,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.ui_petal_v, appVersionName, appVersionCode),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val channelSubtitle = if (com.petal.browser.BuildConfig.IS_PLAY_STORE_BUILD) {
                                        "Channel: Google Play • Last Checked: $formattedLastCheck"
                                    } else {
                                        stringResource(
                                            if (preReleaseEnabled) R.string.ui_channel_github_prerelease_last_checked
                                            else R.string.ui_channel_github_official_last_checked,
                                            formattedLastCheck
                                        )
                                    }
                                    Text(
                                        text = channelSubtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (!com.petal.browser.BuildConfig.IS_PLAY_STORE_BUILD) {
                                val activeUpdateState by PetalUpdateManager.downloadState.collectAsStateWithLifecycle()
                                LaunchedEffect(Unit) {
                                    PetalUpdateManager.initialize(context)
                                }

                                when (val state = activeUpdateState) {
                                    is PetalUpdateManager.UpdateDownloadState.Downloading -> {
                                        Surface(
                                            shape = RoundedCornerShape(18.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(8.dp)
                                                                .clip(ExpressiveButtonShape)
                                                                .background(MaterialTheme.colorScheme.primary)
                                                        )
                                                        Text(
                                                            text = "Downloading v${state.version}",
                                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                    Text(
                                                        text = "${state.progress}%",
                                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                LinearWavyProgressIndicator(
                                                    progress = { (state.progress / 100f).coerceIn(0f, 1f) },
                                                    modifier = Modifier.fillMaxWidth().height(8.dp),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    val downloadedStr = formatBytes(state.downloadedBytes)
                                                    val totalStr = if (state.totalBytes > 0) formatBytes(state.totalBytes) else "..."
                                                    Text(
                                                        text = "$downloadedStr / $totalStr",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        text = if (state.bytesPerSecond > 0) formatSpeed(state.bytesPerSecond) else "Connecting...",
                                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.End
                                                ) {
                                                    FilledTonalButton(
                                                        onClick = {
                                                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                                                            PetalUpdateManager.pauseUpdateDownload(context)
                                                        },
                                                        shape = RoundedCornerShape(50.dp),
                                                        colors = ButtonDefaults.filledTonalButtonColors(
                                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                            contentColor = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    ) {
                                                        Icon(Icons.Rounded.PauseCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(Modifier.width(6.dp))
                                                        Text("Pause")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    is PetalUpdateManager.UpdateDownloadState.Paused -> {
                                        Surface(
                                            shape = RoundedCornerShape(18.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(8.dp)
                                                                .clip(ExpressiveButtonShape)
                                                                .background(MaterialTheme.colorScheme.tertiary)
                                                        )
                                                        Text(
                                                            text = "Update Paused (${state.progress}%)",
                                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Button(
                                                            onClick = {
                                                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                                                                PetalUpdateManager.resumeUpdateDownload(context)
                                                            },
                                                            shape = RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp, topEnd = 6.dp, bottomEnd = 6.dp)
                                                        ) {
                                                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                                            Spacer(Modifier.width(4.dp))
                                                            Text("Resume")
                                                        }
                                                        FilledTonalButton(
                                                            onClick = {
                                                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                                                                PetalUpdateManager.cancelUpdateDownload(context)
                                                            },
                                                            shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 50.dp, bottomEnd = 50.dp),
                                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                                contentColor = MaterialTheme.colorScheme.onSurface
                                                            )
                                                        ) {
                                                            Text("Cancel")
                                                        }
                                                    }
                                                }
                                                LinearWavyProgressIndicator(
                                                    progress = { (state.progress / 100f).coerceIn(0f, 1f) },
                                                    modifier = Modifier.fillMaxWidth().height(8.dp),
                                                    color = MaterialTheme.colorScheme.tertiary,
                                                    trackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
                                                )
                                            }
                                        }
                                    }
                                    is PetalUpdateManager.UpdateDownloadState.Completed -> {
                                        Surface(
                                            shape = RoundedCornerShape(18.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    modifier = Modifier.weight(1f),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(32.dp)
                                                            .background(Color(0xFF2E7D32).copy(alpha = 0.15f), ExpressiveButtonShape),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            Icons.Rounded.CheckCircle,
                                                            contentDescription = null,
                                                            tint = Color(0xFF2E7D32),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                    Column {
                                                        Text(
                                                            text = "Update Ready to Install",
                                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                        Text(
                                                            text = "Petal v${state.version} downloaded",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                                        )
                                                    }
                                                }
                                                Button(
                                                    onClick = {
                                                        PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.HEAVY_CLICK, 0.9f)
                                                        PetalUpdateManager.installActiveUpdate(context)
                                                    },
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Icon(Icons.Rounded.DownloadDone, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Install")
                                                }
                                            }
                                        }
                                    }
                                    else -> {}
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isCheckingUpdate) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(42.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        ContainedLoadingIndicator(modifier = Modifier.size(32.dp))
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.7f)
                                            isCheckingUpdate = true
                                            var act: Activity? = null
                                            var ctx = context
                                            while (ctx is ContextWrapper) {
                                                if (ctx is Activity) {
                                                    act = ctx
                                                    break
                                                }
                                                ctx = ctx.baseContext
                                            }
                                            if (act != null) {
                                                PetalUpdateUtils.checkForUpdates(act, false) {
                                                    isCheckingUpdate = false
                                                    lastCheckTimestamp = System.currentTimeMillis()
                                                }
                                            } else {
                                                isCheckingUpdate = false
                                                PetalToast.show(context, "Checking for updates...")
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                        shape = RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp, topEnd = 6.dp, bottomEnd = 6.dp)
                                    ) {
                                        Icon(Icons.Rounded.CloudSync, contentDescription = null, modifier = Modifier.size(17.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text(stringResource(R.string.ui_check_now), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.6f)
                                            var act: Activity? = null
                                            var ctx = context
                                            while (ctx is ContextWrapper) {
                                                if (ctx is Activity) {
                                                    act = ctx
                                                    break
                                                }
                                                ctx = ctx.baseContext
                                            }
                                            if (act != null) {
                                                com.petal.browser.ui.components.PetalUpdateSheetBridge.showChangelogHistorySheet(act as androidx.activity.ComponentActivity)
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                        shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 50.dp, bottomEnd = 50.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    ) {
                                        Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(17.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text(stringResource(R.string.ui_changelog), maxLines = 1, softWrap = false)
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Section 2: Crash Reporting & Diagnostic Tracker (Inspired by Essentials) ──
                PetalSettingsSection(
                    title = stringResource(R.string.ui_crash_reporting_diagnostics),
                    icon = Icons.Rounded.BugReport
                ) {
                    PetalCrashReportingPicker(
                        selectedMode = crashReportMode,
                        onModeSelected = { mode ->
                            crashReportMode = mode
                            sp.edit().putString(PetalAppLogger.PREF_CRASH_REPORT_MODE, mode).apply()
                            PetalToast.show(context, if (mode == "auto") "Crash reporting set to Auto" else "Crash reporting disabled")
                        }
                    )
                    // Diagnostic Actions: Export ZIP Logs
                    com.petal.browser.ui.containment.PetalGroupRow(
                        icon = Icons.Rounded.FolderZip,
                        title = stringResource(R.string.ui_export_logs_zip),
                        position = com.petal.browser.ui.containment.PetalGroupPosition.BOTTOM,
                        onClick = {
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.6f)
                            PetalAppLogger.shareLogsZip(context)
                        }
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PetalPreReleaseSheet(
    isEnabling: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(
                    if (isEnabling) R.string.ui_prerelease_enable_title else R.string.ui_prerelease_disable_title
                ),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(
                    if (isEnabling) R.string.ui_prerelease_enable_desc else R.string.ui_prerelease_disable_desc
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ui_prerelease_abort))
                }
                androidx.compose.material3.Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ui_prerelease_continue))
                }
            }
        }
    }
}
