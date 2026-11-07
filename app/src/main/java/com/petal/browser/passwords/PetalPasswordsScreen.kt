package com.petal.browser.passwords
import com.petal.browser.ui.theme.ExpressiveButtonShape
import com.petal.browser.ui.components.PetalExpressiveScrollBar
import androidx.compose.foundation.lazy.rememberLazyListState
import com.petal.browser.ui.components.PetalChoiceButtonGroup
import com.petal.browser.ui.components.PetalButtonGroupItem

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.petal.browser.compose.composable.PetalContainedLoadingIndicator
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.IconSwitch
import com.petal.browser.ui.components.PetalSlider
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.PetalShapedPasswordInput
import com.petal.browser.ui.containment.*
import com.petal.browser.ui.glass.*
import com.petal.browser.unit.PasswordBreachAuditManager
import com.petal.browser.view.PetalToast
import java.util.UUID

/**
 * PetalPasswordsScreen
 *
 * Modern Brave-style Password Manager & Autofill Control Center:
 * 1. Material 3 Expressive & Liquid Glass architecture adhering strictly to petal-liquid-glass skill.
 * 2. Window-level backdrop recording with real refraction, sheen, and see-through surfaces.
 * 3. Domain & Subdomain grouping like Brave/Chromium with expandable credential rows.
 * 4. Never-Save domain management list with remove/toggle actions.
 * 5. Customizable password generator presets (Balanced, High Entropy, Numeric PIN, Custom).
 * 6. Quick copy, password reveal, breach status auditing, import/export dialogs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalPasswordsScreen(
    activity: ComponentActivity,
    onNavigateBack: () -> Unit,
    backgroundSnapshot: androidx.compose.ui.graphics.ImageBitmap? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = remember { PetalHapticEngine.getInstance(context) }
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val ambientWallpaper = LocalPetalGlassWallpaper.current
    val wallpaper = ambientWallpaper ?: rememberPetalGlassWallpaper()

    var credentials by remember { mutableStateOf(emptyList<PetalCredential>()) }
    var neverSaveDomains by remember { mutableStateOf(emptySet<String>()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedSortMode by remember { mutableIntStateOf(0) } // 0: Domain, 1: Recently Updated, 2: Favorites
    var selectedMainTab by remember { mutableIntStateOf(0) } // 0: Logins, 1: Never-Saved, 2: Security & Generator
    var genPreset by remember { mutableStateOf(PetalPasswordGenerator.Preset.BALANCED) }
    var genLength by remember { mutableFloatStateOf(16f) }
    var generatedPassword by remember { mutableStateOf(PetalPasswordGenerator.generateByPreset(PetalPasswordGenerator.Preset.BALANCED)) }

    var showTermsDialog by remember { mutableStateOf(false) }
    var showImportSheet by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showAddEditDialog by remember { mutableStateOf<PetalCredential?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }
    var selectedCredentialForDetails by remember { mutableStateOf<PetalCredential?>(null) }
    var showNeverSaveSheet by remember { mutableStateOf(false) }

    fun reloadData() {
        credentials = PetalCredentialVault.getAll()
        neverSaveDomains = PetalCredentialVault.getNeverSaveDomains(context)
    }

    LaunchedEffect(Unit) {
        PetalCredentialVault.init(context)
        if (PetalPasswordTermsManager.shouldShow(context)) {
            showTermsDialog = true
        } else {
            reloadData()
        }
    }

    // Domain grouped credentials matching Brave's grouped architecture
    val groupedLogins = remember(credentials, searchQuery, selectedSortMode) {
        val filtered = if (searchQuery.isBlank()) {
            credentials
        } else {
            val q = searchQuery.trim().lowercase()
            credentials.filter {
                it.domain.lowercase().contains(q) ||
                it.username.lowercase().contains(q) ||
                it.notes.lowercase().contains(q)
            }
        }

        val sorted = when (selectedSortMode) {
            1 -> filtered.sortedByDescending { it.updatedAt }
            2 -> filtered.sortedWith(compareByDescending<PetalCredential> { it.isFavorite }.thenBy { it.domain })
            else -> filtered.sortedBy { it.domain.lowercase() }
        }

        // Group by primary domain / eTLD+1 base
        sorted.groupBy { cred ->
            val d = cred.domain.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").split("/")[0]
            val parts = d.split(".")
            if (parts.size >= 2) {
                // Return base domain like google.com for accounts.google.com
                parts.takeLast(2).joinToString(".")
            } else {
                d
            }
        }
    }

    if (showTermsDialog) {
        PetalPasswordTermsDialog(
            onAccept = {
                showTermsDialog = false
                reloadData()
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
            onImportComplete = { reloadData() },
            onDismiss = { showImportSheet = false }
        )
    }

    if (showExportDialog) {
        PetalPasswordExportDialog(
            activity = activity,
            onDismiss = { showExportDialog = false }
        )
    }

    if (showNeverSaveSheet) {
        NeverSaveSitesSheet(
            domains = neverSaveDomains,
            onRemove = { domain ->
                PetalCredentialVault.removeNeverSaveDomain(context, domain)
                reloadData()
                haptic.playClick(context)
            },
            onClearAll = {
                neverSaveDomains.forEach { PetalCredentialVault.removeNeverSaveDomain(context, it) }
                reloadData()
                haptic.playClick(context)
            },
            onDismiss = { showNeverSaveSheet = false }
        )
    }

    if (isAddingNew || showAddEditDialog != null) {
        val editing = showAddEditDialog
        PasswordEditSheet(
            initial = editing,
            onDismiss = {
                isAddingNew = false
                showAddEditDialog = null
            },
            onSave = { saved ->
                PetalCredentialVault.save(saved)
                reloadData()
                isAddingNew = false
                showAddEditDialog = null
                haptic.playClick(context)
                PetalToast.show(context, "Password saved")
            }
        )
    }

    if (selectedCredentialForDetails != null) {
        val cred = selectedCredentialForDetails!!
        PasswordDetailsSheet(
            credential = cred,
            onDismiss = { selectedCredentialForDetails = null },
            onEdit = {
                selectedCredentialForDetails = null
                showAddEditDialog = cred
            },
            onDelete = {
                PetalCredentialVault.delete(cred.id)
                reloadData()
                selectedCredentialForDetails = null
                haptic.playClick(context)
                PetalToast.show(context, "Password deleted")
            }
        )
    }

    val hasSubOverlay = selectedCredentialForDetails != null || isAddingNew || showAddEditDialog != null || showNeverSaveSheet || showImportSheet || showExportDialog || searchQuery.isNotEmpty()
    val handleBack: () -> Unit = {
        when {
            selectedCredentialForDetails != null -> selectedCredentialForDetails = null
            isAddingNew || showAddEditDialog != null -> {
                isAddingNew = false
                showAddEditDialog = null
            }
            showNeverSaveSheet -> showNeverSaveSheet = false
            showImportSheet -> showImportSheet = false
            showExportDialog -> showExportDialog = false
            searchQuery.isNotEmpty() -> searchQuery = ""
            else -> onNavigateBack()
        }
    }

    if (hasSubOverlay) {
        androidx.activity.compose.BackHandler(enabled = true) {
            handleBack()
        }
    }

    com.petal.browser.predictive.PetalPredictiveBackSurface(
        enabled = !hasSubOverlay,
        onBack = onNavigateBack
    ) {
        com.petal.browser.predictive.PetalScreenWrapper(
            backgroundSnapshot = backgroundSnapshot,
            modifier = modifier.fillMaxSize()
        ) {
            CompositionLocalProvider(LocalPetalGlassWallpaper provides wallpaper) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
            // Sibling wallpaper backdrop recorder as required by petal-liquid-glass skill
            M3ExpressiveVariableBackground(
                pageSeed = "petal_passwords_screen",
                modifier = Modifier
                    .matchParentSize()
            )

            Column(modifier = Modifier.fillMaxSize()) {
                ExpressiveHeader(
                    title = "Password Manager",
                    subtitle = "Brave-style encrypted vault & autofill",
                    onBack = { if (hasSubOverlay) handleBack() else onNavigateBack() }
                )

                val passwordsListState = rememberLazyListState()
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(
                        state = passwordsListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp)
                    ) {
                    // 1. Vault Security Hero Card
                    item {
                        PetalStatusHeroCard(
                            title = "Petal Secure Vault",
                            subtitle = "${credentials.size} saved logins • Hardware Keystore AES-256",
                            statusText = "Hardware Encrypted",
                            icon = Icons.Rounded.Security,
                            statusActive = true,
                            actionLabel = "System Autofill",
                            onActionClick = {
                                haptic.playClick(context)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    try {
                                        val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                                            data = android.net.Uri.parse("package:${context.packageName}")
                                        }
                                        activity.startActivity(intent)
                                    } catch (_: Exception) {
                                        try {
                                            activity.startActivity(Intent(Settings.ACTION_SETTINGS))
                                        } catch (_: Exception) {
                                            PetalToast.show(context, "Open Settings > System > Autofill")
                                        }
                                    }
                                }
                            }
                        )
                    }

                    // 1.5. PixelMusic Inspired Material 3 Expressive Segmented Tab Bar
                    item {
                        val mainTabs = listOf(
                            PetalButtonGroupItem(value = 0, label = "Logins (${credentials.size})"),
                            PetalButtonGroupItem(value = 1, label = "Never-Save (${neverSaveDomains.size})"),
                            PetalButtonGroupItem(value = 2, label = "Generator & Audit")
                        )
                        PetalChoiceButtonGroup(
                            options = mainTabs,
                            selected = selectedMainTab,
                            onSelect = { tab ->
                                haptic.playClick(context)
                                selectedMainTab = tab
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (selectedMainTab == 0) {
                        // 2. Search & Sort Bar
                        item {
                            val passSearchShape = RoundedCornerShape(50.dp)
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search logins, domains, notes...") },
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
                                shape = passSearchShape,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    unfocusedContainerColor = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .liquidGlassChrome(shape = passSearchShape, enabled = isGlass, sheenIntensity = 0.45f)
                            )
                        }

                        // 3. Vault Quick Actions & Settings
                        item {
                            PetalSettingsSection(
                                title = "Vault Management",
                                icon = Icons.Rounded.ManageAccounts
                            ) {
                                PetalGroup(rowCount = 4) { index, position ->
                                    when (index) {
                                        0 -> PetalGroupRow(
                                            icon = Icons.Rounded.AddCircle,
                                            title = "Add New Login",
                                            subtitle = "Save a website username and password",
                                            position = position,
                                            iconContainer = MaterialTheme.colorScheme.primaryContainer,
                                            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            onClick = {
                                                haptic.playClick(context)
                                                isAddingNew = true
                                            }
                                        )
                                        1 -> PetalGroupRow(
                                            icon = Icons.Rounded.Block,
                                            title = "Never-Saved Sites",
                                            subtitle = "${neverSaveDomains.size} websites ignored",
                                            position = position,
                                            iconContainer = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            onClick = {
                                                haptic.playClick(context)
                                                selectedMainTab = 1
                                            }
                                        )
                                        2 -> PetalGroupRow(
                                            icon = Icons.Rounded.FileDownload,
                                            title = "Import Passwords",
                                            subtitle = "Chrome, Brave, Firefox, Bitwarden, 1Password",
                                            position = position,
                                            iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                                            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            onClick = {
                                                haptic.playClick(context)
                                                showImportSheet = true
                                            }
                                        )
                                        3 -> PetalGroupRow(
                                            icon = Icons.Rounded.FileUpload,
                                            title = "Export Vault Backup",
                                            subtitle = "Password-encrypted (.petal) or CSV/JSON",
                                            position = position,
                                            iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                                            iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            onClick = {
                                                haptic.playClick(context)
                                                showExportDialog = true
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // 4. Grouped Passwords List (Brave Browser Hierarchy)
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PetalSectionLabel(
                                    text = if (searchQuery.isBlank()) "Saved Logins (${credentials.size})" else "Matches (${groupedLogins.values.flatten().size})"
                                )

                                val sortChipColors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onSurface,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                // Sort selector pill
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FilterChip(
                                        selected = selectedSortMode == 0,
                                        onClick = { selectedSortMode = 0 },
                                        label = { Text("Name", fontSize = 12.sp) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = sortChipColors,
                                        border = null
                                    )
                                    FilterChip(
                                        selected = selectedSortMode == 1,
                                        onClick = { selectedSortMode = 1 },
                                        label = { Text("Recent", fontSize = 12.sp) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = sortChipColors,
                                        border = null
                                    )
                                    FilterChip(
                                        selected = selectedSortMode == 2,
                                        onClick = { selectedSortMode = 2 },
                                        label = { Text("Favorites", fontSize = 12.sp) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = sortChipColors,
                                        border = null
                                    )
                                }
                            }
                        }

                        if (groupedLogins.isEmpty()) {
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
                                            shape = ExpressiveButtonShape,
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
                                                "Logins saved in Petal or imported from Chrome, Brave, Firefox, or Bitwarden will appear here."
                                            else "Try searching for a different domain, username, or note.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            // Display each primary domain group
                            groupedLogins.forEach { (primaryDomain, groupItems) ->
                                item(key = primaryDomain) {
                                    DomainCredentialGroup(
                                        primaryDomain = primaryDomain,
                                        credentials = groupItems,
                                        onSelect = { cred -> selectedCredentialForDetails = cred },
                                        onToggleFavorite = { cred ->
                                            PetalCredentialVault.toggleFavorite(cred.id)
                                            reloadData()
                                            haptic.playClick(context)
                                        }
                                    )
                                }
                            }
                        }
                    } else if (selectedMainTab == 1) {
                        // ── TAB 1: Never-Saved Sites ──
                        item {
                            PetalSettingsSection(
                                title = "Ignored Websites (${neverSaveDomains.size})",
                                icon = Icons.Rounded.Block
                            ) {
                                if (neverSaveDomains.isEmpty()) {
                                    PetalHeroCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(36.dp)
                                            )
                                            Text(
                                                "No Ignored Websites",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                "Websites where you choose 'Never for this site' when prompted will appear here.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else {
                                    PetalGroup(rowCount = neverSaveDomains.size) { index, position ->
                                        val domainItem = neverSaveDomains.toList()[index]
                                        PetalGroupRow(
                                            icon = Icons.Rounded.Public,
                                            title = domainItem,
                                            subtitle = "Auto-save disabled for this site",
                                            position = position,
                                            onClick = {
                                                PetalCredentialVault.removeNeverSaveDomain(context, domainItem)
                                                reloadData()
                                                haptic.playClick(context)
                                            },
                                            trailing = {
                                                IconButton(onClick = {
                                                    PetalCredentialVault.removeNeverSaveDomain(context, domainItem)
                                                    reloadData()
                                                    haptic.playClick(context)
                                                }) {
                                                    Icon(
                                                        Icons.Rounded.Delete,
                                                        contentDescription = "Remove",
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // ── TAB 2: Generator & Security ──
                        item {
                            PetalSettingsSection(
                                title = "Password Generator",
                                icon = Icons.Rounded.Key
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = generatedPassword,
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(onClick = {
                                                    haptic.playClick(context)
                                                    generatedPassword = PetalPasswordGenerator.generateByPreset(genPreset)
                                                }) {
                                                    Icon(Icons.Rounded.Refresh, contentDescription = "Regenerate")
                                                }
                                                IconButton(onClick = {
                                                    haptic.playClick(context)
                                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                    cm?.setPrimaryClip(ClipData.newPlainText("Password", generatedPassword))
                                                    PetalToast.show(context, "Copied to clipboard")
                                                }) {
                                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy")
                                                }
                                            }
                                        }

                                        val presetItems = listOf(
                                            PetalButtonGroupItem(value = PetalPasswordGenerator.Preset.BALANCED, label = "Balanced (16)"),
                                            PetalButtonGroupItem(value = PetalPasswordGenerator.Preset.HIGH_ENTROPY, label = "High Security (24)"),
                                            PetalButtonGroupItem(value = PetalPasswordGenerator.Preset.PIN, label = "PIN (6)")
                                        )
                                        PetalChoiceButtonGroup(
                                            options = presetItems,
                                            selected = genPreset,
                                            onSelect = { p ->
                                                haptic.playClick(context)
                                                genPreset = p
                                                generatedPassword = PetalPasswordGenerator.generateByPreset(p)
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                PetalExpressiveScrollBar(
                    listState = passwordsListState,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    dragLabelProvider = { index ->
                        credentials.getOrNull(index)?.domain?.let {
                            com.petal.browser.ui.components.extractFastScrollGlyph(it)
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

/**
 * DomainCredentialGroup
 * Displays a Brave-style grouped domain container. If multiple accounts exist for the domain,
 * shows them within the cohesive rounded group.
 */
