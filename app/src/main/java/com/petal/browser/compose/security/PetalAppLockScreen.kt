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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.components.ExpressivePasswordShapes
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.PetalShapedPasswordInput
import com.petal.browser.ui.theme.ExperimentalMaterial3ExpressiveApi

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PetalAppLockScreen(
    backgroundSnapshot: androidx.compose.ui.graphics.ImageBitmap? = null,
    onUnlocked: () -> Unit = {},
    onBackPress: () -> Unit = {}
) {
    val context = LocalContext.current
    val sp = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }

    val savedPasscode = remember { sp.getString("sp_app_lock_passcode", "1234") ?: "1234" }
    val isBiometricAllowed = remember { sp.getBoolean("sp_biometric_lock", false) }
    var enteredPasscode by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isUnlockedSuccess by remember { mutableStateOf(false) }

    fun verifyPasscode() {
        if (enteredPasscode.trim() == savedPasscode.trim()) {
            errorMessage = null
            isUnlockedSuccess = true
            PetalHapticEngine.getInstance(context).playIfEnabled(context, PetalHapticEngine.Pattern.DOUBLE_CLICK, 0.9f)
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
        AlertDialog(
            onDismissRequest = { showChoiceDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Choose Unlock Method",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Select how you would like to authenticate and unlock Petal Browser.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showChoiceDialog = false
                        triggerBiometricUnlock()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Use Fingerprint")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showChoiceDialog = false
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Use Password")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp)
        )
    }

    com.petal.browser.predictive.PetalPredictiveBackSurface(
        enabled = true,
        onBack = onBackPress,
    ) {
        com.petal.browser.predictive.PetalScreenWrapper(backgroundSnapshot = backgroundSnapshot) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    M3ExpressiveVariableBackground(pageSeed = "security_app_lock")

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val animatedShapeIndex by remember { mutableIntStateOf(0) }
                        val headerShape: Shape = ExpressivePasswordShapes[animatedShapeIndex % ExpressivePasswordShapes.size].toShape()
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

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(96.dp)
                                .scale(avatarScale.value)
                                .clip(headerShape)
                                .background(
                                    if (isUnlockedSuccess) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                        ) {
                            Icon(
                                imageVector = if (isUnlockedSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                                contentDescription = "App Lock",
                                tint = if (isUnlockedSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = if (isUnlockedSuccess) "Unlocked Successfully" else "App Protected",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Text(
                            text = "Enter your passcode or use biometric authentication",
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
                            unlockButtonText = "Unlock"
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
                                    Text("Fingerprint", fontWeight = FontWeight.Bold)
                                }

                                IconButton(
                                    onClick = { showChoiceDialog = true },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Rounded.Security, contentDescription = "Choose Lock Option")
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        TextButton(
                            onClick = {
                                enteredPasscode = ""
                                errorMessage = null
                            }
                        ) {
                            Text("Clear Input")
                        }
                    }
                }
            }
        }
    }
}
