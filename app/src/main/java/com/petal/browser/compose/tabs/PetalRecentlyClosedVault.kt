package com.petal.browser.compose.tabs

import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.security.BiometricLockManager
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.ExpressiveTabGroupPill
import com.petal.browser.ui.components.HeaderActionIcon
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.PetalShapedPasswordInput
import com.petal.browser.ui.containment.PetalAlertDialog
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.PetalGroupListRow
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.PetalSettingsSection
import com.petal.browser.ui.containment.petalGroupPositionFor
import com.petal.browser.ui.containment.petalGroupShape
import com.petal.browser.unit.ClosedTabRecord
import com.petal.browser.unit.PetalRecentlyClosedManager

/**
 * Closed Tabs Vault — a true full page (not a dialog/overlay window).
 *
 * It is hosted inside the Tab Manager's own composition, so it shares the edge-to-edge
 * window, status-bar insets and back handling. Its settings are a second page of the same
 * screen that uses the same containment design language as the app's Settings screens
 * (ExpressiveHeader + PetalSettingsSection + grouped rows).
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
    var hasPassword by remember { mutableStateOf(PetalRecentlyClosedManager.hasPasswordConfigured(context)) }
    val canBiometric = remember { BiometricLockManager.canAuthenticate(context) }

    // Whether vault is currently unlocked
    var isUnlocked by remember { mutableStateOf(lockType == "none") }

    // Authentication UI state
    var passwordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }
    var showSettingsPage by remember { mutableStateOf(false) }
    var showForgotLockDialog by remember { mutableStateOf(false) }

    // List of records
    var records by remember { mutableStateOf(PetalRecentlyClosedManager.getRecentlyClosedTabs()) }
    fun refreshRecords() {
        records = PetalRecentlyClosedManager.getRecentlyClosedTabs()
    }

    // Back: settings page -> vault page -> close vault
    BackHandler(enabled = true) {
        if (showSettingsPage) showSettingsPage = false else onDismiss()
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
        Box(Modifier.fillMaxSize()) {
            M3ExpressiveVariableBackground(
                modifier = Modifier.fillMaxSize(),
                pageSeed = if (showSettingsPage) "vault_settings" else "vault_page"
            )

            Column(modifier = Modifier.fillMaxSize()) {
                ExpressiveHeader(
                    title = when {
                        showSettingsPage -> "Vault Settings"
                        isUnlocked -> "Closed Tabs Vault"
                        else -> "Locked Vault"
                    },
                    subtitle = when {
                        showSettingsPage -> "Retention & protection"
                        isUnlocked -> if (records.size == 1) "1 tab saved" else "${records.size} tabs saved"
                        else -> "Unlock to view closed tabs"
                    },
                    onBack = { if (showSettingsPage) showSettingsPage = false else onDismiss() },
                    actions = {
                        if (isUnlocked && !showSettingsPage) {
                            HeaderActionIcon(
                                icon = Icons.Rounded.Settings,
                                contentDescription = stringResource(R.string.ui_vault_settings),
                                onClick = { showSettingsPage = true }
                            )
                            HeaderActionIcon(
                                icon = Icons.Rounded.Lock,
                                contentDescription = stringResource(R.string.ui_lock_vault),
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
                    targetState = when {
                        showSettingsPage -> VaultPage.SETTINGS
                        isUnlocked -> VaultPage.TABS
                        else -> VaultPage.LOCKED
                    },
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.96f) togetherWith
                            fadeOut(animationSpec = tween(200))
                    },
                    label = "VaultContent",
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) { page ->
                    when (page) {
                        VaultPage.LOCKED -> LockScreenContent(
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
                            onForgotLock = { showForgotLockDialog = true },
                            canBiometric = canBiometric,
                            accentColor = accentColor
                        )

                        VaultPage.TABS -> UnlockedVaultContent(
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

                        VaultPage.SETTINGS -> VaultSettingsPage(
                            currentRetention = PreferenceManager.getDefaultSharedPreferences(context)
                                .getString(PetalRecentlyClosedManager.PREF_RETENTION_DAYS, "7") ?: "7",
                            currentLockType = lockType,
                            hasPassword = hasPassword,
                            canBiometric = canBiometric,
                            onCancel = { showSettingsPage = false },
                            onSave = { newRetention, newLockType, newPassword ->
                                PreferenceManager.getDefaultSharedPreferences(context).edit()
                                    .putString(PetalRecentlyClosedManager.PREF_RETENTION_DAYS, newRetention)
                                    .apply()
                                PetalRecentlyClosedManager.pruneExpiredTabs(context)

                                if (newPassword.isNotBlank()) {
                                    PetalRecentlyClosedManager.setPassword(context, newPassword)
                                }
                                PetalRecentlyClosedManager.setLockType(context, newLockType)
                                lockType = newLockType
                                hasPassword = PetalRecentlyClosedManager.hasPasswordConfigured(context)
                                refreshRecords()
                                showSettingsPage = false
                            }
                        )
                    }
                }
            }
        }
    }

    // ── Forgot Lock / Reset Vault Dialog ──
    if (showForgotLockDialog) {
        PetalAlertDialog(
            onDismissRequest = { showForgotLockDialog = false },
            title = stringResource(R.string.ui_reset_vault_clear_data),
            message = stringResource(R.string.ui_for_security_and_privacy_resetting, records.size),
            icon = Icons.Rounded.WarningAmber,
            destructive = true,
            confirmText = stringResource(R.string.ui_clear_all_data_unlock),
            onConfirm = {
                showForgotLockDialog = false
                PetalRecentlyClosedManager.resetLockAndClearData(context)
                lockType = "none"
                hasPassword = false
                isUnlocked = true
                passwordInput = ""
                passwordError = false
                refreshRecords()
                PetalHapticEngine.getInstance(context).playIfEnabled(context, PetalHapticEngine.Pattern.DOUBLE_CLICK, 0.9f)
            },
        )
    }
}

private enum class VaultPage { LOCKED, TABS, SETTINGS }

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
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Expressive Padlock Badge
            PetalGroupIconBadge(
                icon = Icons.Rounded.Lock,
                container = MaterialTheme.colorScheme.primaryContainer,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.scale(pulseScale),
                size = 96.dp,
                iconSize = 46.dp,
            )

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.ui_closed_tabs_vault),
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
                    text = stringResource(R.string.ui_incorrect_passcode_try_again),
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
                Text(stringResource(R.string.ui_unlock), fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))

            TextButton(
                onClick = onForgotLock,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.ui_forgot_passcode), fontWeight = FontWeight.SemiBold)
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
                Text(stringResource(R.string.ui_scan_fingerprint), fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))

            TextButton(
                onClick = onForgotLock,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.ui_forgot_lock_reset_vault), fontWeight = FontWeight.SemiBold)
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
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            PetalGroupIconBadge(
                icon = Icons.Rounded.History,
                container = MaterialTheme.colorScheme.primaryContainer,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                size = 72.dp,
                iconSize = 36.dp,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.ui_no_recently_closed_tabs),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.ui_when_you_close_tabs_their),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(modifier = Modifier.fillMaxSize()) {
        // Bulk actions: connected pair, single line labels so long translations never wrap
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            VaultActionButton(
                label = stringResource(R.string.ui_restore_all_2),
                icon = Icons.Rounded.RestoreFromTrash,
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 6.dp, bottomEnd = 6.dp),
                onClick = onRestoreAll,
                modifier = Modifier.weight(1f)
            )
            VaultActionButton(
                label = stringResource(R.string.ui_clear_all),
                icon = Icons.Rounded.DeleteSweep,
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 24.dp, bottomEnd = 24.dp),
                onClick = onClearAll,
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = "Only titles and links are kept. No page previews are stored.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = navBottom + 24.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(records.size, key = { records[it].id }) { index ->
                val rec = records[index]
                val timeAgo = remember(rec.closedTimestamp) {
                    val diff = System.currentTimeMillis() - rec.closedTimestamp
                    when {
                        diff < 60_000L -> "Just now"
                        diff < 3_600_000L -> "${diff / 60_000L}m ago"
                        diff < 86_400_000L -> "${diff / 3_600_000L}h ago"
                        else -> "${diff / 86_400_000L}d ago"
                    }
                }

                PetalGroupListRow(
                    position = petalGroupPositionFor(index, records.size),
                    onClick = { onRestore(rec) },
                    leading = {
                        PetalGroupIconBadge(
                            icon = Icons.Rounded.History,
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            size = 44.dp,
                        )
                    },
                    content = {
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        rec.groupTitle?.let { grpTitle ->
                            val grpColor = rec.groupColorHex?.let {
                                try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { accentColor }
                            } ?: accentColor
                            Spacer(Modifier.height(4.dp))
                            ExpressiveTabGroupPill(groupName = grpTitle, containerColor = grpColor, contentColor = Color.White)
                        }
                    },
                    trailing = {
                        IconButton(onClick = { onRestore(rec) }, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Rounded.RestoreFromTrash,
                                contentDescription = stringResource(R.string.ui_restore_tab),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = { onDelete(rec) }, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.ui_remove),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun VaultActionButton(
    label: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = container,
        contentColor = content,
        modifier = modifier.heightIn(min = 52.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Vault settings as a full page in the app's containment style: a scrollable list of
 * PetalSettingsSection groups, with a pinned Save / Cancel bar so nothing is ever cut off.
 */
