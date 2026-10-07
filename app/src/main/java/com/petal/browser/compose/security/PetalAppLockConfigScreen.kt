/*
 * PetalAppLockConfigScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Dedicated App & Profile Lock configuration screen for Petal Browser.
 * Allows user to enable/disable App Lock and select authentication method:
 * 1. Fingerprint (Biometric / Device Lock)
 * 2. Password Lock (Shaped-mask Passcode)
 */

package com.petal.browser.compose.security

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.security.BiometricLockManager
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.IconSwitch
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.PetalShapedPasswordInput
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.PetalHeroCard
import com.petal.browser.ui.containment.PetalDialog
import com.petal.browser.ui.containment.PetalSelectableOptionCard
import com.petal.browser.ui.containment.PetalSnackbarHost
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.petal.browser.R
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LockOpen
import com.petal.browser.ui.containment.PetalGroup
import com.petal.browser.ui.containment.PetalGroupListRow
import com.petal.browser.ui.containment.PetalGroupNavigationRow
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.PetalSettingsSection
import com.petal.browser.ui.containment.PetalSettingsToggleRow
import com.petal.browser.ui.containment.PetalStatusHeroCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalAppLockConfigScreen(
    backgroundSnapshot: androidx.compose.ui.graphics.ImageBitmap? = null,
    onBack: () -> Unit,
    wrapPredictive: Boolean = true,
) {
    val context = LocalContext.current
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isLockEnabled by remember { mutableStateOf(sp.getBoolean("sp_app_lock_enabled", false)) }
    var selectedLockType by remember { mutableStateOf(sp.getString("sp_app_lock_type", "FINGERPRINT") ?: "FINGERPRINT") } // FINGERPRINT or PASSWORD
    var savedPasscode by remember { mutableStateOf(sp.getString("sp_app_lock_passcode", "") ?: "") }

    var showPasscodeConfigDialog by remember { mutableStateOf(false) }
    var tempPasscode by remember { mutableStateOf(savedPasscode) }

    fun updateLockConfig(enabled: Boolean, type: String) {
        isLockEnabled = enabled
        selectedLockType = type
        sp.edit()
            .putBoolean("sp_app_lock_enabled", enabled)
            .putString("sp_app_lock_type", type)
            .putBoolean("sp_biometric_lock", enabled && type == "FINGERPRINT")
            .apply()
    }

    fun handleLockToggle(checked: Boolean) {
        if (checked) {
            if (selectedLockType == "FINGERPRINT") {
                val activity = context as? AppCompatActivity
                if (activity != null) {
                    BiometricLockManager.authenticate(
                        activity,
                        "Verify Fingerprint",
                        "Confirm biometric lock setup",
                        Runnable {
                            updateLockConfig(true, "FINGERPRINT")
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Fingerprint lock enabled")
                            }
                        },
                        java.util.function.Consumer { err ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Fingerprint verification failed: $err")
                            }
                        }
                    )
                } else {
                    updateLockConfig(true, "FINGERPRINT")
                }
            } else {
                if (savedPasscode.isBlank()) {
                    showPasscodeConfigDialog = true
                } else {
                    updateLockConfig(true, "PASSWORD")
                }
            }
        } else {
            updateLockConfig(false, selectedLockType)
            coroutineScope.launch {
                snackbarHostState.showSnackbar("App & Profile Lock disabled")
            }
        }
    }

    fun selectBiometricMethod() {
        val activity = context as? AppCompatActivity
        if (isLockEnabled && activity != null) {
            BiometricLockManager.authenticate(
                activity,
                "Verify Fingerprint",
                "Confirm biometric method switch",
                Runnable {
                    updateLockConfig(isLockEnabled, "FINGERPRINT")
                    coroutineScope.launch { snackbarHostState.showSnackbar("Fingerprint lock selected") }
                },
                java.util.function.Consumer { err ->
                    coroutineScope.launch { snackbarHostState.showSnackbar("Fingerprint error: $err") }
                }
            )
        } else updateLockConfig(isLockEnabled, "FINGERPRINT")
    }

    fun selectPasscodeMethod() {
        updateLockConfig(isLockEnabled, "PASSWORD")
        if (savedPasscode.isBlank()) showPasscodeConfigDialog = true
    }

    val content = @Composable {
        Scaffold(
            snackbarHost = { PetalSnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                M3ExpressiveVariableBackground(
                    modifier = Modifier.fillMaxSize(),
                    pageSeed = "app_lock_config"
                )

                Column(modifier = Modifier.fillMaxSize()) {
                    ExpressiveHeader(
                        title = "App & Profile Lock",
                        subtitle = "Configure protection and authentication",
                        onBack = onBack,
                        enableLiquidGlass = true
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // ── Status hero ──
                        PetalStatusHeroCard(
                            title = if (isLockEnabled) "Petal is protected" else "Lock is turned off",
                            subtitle = if (isLockEnabled) {
                                "You'll authenticate every time Petal Browser opens"
                            } else {
                                "Anyone who can open this device can open Petal Browser"
                            },
                            statusText = when {
                                !isLockEnabled -> "Protection disabled"
                                selectedLockType == "FINGERPRINT" -> "Biometric lock enabled"
                                else -> "Passcode lock enabled"
                            },
                            icon = if (isLockEnabled) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                            statusActive = isLockEnabled,
                            actionLabel = if (isLockEnabled) null else "Turn on",
                            onActionClick = if (isLockEnabled) null else ({ handleLockToggle(true) })
                        )

                        // ── Master switch ──
                        PetalSettingsSection(
                            title = "Startup protection",
                            icon = Icons.Rounded.Security
                        ) {
                            PetalSettingsToggleRow(
                                title = stringResource(R.string.ui_require_lock_on_startup),
                                subtitle = if (isLockEnabled) "App lock active • Startup protected" else "Authenticate each time Petal Browser opens",
                                icon = Icons.Rounded.Lock,
                                checked = isLockEnabled,
                                onCheckedChange = { handleLockToggle(it) }
                            )
                        }

                        // ── Method selection ──
                        PetalSettingsSection(
                            title = stringResource(R.string.ui_choose_authentication_method),
                            icon = Icons.Rounded.Fingerprint
                        ) {
                            PetalGroup(rowCount = 2) { index, position ->
                                val isBiometric = index == 0
                                val selected = if (isBiometric) selectedLockType == "FINGERPRINT" else selectedLockType == "PASSWORD"
                                PetalGroupListRow(
                                    position = position,
                                    selected = selected,
                                    onClick = { if (isBiometric) selectBiometricMethod() else selectPasscodeMethod() },
                                    leading = {
                                        PetalGroupIconBadge(
                                            if (isBiometric) Icons.Rounded.Fingerprint else Icons.Rounded.Key,
                                            container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                            tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    },
                                    content = {
                                        Text(
                                            text = if (isBiometric) stringResource(R.string.ui_biometric_device_lock) else stringResource(R.string.ui_custom_passcode_lock),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2
                                        )
                                        Text(
                                            text = if (isBiometric) {
                                                stringResource(R.string.ui_unlock_with_device_fingerprint_sensor)
                                            } else if (savedPasscode.isNotBlank()) {
                                                "Passcode configured"
                                            } else {
                                                "Set custom shaped-mask password for Petal"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2
                                        )
                                    },
                                    trailing = { RadioButton(selected = selected, onClick = null) }
                                )
                            }
                        }

                        // ── Passcode management (only for passcode lock) ──
                        AnimatedVisibility(
                            visible = selectedLockType == "PASSWORD",
                            enter = fadeIn(spring(stiffness = 400f)) + expandVertically(spring(dampingRatio = 0.8f, stiffness = 400f)),
                            exit = fadeOut(spring(stiffness = 600f)) + shrinkVertically(spring(stiffness = 600f))
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                PetalSettingsSection(
                                    title = "Passcode",
                                    icon = Icons.Rounded.Key
                                ) {
                                    PetalGroupNavigationRow(
                                        title = if (savedPasscode.isNotBlank()) "Change passcode" else "Set passcode",
                                        subtitle = if (savedPasscode.isNotBlank()) "Passcode configured • Tap to change" else "Choose the passcode that unlocks Petal",
                                        position = PetalGroupPosition.SINGLE,
                                        onClick = { showPasscodeConfigDialog = true },
                                        leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) }
                                    )
                                }

                                PetalHeroCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "If you forget your passcode, the only way back in is to erase all Petal data. Keep it somewhere safe.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                    }
                }

                // Passcode configuration dialog
                if (showPasscodeConfigDialog) {
                    PetalDialog(onDismissRequest = { showPasscodeConfigDialog = false }) {
                        PetalGroupIconBadge(
                            Icons.Rounded.Key,
                            size = 48.dp,
                            iconSize = 24.dp
                        )
                        Text(stringResource(R.string.ui_set_app_password), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.ui_enter_password_for_petal_browser),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PetalShapedPasswordInput(
                            value = tempPasscode,
                            onValueChange = { tempPasscode = it },
                            hintText = "Enter passcode",
                            accentColor = MaterialTheme.colorScheme.primary,
                            onUnlock = null,
                            unlockButtonText = "",
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
                            disableAutofill = true
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showPasscodeConfigDialog = false }) { Text(stringResource(R.string.ui_cancel)) }
                            Button(onClick = {
                                if (tempPasscode.trim().isNotBlank()) {
                                    savedPasscode = tempPasscode.trim()
                                    sp.edit().putString("sp_app_lock_passcode", savedPasscode).apply()
                                    if (isLockEnabled) updateLockConfig(true, "PASSWORD")
                                    showPasscodeConfigDialog = false
                                    coroutineScope.launch { snackbarHostState.showSnackbar("App password saved successfully") }
                                }
                            }) { Text(stringResource(R.string.ui_save_password), fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }

    if (wrapPredictive) {
        com.petal.browser.predictive.PetalPredictiveBackSurface(
            enabled = true,
            onBack = onBack,
        ) {
            com.petal.browser.predictive.PetalScreenWrapper(backgroundSnapshot = backgroundSnapshot) {
                content()
            }
        }
    } else {
        content()
    }
}
