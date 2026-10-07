/*
 * PetalAppLockScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive App Lock & Security Passcode Screen for Petal Browser.
 * Features PetalShapedPasswordInput with stable MaterialShapes per character.
 */

package com.petal.browser.compose.security

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.rounded.Key
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.components.ExpressivePasswordShapes
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.PetalShapedPasswordInput
import com.petal.browser.ui.containment.PetalAlertDialog
import com.petal.browser.ui.containment.PetalSnackbarHost
import com.petal.browser.ui.theme.ExperimentalMaterial3ExpressiveApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PetalAppLockScreen(
    backgroundSnapshot: androidx.compose.ui.graphics.ImageBitmap? = null,
    onUnlocked: () -> Unit = {},
    onBackPress: () -> Unit = {},
    wrapPredictive: Boolean = true,
) {
    val context = LocalContext.current
    val sp = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }

    val savedPasscode = remember { sp.getString("sp_app_lock_passcode", "1234") ?: "1234" }
    val isBiometricAllowed = remember { sp.getBoolean("sp_biometric_lock", false) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var enteredPasscode by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isUnlockedSuccess by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    fun verifyPasscode() {
        if (enteredPasscode.trim() == savedPasscode.trim()) {
            errorMessage = null
            isUnlockedSuccess = true
            PetalHapticEngine.getInstance(context).playIfEnabled(context, PetalHapticEngine.Pattern.DOUBLE_CLICK, 0.9f)
            val activity = context as? android.app.Activity
            val decor = activity?.window?.decorView
            if (decor != null) {
                com.petal.browser.ui.layout.LiquidRippleEffect.trigger(decor)
            }
            onUnlocked()
        } else {
            errorMessage = "Incorrect app password. Please try again."
            PetalHapticEngine.getInstance(context).playIfEnabled(context, PetalHapticEngine.Pattern.HEAVY_CLICK, 1.0f)
        }
    }

    fun triggerBiometricUnlock() {
        val activity = context as? androidx.appcompat.app.AppCompatActivity
        if (activity != null) {
            com.petal.browser.security.BiometricLockManager.authenticate(
                activity,
                "Petal App Lock",
                "Authenticate with fingerprint to unlock",
                Runnable {
                    errorMessage = null
                    isUnlockedSuccess = true
                    PetalHapticEngine.getInstance(context).playIfEnabled(context, PetalHapticEngine.Pattern.DOUBLE_CLICK, 0.9f)
                    val decor = activity.window?.decorView
                    if (decor != null) {
                        com.petal.browser.ui.layout.LiquidRippleEffect.trigger(decor)
                    }
                    onUnlocked()
                },
                java.util.function.Consumer { error ->
                    errorMessage = "Fingerprint error: $error"
                    PetalHapticEngine.getInstance(context).playIfEnabled(context, PetalHapticEngine.Pattern.HEAVY_CLICK, 1.0f)
                }
            )
        }
    }

    val isBiometricAvailable = isBiometricAllowed && com.petal.browser.security.BiometricLockManager.canAuthenticate(context)
    var showChoiceDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (isBiometricAvailable) {
            triggerBiometricUnlock()
        }
    }

    if (showChoiceDialog && isBiometricAvailable) {
        PetalAlertDialog(
            onDismissRequest = { showChoiceDialog = false },
            title = stringResource(R.string.ui_choose_unlock_method),
            message = stringResource(R.string.ui_select_how_you_would_like),
            icon = Icons.Rounded.Security,
            confirmText = stringResource(R.string.ui_use_fingerprint),
            dismissText = stringResource(R.string.ui_use_password),
            onConfirm = {
                showChoiceDialog = false
                triggerBiometricUnlock()
            },
        )
    }

    val content = @Composable {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { PetalSnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                M3ExpressiveVariableBackground(
                    modifier = Modifier.fillMaxSize(),
                    pageSeed = "security_app_lock"
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val avatarScale = remember { androidx.compose.animation.core.Animatable(0.8f) }
                    LaunchedEffect(isUnlockedSuccess) {
                        avatarScale.animateTo(
                            1f,
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                            )
                        )
                    }

                    Surface(
                        shape = com.petal.browser.ui.theme.PetalMaterialShapes.Bun.toShape(),
                        modifier = Modifier
                            .size(112.dp)
                            .scale(avatarScale.value),
                        color = if (isUnlockedSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
                        tonalElevation = 6.dp
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isUnlockedSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                                contentDescription = stringResource(R.string.ui_app_lock),
                                tint = if (isUnlockedSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = if (isUnlockedSuccess) "Unlocked Successfully" else "App Protected",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Text(
                        text = stringResource(R.string.ui_enter_your_passcode_or_use),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                    )

                    PetalShapedPasswordInput(
                        value = enteredPasscode,
                        onValueChange = { newValue ->
                            enteredPasscode = newValue
                            if (errorMessage != null) errorMessage = null
                            if (savedPasscode.isNotBlank() && newValue.trim() == savedPasscode.trim()) {
                                verifyPasscode()
                            }
                        },
                        hintText = "Enter App Password",
                        isError = errorMessage != null,
                        accentColor = MaterialTheme.colorScheme.primary,
                        onUnlock = { verifyPasscode() },
                        unlockButtonText = "Unlock",
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
                        disableAutofill = true
                    )

                    AnimatedVisibility(
                        visible = errorMessage != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        if (errorMessage != null) {
                            Text(
                                text = errorMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (isBiometricAvailable) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { triggerBiometricUnlock() },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.ui_fingerprint), fontWeight = FontWeight.Bold)
                            }

                            IconButton(
                                onClick = { showChoiceDialog = true },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Rounded.Security, contentDescription = stringResource(R.string.ui_choose_lock_option))
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                enteredPasscode = ""
                                errorMessage = null
                            }
                        ) {
                            Text(stringResource(R.string.ui_clear_input))
                        }

                        TextButton(
                            onClick = {
                                showForgotPasswordDialog = true
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(stringResource(R.string.ui_forgot_password))
                        }
                    }
                }
            }
        }
    }

    if (showForgotPasswordDialog) {
        PetalAlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            title = stringResource(R.string.ui_forgot_password),
            message = stringResource(R.string.ui_petal_uses_on_device_hardware),
            icon = Icons.Rounded.WarningAmber,
            destructive = true,
            confirmText = stringResource(R.string.ui_erase_all_data_unlock),
            onConfirm = {
                showForgotPasswordDialog = false
                com.petal.browser.unit.BrowserUnit.eraseAllAppData(context)
                PetalHapticEngine.getInstance(context).playIfEnabled(context, PetalHapticEngine.Pattern.DOUBLE_CLICK, 0.9f)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = "All app data has been erased. Please do not forget your password again!",
                        duration = SnackbarDuration.Long
                    )
                    delay(1600L)
                    val activity = context as? android.app.Activity
                    val decor = activity?.window?.decorView
                    if (decor != null) {
                        com.petal.browser.ui.layout.LiquidRippleEffect.trigger(decor)
                    }
                    onUnlocked()
                }
            },
        )
    }

    if (wrapPredictive) {
        com.petal.browser.predictive.PetalPredictiveBackSurface(
            enabled = true,
            onBack = onBackPress,
        ) {
            com.petal.browser.predictive.PetalScreenWrapper(backgroundSnapshot = backgroundSnapshot) {
                content()
            }
        }
    } else {
        content()
    }
}
