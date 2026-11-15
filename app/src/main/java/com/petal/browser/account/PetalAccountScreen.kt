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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.preference.PreferenceManager
import com.petal.browser.account.mozilla.FxaUi
import com.petal.browser.account.mozilla.PetalFxa
import com.petal.browser.account.mozilla.PetalFxaSettings
import com.petal.browser.account.progress.PetalProgressStore
import com.petal.browser.account.progress.PetalXp
import com.petal.browser.compose.home.PetalShortcut
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
import mozilla.components.concept.sync.SyncEngine
import com.petal.browser.ui.miuix.wrappers.Icon

/**
 * The one account page: identity and personalisation, Mozilla sign-in and sync, security, and data.
 * Everything is built from the shared expressive containment components, so Material 3 Expressive
 * shapes apply always and Liquid Glass applies automatically whenever the user has it toggled on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalAccountScreen(
    backgroundSnapshot: androidx.compose.ui.graphics.ImageBitmap? = null,
    onBack: () -> Unit = {},
    onOpenOAuth: (PetalShortcut) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    fun say(msg: String) { scope.launch { snackbar.showSnackbar(msg) } }

    // ── identity: local profile + Mozilla state, resolved once ──
    val profile = PetalUserProfileManager.currentProfile
    val fxa by PetalFxa.state.collectAsStateWithLifecycle()
    val syncing by PetalFxa.syncing.collectAsStateWithLifecycle()
    val lastSync by PetalFxa.lastSync.collectAsStateWithLifecycle()
    val devices by PetalFxa.devices.collectAsStateWithLifecycle()
    val received by PetalFxa.receivedTabs.collectAsStateWithLifecycle()
    val remoteTabs by PetalFxa.remoteTabs.collectAsStateWithLifecycle()
    val error by PetalFxa.error.collectAsStateWithLifecycle()
    val account = remember(profile, fxa) { PetalAccountRepository.resolve(profile, fxa) }

    LaunchedEffect(Unit) {
        PetalUserProfileManager.init(context)
        PetalProgressStore.init(context)
    }
    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); PetalFxa.clearError() } }
    LaunchedEffect(fxa) {
        when (val s = fxa) {
            is FxaUi.SignedIn -> {
                PetalUserProfileManager.onFirefoxState(context, s.email, s.displayName, s.avatarUrl)
                PetalFxa.refreshDevices()
            }
            FxaUi.SignedOut -> PetalUserProfileManager.onFirefoxState(context, null, null, null)
            else -> {}
        }
    }

    // ── sync preferences ──
    var bookmarksOn by remember { mutableStateOf(PetalFxaSettings.bookmarks(context)) }
    var historyOn by remember { mutableStateOf(PetalFxaSettings.history(context)) }
    var tabsOn by remember { mutableStateOf(PetalFxaSettings.tabs(context)) }
    var passwordsOn by remember { mutableStateOf(PetalFxaSettings.passwords(context)) }
    var deviceName by remember { mutableStateOf(PetalFxaSettings.deviceName(context)) }
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
    var showSignOut by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var showClearData by remember { mutableStateOf(false) }
    var showXpBackup by remember { mutableStateOf(false) }
    var showRewards by remember { mutableStateOf(false) }
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

    fun startSignIn() {
        scope.launch {
            PetalFxa.beginSignIn()?.let { url ->
                onOpenOAuth(PetalShortcut("Mozilla sign in", url, "firefox-account", Color(0xFFFF9400)))
            }
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
                                    when (fxa) {
                                        is FxaUi.SignedIn -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = { PetalFxa.syncNow() }, enabled = !syncing, modifier = Modifier.weight(1f)) {
                                                Icon(Icons.Rounded.Sync, null, Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(if (syncing) "Syncing…" else "Sync now")
                                            }
                                            OutlinedButton(onClick = { showSignOut = true }, modifier = Modifier.weight(1f)) { Text("Sign out") }
                                        }
                                        FxaUi.Connecting -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp)
                                            Spacer(Modifier.width(12.dp))
                                            Text("Finish signing in on the page that opened.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                            TextButton(onClick = { PetalFxa.cancelSignIn() }) { Text("Cancel") }
                                        }
                                        else -> Button(onClick = { startSignIn() }, modifier = Modifier.fillMaxWidth()) {
                                            Icon(Icons.Rounded.AccountCircle, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(if (account.needsReauth) "Reconnect Mozilla account" else "Sign in with Mozilla")
                                        }
                                    }
                                    if (fxa is FxaUi.SignedIn) {
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            if (syncing) "Syncing…" else if (lastSync > 0) "Last synced ${DateUtils.getRelativeTimeSpanString(lastSync)}" else "Not synced yet",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // ── tabs sent to this device (only when there are any) ──
                        if (received.isNotEmpty()) {
                            item {
                                Column {
                                    PetalSectionLabel("Sent to this device")
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        received.forEachIndexed { i, t ->
                                            PetalGroupNavigationRow(
                                                title = t.title.ifBlank { t.url },
                                                subtitle = "From ${t.from}",
                                                position = petalGroupPositionFor(i, received.size),
                                                variant = PetalBadgeVariant.TERTIARY,
                                                leadingIcon = { Icon(Icons.Rounded.Devices, null) },
                                                onClick = {
                                                    PetalFxa.dismissReceived(t)
                                                    onOpenOAuth(PetalShortcut(t.title, t.url, "received-tab", Color(0xFFFF9400)))
                                                }
                                            )
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
                                        subtitle = when (profile.avatarType) {
                                            AvatarType.FIREFOX -> "Mozilla account photo"
                                            AvatarType.GALLERY_URI -> "Your photo"
                                            AvatarType.PRESET -> "Built-in avatar"
                                        },
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

                        // ── sync & devices (collapsed by default) ──
                        item {
                            Column {
                                PetalSectionLabel("Sync & devices")
                                if (fxa !is FxaUi.SignedIn) {
                                    PetalGroupNavigationRow(
                                        title = "Sync with your Mozilla account",
                                        subtitle = "Bookmarks, history, tabs and passwords, end-to-end encrypted",
                                        position = petalGroupPositionFor(0, 1),
                                        leadingIcon = { Icon(Icons.Rounded.Sync, null) },
                                        onClick = { startSignIn() }
                                    )
                                } else {
                                    val onCount = listOf(bookmarksOn, historyOn, tabsOn, passwordsOn).count { it }
                                    PetalGroupNavigationRow(
                                        title = "Sync settings",
                                        subtitle = "$onCount of 4 on · ${devices.size} other device${if (devices.size == 1) "" else "s"}",
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
                                                    title = "Bookmarks", subtitle = "Shared across your devices",
                                                    checked = bookmarksOn,
                                                    onCheckedChange = { bookmarksOn = it; PetalFxa.setEngine(SyncEngine.Bookmarks, it) },
                                                    position = petalGroupPositionFor(0, 4),
                                                    leadingIcon = { Icon(Icons.Rounded.Bookmark, null) }
                                                )
                                                PetalGroupControlRow(
                                                    title = "History", subtitle = "Pages you have visited",
                                                    checked = historyOn,
                                                    onCheckedChange = { historyOn = it; PetalFxa.setEngine(SyncEngine.History, it) },
                                                    position = petalGroupPositionFor(1, 4),
                                                    variant = PetalBadgeVariant.SECONDARY,
                                                    leadingIcon = { Icon(Icons.Rounded.History, null) }
                                                )
                                                PetalGroupControlRow(
                                                    title = "Open tabs", subtitle = "See tabs from your other devices",
                                                    checked = tabsOn,
                                                    onCheckedChange = { tabsOn = it; PetalFxa.setEngine(SyncEngine.Tabs, it) },
                                                    position = petalGroupPositionFor(2, 4),
                                                    variant = PetalBadgeVariant.TERTIARY,
                                                    leadingIcon = { Icon(Icons.Rounded.Devices, null) }
                                                )
                                                PetalGroupControlRow(
                                                    title = "Passwords", subtitle = "Off until you turn it on",
                                                    checked = passwordsOn,
                                                    onCheckedChange = { passwordsOn = it; PetalFxa.setEngine(SyncEngine.Passwords, it) },
                                                    position = petalGroupPositionFor(3, 4),
                                                    leadingIcon = { Icon(Icons.Rounded.Key, null) }
                                                )
                                            }
                                            OutlinedTextField(
                                                value = deviceName, onValueChange = { deviceName = it }, singleLine = true,
                                                label = { Text("This device's name") }, modifier = Modifier.fillMaxWidth(),
                                                trailingIcon = {
                                                    TextButton(onClick = { PetalFxa.renameDevice(deviceName); say("Device renamed") }) { Text("Save") }
                                                }
                                            )
                                            PetalHeroCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                                                Column(Modifier.padding(16.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text("Your other devices", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                        IconButton(onClick = { PetalFxa.refreshDevices() }) { Icon(Icons.Rounded.Refresh, "Refresh") }
                                                    }
                                                    if (devices.isEmpty()) {
                                                        Text("None yet. Sign in to Firefox on another device to see it here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                    devices.forEach { d ->
                                                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(Icons.Rounded.Devices, null, Modifier.size(20.dp))
                                                            Spacer(Modifier.width(10.dp))
                                                            Text(d.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                            if (d.canReceiveTabs) Text("Send tab", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                        }
                                                    }
                                                }
                                            }
                                            val shownTabs = remoteTabs.take(10)
                                            if (tabsOn && shownTabs.isNotEmpty()) {
                                                Column {
                                                    PetalSectionLabel("Tabs on your other devices")
                                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        shownTabs.forEachIndexed { i, t ->
                                                            PetalGroupNavigationRow(
                                                                title = t.title.ifBlank { t.url },
                                                                subtitle = t.device,
                                                                position = petalGroupPositionFor(i, shownTabs.size),
                                                                leadingIcon = { Icon(Icons.Rounded.Devices, null) },
                                                                onClick = { onOpenOAuth(PetalShortcut(t.title.ifBlank { t.url }, t.url, "synced-tab", Color(0xFFFF9400))) }
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { if (it.length <= PetalUserProfileManager.MAX_NAME) input = it },
                        singleLine = true,
                        label = { Text("Name (max ${PetalUserProfileManager.MAX_NAME})") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (profile.hasCustomName && profile.firefoxDisplayName != null) {
                        TextButton(onClick = { PetalUserProfileManager.useAccountName(context); showNameDialog = false }) {
                            Text("Use my Mozilla account name")
                        }
                    }
                }
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

    if (showSignOut) {
        PetalMaterialAlertDialog(
            onDismissRequest = { showSignOut = false },
            title = { Text("Sign out of Mozilla?", fontWeight = FontWeight.Bold) },
            text = { Text("Syncing stops on this device. Your local bookmarks and history stay here, and you can sign in again any time.") },
            confirmButton = {
                Button(onClick = { showSignOut = false; PetalFxa.signOut() }, shape = petalConnectedShape(1, 2)) { Text("Sign out") }
            },
            dismissButton = {
                FilledTonalButton(onClick = { showSignOut = false }, shape = petalConnectedShape(0, 2)) { Text("Cancel") }
            }
        )
    }

    if (showReset) {
        PetalMaterialAlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("Reset local profile?", fontWeight = FontWeight.Bold) },
            text = { Text("This removes your name, bio, accent colour and photo from this device. Your Mozilla sign-in isn't affected.") },
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
                val canUseFirefox = profile.avatarUrl != null
                val rows = if (canUseFirefox) 2 else 1
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PetalGroupNavigationRow(
                        title = "Choose a photo", subtitle = "Pick an image and crop it",
                        position = petalGroupPositionFor(0, rows),
                        leadingIcon = { Icon(Icons.Rounded.AddPhotoAlternate, null) },
                        onClick = { showAvatarSheet = false; galleryLauncher.launch("image/*") }
                    )
                    if (canUseFirefox) {
                        PetalGroupNavigationRow(
                            title = "Use Mozilla account photo", subtitle = "Matches your Firefox profile",
                            position = petalGroupPositionFor(1, rows),
                            variant = PetalBadgeVariant.TERTIARY,
                            leadingIcon = { Icon(Icons.Rounded.AccountCircle, null) },
                            onClick = { PetalUserProfileManager.updateAvatarFirefox(context); showAvatarSheet = false }
                        )
                    }
                }
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
