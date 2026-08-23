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
import com.petal.browser.haptics.PetalHapticEngine
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
            errorMessage = "Incorrect passcode. Please try again."
            PetalHapticEngine.getInstance(context).playIfEnabled(context, PetalHapticEngine.Pattern.HEAVY_CLICK, 1.0f)
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
                // Header Lock Avatar
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(
                        imageVector = if (isUnlockedSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
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
                    text = "Enter your passcode to unlock private browser access",
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
                    hintText = "Enter Passcode",
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
