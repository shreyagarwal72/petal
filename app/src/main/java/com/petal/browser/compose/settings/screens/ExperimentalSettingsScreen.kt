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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.compose.settings.viewmodel.ExperimentalSettingsViewModel
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.PetalShapedPasswordInput
import com.petal.browser.ui.components.ScrollFadeRow
import com.petal.browser.unit.HelperUnit

@Composable
fun ExperimentalSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExperimentalSettingsViewModel = hiltViewModel()
) {
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val addressBarPosition by viewModel.addressBarPosition.collectAsStateWithLifecycle()
    val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val appLockPasscode by viewModel.appLockPasscode.collectAsStateWithLifecycle()
    val doubleBackExit by viewModel.doubleBackExit.collectAsStateWithLifecycle()

    ExperimentalSettingsScreenContent(
        appLanguage = appLanguage,
        addressBarPosition = addressBarPosition,
        appLockEnabled = appLockEnabled,
        appLockPasscode = appLockPasscode,
        doubleBackExit = doubleBackExit,
        onAppLanguageChange = viewModel::setAppLanguage,
        onAddressBarPositionChange = viewModel::setAddressBarPosition,
        onAppLockEnabledChange = viewModel::setAppLockEnabled,
        onAppLockPasscodeChange = viewModel::setAppLockPasscode,
        onDoubleBackExitChange = viewModel::setDoubleBackExit,
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

@Composable
fun ExperimentalSettingsScreenContent(
    appLanguage: String,
    addressBarPosition: String,
    appLockEnabled: Boolean,
    appLockPasscode: String,
    doubleBackExit: Boolean,
    onAppLanguageChange: (String) -> Unit,
    onAddressBarPositionChange: (String) -> Unit,
    onAppLockEnabledChange: (Boolean) -> Unit,
    onAppLockPasscodeChange: (String) -> Unit,
    onDoubleBackExitChange: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showPasscodeDialog by remember { mutableStateOf(false) }

    if (showPasscodeDialog) {
        var tempPasscode by remember { mutableStateOf(appLockPasscode) }
        var dialogError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showPasscodeDialog = false },
            title = {
                Text(
                    text = "Configure Security Passcode",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter a security passcode. Typed characters will be masked with Material 3 Expressive shapes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PetalShapedPasswordInput(
                        value = tempPasscode,
                        onValueChange = {
                            tempPasscode = it
                            if (dialogError != null) dialogError = null
                        },
                        hintText = "New Passcode",
                        isError = dialogError != null,
                        accentColor = MaterialTheme.colorScheme.primary,
                        onUnlock = {
                            if (tempPasscode.trim().length >= 4) {
                                onAppLockPasscodeChange(tempPasscode.trim())
                                showPasscodeDialog = false
                            } else {
                                dialogError = "Passcode must be at least 4 characters"
                            }
                        },
                        unlockButtonText = "Save"
                    )
                    if (dialogError != null) {
                        Text(
                            text = dialogError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPasscodeDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

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
                                },
                                label = { Text("Bottom") },
                                leadingIcon = if (addressBarPosition == "BOTTOM") {
                                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                }

                // Security & Navigation Safeguards Card
                SettingsCategoryCard(title = "Security & Navigation Safeguards", icon = Icons.Rounded.Lock) {
                    ToggleRow(
                        title = "App Lock Protection",
                        subtitle = "Protect Petal with biometric authentication or custom passcode",
                        icon = Icons.Rounded.Lock,
                        checked = appLockEnabled,
                        onCheckedChange = { enabled ->
                            onAppLockEnabledChange(enabled)
                            if (enabled && appLockPasscode.isBlank()) {
                                showPasscodeDialog = true
                            }
                        }
                    )

                    if (appLockEnabled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (appLockPasscode.isBlank()) "No passcode configured" else "Passcode configured",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(onClick = { showPasscodeDialog = true }) {
                                Text(if (appLockPasscode.isBlank()) "Set Passcode" else "Change Passcode")
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ToggleRow(
                        title = "Double Back Exit",
                        subtitle = "Press back twice quickly to exit the browser",
                        icon = Icons.Rounded.ExitToApp,
                        checked = doubleBackExit,
                        onCheckedChange = onDoubleBackExitChange
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
