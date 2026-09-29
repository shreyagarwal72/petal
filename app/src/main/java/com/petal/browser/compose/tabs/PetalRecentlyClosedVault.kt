package com.petal.browser.compose.tabs

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.security.BiometricLockManager
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.ExpressiveTabGroupPill
import com.petal.browser.ui.components.HeaderActionIcon
import com.petal.browser.ui.components.PetalExpressiveDialog
import com.petal.browser.ui.components.PetalShapedPasswordInput
import com.petal.browser.unit.ClosedTabRecord
import com.petal.browser.unit.PetalRecentlyClosedManager

/**
 * Material 3 Expressive Animated Lock / Unlock Vault Component for Recently Closed Tabs.
 *
 * Features:
 * - Playful, spring-physics lock opening animation.
 * - Hardware Biometric prompt & Material 3 Expressive shaped passcode authentication.
 * - Complete closed tabs management with zero cached thumbnails.
 * - In-page vault settings dialog (retention timing, password/biometric configuration, purge).
 */
@Composable
fun PetalRecentlyClosedVault(
    onDismiss: () -> Unit,
    onRestoreTab: (ClosedTabRecord) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        PetalRecentlyClosedManager.init(context)
    }

    var lockType by remember { mutableStateOf(PetalRecentlyClosedManager.getLockType(context)) }
    val hasPassword = remember { PetalRecentlyClosedManager.hasPasswordConfigured(context) }
    val canBiometric = remember { BiometricLockManager.canAuthenticate(context) }

    // Whether vault is currently unlocked
    var isUnlocked by remember { mutableStateOf(lockType == "none") }

    // Authentication UI state
    var passwordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showForgotLockDialog by remember { mutableStateOf(false) }

    // List of records
    var records by remember { mutableStateOf(PetalRecentlyClosedManager.getRecentlyClosedTabs()) }
    fun refreshRecords() {
        records = PetalRecentlyClosedManager.getRecentlyClosedTabs()
    }

    // Trigger biometric if lockType is biometric
    LaunchedEffect(lockType) {
        if (lockType == "biometric" && !isUnlocked) {
            val act = context as? AppCompatActivity
            if (act != null && canBiometric) {
                BiometricLockManager.authenticate(
                    activity = act,
                    title = "Unlock Closed Tabs Vault",
                    subtitle = "Verify your identity to access recently closed tabs",
                    onSuccess = {
                        isUnlocked = true
                        PetalHapticEngine.getInstance(context).playClick(context)
                    },
                    onError = {
                        // Keep locked on error/cancel
                    }
                )
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveHeader(
                title = if (isUnlocked) "Closed Tabs Vault" else "Locked Vault",
                subtitle = if (isUnlocked) "${records.size} tabs saved · Metadata only" else "Pull down to reveal",
                onBack = onDismiss,
                actions = {
                    if (isUnlocked) {
                        HeaderActionIcon(
                            icon = Icons.Rounded.Settings,
                            contentDescription = "Vault Settings",
                            onClick = { showSettingsDialog = true }
                        )
                        HeaderActionIcon(
                            icon = Icons.Rounded.Lock,
                            contentDescription = "Lock Vault",
                            onClick = {
                                if (lockType != "none") {
                                    isUnlocked = false
                                    passwordInput = ""
                                } else {
                                    onDismiss()
                                }
                            }
                        )
                    }
                }
            )

            AnimatedContent(
                targetState = isUnlocked,
                transitionSpec = {
                    fadeIn(animationSpec = tween(350)) + scaleIn(initialScale = 0.94f) togetherWith
                        fadeOut(animationSpec = tween(250))
                },
                label = "VaultContent"
            ) { unlocked ->
                if (!unlocked) {
                    // ── Animated Material 3 Expressive Lock Screen ──
                    LockScreenContent(
                        lockType = lockType,
                        passwordInput = passwordInput,
                        passwordError = passwordError,
                        onPasswordChange = {
                            passwordInput = it
                            passwordError = false
                        },
                        onUnlockAttempt = {
                            if (PetalRecentlyClosedManager.verifyPassword(context, passwordInput)) {
                                PetalHapticEngine.getInstance(context).playIfEnabled(context, PetalHapticEngine.Pattern.HEAVY_CLICK, 1.0f)
                                isUnlocked = true
                            } else {
                                PetalHapticEngine.getInstance(context).playTick(context)
                                passwordError = true
                            }
                        },
                        onBiometricClick = {
                            val act = context as? AppCompatActivity
                            if (act != null && canBiometric) {
                                BiometricLockManager.authenticate(
                                    activity = act,
                                    title = "Unlock Closed Tabs Vault",
                                    subtitle = "Verify your identity to access recently closed tabs",
                                    onSuccess = {
                                        isUnlocked = true
                                        PetalHapticEngine.getInstance(context).playClick(context)
                                    },
                                    onError = {}
                                )
                            }
                        },
                        onForgotLock = {
                            showForgotLockDialog = true
                        },
                        canBiometric = canBiometric,
                        accentColor = accentColor
                    )
                } else {
                    // ── Unlocked Vault Closed Tabs Browser ──
                    UnlockedVaultContent(
                        records = records,
                        accentColor = accentColor,
                        onRestore = { rec ->
                            PetalRecentlyClosedManager.removeClosedTab(rec.id)
                            refreshRecords()
                            onRestoreTab(rec)
                        },
                        onDelete = { rec ->
                            PetalRecentlyClosedManager.removeClosedTab(rec.id)
                            refreshRecords()
                        },
                        onRestoreAll = {
                            val all = records.toList()
                            PetalRecentlyClosedManager.clear()
                            refreshRecords()
                            all.forEach { onRestoreTab(it) }
                        },
                        onClearAll = {
                            PetalRecentlyClosedManager.clear()
                            refreshRecords()
                        }
                    )
                }
            }
        }
    }

    // ── In-Vault Settings Dialog ──
    if (showSettingsDialog) {
        VaultSettingsDialog(
            currentRetention = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PetalRecentlyClosedManager.PREF_RETENTION_DAYS, "7") ?: "7",
            currentLockType = lockType,
            hasPassword = hasPassword,
            canBiometric = canBiometric,
            accentColor = accentColor,
            onDismiss = { showSettingsDialog = false },
            onSaveSettings = { newRetention: String, newLockType: String, newPassword: String ->
                val sp: android.content.SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
                sp.edit().putString(PetalRecentlyClosedManager.PREF_RETENTION_DAYS, newRetention).apply()
                PetalRecentlyClosedManager.pruneExpiredTabs(context)

                if (newPassword.isNotBlank()) {
                    PetalRecentlyClosedManager.setPassword(context, newPassword)
                }
                PetalRecentlyClosedManager.setLockType(context, newLockType)
                lockType = newLockType
                refreshRecords()
                showSettingsDialog = false
            }
        )
    }

    // ── Forgot Lock / Reset Vault Dialog ──
    if (showForgotLockDialog) {
        AlertDialog(
            onDismissRequest = { showForgotLockDialog = false },
            icon = {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = "Reset Vault & Clear Data?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "For security and privacy, resetting the vault lock will permanently erase all ${records.size} saved closed tabs and remove the passcode protection. You will regain immediate access to the vault.\n\nThis cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showForgotLockDialog = false
                        PetalRecentlyClosedManager.resetLockAndClearData(context)
                        lockType = "none"
                        isUnlocked = true
                        passwordInput = ""
                        passwordError = false
                        refreshRecords()
                        PetalHapticEngine.getInstance(context).playIfEnabled(context, PetalHapticEngine.Pattern.DOUBLE_CLICK, 0.9f)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Clear All Data & Unlock", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showForgotLockDialog = false },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp)
        )
    }
}

