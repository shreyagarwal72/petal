package com.petal.browser.account

import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.preference.PreferenceManager
import com.petal.browser.account.progress.PetalProgressStore
import com.petal.browser.account.progress.PetalXp
import com.petal.browser.account.sync.PetalFolderSync
import com.petal.browser.predictive.PetalPredictiveBackSurface
import com.petal.browser.predictive.PetalScreenWrapper
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.bouncyClickable
import com.petal.browser.ui.containment.PetalBadgeVariant
import com.petal.browser.ui.containment.PetalGroupControlRow
import com.petal.browser.ui.containment.PetalGroupNavigationRow
import com.petal.browser.ui.containment.PetalHeroCard
import com.petal.browser.ui.containment.PetalMaterialAlertDialog
import com.petal.browser.ui.containment.PetalSectionLabel
import com.petal.browser.ui.containment.PetalSheet
import com.petal.browser.ui.containment.petalConnectedShape
import com.petal.browser.ui.containment.petalGroupPositionFor
import com.petal.browser.ui.theme.ExpressiveButtonShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The one account page: local profile and personalisation, sync through a folder the user owns,
 * security, and data. It is built from the shared expressive containment components, so Material 3
 * Expressive shapes always apply and Liquid Glass applies whenever the user has it toggled on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalAccountScreen(
    backgroundSnapshot: androidx.compose.ui.graphics.ImageBitmap? = null,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    fun say(msg: String) { scope.launch { snackbar.showSnackbar(msg) } }

    // ── identity and sync state ──
    val profile = PetalUserProfileManager.currentProfile
    val configured by PetalFolderSync.configured.collectAsStateWithLifecycle()
    val folderName by PetalFolderSync.folderName.collectAsStateWithLifecycle()
    val syncing by PetalFolderSync.syncing.collectAsStateWithLifecycle()
    val lastSync by PetalFolderSync.lastSync.collectAsStateWithLifecycle()
    val syncError by PetalFolderSync.error.collectAsStateWithLifecycle()
    val syncMessage by PetalFolderSync.message.collectAsStateWithLifecycle()
    val account = remember(profile, configured, folderName) {
        PetalAccountRepository.resolve(profile, configured, folderName)
    }

    LaunchedEffect(Unit) {
        PetalUserProfileManager.init(context)
        PetalProgressStore.init(context)
        PetalFolderSync.init(context)
    }
    LaunchedEffect(syncError) { syncError?.let { snackbar.showSnackbar(it); PetalFolderSync.clearError() } }

    // ── sync preferences ──
    var bookmarksOn by remember { mutableStateOf(PetalFolderSync.isOn(context, PetalFolderSync.KEY_BOOKMARKS)) }
    var historyOn by remember { mutableStateOf(PetalFolderSync.isOn(context, PetalFolderSync.KEY_HISTORY)) }
    var shortcutsOn by remember { mutableStateOf(PetalFolderSync.isOn(context, PetalFolderSync.KEY_SHORTCUTS)) }
    var rulesOn by remember { mutableStateOf(PetalFolderSync.isOn(context, PetalFolderSync.KEY_RULES)) }
    var autoOn by remember { mutableStateOf(PetalFolderSync.isOn(context, PetalFolderSync.KEY_AUTO)) }
    var syncExpanded by remember { mutableStateOf(false) }

    // ── security prefs (live) ──
    var appLockOn by remember { mutableStateOf(sp.getBoolean("sp_app_lock_enabled", false)) }
    var appLockType by remember { mutableStateOf(sp.getString("sp_app_lock_type", "FINGERPRINT") ?: "FINGERPRINT") }
    var clearOnExit by remember { mutableStateOf(sp.getBoolean("sp_clear_quit", false) || sp.getBoolean("sp_clear_on_exit", false)) }
    var httpsOnly by remember { mutableStateOf(sp.getBoolean("sp_https_only", false)) }
    DisposableEffect(sp) {
        val l = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "sp_app_lock_enabled" -> appLockOn = sp.getBoolean("sp_app_lock_enabled", false)
                "sp_app_lock_type" -> appLockType = sp.getString("sp_app_lock_type", "FINGERPRINT") ?: "FINGERPRINT"
            }
        }
        sp.registerOnSharedPreferenceChangeListener(l)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(l) }
    }

    // ── ui state ──
    var showNameDialog by remember { mutableStateOf(false) }
    var showBioDialog by remember { mutableStateOf(false) }
    var showAccentDialog by remember { mutableStateOf(false) }
    var showAvatarSheet by remember { mutableStateOf(false) }
    var showSetup by remember { mutableStateOf(false) }
    var showPassphrase by remember { mutableStateOf(false) }
    var showDisconnect by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var showClearData by remember { mutableStateOf(false) }
    var showXpBackup by remember { mutableStateOf(false) }
    var showRewards by remember { mutableStateOf(false) }
    var pickedFolder by remember { mutableStateOf<Uri?>(null) }
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    var cacheRefresh by remember { mutableIntStateOf(0) }
    var cacheSize by remember { mutableStateOf("…") }
    LaunchedEffect(cacheRefresh) {
        cacheSize = withContext(Dispatchers.IO) {
            try {
                val bytes = context.cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
                String.format("%.1f MB", bytes / (1024f * 1024f))
            } catch (_: Exception) { "0.0 MB" }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) cropUri = cacheForCrop(context, uri)
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            if (showSetup) pickedFolder = uri else PetalFolderSync.setFolder(context, uri)
        }
    }

    // Sub-overlays that own the back press; everything else is a dialog/sheet that handles its own.
    val hasSubOverlay = showRewards || showXpBackup
    if (hasSubOverlay) {
        BackHandler(enabled = true) {
            if (showRewards) showRewards = false else if (showXpBackup) showXpBackup = false
        }
    }
    if (showRewards) com.petal.browser.account.progress.PetalRewardsSheet(onDismiss = { showRewards = false })
    if (showXpBackup) com.petal.browser.account.progress.PetalXpBackupRestoreDialog(
        onDismiss = { showXpBackup = false },
        onMessage = { say(it) }
    )

    PetalPredictiveBackSurface(enabled = !hasSubOverlay, onBack = onBack) {
        PetalScreenWrapper(backgroundSnapshot = backgroundSnapshot) {
            Scaffold(
                modifier = modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    ExpressiveHeader(
                        title = "Account",
                        subtitle = account.subtitle,
                        onBack = onBack,
                        maxTitleLines = 1,
                        maxSubtitleLines = 1
                    )
                },
                snackbarHost = { com.petal.browser.ui.containment.PetalSnackbarHost(hostState = snackbar) }
            ) { pad ->
                Box(Modifier.fillMaxSize()) {
                    M3ExpressiveVariableBackground(pageSeed = "account_page")
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(pad),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // ── identity hero ──
                        item {
                            PetalHeroCard(Modifier.fillMaxWidth()) {
                                Column(
                                    Modifier.fillMaxWidth().padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        contentAlignment = Alignment.BottomEnd,
                                        modifier = Modifier.bouncyClickable { showAvatarSheet = true }
                                    ) {
                                        ProfileAvatarDisplay(profile = profile, sizeDp = 96)
                                        Surface(
                                            shape = CircleShape,
                                            color = account.accent ?: MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Rounded.CameraAlt, "Change profile picture", Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(14.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clip(CircleShape).clickable { showNameDialog = true }.padding(horizontal = 8.dp)
                                    ) {
                                        Text(
                                            account.title,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Icon(Icons.Rounded.Edit, "Edit name", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        account.subtitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (profile.bio.isNotBlank()) {
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            profile.bio,
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = TextAlign.Center,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    val level = PetalProgressStore.level
                                    Surface(
                                        shape = CircleShape,
                                        color = (account.accent ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.18f)
                                    ) {
                                        Text(
                                            "Lv $level · ${PetalXp.title(level)}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                        )
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    if (configured) {
                                        Button(
                                            onClick = { PetalFolderSync.syncNow(context) },
                                            enabled = !syncing,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            if (syncing) {
                                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                            } else {
                                                Icon(Icons.Rounded.Sync, null, Modifier.size(18.dp))
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Text(if (syncing) "Syncing…" else "Sync now")
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            if (lastSync > 0) "Last synced ${DateUtils.getRelativeTimeSpanString(lastSync)}" else "Not synced yet",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Button(onClick = { pickedFolder = null; showSetup = true }, modifier = Modifier.fillMaxWidth()) {
                                            Icon(Icons.Rounded.Sync, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Set up sync")
                                        }
                                    }
                                }
                            }
                        }

                        // ── personalise ──
                        item {
                            Column {
                                PetalSectionLabel("Personalise")
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    PetalGroupNavigationRow(
                                        title = "Display name",
                                        subtitle = profile.displayName,
                                        position = petalGroupPositionFor(0, 4),
                                        leadingIcon = { Icon(Icons.Rounded.Person, null) },
                                        onClick = { showNameDialog = true }
                                    )
                                    PetalGroupNavigationRow(
                                        title = "Bio",
                                        subtitle = profile.bio.ifBlank { "Add a short line about you" },
                                        position = petalGroupPositionFor(1, 4),
                                        variant = PetalBadgeVariant.SECONDARY,
                                        leadingIcon = { Icon(Icons.Rounded.Description, null) },
                                        onClick = { showBioDialog = true }
                                    )
                                    PetalGroupNavigationRow(
                                        title = "Profile picture",
                                        subtitle = if (profile.avatarType == AvatarType.GALLERY_URI) "Your photo" else "Built-in avatar",
                                        position = petalGroupPositionFor(2, 4),
                                        variant = PetalBadgeVariant.TERTIARY,
                                        leadingIcon = { Icon(Icons.Rounded.AddPhotoAlternate, null) },
                                        onClick = { showAvatarSheet = true }
                                    )
                                    PetalGroupNavigationRow(
                                        title = "Accent colour",
                                        subtitle = if (account.accent != null) "Custom" else "Follows app theme",
                                        position = petalGroupPositionFor(3, 4),
                                        leadingIcon = { Icon(Icons.Rounded.Palette, null) },
                                        onClick = { showAccentDialog = true }
                                    )
                                }
                            }
                        }

                        // ── sync through your own storage (collapsed by default) ──
                        item {
                            Column {
                                PetalSectionLabel("Sync")
                                if (!configured) {
                                    PetalGroupNavigationRow(
                                        title = "Sync through your own storage",
                                        subtitle = "Pick a folder you own. Petal keeps one encrypted file in it.",
                                        position = petalGroupPositionFor(0, 1),
                                        leadingIcon = { Icon(Icons.Rounded.Sync, null) },
                                        onClick = { pickedFolder = null; showSetup = true }
                                    )
                                } else {
                                    val onCount = listOf(bookmarksOn, historyOn, shortcutsOn, rulesOn).count { it }
                                    PetalGroupNavigationRow(
                                        title = "Sync settings",
                                        subtitle = "${folderName ?: "Folder selected"} · $onCount of 4 on",
                                        position = petalGroupPositionFor(0, 1),
                                        leadingIcon = { Icon(Icons.Rounded.Sync, null) },
                                        trailingIcon = { Icon(if (syncExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null) },
                                        onClick = { syncExpanded = !syncExpanded }
                                    )
                                    AnimatedVisibility(
                                        visible = syncExpanded,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                PetalGroupControlRow(
                                                    title = "Bookmarks", subtitle = "Including your reading list",
                                                    checked = bookmarksOn,
                                                    onCheckedChange = { bookmarksOn = it; PetalFolderSync.setOn(context, PetalFolderSync.KEY_BOOKMARKS, it) },
                                                    position = petalGroupPositionFor(0, 5),
                                                    leadingIcon = { Icon(Icons.Rounded.Bookmark, null) }
                                                )
                                                PetalGroupControlRow(
                                                    title = "History", subtitle = "Pages you have visited",
                                                    checked = historyOn,
                                                    onCheckedChange = { historyOn = it; PetalFolderSync.setOn(context, PetalFolderSync.KEY_HISTORY, it) },
                                                    position = petalGroupPositionFor(1, 5),
                                                    variant = PetalBadgeVariant.SECONDARY,
                                                    leadingIcon = { Icon(Icons.Rounded.History, null) }
                                                )
                                                PetalGroupControlRow(
                                                    title = "Home shortcuts", subtitle = "Your start page sites",
                                                    checked = shortcutsOn,
                                                    onCheckedChange = { shortcutsOn = it; PetalFolderSync.setOn(context, PetalFolderSync.KEY_SHORTCUTS, it) },
                                                    position = petalGroupPositionFor(2, 5),
                                                    variant = PetalBadgeVariant.TERTIARY,
                                                    leadingIcon = { Icon(Icons.Rounded.Description, null) }
                                                )
                                                PetalGroupControlRow(
                                                    title = "Site rules", subtitle = "Saved, trusted and protected sites",
                                                    checked = rulesOn,
                                                    onCheckedChange = { rulesOn = it; PetalFolderSync.setOn(context, PetalFolderSync.KEY_RULES, it) },
                                                    position = petalGroupPositionFor(3, 5),
                                                    leadingIcon = { Icon(Icons.Rounded.Https, null) }
                                                )
                                                PetalGroupControlRow(
                                                    title = "Sync automatically", subtitle = "When Petal opens, at most every few hours",
                                                    checked = autoOn,
                                                    onCheckedChange = { autoOn = it; PetalFolderSync.setOn(context, PetalFolderSync.KEY_AUTO, it) },
                                                    position = petalGroupPositionFor(4, 5),
                                                    variant = PetalBadgeVariant.SECONDARY,
                                                    leadingIcon = { Icon(Icons.Rounded.Refresh, null) }
                                                )
                                            }
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                PetalGroupNavigationRow(
                                                    title = "Change folder", subtitle = folderName ?: "Pick a different folder",
                                                    position = petalGroupPositionFor(0, 3),
                                                    leadingIcon = { Icon(Icons.Rounded.Storage, null) },
                                                    onClick = { folderLauncher.launch(null) }
                                                )
                                                PetalGroupNavigationRow(
                                                    title = "Change passphrase", subtitle = "Every device must use the same one",
                                                    position = petalGroupPositionFor(1, 3),
                                                    variant = PetalBadgeVariant.SECONDARY,
                                                    leadingIcon = { Icon(Icons.Rounded.Key, null) },
                                                    onClick = { showPassphrase = true }
                                                )
                                                PetalGroupNavigationRow(
                                                    title = "Turn off sync", subtitle = "Forget the folder and passphrase on this device",
                                                    position = petalGroupPositionFor(2, 3),
                                                    variant = PetalBadgeVariant.ERROR,
                                                    leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                                    onClick = { showDisconnect = true }
                                                )
                                            }
                                            PetalHeroCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    syncMessage?.let {
                                                        Text(it, style = MaterialTheme.typography.bodySmall)
                                                    }
                                                    Text(
                                                        "Data is encrypted on this device before it reaches your folder, so only your passphrase can read it. " +
                                                            "Items are merged, so deleting something here won't delete it on your other devices. " +
                                                            "Passwords aren't synced: use Encrypted backup for those.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ── security & privacy ──
                        item {
                            Column {
                                PetalSectionLabel("Security & privacy")
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    PetalGroupNavigationRow(
                                        title = "App lock",
                                        subtitle = if (appLockOn) "On · ${if (appLockType == "PASSWORD") "Passcode" else "Fingerprint"}" else "Off · protect Petal when it opens",
                                        position = petalGroupPositionFor(0, 3),
                                        leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                                        onClick = { if (!PetalAccountRepository.openAppLock(context)) say("Couldn't open App Lock") }
                                    )
                                    PetalGroupControlRow(
                                        title = "Clear data on exit", subtitle = "Purge cache and history when Petal closes",
                                        checked = clearOnExit,
                                        onCheckedChange = {
                                            clearOnExit = it
                                            sp.edit().putBoolean("sp_clear_quit", it).putBoolean("sp_clear_on_exit", it).apply()
                                        },
                                        position = petalGroupPositionFor(1, 3),
                                        variant = PetalBadgeVariant.SECONDARY,
                                        leadingIcon = { Icon(Icons.Rounded.CleaningServices, null) }
                                    )
                                    PetalGroupControlRow(
                                        title = "HTTPS-only mode", subtitle = "Upgrade insecure connections",
                                        checked = httpsOnly,
                                        onCheckedChange = { httpsOnly = it; sp.edit().putBoolean("sp_https_only", it).apply() },
                                        position = petalGroupPositionFor(2, 3),
                                        variant = PetalBadgeVariant.TERTIARY,
                                        leadingIcon = { Icon(Icons.Rounded.Https, null) }
                                    )
                                }
                            }
                        }

                        // ── data & backup ──
                        item {
                            Column {
                                PetalSectionLabel("Data & backup")
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    PetalGroupNavigationRow(
                                        title = "Clear browsing data", subtitle = "Web cache · $cacheSize",
                                        position = petalGroupPositionFor(0, 3),
                                        variant = PetalBadgeVariant.ERROR,
                                        leadingIcon = { Icon(Icons.Rounded.DeleteSweep, null) },
                                        onClick = { showClearData = true }
                                    )
                                    PetalGroupNavigationRow(
                                        title = "Encrypted backup & recovery", subtitle = "Export and restore XP, levels and stats",
                                        position = petalGroupPositionFor(1, 3),
                                        variant = PetalBadgeVariant.SECONDARY,
                                        leadingIcon = { Icon(Icons.Rounded.EnhancedEncryption, null) },
                                        onClick = { showXpBackup = true }
                                    )
                                    PetalGroupNavigationRow(
                                        title = "Reset local profile", subtitle = "Remove your name, bio, accent and photo from this device",
                                        position = petalGroupPositionFor(2, 3),
                                        variant = PetalBadgeVariant.ERROR,
                                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                        onClick = { showReset = true }
                                    )
                                }
                            }
                        }

                        // ── progress & support ──
                        item { com.petal.browser.account.progress.PetalRewardsCatalogCard(onOpen = { showRewards = true }) }
                        item { com.petal.browser.account.progress.PetalSupportCard(onMessage = { say(it) }) }
                    }
                }
            }
        }
    }

    // ═══ dialogs & sheets ═══

    if (showNameDialog) {
        var input by remember { mutableStateOf(profile.displayName) }
        PetalMaterialAlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Display name", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { if (it.length <= PetalUserProfileManager.MAX_NAME) input = it },
                    singleLine = true,
                    label = { Text("Name (max ${PetalUserProfileManager.MAX_NAME})") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = { PetalUserProfileManager.updateDisplayName(context, input); showNameDialog = false }, shape = petalConnectedShape(1, 2)) { Text("Save") }
            },
            dismissButton = {
                FilledTonalButton(onClick = { showNameDialog = false }, shape = petalConnectedShape(0, 2)) { Text("Cancel") }
            }
        )
    }

    if (showBioDialog) {
        var input by remember { mutableStateOf(profile.bio) }
        PetalMaterialAlertDialog(
            onDismissRequest = { showBioDialog = false },
            title = { Text("Bio", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { if (it.length <= PetalUserProfileManager.MAX_BIO) input = it },
                    maxLines = 3,
                    label = { Text("About you (max ${PetalUserProfileManager.MAX_BIO})") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = { PetalUserProfileManager.updateBio(context, input); showBioDialog = false }, shape = petalConnectedShape(1, 2)) { Text("Save") }
            },
            dismissButton = {
                FilledTonalButton(onClick = { showBioDialog = false }, shape = petalConnectedShape(0, 2)) { Text("Cancel") }
            }
        )
    }

    if (showAccentDialog) {
        PetalMaterialAlertDialog(
            onDismissRequest = { showAccentDialog = false },
            title = { Text("Accent colour", fontWeight = FontWeight.Bold) },
            text = {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PetalUserProfileManager.accentChoices.forEach { hex ->
                        val c = PetalAccountRepository.parseAccent(hex) ?: Color.Gray
                        val selected = profile.accentColorHex.equals(hex, ignoreCase = true)
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(c)
                                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { PetalUserProfileManager.updateAccent(context, hex); showAccentDialog = false },
                            contentAlignment = Alignment.Center
                        ) { if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White) }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { PetalUserProfileManager.updateAccent(context, null); showAccentDialog = false }, shape = petalConnectedShape(1, 2)) { Text("Use theme") }
            },
            dismissButton = {
                FilledTonalButton(onClick = { showAccentDialog = false }, shape = petalConnectedShape(0, 2)) { Text("Close") }
            }
        )
    }

    // First-time sync setup: choose a folder, then a passphrase.
    if (showSetup) {
        var pass by remember { mutableStateOf("") }
        var confirm by remember { mutableStateOf("") }
        val valid = pickedFolder != null && pass.length >= 8 && pass == confirm
        PetalMaterialAlertDialog(
            onDismissRequest = { showSetup = false },
            title = { Text("Set up sync", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Pick a folder you own, such as one on Google Drive, Nextcloud, Syncthing or your SD card. " +
                            "Use the same folder and passphrase on each device.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(onClick = { folderLauncher.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Storage, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            pickedFolder?.let { Uri.decode(it.lastPathSegment ?: "Folder selected") } ?: "Choose folder",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedTextField(
                        value = pass, onValueChange = { pass = it }, singleLine = true,
                        label = { Text("Passphrase (8+ characters)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirm, onValueChange = { confirm = it }, singleLine = true,
                        label = { Text("Repeat passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = confirm.isNotEmpty() && confirm != pass,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "If you forget the passphrase, the synced data can't be recovered.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = valid,
                    onClick = {
                        pickedFolder?.let { PetalFolderSync.setFolder(context, it) }
                        PetalFolderSync.setPassphrase(context, pass)
                        showSetup = false
                        PetalFolderSync.syncNow(context)
                    },
                    shape = petalConnectedShape(1, 2)
                ) { Text("Start syncing") }
            },
            dismissButton = {
                FilledTonalButton(onClick = { showSetup = false }, shape = petalConnectedShape(0, 2)) { Text("Cancel") }
            }
        )
    }

    if (showPassphrase) {
        var pass by remember { mutableStateOf("") }
        var confirm by remember { mutableStateOf("") }
        val valid = pass.length >= 8 && pass == confirm
        PetalMaterialAlertDialog(
            onDismissRequest = { showPassphrase = false },
            title = { Text("Change passphrase", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enter the passphrase your other devices use. If you're choosing a new one, the existing synced file " +
                            "can't be read with it, so sync will stop with an error until it matches.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = pass, onValueChange = { pass = it }, singleLine = true,
                        label = { Text("Passphrase (8+ characters)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirm, onValueChange = { confirm = it }, singleLine = true,
                        label = { Text("Repeat passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = confirm.isNotEmpty() && confirm != pass,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = valid,
                    onClick = {
                        PetalFolderSync.setPassphrase(context, pass)
                        showPassphrase = false
                        PetalFolderSync.syncNow(context)
                    },
                    shape = petalConnectedShape(1, 2)
                ) { Text("Save") }
            },
            dismissButton = {
                FilledTonalButton(onClick = { showPassphrase = false }, shape = petalConnectedShape(0, 2)) { Text("Cancel") }
            }
        )
    }

    if (showDisconnect) {
        PetalMaterialAlertDialog(
            onDismissRequest = { showDisconnect = false },
            title = { Text("Turn off sync?", fontWeight = FontWeight.Bold) },
            text = { Text("Petal forgets the folder and passphrase on this device. Your bookmarks and history here stay, and the file in your folder is left as it is.") },
            confirmButton = {
                Button(onClick = { showDisconnect = false; PetalFolderSync.disconnect(context); syncExpanded = false }, shape = petalConnectedShape(1, 2)) { Text("Turn off") }
            },
            dismissButton = {
                FilledTonalButton(onClick = { showDisconnect = false }, shape = petalConnectedShape(0, 2)) { Text("Cancel") }
            }
        )
    }

    if (showReset) {
        PetalMaterialAlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("Reset local profile?", fontWeight = FontWeight.Bold) },
            text = { Text("This removes your name, bio, accent colour and photo from this device. Sync settings aren't affected.") },
            confirmButton = {
                Button(onClick = { PetalUserProfileManager.resetLocalProfile(context); showReset = false; say("Local profile reset") }, shape = petalConnectedShape(1, 2)) { Text("Reset") }
            },
            dismissButton = {
                FilledTonalButton(onClick = { showReset = false }, shape = petalConnectedShape(0, 2)) { Text("Cancel") }
            }
        )
    }

    if (showClearData) {
        com.petal.browser.ui.components.PetalClearBrowsingDataDialog(
            onDismiss = { showClearData = false },
            onPerformClear = { cache, cookies, storage, autofill, permissions ->
                showClearData = false
                com.petal.browser.unit.BrowsingDataManager.clearBrowsingDataAsync(
                    context, cache, cookies, storage, autofill, permissions
                ) {
                    cacheRefresh++
                    say("Selected browsing data cleared")
                }
            }
        )
    }

    if (showAvatarSheet) {
        PetalSheet(
            onDismissRequest = { showAvatarSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = null
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Profile picture", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PetalUserProfileManager.builtinAvatarPresets.forEach { (id, _) ->
                        val selected = profile.avatarType == AvatarType.PRESET && profile.avatarPresetId == id
                        Surface(
                            shape = ExpressiveButtonShape,
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(56.dp).bouncyClickable {
                                PetalUserProfileManager.updateAvatarPreset(context, id)
                                showAvatarSheet = false
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(getPresetMaterialIcon(id) ?: Icons.Rounded.Person, null, Modifier.size(26.dp))
                            }
                        }
                    }
                }
                PetalGroupNavigationRow(
                    title = "Choose a photo", subtitle = "Pick an image and crop it",
                    position = petalGroupPositionFor(0, 1),
                    leadingIcon = { Icon(Icons.Rounded.AddPhotoAlternate, null) },
                    onClick = { showAvatarSheet = false; galleryLauncher.launch("image/*") }
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    cropUri?.let { uri ->
        PetalAvatarCropSheet(
            imageUri = uri,
            onDismiss = { cropUri = null },
            onAvatarCropped = { cropUri = null; say("Profile picture updated") }
        )
    }
}

/** Copies a picked image into cache so the cropper can always read it, falling back to the original Uri. */
private fun cacheForCrop(context: android.content.Context, uri: Uri): Uri {
    return try {
        val temp = java.io.File(context.cacheDir, "temp_avatar_crop.png")
        context.contentResolver.openInputStream(uri)?.use { input ->
            java.io.FileOutputStream(temp).use { out -> input.copyTo(out) }
        }
        if (temp.exists() && temp.length() > 0) Uri.fromFile(temp) else uri
    } catch (_: Exception) {
        uri
    }
}