@Composable
private fun DomainCredentialGroup(
    primaryDomain: String,
    credentials: List<PetalCredential>,
    onSelect: (PetalCredential) -> Unit,
    onToggleFavorite: (PetalCredential) -> Unit
) {
    PetalGroup(rowCount = credentials.size) { index, position ->
        val cred = credentials[index]
        PetalGroupListRow(
            position = position,
            onClick = { onSelect(cred) },
            leading = {
                val (badgeBg, badgeFg) = when (Math.abs(primaryDomain.hashCode()) % 4) {
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
                        text = primaryDomain.take(1).uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            content = {
                Text(
                    text = cred.domain,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = cred.username.ifBlank { "Password only" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onToggleFavorite(cred) }) {
                        Icon(
                            imageVector = if (cred.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = "Favorite",
                            tint = if (cred.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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

/**
 * PasswordDetailsSheet
 * Liquid Glass & Material 3 Expressive bottom sheet for credential inspection and actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordDetailsSheet(
    credential: PetalCredential,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val haptic = remember { PetalHapticEngine.getInstance(context) }
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    var isPasswordVisible by remember { mutableStateOf(false) }
    var breachStatus by remember { mutableStateOf<String?>(null) }
    var isCheckingBreach by remember { mutableStateOf(false) }

    PetalSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
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

            // Username card
            val fieldShape = RoundedCornerShape(16.dp)
            Surface(
                shape = fieldShape,
                color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isGlass) Modifier.liquidGlassChrome(fieldShape, enabled = true, sheenIntensity = 0.45f) else Modifier)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
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
                            haptic.playClick(context)
                            PetalToast.show(context, "Username copied")
                        }
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Password card with reveal and copy
            Surface(
                shape = fieldShape,
                color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isGlass) Modifier.liquidGlassChrome(fieldShape, enabled = true, sheenIntensity = 0.45f) else Modifier)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
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
                        IconButton(onClick = {
                            haptic.playClick(context)
                            isPasswordVisible = !isPasswordVisible
                        }) {
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
                                haptic.playClick(context)
                                PetalToast.show(context, "Password copied")
                            }
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Optional notes
            if (credential.notes.isNotBlank()) {
                Surface(
                    shape = fieldShape,
                    color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isGlass) Modifier.liquidGlassChrome(fieldShape, enabled = true, sheenIntensity = 0.45f) else Modifier)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Notes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(credential.notes, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Breach audit check button
            val checkBtnShape = RoundedCornerShape(50.dp)
            FilledTonalButton(
                onClick = {
                    haptic.playClick(context)
                    isCheckingBreach = true
                    breachStatus = null
                    PasswordBreachAuditManager.checkPassword(credential.password) { isPwned, count ->
                        isCheckingBreach = false
                        breachStatus = if (isPwned) {
                            "⚠️ Found in $count data breaches! We recommend changing this password."
                        } else {
                            "✅ Safe! Not found in known public breaches."
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .then(if (isGlass) Modifier.liquidGlassChrome(checkBtnShape, enabled = true, sheenIntensity = 0.4f) else Modifier),
                shape = checkBtnShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                if (isCheckingBreach) {
                    PetalContainedLoadingIndicator(size = 16.dp)
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
                    color = if (breachStatus!!.contains("Found")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .then(if (isGlass) Modifier.liquidGlassChrome(petalConnectedShape(0, 2), enabled = true, sheenIntensity = 0.4f) else Modifier),
                    shape = petalConnectedShape(0, 2)
                ) {
                    Text("Delete", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onEdit,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = petalConnectedShape(1, 2)
                ) {
                    Text("Edit", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * PasswordEditSheet
 * Liquid Glass & M3 Expressive editor sheet equipped with customizable password generator presets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordEditSheet(
    initial: PetalCredential?,
    onDismiss: () -> Unit,
    onSave: (PetalCredential) -> Unit
) {
    val context = LocalContext.current
    val haptic = remember { PetalHapticEngine.getInstance(context) }
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")

    var domain by remember { mutableStateOf(initial?.domain.orEmpty()) }
    var originUrl by remember { mutableStateOf(initial?.originUrl.orEmpty()) }
    var username by remember { mutableStateOf(initial?.username.orEmpty()) }
    var password by remember { mutableStateOf(initial?.password.orEmpty()) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }

    var selectedPreset by remember { mutableStateOf(PetalPasswordGenerator.Preset.BALANCED) }
    var customLength by remember { mutableFloatStateOf(16f) }
    var includeSymbols by remember { mutableStateOf(true) }
    var showGeneratorOptions by remember { mutableStateOf(false) }

    PetalSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (initial == null) "Add Password" else "Edit Password",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            val fieldShape = RoundedCornerShape(16.dp)
            val fieldColors = TextFieldDefaults.colors(
                focusedContainerColor = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            )

            TextField(
                value = domain,
                onValueChange = { domain = it },
                label = { Text("Website Domain (e.g. google.com)") },
                singleLine = true,
                shape = fieldShape,
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isGlass) Modifier.liquidGlassChrome(fieldShape, enabled = true, sheenIntensity = 0.45f) else Modifier)
            )

            TextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username or Email") },
                singleLine = true,
                shape = fieldShape,
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isGlass) Modifier.liquidGlassChrome(fieldShape, enabled = true, sheenIntensity = 0.45f) else Modifier)
            )

            PetalShapedPasswordInput(
                value = password,
                onValueChange = { password = it },
                hintText = "Password",
                containerColor = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isGlass) Modifier.liquidGlassChrome(RoundedCornerShape(20.dp), enabled = true, sheenIntensity = 0.45f) else Modifier)
            )

            // Password Generator Controls (Brave-Style Presets)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = {
                        haptic.playClick(context)
                        password = if (selectedPreset == PetalPasswordGenerator.Preset.CUSTOM) {
                            PetalPasswordGenerator.generate(
                                length = customLength.toInt(),
                                includeSymbols = includeSymbols
                            )
                        } else {
                            PetalPasswordGenerator.generateByPreset(selectedPreset)
                        }
                    },
                    shape = RoundedCornerShape(50.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Generate (${selectedPreset.displayName.split(" ")[0]})")
                }

                TextButton(onClick = { showGeneratorOptions = !showGeneratorOptions }) {
                    Text(if (showGeneratorOptions) "Hide Presets" else "Customize")
                }
            }

            AnimatedVisibility(visible = showGeneratorOptions) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainer)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Generator Preset", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PetalPasswordGenerator.Preset.values().forEach { preset ->
                            FilterChip(
                                selected = selectedPreset == preset,
                                onClick = {
                                    selectedPreset = preset
                                    haptic.playClick(context)
                                },
                                label = { Text(preset.name.lowercase().capitalize(), fontSize = 11.sp) },
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onSurface,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                border = null
                            )
                        }
                    }

                    if (selectedPreset == PetalPasswordGenerator.Preset.CUSTOM) {
                        Text("Length: ${customLength.toInt()} characters", style = MaterialTheme.typography.bodySmall)
                        PetalSlider(
                            value = customLength,
                            onValueChange = { customLength = it },
                            valueRange = 8f..32f,
                            steps = 23
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Include Special Symbols", style = MaterialTheme.typography.bodySmall)
                            IconSwitch(checked = includeSymbols, icon = Icons.Rounded.Check, onCheckedChange = { includeSymbols = it })
                        }
                    }
                }
            }

            TextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                singleLine = false,
                maxLines = 3,
                shape = fieldShape,
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isGlass) Modifier.liquidGlassChrome(fieldShape, enabled = true, sheenIntensity = 0.45f) else Modifier)
            )

            // Submit Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = petalConnectedShape(0, 2),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
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
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = petalConnectedShape(1, 2),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    enabled = domain.isNotBlank() && password.isNotBlank()
                ) {
                    Text("Save", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * NeverSaveSitesSheet
 * Brave-style ignored/never-save domains list sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NeverSaveSitesSheet(
    domains: Set<String>,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    PetalSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Never-Saved Sites (${domains.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (domains.isNotEmpty()) {
                    TextButton(onClick = onClearAll) {
                        Text("Clear All", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Text(
                text = "Petal will not ask to save or autofill credentials for these domains.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (domains.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No ignored sites.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                PetalGroup(rowCount = domains.size) { index, position ->
                    val domain = domains.toList()[index]
                    PetalGroupListRow(
                        position = position,
                        onClick = {},
                        leading = {
                            PetalGroupIconBadge(
                                icon = Icons.Rounded.Block,
                                container = MaterialTheme.colorScheme.surfaceContainerHighest,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                size = 36.dp,
                                iconSize = 18.dp
                            )
                        },
                        content = {
                            Text(
                                text = domain,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        trailing = {
                            IconButton(onClick = { onRemove(domain) }) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.error,
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
