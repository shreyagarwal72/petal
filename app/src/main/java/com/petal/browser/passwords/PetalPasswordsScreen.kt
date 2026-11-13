package com.petal.browser.passwords

import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import com.petal.browser.ui.kit.Button
import com.petal.browser.ui.kit.FilledTonalButton
import com.petal.browser.ui.kit.FilterChip
import com.petal.browser.ui.kit.IconButton
import com.petal.browser.ui.kit.TextButton
import com.petal.browser.ui.kit.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.petal.browser.compose.composable.PetalContainedLoadingIndicator
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.predictive.PetalPredictiveBackSurface
import com.petal.browser.predictive.PetalScreenWrapper
import com.petal.browser.ui.components.ExpressiveButtonGroup
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.ExpressiveSegmentItem
import com.petal.browser.ui.components.IconSwitch
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.PetalButtonGroupItem
import com.petal.browser.ui.components.PetalChoiceButtonGroup
import com.petal.browser.ui.components.PetalExpressiveScrollBar
import com.petal.browser.ui.components.PetalShapedPasswordInput
import com.petal.browser.ui.components.PetalSlider
import com.petal.browser.ui.components.extractFastScrollGlyph
import com.petal.browser.ui.containment.*
import com.petal.browser.ui.glass.*
import com.petal.browser.ui.theme.ExpressiveButtonShape
import com.petal.browser.ui.theme.PetalMaterialShapes
import com.petal.browser.unit.PasswordBreachAuditManager
import com.petal.browser.view.PetalToast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * PetalPasswordsScreen
 *
 * Modern Material 3 Expressive Password Manager & Autofill Control Center:
 * 1. Material 3 Expressive Theme & Liquid Glass integration.
 * 2. Accurate eTLD+1 domain grouping with app-friendly display names.
 * 3. Sorting, instant search, and real-time fast scroll with accurate glyph indexing.
 * 4. Alphabetically sorted Never-Save list with swipe/button removal and Undo action.
 * 5. Full vault-wide security audit (reused, weak, old, breached passwords).
 * 6. High-entropy configurable generator (Balanced, High Security, Numeric PIN, Custom).
 * 7. Biometric re-authentication required to reveal or copy credentials.
 * 8. Sensitive clipboard copying with auto-clear timeout.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var credentials by remember { mutableStateOf(emptyList<PetalCredential>()) }
    var neverSaveDomains by remember { mutableStateOf(emptySet<String>()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedSortMode by remember { mutableIntStateOf(0) } // 0: Domain, 1: Recently Updated, 2: Favorites
    var selectedMainTab by remember { mutableIntStateOf(0) } // 0: Logins, 1: Never-Saved, 2: Generator & Audit
    var genPreset by remember { mutableStateOf(PetalPasswordGenerator.Preset.BALANCED) }
    var genLength by remember { mutableFloatStateOf(16f) }
    var generatedPassword by remember { mutableStateOf(PetalPasswordGenerator.generateByPreset(PetalPasswordGenerator.Preset.BALANCED)) }

    // Live state tracking
    var isAutofillEnabled by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val afm = context.getSystemService(AutofillManager::class.java)
                afm?.hasEnabledAutofillServices() == true
            } else false
        )
    }
    var isWriteBlocked by remember { mutableStateOf(PetalCredentialVault.isWriteBlocked()) }

    var showTermsDialog by remember { mutableStateOf(false) }
    var showImportSheet by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showAddEditDialog by remember { mutableStateOf<PetalCredential?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }
    var selectedCredentialForDetails by remember { mutableStateOf<PetalCredential?>(null) }

    fun reloadData() {
        credentials = PetalCredentialVault.getAll()
        neverSaveDomains = PetalCredentialVault.getNeverSaveDomains(context)
        isWriteBlocked = PetalCredentialVault.isWriteBlocked()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val afm = context.getSystemService(AutofillManager::class.java)
            isAutofillEnabled = afm?.hasEnabledAutofillServices() == true
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                reloadData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        PetalCredentialVault.init(context)
        if (PetalPasswordTermsManager.shouldShow(context)) {
            showTermsDialog = true
        } else {
            reloadData()
        }
    }

    // Grouping by eTLD+1 using centralized matcher
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

        sorted.groupBy { cred ->
            if (cred.domain.startsWith("app://")) {
                cred.domain
            } else {
                PetalCredentialVault.getEffectiveTldPlusOne(cred.domain)
            }
        }
    }

    val displayKeys = remember(groupedLogins) { groupedLogins.keys.toList() }

    if (showTermsDialog) {
        PetalPasswordTermsDialog(
            onAccept = {
                showTermsDialog = false
                PetalPasswordTermsManager.markAccepted(context)
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
            activity = activity,
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

    val hasSubOverlay = selectedCredentialForDetails != null || isAddingNew || showAddEditDialog != null || showImportSheet || showExportDialog || searchQuery.isNotEmpty()
    val handleBack: () -> Unit = {
        when {
            selectedCredentialForDetails != null -> selectedCredentialForDetails = null
            isAddingNew || showAddEditDialog != null -> {
                isAddingNew = false
                showAddEditDialog = null
            }
            showImportSheet -> showImportSheet = false
            showExportDialog -> showExportDialog = false
            searchQuery.isNotEmpty() -> searchQuery = ""
            else -> onNavigateBack()
        }
    }

    if (hasSubOverlay) {
        BackHandler(enabled = true) {
            handleBack()
        }
    }

    PetalPredictiveBackSurface(
        enabled = !hasSubOverlay,
        onBack = onNavigateBack
    ) {
        PetalScreenWrapper(
            backgroundSnapshot = backgroundSnapshot,
            modifier = modifier.fillMaxSize()
        ) {
            CompositionLocalProvider(LocalPetalGlassWallpaper provides wallpaper) {
                androidx.compose.material3.MaterialExpressiveTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        M3ExpressiveVariableBackground(
                            pageSeed = "petal_passwords_screen",
                            modifier = Modifier.matchParentSize()
                        )

                        Scaffold(
                            containerColor = Color.Transparent,
                            snackbarHost = { SnackbarHost(snackbarHostState) }
                        ) { innerPadding ->
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                ExpressiveHeader(
                                    title = "Password Manager",
                                    subtitle = "Encrypted vault & autofill",
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
                                        contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
                                    ) {
                                        // 1. Vault Security Hero Card
                                        item {
                                            PetalStatusHeroCard(
                                                title = "Petal Secure Vault",
                                                subtitle = "${credentials.size} saved logins • Hardware Keystore AES-256",
                                                statusText = if (isWriteBlocked) "Vault Read-Only" else "Hardware Encrypted",
                                                icon = Icons.Rounded.Security,
                                                statusActive = !isWriteBlocked,
                                                actionLabel = if (isAutofillEnabled) "Autofill Enabled" else "Set up Autofill",
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

                                        // 2. Compact 3-way Segmented Tab Bar (never overflows at 360dp)
                                        item {
                                            val mainTabs = listOf(
                                                PetalButtonGroupItem(value = 0, label = "Logins (${credentials.size})"),
                                                PetalButtonGroupItem(value = 1, label = "Ignored (${neverSaveDomains.size})"),
                                                PetalButtonGroupItem(value = 2, label = "Audit & Gen")
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
                                            // --- TAB 0: LOGINS ---
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
                                                        .liquidGlassChrome(shape = passSearchShape, enabled = isGlass, sheenIntensity = 0.45f), glass = false
                                                )
                                            }

                                            // Quick Management Actions
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
                                                                subtitle = "Save a website or app login",
                                                                position = position,
                                                                iconContainer = MaterialTheme.colorScheme.primaryContainer,
                                                                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                                onClick = {
                                                                    haptic.playClick(context)
                                                                    isAddingNew = true
                                                                }
                                                            )
                                                            1 -> PetalGroupRow(
                                                                icon = Icons.Rounded.FileDownload,
                                                                title = "Import Passwords",
                                                                subtitle = "Chrome, Firefox, Bitwarden, 1Password, Dashlane",
                                                                position = position,
                                                                iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                                                                iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                                onClick = {
                                                                    haptic.playClick(context)
                                                                    showImportSheet = true
                                                                }
                                                            )
                                                            2 -> PetalGroupRow(
                                                                icon = Icons.Rounded.FileUpload,
                                                                title = "Export Vault Backup",
                                                                subtitle = "Password-encrypted (.petal) or standard JSON",
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

                                            // Sort Selector
                                            item {
                                                Column(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    PetalSectionLabel(
                                                        text = if (searchQuery.isBlank()) "Saved Logins (${credentials.size})" else "Matches (${groupedLogins.values.flatten().size})"
                                                    )

                                                    ExpressiveButtonGroup(
                                                        items = listOf(
                                                            ExpressiveSegmentItem("0", "Name"),
                                                            ExpressiveSegmentItem("1", "Recent"),
                                                            ExpressiveSegmentItem("2", "Favorites"),
                                                        ),
                                                        selectedId = selectedSortMode.toString(),
                                                        onItemSelected = { selectedSortMode = it.toInt() },
                                                        modifier = Modifier.fillMaxWidth(),
                                                    )
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
                                                                    "Logins saved in Petal or imported from Chrome, Firefox, or Bitwarden will appear here."
                                                                else "Try searching for a different domain, username, or note.",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                            } else {
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
                                            // --- TAB 1: NEVER-SAVED / IGNORED SITES ---
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
                                                        val sortedNeverSave = remember(neverSaveDomains) { neverSaveDomains.sorted() }
                                                        PetalGroup(rowCount = sortedNeverSave.size) { index, position ->
                                                            val domainItem = sortedNeverSave[index]
                                                            PetalGroupRow(
                                                                icon = Icons.Rounded.Public,
                                                                title = domainItem,
                                                                subtitle = "Auto-save disabled for this site",
                                                                position = position,
                                                                onClick = {},
                                                                trailing = {
                                                                    IconButton(onClick = {
                                                                        PetalCredentialVault.removeNeverSaveDomain(context, domainItem)
                                                                        reloadData()
                                                                        haptic.playClick(context)
                                                                        coroutineScope.launch {
                                                                            val res = snackbarHostState.showSnackbar(
                                                                                message = "Removed $domainItem from ignored list",
                                                                                actionLabel = "Undo",
                                                                                duration = SnackbarDuration.Short
                                                                            )
                                                                            if (res == SnackbarResult.ActionPerformed) {
                                                                                PetalCredentialVault.addNeverSaveDomain(context, domainItem)
                                                                                reloadData()
                                                                            }
                                                                        }
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
                                            // --- TAB 2: AUDIT & GENERATOR ---
                                            item {
                                                VaultSecurityAuditSection(
                                                    credentials = credentials,
                                                    onAuditComplete = { reloadData() }
                                                )
                                            }

                                            item {
                                                PasswordGeneratorSection(
                                                    genPreset = genPreset,
                                                    genLength = genLength,
                                                    generatedPassword = generatedPassword,
                                                    onPresetChange = { p ->
                                                        genPreset = p
                                                        generatedPassword = PetalPasswordGenerator.generateByPreset(p, genLength.toInt())
                                                    },
                                                    onLengthChange = { len ->
                                                        genLength = len
                                                        generatedPassword = PetalPasswordGenerator.generateByPreset(genPreset, len.toInt())
                                                    },
                                                    onRegenerate = {
                                                        generatedPassword = PetalPasswordGenerator.generateByPreset(genPreset, genLength.toInt())
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Accurate Fast-scroll indexing
                                    if (selectedMainTab == 0 && displayKeys.isNotEmpty()) {
                                        PetalExpressiveScrollBar(
                                            listState = passwordsListState,
                                            modifier = Modifier.align(Alignment.CenterEnd),
                                            dragLabelProvider = { index ->
                                                displayKeys.getOrNull(index)?.let {
                                                    extractFastScrollGlyph(it)
                                                }
                                            }
                                        )
                                    }

                                    if (selectedMainTab == 0) {
                                        ExtendedFloatingActionButton(
                                            onClick = {
                                                haptic.playClick(context)
                                                isAddingNew = true
                                            },
                                            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                                            text = { Text("Add Login") },
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(end = 20.dp, bottom = 20.dp)
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

/**
 * Material 3 Expressive Vault-Wide Security Audit Section
 */
@Composable
private fun VaultSecurityAuditSection(
    credentials: List<PetalCredential>,
    onAuditComplete: () -> Unit
) {
    val context = LocalContext.current
    val haptic = remember { PetalHapticEngine.getInstance(context) }
    var isAuditing by remember { mutableStateOf(false) }

    val reusedCount = remember(credentials) {
        val groups = credentials.groupBy { it.password }.filter { it.key.isNotBlank() && it.value.size > 1 }
        groups.values.sumOf { it.size }
    }

    val weakCount = remember(credentials) {
        credentials.count {
            it.password.isNotBlank() && (it.password.length < 10 || it.password.all { ch -> ch.isLetterOrDigit() && !ch.isUpperCase() })
        }
    }

    val oldCutoff = remember { System.currentTimeMillis() - (180L * 24 * 60 * 60 * 1000) }
    val oldCount = remember(credentials) {
        credentials.count { it.updatedAt > 0 && it.updatedAt < oldCutoff }
    }

    val compromisedCount = remember(credentials) {
        credentials.count { it.isCompromised }
    }

    PetalSettingsSection(
        title = "Vault Security Audit",
        icon = Icons.Rounded.Shield
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Vault Health Score",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val health = if (credentials.isEmpty()) 100 else {
                            val issues = (compromisedCount * 3) + (reusedCount * 2) + weakCount
                            (100 - (issues * 10)).coerceIn(0, 100)
                        }
                        Text(
                            text = "$health% Protected",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (health > 80) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            haptic.playClick(context)
                            isAuditing = true
                            var checked = 0
                            val total = credentials.size
                            if (total == 0) {
                                isAuditing = false
                                return@FilledTonalButton
                            }
                            credentials.forEach { cred ->
                                PasswordBreachAuditManager.checkPassword(cred.password) { isPwned, _ ->
                                    if (isPwned) {
                                        PetalCredentialVault.setCompromised(cred.id, true)
                                    }
                                    checked++
                                    if (checked >= total) {
                                        isAuditing = false
                                        onAuditComplete()
                                        PetalToast.show(context, "Audit complete")
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(50.dp),
                        enabled = !isAuditing && credentials.isNotEmpty()
                    ) {
                        if (isAuditing) {
                            PetalContainedLoadingIndicator(size = 16.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Auditing...")
                        } else {
                            Icon(Icons.Rounded.SecurityUpdateGood, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Audit Vault")
                        }
                    }
                }

                // 4-Pillar Metric Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AuditMetricCard(
                        modifier = Modifier.weight(1f),
                        count = compromisedCount,
                        label = "Compromised",
                        isAlert = compromisedCount > 0
                    )
                    AuditMetricCard(
                        modifier = Modifier.weight(1f),
                        count = reusedCount,
                        label = "Reused",
                        isAlert = reusedCount > 0
                    )
                    AuditMetricCard(
                        modifier = Modifier.weight(1f),
                        count = weakCount,
                        label = "Weak",
                        isAlert = weakCount > 0
                    )
                    AuditMetricCard(
                        modifier = Modifier.weight(1f),
                        count = oldCount,
                        label = "Old (>180d)",
                        isAlert = false
                    )
                }
            }
        }
    }
}

@Composable
private fun AuditMetricCard(
    modifier: Modifier = Modifier,
    count: Int,
    label: String,
    isAlert: Boolean
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isAlert) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isAlert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Generator Section with Preset.CUSTOM & genLength fully wired
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PasswordGeneratorSection(
    genPreset: PetalPasswordGenerator.Preset,
    genLength: Float,
    generatedPassword: String,
    onPresetChange: (PetalPasswordGenerator.Preset) -> Unit,
    onLengthChange: (Float) -> Unit,
    onRegenerate: () -> Unit
) {
    val context = LocalContext.current
    val haptic = remember { PetalHapticEngine.getInstance(context) }

    PetalSettingsSection(
        title = "Password Generator",
        icon = Icons.Rounded.Key
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
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
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            haptic.playClick(context)
                            onRegenerate()
                        }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Regenerate")
                        }
                        IconButton(onClick = {
                            haptic.playClick(context)
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = ClipData.newPlainText("Password", generatedPassword).apply {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    description.extras = PersistableBundle().apply {
                                        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                                    }
                                }
                            }
                            cm?.setPrimaryClip(clip)
                            PetalToast.show(context, "Copied to clipboard")
                        }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy")
                        }
                    }
                }

                // Preset selector chips using FlowRow
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PetalPasswordGenerator.Preset.values().forEach { preset ->
                        FilterChip(
                            selected = genPreset == preset,
                            onClick = {
                                haptic.playClick(context)
                                onPresetChange(preset)
                            },
                            label = { Text(preset.displayName.split(" ")[0]) },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSurface,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                if (genPreset == PetalPasswordGenerator.Preset.CUSTOM || genPreset == PetalPasswordGenerator.Preset.PIN) {
                    val range = if (genPreset == PetalPasswordGenerator.Preset.PIN) 4f..12f else 8f..32f
                    val steps = if (genPreset == PetalPasswordGenerator.Preset.PIN) 7 else 23
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Length: ${genLength.toInt()} characters",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        PetalSlider(
                            value = genLength.coerceIn(range.start, range.endInclusive),
                            onValueChange = onLengthChange,
                            valueRange = range,
                            steps = steps
                        )
                    }
                }
            }
        }
    }
}

/**
 * DomainCredentialGroup
 * Displays grouped credentials with MaterialShapes domain avatars and 3dp gap styling.
 */
@Composable
private fun DomainCredentialGroup(
    primaryDomain: String,
    credentials: List<PetalCredential>,
    onSelect: (PetalCredential) -> Unit,
    onToggleFavorite: (PetalCredential) -> Unit
) {
    val context = LocalContext.current
    val isApp = primaryDomain.startsWith("app://")
    val displayLabel = if (isApp) PetalAutofillService.cleanDisplayLabel(context, primaryDomain) else primaryDomain

    // Material 3 Expressive Shape selection
    val shapeIndex = Math.abs(primaryDomain.hashCode()) % PetalMaterialShapes.allShapes.size
    val shapeHolder = PetalMaterialShapes.allShapes[shapeIndex]
    val avatarShape = shapeHolder.toShape()

    val (badgeBg, badgeFg) = when (Math.abs(primaryDomain.hashCode()) % 4) {
        0 -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        1 -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        2 -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
    }

    PetalGroup(rowCount = credentials.size) { index, position ->
        val cred = credentials[index]
        PetalGroupListRow(
            position = position,
            onClick = { onSelect(cred) },
            leading = {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(avatarShape)
                        .background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    if (isApp) {
                        Icon(
                            imageVector = Icons.Rounded.Android,
                            contentDescription = null,
                            tint = badgeFg,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Text(
                            text = displayLabel.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = badgeFg
                        )
                    }
                }
            },
            content = {
                Text(
                    text = displayLabel,
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

sealed class BreachState {
    object Idle : BreachState()
    object Checking : BreachState()
    data class Breached(val count: Int) : BreachState()
    object Safe : BreachState()
    data class Error(val message: String) : BreachState()
}

/**
 * PasswordDetailsSheet
 * Details sheet with biometric re-authentication for revealing and copying passwords.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordDetailsSheet(
    activity: ComponentActivity,
    credential: PetalCredential,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val haptic = remember { PetalHapticEngine.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")

    var isPasswordVisible by remember { mutableStateOf(false) }
    var breachState by remember { mutableStateOf<BreachState>(BreachState.Idle) }

    fun requestAuth(actionName: String, onAuthenticated: () -> Unit) {
        val km = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isDeviceSecure = km?.isDeviceSecure == true
        if (!isDeviceSecure) {
            onAuthenticated()
            return
        }

        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authentication Required")
            .setSubtitle("Confirm your identity to $actionName")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            promptInfoBuilder.setAllowedAuthenticators(authenticators)
        } else {
            @Suppress("DEPRECATION")
            promptInfoBuilder.setDeviceCredentialAllowed(true)
        }

        val fragmentActivity = activity as? androidx.fragment.app.FragmentActivity
        if (fragmentActivity == null) {
            onAuthenticated()
            return
        }

        val prompt = BiometricPrompt(
            fragmentActivity,
            ContextCompat.getMainExecutor(fragmentActivity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onAuthenticated()
                }
            }
        )
        try {
            prompt.authenticate(promptInfoBuilder.build())
        } catch (_: Exception) {
            onAuthenticated()
        }
    }

    PetalSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val shapeIndex = Math.abs(credential.domain.hashCode()) % PetalMaterialShapes.allShapes.size
                val shape = PetalMaterialShapes.allShapes[shapeIndex].toShape()
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(shape)
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
                        text = PetalAutofillService.cleanDisplayLabel(context, credential.domain),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                        Text(
                            credential.username.ifBlank { "(empty)" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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

            // Password card with biometric re-auth for reveal and copy
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
                            if (!isPasswordVisible) {
                                requestAuth("reveal password") {
                                    isPasswordVisible = true
                                }
                            } else {
                                isPasswordVisible = false
                            }
                        }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "Toggle visibility",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                requestAuth("copy password") {
                                    haptic.playClick(context)
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Password", credential.password).apply {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            description.extras = PersistableBundle().apply {
                                                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                                            }
                                        }
                                    }
                                    cm.setPrimaryClip(clip)
                                    PetalToast.show(context, "Password copied (clears in 30s)")

                                    // Sensitive auto-clear after 30 seconds
                                    coroutineScope.launch {
                                        delay(30_000L)
                                        val currentClip = cm.primaryClip
                                        if (currentClip != null && currentClip.itemCount > 0 && currentClip.getItemAt(0)?.text == credential.password) {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                                cm.clearPrimaryClip()
                                            } else {
                                                cm.setPrimaryClip(ClipData.newPlainText("", ""))
                                            }
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

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

            // Breach audit check
            val checkBtnShape = RoundedCornerShape(50.dp)
            FilledTonalButton(
                onClick = {
                    haptic.playClick(context)
                    breachState = BreachState.Checking
                    PasswordBreachAuditManager.checkPassword(credential.password) { isPwned, count ->
                        Handler(Looper.getMainLooper()).post {
                            breachState = if (isPwned) BreachState.Breached(count) else BreachState.Safe
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
                ), glass = false
            ) {
                if (breachState is BreachState.Checking) {
                    PetalContainedLoadingIndicator(size = 16.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Checking HIBP...")
                } else {
                    Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Check for Breaches")
                }
            }

            when (val state = breachState) {
                is BreachState.Breached -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Found in ${state.count} public data breaches! Recommended to update.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                is BreachState.Safe -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Safe! Not found in known public breaches.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                else -> {}
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
                    shape = petalConnectedShape(0, 2), glass = false
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
 * Editor sheet with flow preset chips and updated originUrl binding.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
                .navigationBarsPadding()
                .imePadding()
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
                onValueChange = {
                    domain = it
                    // Automatically sync originUrl with edited domain if default
                    val clean = PetalCredentialVault.cleanDomain(it)
                    if (originUrl.isBlank() || originUrl == "https://${initial?.domain}") {
                        originUrl = if (clean.startsWith("app://")) clean else "https://$clean"
                    }
                },
                label = { Text("Website Domain (e.g. google.com or app://pkg)") },
                singleLine = true,
                shape = fieldShape,
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isGlass) Modifier.liquidGlassChrome(fieldShape, enabled = true, sheenIntensity = 0.45f) else Modifier), glass = false
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
                    .then(if (isGlass) Modifier.liquidGlassChrome(fieldShape, enabled = true, sheenIntensity = 0.45f) else Modifier), glass = false
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
                            PetalPasswordGenerator.generateByPreset(selectedPreset, customLength.toInt())
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
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PetalPasswordGenerator.Preset.values().forEach { preset ->
                            FilterChip(
                                selected = selectedPreset == preset,
                                onClick = {
                                    selectedPreset = preset
                                    haptic.playClick(context)
                                },
                                label = { Text(preset.name.lowercase().replaceFirstChar { it.uppercaseChar() }, fontSize = 11.sp) },
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

                    if (selectedPreset == PetalPasswordGenerator.Preset.CUSTOM || selectedPreset == PetalPasswordGenerator.Preset.PIN) {
                        val range = if (selectedPreset == PetalPasswordGenerator.Preset.PIN) 4f..12f else 8f..32f
                        Text("Length: ${customLength.toInt()} characters", style = MaterialTheme.typography.bodySmall)
                        PetalSlider(
                            value = customLength.coerceIn(range.start, range.endInclusive),
                            onValueChange = { customLength = it },
                            valueRange = range,
                            steps = if (selectedPreset == PetalPasswordGenerator.Preset.PIN) 7 else 23
                        )
                        if (selectedPreset == PetalPasswordGenerator.Preset.CUSTOM) {
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
                    .then(if (isGlass) Modifier.liquidGlassChrome(fieldShape, enabled = true, sheenIntensity = 0.45f) else Modifier), glass = false
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
                        val clean = PetalCredentialVault.cleanDomain(domain)
                        if (clean.isNotBlank() && password.isNotBlank()) {
                            val cred = initial?.copy(
                                domain = clean,
                                originUrl = originUrl.ifBlank { if (clean.startsWith("app://")) clean else "https://$clean" },
                                username = username.trim(),
                                password = password,
                                notes = notes,
                                updatedAt = System.currentTimeMillis()
                            ) ?: PetalCredential(
                                id = UUID.randomUUID().toString(),
                                domain = clean,
                                originUrl = originUrl.ifBlank { if (clean.startsWith("app://")) clean else "https://$clean" },
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
