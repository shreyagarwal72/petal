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
                // Header Lock Avatar with Monogram Morphing Shape Animation
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
                        .size(88.dp)
                        .graphicsLayer {
                            scaleX = avatarScale.value
                            scaleY = avatarScale.value
                        }
                        .clip(headerShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(
                        imageVector = if (isUnlockedSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Petal Security Lock",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Enter your custom app password or use fingerprint to unlock",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Shaped Mask Password Input Component
                PetalShapedPasswordInput(
                    value = enteredPasscode,
                    onValueChange = {
                        enteredPasscode = it
                        if (errorMessage != null) errorMessage = null
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

                if (isBiometricAllowed && com.petal.browser.security.BiometricLockManager.canAuthenticate(context)) {
                    OutlinedButton(
                        onClick = { triggerBiometricUnlock() },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Unlock with Fingerprint", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Passcode Action Helper
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