@Composable
private fun VaultSettingsPage(
    currentRetention: String,
    currentLockType: String,
    hasPassword: Boolean,
    canBiometric: Boolean,
    onCancel: () -> Unit,
    onSave: (retentionDays: String, lockType: String, newPassword: String) -> Unit
) {
    var selectedRetention by remember { mutableStateOf(currentRetention) }
    var selectedLockType by remember { mutableStateOf(currentLockType) }
    var newPasswordInput by remember { mutableStateOf("") }

    val needsNewPassword = selectedLockType == "password" && !hasPassword
    val canSave = !(needsNewPassword && newPasswordInput.isBlank())

    val retentionOptions = listOf(
        Triple("1", "1 day", Icons.Rounded.Today),
        Triple("7", "7 days", Icons.Rounded.DateRange),
        Triple("14", "14 days", Icons.Rounded.CalendarMonth),
        Triple("30", "30 days", Icons.Rounded.CalendarMonth),
        Triple("never", "Never", Icons.Rounded.AllInclusive),
    )
    data class LockOption(val key: String, val title: String, val subtitle: String, val icon: ImageVector)
    val lockOptions = buildList {
        add(LockOption("none", "None", "Open the vault directly", Icons.Rounded.LockOpen))
        if (canBiometric) add(LockOption("biometric", "Biometric", "Fingerprint or face unlock", Icons.Rounded.Fingerprint))
        add(LockOption("password", "Passcode / PIN", "Enter a passcode to open", Icons.Rounded.Password))
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            PetalSettingsSection(
                title = stringResource(R.string.ui_tab_retention_duration),
                icon = Icons.Rounded.Timer,
            ) {
                Text(
                    text = "How long closed tabs stay in the vault before they are removed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
                retentionOptions.forEachIndexed { index, (key, label, icon) ->
                    VaultChoiceRow(
                        title = label,
                        icon = icon,
                        selected = selectedRetention == key,
                        position = petalGroupPositionFor(index, retentionOptions.size),
                        onClick = { selectedRetention = key }
                    )
                }
            }

            PetalSettingsSection(
                title = stringResource(R.string.ui_protection_type),
                icon = Icons.Rounded.Lock,
            ) {
                Text(
                    text = "Choose how the vault is unlocked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
                lockOptions.forEachIndexed { index, opt ->
                    VaultChoiceRow(
                        title = opt.title,
                        subtitle = opt.subtitle,
                        icon = opt.icon,
                        selected = selectedLockType == opt.key,
                        position = petalGroupPositionFor(index, lockOptions.size),
                        onClick = { selectedLockType = opt.key }
                    )
                }

                AnimatedVisibility(
                    visible = selectedLockType == "password",
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        shape = petalGroupShape(PetalGroupPosition.SINGLE),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = newPasswordInput,
                            onValueChange = { newPasswordInput = it },
                            label = { Text(if (hasPassword) "New passcode (optional)" else "Set a passcode") },
                            supportingText = {
                                Text(
                                    if (hasPassword) "Leave empty to keep your current passcode."
                                    else "You need a passcode before you can save."
                                )
                            },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        )
                    }
                }
            }
        }

        // Pinned action bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Text(stringResource(R.string.ui_cancel), maxLines = 1)
                }
                Button(
                    onClick = { onSave(selectedRetention, selectedLockType, newPasswordInput) },
                    enabled = canSave,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Text(stringResource(R.string.ui_save), fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

/** Single-choice row in the containment style: tonal badge, label, and a check when selected. */
@Composable
private fun VaultChoiceRow(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    position: PetalGroupPosition,
    onClick: () -> Unit,
    subtitle: String? = null
) {
    PetalGroupListRow(
        position = position,
        selected = selected,
        onClick = onClick,
        leading = {
            PetalGroupIconBadge(
                icon = icon,
                container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
            )
        },
        content = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        trailing = {
            if (selected) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                }
            } else {
                Box(
                    Modifier.size(28.dp).border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                )
            }
        }
    )
}
