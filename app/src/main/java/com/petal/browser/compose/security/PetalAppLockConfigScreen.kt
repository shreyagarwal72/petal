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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalAppLockConfigScreen(
    onBack: () -> Unit
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

    com.petal.browser.predictive.PetalPredictiveBackSurface(
        enabled = true,
        onBack = onBack
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Underlay Screen (User & Account Profile Preview) - Rendered behind App Lock Config during back gesture
            com.petal.browser.predictive.PetalScreenWrapper(isBehind = true) {
                com.petal.browser.account.PetalUserProfileScreen(onBack = {})
            }

            // 2. Foreground Screen (App Lock Config Page) - Shrinks to 88% card & 32dp corners
            com.petal.browser.predictive.PetalScreenWrapper(isBehind = false) {
            Scaffold(
                topBar = {
                    ExpressiveHeader(
                        title = "App & Profile Lock",
                        subtitle = "Configure protection options",
                        onBack = onBack
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    M3ExpressiveVariableBackground(pageSeed = "app_lock_config")

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Main Master Lock Toggle Card
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isLockEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Rounded.Lock,
                                            contentDescription = null,
                                            tint = if (isLockEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Require Lock on Startup",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = if (isLockEnabled) "App lock active • Protection enabled" else "Secure Petal Browser startup with authentication",
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

                        // Lock Method Options Card
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                tonalElevation = 2.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Text(
                                        text = "Choose Authentication Method",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    Spacer(Modifier.height(14.dp))

                                    // Option 1: Fingerprint (Biometric / Device Credential)
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (selectedLockType == "FINGERPRINT") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val activity = context as? AppCompatActivity
                                                if (isLockEnabled && activity != null) {
                                                    BiometricLockManager.authenticate(
                                                        activity,
                                                        "Verify Fingerprint",
                                                        "Confirm biometric method switch",
                                                        Runnable {
                                                            updateLockConfig(isLockEnabled, "FINGERPRINT")
                                                            coroutineScope.launch {
                                                                snackbarHostState.showSnackbar("Fingerprint lock selected")
                                                            }
                                                        },
                                                        java.util.function.Consumer { err ->
                                                            coroutineScope.launch {
                                                                snackbarHostState.showSnackbar("Fingerprint error: $err")
                                                            }
                                                        }
                                                    )
                                                } else {
                                                    updateLockConfig(isLockEnabled, "FINGERPRINT")
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Fingerprint,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "1. Fingerprint / Biometric Lock",
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Unlock with device fingerprint sensor or system PIN",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            RadioButton(
                                                selected = selectedLockType == "FINGERPRINT",
                                                onClick = null
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(10.dp))

                                    // Option 2: Custom Password Lock (Shaped-mask Passcode)
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (selectedLockType == "PASSWORD") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                updateLockConfig(isLockEnabled, "PASSWORD")
                                                if (savedPasscode.isBlank()) {
                                                    showPasscodeConfigDialog = true
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Key,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "2. Password Lock",
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = if (savedPasscode.isNotBlank()) "Custom passcode configured • Tap to edit" else "Set custom shaped-mask password for Petal",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            RadioButton(
                                                selected = selectedLockType == "PASSWORD",
                                                onClick = null
                                            )
                                        }
                                    }

                                    if (selectedLockType == "PASSWORD") {
                                        Spacer(Modifier.height(12.dp))
                                        OutlinedButton(
                                            onClick = { showPasscodeConfigDialog = true },
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(if (savedPasscode.isNotBlank()) "Change App Password" else "Configure App Password")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Passcode Configuration Dialog
                    if (showPasscodeConfigDialog) {
                        AlertDialog(
                            onDismissRequest = { showPasscodeConfigDialog = false },
                            title = {
                                Text(
                                    "Set App Password",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        "Enter password for Petal Browser lock screen:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    PetalShapedPasswordInput(
                                        value = tempPasscode,
                                        onValueChange = { tempPasscode = it },
                                        hintText = "Enter passcode",
                                        accentColor = MaterialTheme.colorScheme.primary,
                                        onUnlock = {},
                                        unlockButtonText = ""
                                    )
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        if (tempPasscode.trim().isNotBlank()) {
                                            savedPasscode = tempPasscode.trim()
                                            sp.edit().putString("sp_app_lock_passcode", savedPasscode).apply()
                                            if (isLockEnabled) updateLockConfig(true, "PASSWORD")
                                            showPasscodeConfigDialog = false
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("App password saved successfully")
                                            }
                                        }
                                    }
                                ) {
                                    Text("Save Password")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showPasscodeConfigDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
    }
}
