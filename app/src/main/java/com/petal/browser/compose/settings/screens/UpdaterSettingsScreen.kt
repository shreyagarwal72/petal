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
import com.petal.browser.compose.composable.ContainedLoadingIndicator
import com.petal.browser.compose.settings.viewmodel.MiscSettingsViewModel
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.unit.UpdateUnit
import com.petal.browser.view.NinjaToast

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
    var isCheckingUpdate by remember { mutableStateOf(false) }

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

    Box(modifier = modifier.fillMaxSize()) {
        M3ExpressiveVariableBackground(pageSeed = "updater_settings")

        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveHeader(
                title = "App Updates",
                subtitle = "Check for updates and auto-check on launch",
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
                // App Updates & Inbuilt Updater Card
                SettingsCategoryCard(title = "App Updates & Inbuilt Updater", iconRes = com.petal.browser.R.drawable.update_rounded) {
                    ToggleRow(
                        title = "Check for Updates on Launch",
                        subtitle = "Automatically check for new browser releases when app starts",
                        icon = Icons.Rounded.SystemUpdate,
                        checked = checkUpdateOnLaunch,
                        onCheckedChange = onCheckUpdateOnLaunchChange
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.Sync,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Check for Updates Now",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isCheckingUpdate) "Checking for updates..." else "Version v$appVersionName ($appVersionCode)",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isCheckingUpdate) {
                                ContainedLoadingIndicator(
                                    modifier = Modifier.size(32.dp)
                                )
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
                                            }
                                        } else {
                                            isCheckingUpdate = false
                                            NinjaToast.show(context, "Checking for updates...")
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Check", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
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
                                UpdateUnit.checkForUpdates(act, false, null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("View Release History on GitHub")
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
