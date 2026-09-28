package com.petal.browser.passwords

import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.components.PetalExpressiveDialog
import com.petal.browser.view.PetalToast
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

@Composable
fun PetalPasswordExportDialog(
    activity: ComponentActivity,
    onDismiss: () -> Unit
) {
    PetalExpressiveDialog(
        onDismissRequest = onDismiss
    ) {
        Text(
            text = stringResource(R.string.ui_export_password_backup),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = stringResource(R.string.ui_your_password_vault_will_be),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.ui_cancel))
            }

            Button(
                onClick = {
                    try {
                        val json = PetalCredentialVault.exportToJson()
                        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        val fileName = "petal_passwords_backup_$timeStamp.json"
                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        if (!downloadsDir.exists()) {
                            downloadsDir.mkdirs()
                        }
                        val destFile = File(downloadsDir, fileName)
                        destFile.writeText(json, Charsets.UTF_8)

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
                    Icons.Rounded.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.ui_export_json))
            }
        }
    }
}