/**
 * Animated Material 3 Expressive Lock View with spring morphing and shaped passcode input.
 */
@Composable
private fun LockScreenContent(
    lockType: String,
    passwordInput: String,
    passwordError: Boolean,
    onPasswordChange: (String) -> Unit,
    onUnlockAttempt: () -> Unit,
    onBiometricClick: () -> Unit,
    onForgotLock: () -> Unit,
    canBiometric: Boolean,
    accentColor: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Expressive Padlock Badge
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = accentColor.copy(alpha = 0.12f),
            border = BorderStroke(2.dp, accentColor.copy(alpha = 0.4f)),
            modifier = Modifier
                .size(96.dp)
                .scale(pulseScale)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = "Locked",
                    tint = accentColor,
                    modifier = Modifier.size(46.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Closed Tabs Vault",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = if (lockType == "password") "Enter your vault passcode to unlock"
            else if (lockType == "biometric") "Verify your biometric identity to unlock"
            else "Vault is ready to access",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        if (lockType == "password" || lockType == "none") {
            // Material 3 Expressive shaped passcode input
            PetalShapedPasswordInput(
                value = passwordInput,
                onValueChange = onPasswordChange,
                hintText = "Enter passcode",
                isError = passwordError,
                onUnlock = onUnlockAttempt,
                modifier = Modifier.fillMaxWidth(0.85f)
            )

            if (passwordError) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Incorrect passcode, try again",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onUnlockAttempt,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(50.dp)
            ) {
                Icon(Icons.Rounded.LockOpen, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Unlock", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))

            TextButton(
                onClick = onForgotLock,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Forgot Passcode?", fontWeight = FontWeight.SemiBold)
            }
        }

        if (canBiometric && lockType == "biometric") {
            Button(
                onClick = onBiometricClick,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(50.dp)
            ) {
                Icon(Icons.Rounded.Fingerprint, null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Scan Fingerprint", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))

            TextButton(
                onClick = onForgotLock,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Forgot Lock / Reset Vault", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Unlocked Vault tab records view.
 */
@Composable
private fun UnlockedVaultContent(
    records: List<ClosedTabRecord>,
    accentColor: Color,
    onRestore: (ClosedTabRecord) -> Unit,
    onDelete: (ClosedTabRecord) -> Unit,
    onRestoreAll: () -> Unit,
    onClearAll: () -> Unit
) {
    if (records.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.12f),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.History,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "No recently closed tabs",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "When you close tabs, their metadata will be stored in this private vault without thumbnail previews.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRestoreAll,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.RestoreFromTrash, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Restore All", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = onClearAll,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.DeleteSweep, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Clear All", style = MaterialTheme.typography.labelLarge)
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(records, key = { it.id }) { rec ->
                    val timeAgo = remember(rec.closedTimestamp) {
                        val diff = System.currentTimeMillis() - rec.closedTimestamp
                        when {
                            diff < 60_000L -> "Just now"
                            diff < 3_600_000L -> "${diff / 60_000L}m ago"
                            diff < 86_400_000L -> "${diff / 3_600_000L}h ago"
                            else -> "${diff / 86_400_000L}d ago"
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = accentColor.copy(alpha = 0.15f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = rec.title.take(1).uppercase().ifBlank { "?" },
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = accentColor
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rec.title.ifBlank { rec.url },
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = rec.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Text(
                                        text = "· $timeAgo",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                                rec.groupTitle?.let { grpTitle ->
                                    val grpColor = rec.groupColorHex?.let {
                                        try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { accentColor }
                                    } ?: accentColor
                                    Spacer(Modifier.height(4.dp))
                                    ExpressiveTabGroupPill(groupName = grpTitle, containerColor = grpColor, contentColor = Color.White)
                                }
                            }

                            IconButton(
                                onClick = { onRestore(rec) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Restore tab", tint = accentColor, modifier = Modifier.size(20.dp))
                            }

                            IconButton(
                                onClick = { onDelete(rec) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Vault Configuration Dialog: retention days, password / biometric toggle.
 */
@Composable
private fun VaultSettingsDialog(
    currentRetention: String,
    currentLockType: String,
    hasPassword: Boolean,
    canBiometric: Boolean,
    accentColor: Color,
    onDismiss: () -> Unit,
    onSaveSettings: (retentionDays: String, lockType: String, newPassword: String) -> Unit
) {
    var selectedRetention by remember { mutableStateOf(currentRetention) }
    var selectedLockType by remember { mutableStateOf(currentLockType) }
    var newPasswordInput by remember { mutableStateOf("") }

    PetalExpressiveDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.92f),
        shape = RoundedCornerShape(28.dp)
    ) {
        Text(
            text = "Vault Settings",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Tab Retention Duration",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("1" to "1d", "7" to "7d", "14" to "14d", "30" to "30d", "never" to "∞").forEach { (days, label) ->
                FilterChip(
                    selected = selectedRetention == days,
                    onClick = { selectedRetention = days },
                    label = { Text(label) },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "Protection Type",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedLockType = "none" }
                    .padding(vertical = 4.dp)
            ) {
                RadioButton(selected = selectedLockType == "none", onClick = { selectedLockType = "none" })
                Spacer(Modifier.width(8.dp))
                Text("None (Direct access)")
            }

            if (canBiometric) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedLockType = "biometric" }
                        .padding(vertical = 4.dp)
                ) {
                    RadioButton(selected = selectedLockType == "biometric", onClick = { selectedLockType = "biometric" })
                    Spacer(Modifier.width(8.dp))
                    Text("Biometric (Fingerprint / Face)")
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedLockType = "password" }
                    .padding(vertical = 4.dp)
            ) {
                RadioButton(selected = selectedLockType == "password", onClick = { selectedLockType = "password" })
                Spacer(Modifier.width(8.dp))
                Text("Passcode / PIN")
            }
        }

        if (selectedLockType == "password") {
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = newPasswordInput,
                onValueChange = { newPasswordInput = it },
                label = { Text(if (hasPassword) "Change Passcode (optional)" else "Set New Passcode") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    onSaveSettings(selectedRetention, selectedLockType, newPasswordInput)
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                modifier = Modifier.weight(1f)
            ) {
                Text("Save")
            }
        }
    }
}
