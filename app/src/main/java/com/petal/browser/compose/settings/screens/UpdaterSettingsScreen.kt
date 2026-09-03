package com.petal.browser.compose.settings.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.preference.PreferenceManager
import com.petal.browser.compose.composable.ContainedLoadingIndicator
import com.petal.browser.compose.settings.viewmodel.MiscSettingsViewModel
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.logger.PetalAppLogger
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.PetalCrashReportingPicker
import com.petal.browser.unit.UpdateUnit
import com.petal.browser.view.NinjaToast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var crashReportMode by remember {
        mutableStateOf(sp.getString(PetalAppLogger.PREF_CRASH_REPORT_MODE, "auto") ?: "auto")
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
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Section 1: Inbuilt Update Engine & Tracker ──
                SettingsCategoryCard(
                    title = "Update Tracker & Releases",
                    iconRes = com.petal.browser.R.drawable.update_rounded
                ) {
                    ToggleRow(
                        title = "Check for Updates on Launch",
                        subtitle = "Automatically check for new browser releases when app starts",
                        icon = Icons.Rounded.SystemUpdate,
                        checked = checkUpdateOnLaunch,
                        onCheckedChange = onCheckUpdateOnLaunchChange
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Channel & Version Hero Card
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
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
                                    shape = CircleShape,
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
                                        text = "Petal v ()",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Channel: GitHub Official • Last Checked: ",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
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
                                                UpdateUnit.checkForUpdates(act, false) {
                                                    isCheckingUpdate = false
                                                    lastCheckTimestamp = System.currentTimeMillis()
                                                }
                                            } else {
                                                isCheckingUpdate = false
                                                NinjaToast.show(context, "Checking for updates...")
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Rounded.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Check Now", fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
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
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Changelog")
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Section 2: Crash Reporting & Diagnostic Tracker (Inspired by Essentials) ──
                SettingsCategoryCard(
                    title = "Crash Reporting & Diagnostics",
                    icon = Icons.Rounded.BugReport
                ) {
                    PetalCrashReportingPicker(
                        selectedMode = crashReportMode,
                        onModeSelected = { mode ->
                            crashReportMode = mode
                            sp.edit().putString(PetalAppLogger.PREF_CRASH_REPORT_MODE, mode).apply()
                            NinjaToast.show(context, if (mode == "auto") "Crash reporting set to Auto" else "Crash reporting disabled")
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Diagnostic Actions Row: Export ZIP and Simulate Crash
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.6f)
                                PetalAppLogger.shareLogsZip(context)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export Logs")
                        }

                        Button(
                            onClick = {
                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.VIRTUAL_KEY, 0.8f)
                                PetalAppLogger.simulateCrash()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Icon(Icons.Rounded.WarningAmber, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Simulate Crash")
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
