package com.petal.browser.passwords

import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.petal.browser.compose.file.PetalFilePickerBridge
import com.petal.browser.ui.containment.PetalBadgeVariant
import com.petal.browser.ui.containment.PetalGroup
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.PetalGroupNavigationRow
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.PetalSectionLabel
import com.petal.browser.ui.containment.PetalSheet
import com.petal.browser.ui.containment.petalGroupShape
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
                        count = if (PetalCredentialVault.isPasswordBackup(content)) {
                            val pw = withContext(Dispatchers.Main) { PetalBackupPasswordPrompt.ask(activity) }
                                ?: throw IllegalStateException("Import cancelled")
                            PetalCredentialVault.importWithPasswordOrThrow(content, pw)
                        } else {
                            PetalCredentialVault.importEncryptedOrThrow(content)
                        }
                    }
                    else -> {
                        val (_, creds) = PetalCredentialImporter.detectAndImport(file.name, content)
                        creds.forEach { PetalCredentialVault.save(it) }
                        count = creds.size
                    }
                }
                withContext(Dispatchers.Main) {
                    isImporting = false
                    PetalToast.show(
                        activity,
                        if (count > 0) "Successfully imported $count passwords"
                        else "No new passwords imported; the backup may be empty or its entries already exist."
                    )
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

    fun pickFile(sourceType: String, mimeTypes: Array<String>) {
        PetalFilePickerBridge.showFilePicker(
            activity = activity,
            mimeTypes = mimeTypes,
            asModalDialog = true,
            onFileSelected = { file -> processFile(file, sourceType) },
            onDismiss = {}
        )
    }

    val csvMimes = arrayOf("text/csv", "text/plain", "application/csv")
    val jsonMimes = arrayOf("application/json", "text/plain")
    val anyMimes = arrayOf("*/*", "application/octet-stream", "text/plain")

    val otherSources = listOf(
        ImportSource(Icons.Rounded.Key, "Google Chrome / Chromium", "CSV export (*.csv)", PetalBadgeVariant.PRIMARY, "CHROME", csvMimes),
        ImportSource(Icons.Rounded.Public, "Mozilla Firefox", "CSV export (*.csv)", PetalBadgeVariant.SECONDARY, "FIREFOX", csvMimes),
        ImportSource(Icons.Rounded.Shield, "Bitwarden", "JSON export (*.json)", PetalBadgeVariant.TERTIARY, "BITWARDEN", jsonMimes),
        ImportSource(Icons.Rounded.Lock, "1Password", "CSV export (*.csv)", PetalBadgeVariant.PRIMARY, "ONE_PASSWORD", csvMimes),
        ImportSource(Icons.Rounded.VpnKey, "Dashlane", "CSV export (*.csv)", PetalBadgeVariant.SECONDARY, "DASHLANE", csvMimes),
    )
    val petalSources = listOf(
        ImportSource(Icons.Rounded.Backup, "Petal Vault Backup", "Petal backup JSON (*.json)", PetalBadgeVariant.PRIMARY, "PETAL_JSON", jsonMimes),
        ImportSource(Icons.Rounded.EnhancedEncryption, "Encrypted Petal Vault", "Petal backup (*.petal), password asked if needed", PetalBadgeVariant.TERTIARY, "PETAL_ENC", anyMimes),
    )

    PetalSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PetalGroupIconBadge(
                    icon = Icons.Rounded.FileDownload,
                    variant = PetalBadgeVariant.PRIMARY,
                    size = 48.dp,
                    iconSize = 24.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Import Passwords",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Pick a source format, then choose the file with Petal File Picker",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isImporting) {
                Surface(
                    shape = petalGroupShape(PetalGroupPosition.SINGLE),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Importing passwords...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column {
                    PetalSectionLabel("Browsers & password managers")
                    ImportSourceGroup(otherSources, ::pickFile)
                }
                Column {
                    PetalSectionLabel("Petal backups")
                    ImportSourceGroup(petalSources, ::pickFile)
                }
            }
        }
    }
}

private data class ImportSource(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val variant: PetalBadgeVariant,
    val type: String,
    val mimeTypes: Array<String>
)

@Composable
private fun ImportSourceGroup(
    sources: List<ImportSource>,
    onPick: (String, Array<String>) -> Unit
) {
    PetalGroup(rowCount = sources.size) { index, position ->
        val source = sources[index]
        PetalGroupNavigationRow(
            title = source.title,
            subtitle = source.subtitle,
            position = position,
            variant = source.variant,
            leadingIcon = {
                Icon(source.icon, contentDescription = null, modifier = Modifier.size(22.dp))
            },
            onClick = { onPick(source.type, source.mimeTypes) }
        )
    }
}
