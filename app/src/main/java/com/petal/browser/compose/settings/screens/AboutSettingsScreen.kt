package com.petal.browser.compose.settings.screens

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.compose.settings.SettingsCategory
import com.petal.browser.ui.components.*
import com.petal.browser.unit.BrowserUnit
import com.petal.browser.view.NinjaToast

@Composable
fun AboutSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

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
                DeveloperHeroCard(
                    onCopyGithub = {
                        try {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = android.content.ClipData.newPlainText("GitHub URL", "https://github.com/shreyagarwal72")
                            clipboard.setPrimaryClip(clip)
                            NinjaToast.show(context, "Copied GitHub URL to clipboard")
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                )

                DeveloperMissionCard()
                DeveloperMetricsGrid()
                DeveloperTechStackCard()

                DeveloperActionsCard(
                    onOpenUrl = { url ->
                        try {
                            if (url == "petal://credits") {
                                (context as? ComponentActivity)?.let { act ->
                                    val browserActivity = act as? BrowserActivity
                                    if (browserActivity != null) {
                                        browserActivity.showCreditsScreen {
                                            browserActivity.openSettingsScreen(SettingsCategory.ABOUT)
                                        }
                                    } else {
                                        PetalCreditsBridge.show(act) {
                                            onNavigateBack()
                                        }
                                    }
                                }
                            } else {
                                BrowserUnit.intentURL(context, Uri.parse(url))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                )

                // Footer Copyright
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Petal Browser • Open Source Project",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Made with Jetpack Compose & Material 3 Expressive UI",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
