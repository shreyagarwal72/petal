package com.petal.browser.passwords

import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import com.petal.browser.ui.kit.TextField
import com.petal.browser.ui.kit.FilledTonalButton
import com.petal.browser.ui.kit.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.components.PetalExpressiveDialog
import com.petal.browser.view.PetalToast
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PetalPasswordExportDialog(
    activity: ComponentActivity,
    onDismiss: () -> Unit
) {
    var exportEncrypted by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var backupPasswordConfirm by remember { mutableStateOf("") }

    PetalExpressiveDialog(
        onDismissRequest = onDismiss
    ) {
        Text(
            text = "Export Password Backup",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = if (exportEncrypted) {
                "Your password vault will be encrypted with a password you choose. You can restore it in Petal on any phone or after reinstalling. If you forget this password, the backup cannot be opened by anyone."
            } else {
                "Your password vault will be exported as a plaintext JSON file saved to your Downloads folder.\n\nKeep this file secure, as anyone with access can read the exported passwords."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
        val optionShape = RoundedCornerShape(20.dp)
        Surface(
            shape = optionShape,
            color = if (isGlass) petalGroupSurfaceColor()
                    else if (exportEncrypted) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isGlass) Modifier.liquidGlassChrome(optionShape, enabled = true, sheenIntensity = 0.5f) else Modifier)
                .clip(optionShape)
                .clickable { exportEncrypted = !exportEncrypted }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (exportEncrypted) Icons.Rounded.EnhancedEncryption else Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = if (exportEncrypted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Password-protected backup",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (exportEncrypted) "Opens only with your password (*.petal)" else "Standard JSON format (*.json)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                com.petal.browser.ui.components.IconSwitch(
                    checked = exportEncrypted,
                    icon = if (exportEncrypted) Icons.Rounded.EnhancedEncryption else Icons.Rounded.Lock,
                    onCheckedChange = { exportEncrypted = it }
                )
            }
        }

        if (exportEncrypted) {
            TextField(
                value = backupPassword,
                onValueChange = { backupPassword = it },
                label = { Text("Backup password (min 8 characters)") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                visualTransformation = PasswordVisualTransformation(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            TextField(
                value = backupPasswordConfirm,
                onValueChange = { backupPasswordConfirm = it },
                label = { Text("Repeat password") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                visualTransformation = PasswordVisualTransformation(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilledTonalButton(
                onClick = onDismiss,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp, topEnd = 6.dp, bottomEnd = 6.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("Cancel")
            }

            Button(
                onClick = {
                    val validationError: String? = when {
                        !exportEncrypted -> null
                        backupPassword.length < PetalCredentialVault.MIN_BACKUP_PASSWORD_LENGTH ->
                            "Password must be at least ${PetalCredentialVault.MIN_BACKUP_PASSWORD_LENGTH} characters"
                        backupPassword != backupPasswordConfirm -> "Passwords do not match"
                        else -> null
                    }
                    if (validationError != null) {
                        PetalToast.show(activity, validationError)
                    } else
                    try {
                        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        val fileName: String
                        val fileData: String

                        if (exportEncrypted) {
                            fileName = "petal_passwords_encrypted_$timeStamp.petal"
                            fileData = PetalCredentialVault.exportWithPassword(backupPassword)
                        } else {
                            fileName = "petal_passwords_backup_$timeStamp.json"
                            fileData = PetalCredentialVault.exportToJson()
                        }

                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        if (!downloadsDir.exists()) {
                            downloadsDir.mkdirs()
                        }
                        val destFile = File(downloadsDir, fileName)
                        destFile.writeText(fileData, Charsets.UTF_8)

                        PetalToast.show(activity, "Exported successfully to Downloads/$fileName")
                        onDismiss()
                    } catch (e: Exception) {
                        PetalToast.show(activity, "Export failed: ${e.message}")
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 50.dp, bottomEnd = 50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    if (exportEncrypted) Icons.Rounded.EnhancedEncryption else Icons.Rounded.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (exportEncrypted) "Export .petal" else "Export JSON")
            }
        }
    }
}
