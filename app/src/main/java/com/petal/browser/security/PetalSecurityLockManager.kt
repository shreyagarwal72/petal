package com.petal.browser.security

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.preference.PreferenceManager
import com.petal.browser.haptics.PetalHapticEngine

/**
 * Petal Pin & Pattern Security Manager
 * Dedicated in-app PIN and Pattern lock inspired by Soul Browser.
 * Securely protects Incognito tabs and secret bookmarks without sharing OS master credentials.
 */
object PetalSecurityLockManager {
    private const val PREF_LOCK_PIN = "sp_petal_security_pin"
    private const val PREF_LOCK_PATTERN = "sp_petal_security_pattern"
    private const val PREF_SECRET_TAB_LOCK_ENABLED = "sp_secret_tab_lock_enabled"

    fun isPinSet(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return !sp.getString(PREF_LOCK_PIN, null).isNullOrBlank()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getString(PREF_LOCK_PIN, "") == pin
    }

    fun setPin(context: Context, pin: String) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(PREF_LOCK_PIN, pin)
            .apply()
    }

    fun clearPin(context: Context) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .remove(PREF_LOCK_PIN)
            .apply()
    }

    fun isSecretLockEnabled(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean(PREF_SECRET_TAB_LOCK_ENABLED, false)
    }

    fun setSecretLockEnabled(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(PREF_SECRET_TAB_LOCK_ENABLED, enabled)
            .apply()
    }
}

/**
 * Material 3 Pin Lock Dialog with haptic feedback & animated dots.
 */
@Composable
fun PetalPinLockDialog(
    title: String = "Enter Security PIN",
    subtitle: String = "Protecting Secret Tabs & Downloads",
    isSettingPin: Boolean = false,
    onSuccess: (String) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var enteredPin by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    fun onDigitPress(digit: String) {
        if (enteredPin.length < 4) {
            enteredPin += digit
            PetalHapticEngine.getInstance(context).playClick(context)
            if (enteredPin.length == 4) {
                if (isSettingPin) {
                    PetalSecurityLockManager.setPin(context, enteredPin)
                    PetalHapticEngine.getInstance(context).playHeavyClick(context)
                    onSuccess(enteredPin)
                } else {
                    if (PetalSecurityLockManager.verifyPin(context, enteredPin)) {
                        PetalHapticEngine.getInstance(context).playHeavyClick(context)
                        onSuccess(enteredPin)
                    } else {
                        PetalHapticEngine.getInstance(context).playClick(context)
                        errorText = "Incorrect PIN"
                        enteredPin = ""
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header Lock Icon
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = errorText ?: subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (errorText != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 4 PIN Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // 3x4 Number Keypad
                val keypad = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("C", "0", "⌫")
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    keypad.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            row.forEach { key ->
                                Surface(
                                    shape = CircleShape,
                                    color = if (key in "0".."9") MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clickable {
                                            when (key) {
                                                "C" -> {
                                                    enteredPin = ""
                                                    errorText = null
                                                    PetalHapticEngine.getInstance(context).playClick(context)
                                                }
                                                "⌫" -> {
                                                    if (enteredPin.isNotEmpty()) {
                                                        enteredPin = enteredPin.dropLast(1)
                                                        errorText = null
                                                        PetalHapticEngine.getInstance(context).playClick(context)
                                                    }
                                                }
                                                else -> onDigitPress(key)
                                            }
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = key,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
