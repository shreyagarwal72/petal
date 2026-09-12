package com.petal.browser.compose.settings.screens

import android.content.Context
import androidx.compose.foundation.horizontalScroll
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.compose.settings.viewmodel.ExperimentalSettingsViewModel
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.PetalShapedPasswordInput
import com.petal.browser.ui.components.PetalSlider
import com.petal.browser.ui.components.ScrollFadeRow
import com.petal.browser.unit.HelperUnit
import com.petal.browser.appleduo.AppleDuoManager

@Composable
fun ExperimentalSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExperimentalSettingsViewModel = hiltViewModel()
) {
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val addressBarPosition by viewModel.addressBarPosition.collectAsStateWithLifecycle()

    ExperimentalSettingsScreenContent(
        appLanguage = appLanguage,
        addressBarPosition = addressBarPosition,
        onAppLanguageChange = viewModel::setAppLanguage,
        onAddressBarPositionChange = viewModel::setAddressBarPosition,
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

@Composable
fun ExperimentalSettingsScreenContent(
    appLanguage: String,
    addressBarPosition: String,
    onAppLanguageChange: (String) -> Unit,
    onAddressBarPositionChange: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize()) {
        M3ExpressiveVariableBackground(pageSeed = "experimental_settings")

        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveHeader(
                title = "Experimental",
                subtitle = "App language, experimental features and advanced settings",
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
                // App Language Card
                SettingsCategoryCard(title = "App Language", iconRes = com.petal.browser.R.drawable.translate) {
                    Text(
                        "Choose your preferred display language:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val languages = listOf(
                        Pair("system", "System Default"),
                        Pair("en", "English"),
                        Pair("hi-Latn", "Hinglish (Hindi in English)"),
                        Pair("hi", "हिन्दी (Hindi)"),
                        Pair("es", "Español (Spanish)"),
                        Pair("fr", "Français (French)"),
                        Pair("de", "Deutsch (German)"),
                        Pair("zh", "中文 (Chinese)"),
                        Pair("ar", "العربية (Arabic)"),
                        Pair("pt", "Português (Portuguese)"),
                        Pair("ru", "Русский (Russian)"),
                        Pair("ja", "日本語 (Japanese)")
                    )

                    val languageScrollState = rememberScrollState()
                    ScrollFadeRow(
                        scrollState = languageScrollState,
                        edgeColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(languageScrollState),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            languages.forEach { (tag, label) ->
                                FilterChip(
                                    selected = appLanguage == tag,
                                    onClick = {
                                        if (appLanguage != tag) {
                                            onAppLanguageChange(tag)
                                            HelperUnit.setAppLanguage(context, tag)
                                        }
                                    },
                                    label = { Text(label) },
                                    leadingIcon = if (appLanguage == tag) {
                                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }
                    }
                }

                // Address Bar Position Card
                SettingsCategoryCard(title = "Address Bar Position (Experimental)", iconRes = com.petal.browser.R.drawable.build_filled) {
                    Text(
                        "Choose whether the URL search address bar appears at the top or bottom of the screen:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val addressBarScrollState = rememberScrollState()
                    ScrollFadeRow(
                        scrollState = addressBarScrollState,
                        edgeColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(addressBarScrollState),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = addressBarPosition == "TOP",
                                onClick = {
                                    onAddressBarPositionChange("TOP")
                                    (context as? BrowserActivity)?.applyAddressBarPosition()
                                    (context as? BrowserActivity)?.window?.decorView?.post { (context as? BrowserActivity)?.applyAddressBarPosition() }
                                },
                                label = { Text("Top (Default)") },
                                leadingIcon = if (addressBarPosition == "TOP") {
                                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                            FilterChip(
                                selected = addressBarPosition == "BOTTOM",
                                onClick = {
                                    onAddressBarPositionChange("BOTTOM")
                                    (context as? BrowserActivity)?.applyAddressBarPosition()
                                    (context as? BrowserActivity)?.window?.decorView?.post { (context as? BrowserActivity)?.applyAddressBarPosition() }
                                },
                                label = { Text("Bottom") },
                                leadingIcon = if (addressBarPosition == "BOTTOM") {
                                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
