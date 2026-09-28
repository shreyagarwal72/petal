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

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    ExpressiveHeader(
                        title = "App & Profile Lock",
                        subtitle = "Configure protection and authentication",
                        onBack = onBack,
                        enableLiquidGlass = true
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Master Lock Toggle Card
                        PetalHeroCard(
                            shape = RoundedCornerShape(32.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                PetalGroupIconBadge(
                                    Icons.Rounded.Lock,
                                    container = if (isLockEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    tint = if (isLockEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    size = 48.dp,
                                    iconSize = 24.dp,
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.ui_require_lock_on_startup),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = if (isLockEnabled) "App lock active • Startup protected" else "Authenticate each time Petal Browser opens",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconSwitch(
                                    checked = isLockEnabled,
                                    icon = Icons.Rounded.Lock,
                                    onCheckedChange = { checked ->
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
                                )
                            }
                        }

                        // Lock Method Selection Card
                        PetalHeroCard(
                            shape = RoundedCornerShape(32.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = stringResource(R.string.ui_choose_authentication_method),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Spacer(Modifier.height(14.dp))

                                // Option 1: Fingerprint (Biometric / Device Credential)
                                PetalSelectableOptionCard(
                                    title = stringResource(R.string.ui_biometric_device_lock),
                                    subtitle = stringResource(R.string.ui_unlock_with_device_fingerprint_sensor),
                                    selected = selectedLockType == "FINGERPRINT",
                                    leading = { PetalGroupIconBadge(Icons.Rounded.Fingerprint) },
                                    onClick = {
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
                                )

                                Spacer(Modifier.height(10.dp))

                                // Option 2: Custom Password Lock
                                PetalSelectableOptionCard(
                                    title = stringResource(R.string.ui_custom_passcode_lock),
                                    subtitle = if (savedPasscode.isNotBlank()) "Passcode configured • Tap below to change" else "Set custom shaped-mask password for Petal",
                                    selected = selectedLockType == "PASSWORD",
                                    leading = { PetalGroupIconBadge(Icons.Rounded.Key) },
                                    onClick = {
                                        updateLockConfig(isLockEnabled, "PASSWORD")
                                        if (savedPasscode.isBlank()) showPasscodeConfigDialog = true
                                    }
                                )

                                if (selectedLockType == "PASSWORD") {
                                    Spacer(Modifier.height(12.dp))
                                    OutlinedButton(
                                        onClick = { showPasscodeConfigDialog = true },
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (savedPasscode.isNotBlank()) "Change Passcode" else "Set Passcode")
                                    }
                                }
                            }
                        }
                    }

                    // Passcode Configuration Dialog
                    if (showPasscodeConfigDialog) {
                        PetalDialog(onDismissRequest = { showPasscodeConfigDialog = false }) {
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
