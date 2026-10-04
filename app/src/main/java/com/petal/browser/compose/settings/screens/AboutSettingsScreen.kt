package com.petal.browser.compose.settings.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.R
import com.petal.browser.ui.components.*
import com.petal.browser.ui.containment.PetalSettingsSection
import com.petal.browser.unit.BrowserUnit
import com.petal.browser.view.PetalToast

@Composable
fun AboutSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    fun copyToClipboard(label: String, text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            PetalToast.show(context, "Copied $label to clipboard")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun openUrl(url: String) {
        try {
            BrowserUnit.intentURL(context, Uri.parse(url))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        M3ExpressiveVariableBackground(pageSeed = "about_settings")

        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveHeader(
                title = "About & Developer",
                subtitle = "App version, licenses, GitHub & developer",
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
                // Section 1: Developer Profile & Identification
                PetalSettingsSection(
                    title = "Developer",
                    icon = Icons.Rounded.Person,
                    cardId = "about_developer_spotlight"
                ) {
                    DeveloperProfileGroup(
                        onCopyGithub = { copyToClipboard("GitHub URL", "https://github.com/shreyagarwal72") },
                        onOpenUrl = { openUrl(it) }
                    )
                }

                // Section 2: Application Details & Environment
                val pInfo = remember {
                    try {
                        context.packageManager.getPackageInfo(context.packageName, 0)
                    } catch (_: Throwable) {
                        null
                    }
                }
                val verName = pInfo?.versionName ?: "3.9"
                val verCode = @Suppress("DEPRECATION") (pInfo?.versionCode ?: 390)
                PetalSettingsSection(
                    title = "Application & Engine",
                    icon = Icons.Rounded.Info,
                    cardId = "about_app_info"
                ) {
                    AppInfoGroup(
                        versionName = verName,
                        versionCode = verCode,
                        onCopyVersion = { copyToClipboard("Version", "$verName ($verCode)") }
                    )
                }

                // Section 3: Project & Source Code
                PetalSettingsSection(
                    title = "Project & Source",
                    icon = Icons.Rounded.Code,
                    cardId = "about_repo"
                ) {
                    ProjectSourceGroup(
                        onOpenUrl = { openUrl(it) }
                    )
                }

                // Section 4: Ecosystem & Community
                PetalSettingsSection(
                    title = "Ecosystem & Community",
                    icon = Icons.Rounded.Favorite,
                    cardId = "about_community"
                ) {
                    CommunityEcosystemGroup(
                        onOpenUrl = { openUrl(it) }
                    )
                }

                // Section 5: Technologies & Frameworks
                PetalSettingsSection(
                    title = "Technologies & Frameworks",
                    icon = Icons.Rounded.Layers,
                    cardId = "about_tech"
                ) {
                    CoreTechStackGroup()
                }

                // Section 6: Mission & Philosophy
                PetalSettingsSection(
                    title = "Mission & Philosophy",
                    icon = Icons.Rounded.RocketLaunch,
                    cardId = "about_mission"
                ) {
                    MissionMetricsGroup()
                }

                // Footer Copyright
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.ui_petal_browser_open_source_project),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.ui_made_with_jetpack_compose_material),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
