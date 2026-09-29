package com.petal.browser.passwords

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.petal.browser.compose.file.PetalFilePickerBridge
import com.petal.browser.view.PetalToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalPasswordImportSheet(
    activity: ComponentActivity,
    onImportComplete: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isImporting by remember { mutableStateOf(false) }

    fun processFile(file: File, parserType: String) {
        isImporting = true
        coroutineScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    file.readText(Charsets.UTF_8)
                }
                var count = 0
                when (parserType) {
                    "CHROME" -> {
                        val creds = PetalCredentialImporter.importFromChromeCsv(content)
                        creds.forEach { PetalCredentialVault.save(it) }
                        count = creds.size
                    }
                    "FIREFOX" -> {
                        val creds = PetalCredentialImporter.importFromFirefoxCsv(content)
                        creds.forEach { PetalCredentialVault.save(it) }
                        count = creds.size
                    }
                    "BITWARDEN" -> {
                        val creds = PetalCredentialImporter.importFromBitwardenJson(content)
                        creds.forEach { PetalCredentialVault.save(it) }
                        count = creds.size
                    }
                    "ONE_PASSWORD" -> {
                        val creds = PetalCredentialImporter.importFromOnePasswordCsv(content)
                        creds.forEach { PetalCredentialVault.save(it) }
                        count = creds.size
                    }
                    "DASHLANE" -> {
                        val creds = PetalCredentialImporter.importFromDashlaneCsv(content)
                        creds.forEach { PetalCredentialVault.save(it) }
                        count = creds.size
                    }
                    "PETAL_JSON" -> {
                        count = PetalCredentialVault.importFromJson(content)
                    }
                    "PETAL_ENC" -> {
                        count = PetalCredentialVault.importEncrypted(content)
                    }
                    else -> {
                        val (_, creds) = PetalCredentialImporter.detectAndImport(file.name, content)
                        creds.forEach { PetalCredentialVault.save(it) }
                        count = creds.size
                    }
                }
                withContext(Dispatchers.Main) {
                    isImporting = false
                    PetalToast.show(activity, "Successfully imported $count passwords")
                    onImportComplete(count)
                    onDismiss()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isImporting = false
                    PetalToast.show(activity, "Failed to import: ${e.message}")
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Import Passwords",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Select source format to browse files using Petal File Picker",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (isImporting) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        ImportSourceOption(
                            icon = Icons.Rounded.Key,
                            title = "Google Chrome / Chromium",
                            subtitle = "CSV export (*.csv)",
                            onClick = {
                                PetalFilePickerBridge.showFilePicker(
                                    activity = activity,
                                    mimeTypes = arrayOf("text/csv", "text/plain", "application/csv"),
                                    asModalDialog = true,
                                    onFileSelected = { file -> processFile(file, "CHROME") },
                                    onDismiss = {}
                                )
                            }
                        )
                    }
                    item {
                        ImportSourceOption(
                            icon = Icons.Rounded.Public,
                            title = "Mozilla Firefox",
                            subtitle = "CSV export (*.csv)",
                            onClick = {
                                PetalFilePickerBridge.showFilePicker(
                                    activity = activity,
                                    mimeTypes = arrayOf("text/csv", "text/plain", "application/csv"),
                                    asModalDialog = true,
                                    onFileSelected = { file -> processFile(file, "FIREFOX") },
                                    onDismiss = {}
                                )
                            }
                        )
                    }
                    item {
                        ImportSourceOption(
                            icon = Icons.Rounded.Shield,
                            title = "Bitwarden",
                            subtitle = "JSON export (*.json)",
                            onClick = {
                                PetalFilePickerBridge.showFilePicker(
                                    activity = activity,
                                    mimeTypes = arrayOf("application/json", "text/plain"),
                                    asModalDialog = true,
                                    onFileSelected = { file -> processFile(file, "BITWARDEN") },
                                    onDismiss = {}
                                )
                            }
                        )
                    }
                    item {
                        ImportSourceOption(
                            icon = Icons.Rounded.Lock,
                            title = "1Password",
                            subtitle = "1Password CSV export (*.csv)",
                            onClick = {
                                PetalFilePickerBridge.showFilePicker(
                                    activity = activity,
                                    mimeTypes = arrayOf("text/csv", "text/plain", "application/csv"),
                                    asModalDialog = true,
                                    onFileSelected = { file -> processFile(file, "ONE_PASSWORD") },
                                    onDismiss = {}
                                )
                            }
                        )
                    }
                    item {
                        ImportSourceOption(
                            icon = Icons.Rounded.VpnKey,
                            title = "Dashlane",
                            subtitle = "Dashlane CSV export (*.csv)",
                            onClick = {
                                PetalFilePickerBridge.showFilePicker(
                                    activity = activity,
                                    mimeTypes = arrayOf("text/csv", "text/plain", "application/csv"),
                                    asModalDialog = true,
                                    onFileSelected = { file -> processFile(file, "DASHLANE") },
                                    onDismiss = {}
                                )
                            }
                        )
                    }
                    item {
                        ImportSourceOption(
                            icon = Icons.Rounded.Backup,
                            title = "Petal Vault Backup",
                            subtitle = "Petal backup JSON (*.json)",
                            onClick = {
                                PetalFilePickerBridge.showFilePicker(
                                    activity = activity,
                                    mimeTypes = arrayOf("application/json", "text/plain"),
                                    asModalDialog = true,
                                    onFileSelected = { file -> processFile(file, "PETAL_JSON") },
                                    onDismiss = {}
                                )
                            }
                        )
                    }
                    item {
                        ImportSourceOption(
                            icon = Icons.Rounded.EnhancedEncryption,
                            title = "Encrypted Petal Vault",
                            subtitle = "Hardware-encrypted Petal backup (*.petal)",
                            onClick = {
                                PetalFilePickerBridge.showFilePicker(
                                    activity = activity,
                                    mimeTypes = arrayOf("*/*", "application/octet-stream", "text/plain"),
                                    asModalDialog = true,
                                    onFileSelected = { file -> processFile(file, "PETAL_ENC") },
                                    onDismiss = {}
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportSourceOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                Icons.AutoMirrored.Rounded.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
