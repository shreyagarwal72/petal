package com.petal.browser.passwords

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.PetalExpressiveDialog
import com.petal.browser.ui.components.PetalShapedPasswordInput
import com.petal.browser.ui.containment.*
import com.petal.browser.unit.PasswordBreachAuditManager
import com.petal.browser.view.PetalToast
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalPasswordsScreen(
    activity: ComponentActivity,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var credentials by remember { mutableStateOf(emptyList<PetalCredential>()) }
    var searchQuery by remember { mutableStateOf("") }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showImportSheet by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showAddEditDialog by remember { mutableStateOf<PetalCredential?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }
    var selectedCredentialForDetails by remember { mutableStateOf<PetalCredential?>(null) }

    LaunchedEffect(Unit) {
        PetalCredentialVault.init(context)
        if (PetalPasswordTermsManager.shouldShow(context)) {
            showTermsDialog = true
        } else {
            credentials = PetalCredentialVault.getAll()
        }
    }

    fun reloadCredentials() {
        credentials = PetalCredentialVault.getAll()
    }

    val filteredList = remember(credentials, searchQuery) {
        if (searchQuery.isBlank()) {
            credentials
        } else {
            val q = searchQuery.trim().lowercase()
            credentials.filter {
                it.domain.lowercase().contains(q) ||
                it.username.lowercase().contains(q) ||
                it.notes.lowercase().contains(q)
            }
        }
    }

    if (showTermsDialog) {
        PetalPasswordTermsDialog(
            onAccept = {
                showTermsDialog = false
                reloadCredentials()
            },
            onDismiss = {
                showTermsDialog = false
                onNavigateBack()
            }
        )
    }

    if (showImportSheet) {
        PetalPasswordImportSheet(
            activity = activity,
            onImportComplete = { reloadCredentials() },
            onDismiss = { showImportSheet = false }
        )
    }

    if (showExportDialog) {
        PetalPasswordExportDialog(
            activity = activity,
            onDismiss = { showExportDialog = false }
        )
    }

    if (isAddingNew || showAddEditDialog != null) {
        val editing = showAddEditDialog
        PasswordEditDialog(
            initial = editing,
            onDismiss = {
                isAddingNew = false
                showAddEditDialog = null
            },
            onSave = { saved ->
                PetalCredentialVault.save(saved)
                reloadCredentials()
                isAddingNew = false
                showAddEditDialog = null
                PetalToast.show(context, "Password saved")
            }
        )
    }

    if (selectedCredentialForDetails != null) {
        val cred = selectedCredentialForDetails!!
        PasswordDetailsDialog(
            credential = cred,
            onDismiss = { selectedCredentialForDetails = null },
            onEdit = {
                selectedCredentialForDetails = null
                showAddEditDialog = cred
            },
            onDelete = {
                PetalCredentialVault.delete(cred.id)
                reloadCredentials()
                selectedCredentialForDetails = null
                PetalToast.show(context, "Password deleted")
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        M3ExpressiveVariableBackground(pageSeed = "petal_passwords_screen")

        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveHeader(
                title = "Password Manager",
                subtitle = "Hardware-encrypted local vault & autofill",
                onBack = onNavigateBack
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
            ) {
                // 1. Status Hero Card (Hardware Vault status & stats)
                item {
                    PetalStatusHeroCard(
                        title = "Petal Secure Vault",
                        subtitle = "${credentials.size} passwords stored locally • AES-256 GCM",
                        statusText = "Hardware Encrypted",
                        icon = Icons.Rounded.Security,
                        statusActive = true,
                        actionLabel = "System Autofill",
                        onActionClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                    }
                                    activity.startActivity(intent)
                                } catch (_: Exception) {
                                    try {
                                        val intent = Intent(Settings.ACTION_SETTINGS)
                                        activity.startActivity(intent)
                                    } catch (_: Exception) {
                                        PetalToast.show(context, "Open Android Settings > System > Autofill")
                                    }
                                }
                            }
                        }
                    )
                }

                // 2. Search & Filter Bar
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search logins & domains...") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 3. Quick Actions Group
                item {
                    PetalSettingsSection(
                        title = "Vault Management",
                        icon = Icons.Rounded.ManageAccounts
                    ) {
                        PetalGroup(rowCount = 3) { index, position ->
                            when (index) {
                                0 -> PetalGroupRow(
                                    icon = Icons.Rounded.AddCircle,
                                    title = "Add New Login",
                                    subtitle = "Store a username and password",
                                    position = position,
                                    iconContainer = MaterialTheme.colorScheme.primaryContainer,
                                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    onClick = { isAddingNew = true }
                                )
                                1 -> PetalGroupRow(
                                    icon = Icons.Rounded.FileDownload,
                                    title = "Import Passwords",
                                    subtitle = "Chrome, Firefox, Bitwarden, 1Password, Petal",
                                    position = position,
                                    iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    onClick = { showImportSheet = true }
                                )
                                2 -> PetalGroupRow(
                                    icon = Icons.Rounded.FileUpload,
                                    title = "Export Backup",
                                    subtitle = "Encrypted (.petal) or JSON backup",
                                    position = position,
                                    iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                                    iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    onClick = { showExportDialog = true }
                                )
                            }
                        }
                    }
                }

                // 4. Saved Logins Section
                item {
                    PetalSectionLabel(
                        text = if (searchQuery.isBlank()) "Saved Logins (${filteredList.size})" else "Matching Logins (${filteredList.size})"
                    )
                }

                if (filteredList.isEmpty()) {
                    item {
                        PetalHeroCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
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
                                Text(
                                    text = if (searchQuery.isBlank()) "No Passwords Stored" else "No Matches Found",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (searchQuery.isBlank())
                                        "Saved logins in Petal or imported from Chrome, Firefox, or Bitwarden will appear here."
                                    else "Try searching for a different domain or username.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    item {
                        PetalGroup(rowCount = filteredList.size) { index, position ->
                            val item = filteredList[index]
                            PetalGroupListRow(
                                position = position,
                                onClick = { selectedCredentialForDetails = item },
                                leading = {
                                    val (badgeBg, badgeFg) = when (index % 4) {
                                        0 -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                                        1 -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
                                        2 -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                                        else -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    PetalGroupIconBadge(
                                        shape = RoundedCornerShape(12.dp),
                                        containerColor = badgeBg,
                                        contentColor = badgeFg,
                                        size = 40.dp
                                    ) {
                                        Text(
                                            text = item.domain.take(1).uppercase(),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                },
                                headline = {
                                    Text(
                                        text = item.domain,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                supporting = {
                                    Text(
                                        text = item.username.ifBlank { "Password only" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                trailing = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                PetalCredentialVault.toggleFavorite(item.id)
                                                reloadCredentials()
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (item.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                                contentDescription = "Favorite",
                                                tint = if (item.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Icon(
                                            Icons.Rounded.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordDetailsDialog(
    credential: PetalCredential,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var isPasswordVisible by remember { mutableStateOf(false) }
    var breachStatus by remember { mutableStateOf<String?>(null) }
    var isCheckingBreach by remember { mutableStateOf(false) }

    PetalExpressiveDialog(onDismissRequest = onDismiss) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = credential.domain.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = credential.domain,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (credential.originUrl.isNotBlank()) {
                    Text(
                        text = credential.originUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Username row
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Username", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(credential.username.ifBlank { "(empty)" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                IconButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Username", credential.username))
                        PetalToast.show(context, "Username copied")
                    }
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
                }
            }
        }

        // Password row
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Password", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (isPasswordVisible) credential.password else "••••••••••••",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Row {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "Toggle visibility",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Password", credential.password))
                            PetalToast.show(context, "Password copied")
                        }
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Breach check button
        OutlinedButton(
            onClick = {
                isCheckingBreach = true
                breachStatus = null
                PasswordBreachAuditManager.checkPassword(credential.password) { isPwned, count ->
                    isCheckingBreach = false
                    breachStatus = if (isPwned) {
                        "⚠️ Compromised! Found in $count data breaches."
                    } else {
                        "✅ Safe! Not found in known public breaches."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (isCheckingBreach) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Checking HIBP...")
            } else {
                Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Check for Breaches")
            }
        }

        if (breachStatus != null) {
            Text(
                text = breachStatus!!,
                style = MaterialTheme.typography.bodySmall,
                color = if (breachStatus!!.contains("Compromised")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onDelete,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Delete")
            }
            Button(
                onClick = onEdit,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Edit")
            }
        }
    }
}

@Composable
private fun PasswordEditDialog(
    initial: PetalCredential?,
    onDismiss: () -> Unit,
    onSave: (PetalCredential) -> Unit
) {
    var domain by remember { mutableStateOf(initial?.domain.orEmpty()) }
    var originUrl by remember { mutableStateOf(initial?.originUrl.orEmpty()) }
    var username by remember { mutableStateOf(initial?.username.orEmpty()) }
    var password by remember { mutableStateOf(initial?.password.orEmpty()) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }

    PetalExpressiveDialog(onDismissRequest = onDismiss) {
        Text(
            text = if (initial == null) "Add Password" else "Edit Password",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = domain,
                onValueChange = { domain = it },
                label = { Text("Website Domain (e.g. google.com)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username or Email") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            PetalShapedPasswordInput(
                value = password,
                onValueChange = { password = it },
                hintText = "Password",
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                singleLine = false,
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
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
                    if (domain.isNotBlank()) {
                        val cleanDomain = domain.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").split("/")[0]
                        val cred = initial?.copy(
                            domain = cleanDomain,
                            originUrl = originUrl.ifBlank { "https://$cleanDomain" },
                            username = username.trim(),
                            password = password,
                            notes = notes,
                            updatedAt = System.currentTimeMillis()
                        ) ?: PetalCredential(
                            id = UUID.randomUUID().toString(),
                            domain = cleanDomain,
                            originUrl = originUrl.ifBlank { "https://$cleanDomain" },
                            username = username.trim(),
                            password = password,
                            notes = notes
                        )
                        onSave(cred)
                    }
                },
                enabled = domain.isNotBlank() && (password.isNotBlank() || username.isNotBlank()),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Save")
            }
        }
    }
}
