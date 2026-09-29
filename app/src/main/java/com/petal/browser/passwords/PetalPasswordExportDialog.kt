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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
                "Your password vault will be encrypted using Petal's hardware-backed AES-256 GCM key. It can ONLY be decrypted and restored by Petal on this device/installation."
            } else {
                "Your password vault will be exported as a plaintext JSON file saved to your Downloads folder.\n\nKeep this file secure, as anyone with access can read the exported passwords."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (exportEncrypted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
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
                        tint = if (exportEncrypted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Petal Hardware Encryption",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (exportEncrypted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (exportEncrypted) "Only accessible by Petal (*.petal)" else "Standard JSON format (*.json)",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (exportEncrypted) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = exportEncrypted,
                    onCheckedChange = { exportEncrypted = it }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Cancel")
            }

            Button(
                onClick = {
                    try {
                        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        val fileName: String
                        val fileData: String

                        if (exportEncrypted) {
                            fileName = "petal_passwords_encrypted_$timeStamp.petal"
                            fileData = PetalCredentialVault.exportEncrypted()
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
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
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
