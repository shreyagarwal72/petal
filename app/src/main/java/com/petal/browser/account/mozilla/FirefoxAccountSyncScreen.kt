package com.petal.browser.account.mozilla

import android.content.Context
import android.net.Uri
import android.preference.PreferenceManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.compose.AsyncImage
import com.petal.browser.compose.home.PetalShortcut
import com.petal.browser.account.AvatarType
import com.petal.browser.account.GoogleAccountManager
import com.petal.browser.account.GoogleUserProfile
import com.petal.browser.account.ProfileAvatarDisplay
import com.petal.browser.account.getPresetMaterialIcon
import com.petal.browser.account.PetalAvatarCropSheet
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.containment.petalGroupPositionFor
import com.petal.browser.ui.containment.petalGroupShape
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.PetalThemedSnackbarHost
import com.petal.browser.ui.components.bouncyClickable
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirefoxAccountSyncScreen(
    backgroundSnapshot: androidx.compose.ui.graphics.ImageBitmap? = null,
    onBack: () -> Unit = {},
    onOpenOAuth: (PetalShortcut) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val fxaManager = remember { FxAccountManager.getInstance() }
    val syncManager = remember { PetalMozillaSyncManager.getInstance() }

    val accountState by fxaManager.accountState.collectAsState()
    val syncState by syncManager.syncState.collectAsState()

    val profile = GoogleAccountManager.currentProfile
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    var showAppLockConfigPage by remember { mutableStateOf(false) }
    var showDesktopPairDialog by remember { mutableStateOf(false) }
    var desktopPairUrlInput by remember { mutableStateOf("") }
    var showCropDialog by remember { mutableStateOf(false) }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    var showMediaPickerSheet by remember { mutableStateOf(false) }

    var showClearDataDialog by remember { mutableStateOf(false) }
    var showDirectEmailDialog by remember { mutableStateOf(false) }
    var directEmailInput by remember { mutableStateOf("") }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var nameInput by remember(profile.displayName) { mutableStateOf(profile.displayName ?: "") }

    var isClearOnExit by remember {
        mutableStateOf(sp.getBoolean("sp_clear_quit", false) || sp.getBoolean("sp_clear_on_exit", false))
    }
    var isHttpsOnly by remember {
        mutableStateOf(sp.getBoolean("sp_https_only", false))
    }

    // Engine states
    var syncBookmarks by remember { mutableStateOf(fxaManager.isEngineEnabled(SyncEngine.BOOKMARKS)) }
    var syncHistory by remember { mutableStateOf(fxaManager.isEngineEnabled(SyncEngine.HISTORY)) }
    var syncTabs by remember { mutableStateOf(fxaManager.isEngineEnabled(SyncEngine.TABS)) }

    var cacheSizeMb by remember {
        mutableStateOf(
            try {
                val cacheDir = context.cacheDir
                val bytes = cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
                String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
            } catch (_: Exception) {
                "0.0 MB"
            }
        )
    }

    var showAvatarPetalPicker by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val tempFile = java.io.File(context.cacheDir, "temp_avatar_crop.png")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                pendingCropUri = if (tempFile.exists() && tempFile.length() > 0) Uri.fromFile(tempFile) else uri
                showCropDialog = true
            } catch (e: Exception) {
                pendingCropUri = uri
                showCropDialog = true
            }
        }
    }

    val requestMediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) {
            showMediaPickerSheet = true
        } else {
            showAvatarPetalPicker = true
        }
    }

    fun openAvatarMediaPicker() {
        if (com.petal.browser.media.PetalMediaPickerManager.hasMediaPermissions(context)) {
            showMediaPickerSheet = true
        } else {
            requestMediaPermissionLauncher.launch(com.petal.browser.media.PetalMediaPickerManager.getRequiredMediaPermissions())
        }
    }


    com.petal.browser.predictive.PetalPredictiveBackSurface(
        enabled = true,
        onBack = {
            if (showAppLockConfigPage) {
                showAppLockConfigPage = false
            } else {
                onBack()
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            com.petal.browser.predictive.PetalScreenWrapper(
                isBehind = showAppLockConfigPage,
                backgroundSnapshot = if (!showAppLockConfigPage) backgroundSnapshot else null
            ) {
                Scaffold(
                    topBar = {
                        ExpressiveHeader(
                            title = "Account & Mozilla Sync",
                            subtitle = "Firefox Accounts & Profile",
                            onBack = onBack,
                            maxTitleLines = 1,
                            maxSubtitleLines = 1
                        )
                    },
                    snackbarHost = { com.petal.browser.ui.containment.PetalSnackbarHost(hostState = snackbarHostState) },
                    containerColor = MaterialTheme.colorScheme.background,
                    modifier = modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        M3ExpressiveVariableBackground(pageSeed = "fxa_account_page")

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // ── Section 1: User Profile Hero Card ─────────────────────
                            com.petal.browser.ui.containment.PetalHeroCard(
                                shape = com.petal.browser.ui.containment.PetalContainmentShapes.Hero,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        contentAlignment = Alignment.BottomEnd,
                                        modifier = Modifier.bouncyClickable {
                                            val permanentFile = java.io.File(context.filesDir, "petal_user_avatar.png")
                                            if (profile.avatarType == AvatarType.GALLERY_URI && permanentFile.exists() && permanentFile.length() > 0) {
                                                pendingCropUri = Uri.fromFile(permanentFile)
                                                showCropDialog = true
                                            } else {
                                                openAvatarMediaPicker()
                                            }
                                        }
                                    ) {
                                        ProfileAvatarDisplay(profile = profile, sizeDp = 88)
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.surfaceContainerLow),
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = if (profile.avatarType == AvatarType.GALLERY_URI && !profile.customAvatarUri.isNullOrEmpty()) Icons.Rounded.Crop else Icons.Rounded.AddPhotoAlternate,
                                                    contentDescription = stringResource(R.string.ui_change_profile_picture),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(14.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = profile.displayName,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .bouncyClickable {
                                                    nameInput = profile.displayName
                                                    showEditNameDialog = true
                                                }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Rounded.Edit,
                                                    contentDescription = stringResource(R.string.ui_edit_user_name),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(2.dp))

                                    Text(
                                        text = stringResource(R.string.ui_petal_browser_profile),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(Modifier.height(18.dp))

                                    // Avatar Preset Row
                                    Text(
                                        text = stringResource(R.string.ui_choose_profile_picture),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.2.sp
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.align(Alignment.Start)
                                    )

                                    Spacer(Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (profile.avatarType == AvatarType.GALLERY_URI) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = if (profile.avatarType == AvatarType.GALLERY_URI) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                            border = if (profile.avatarType == AvatarType.GALLERY_URI) BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                            modifier = Modifier
                                                .size(52.dp)
                                                .bouncyClickable { openAvatarMediaPicker() }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Rounded.AddPhotoAlternate,
                                                    contentDescription = stringResource(R.string.ui_select_photo_crop),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }

                                        GoogleAccountManager.builtinAvatarPresets.forEach { (presetId, label) ->
                                            val isSelected = profile.avatarType == AvatarType.PRESET && profile.avatarPresetId == presetId
                                            Surface(
                                                shape = CircleShape,
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                                border = if (isSelected) BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary) else null,
                                                modifier = Modifier
                                                    .size(52.dp)
                                                    .bouncyClickable { GoogleAccountManager.updateAvatarPreset(context, presetId) }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    val iconVec = getPresetMaterialIcon(presetId)
                                                    Icon(
                                                        imageVector = iconVec ?: Icons.Rounded.Person,
                                                        contentDescription = label,
                                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // ── Section 2: FIREFOX ACCOUNT & SYNC HERO CARD ────────────
                            Text(
                                text = stringResource(R.string.ui_firefox_account_sync),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp)
                            )

                            com.petal.browser.ui.containment.PetalHeroCard(
                                shape = com.petal.browser.ui.containment.PetalContainmentShapes.Hero,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    if (accountState is FxaState.SignedIn) {
                                        val signedIn = accountState as FxaState.SignedIn

                                        // Auto-refresh profile if name or avatar is missing or on screen entry
                                        LaunchedEffect(signedIn.email) {
                                            fxaManager.refreshProfile()
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(52.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (!signedIn.avatarUrl.isNullOrBlank()) {
                                                    AsyncImage(
                                                        model = signedIn.avatarUrl,
                                                        contentDescription = stringResource(R.string.ui_firefox_avatar),
                                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Icon(
                                                        Icons.Rounded.AccountCircle,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.size(36.dp)
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.width(16.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = signedIn.displayName ?: signedIn.email.substringBefore("@"),
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = signedIn.email,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(Modifier.height(4.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color(0xFF4CAF50).copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.ui_firefox_account_connected),
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                        color = Color(0xFF2E7D32),
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(16.dp))

                                        // Last Sync Status
                                        val lastSync = fxaManager.getLastSyncTime()
                                        val lastSyncStr = if (lastSync > 0) {
                                            SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(lastSync))
                                        } else {
                                            "Never synced"
                                        }

                                        val isSyncing = syncState is MozSyncState.Syncing
                                        val infiniteTransition = rememberInfiniteTransition()
                                        val rotation by infiniteTransition.animateFloat(
                                            initialValue = 0f,
                                            targetValue = 360f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(1000, easing = LinearEasing),
                                                repeatMode = RepeatMode.Restart
                                            )
                                        )

                                        Column(
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.ui_last_synced),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = when (syncState) {
                                                        is MozSyncState.Syncing -> (syncState as MozSyncState.Syncing).message
                                                        is MozSyncState.Error -> "Error: ${(syncState as MozSyncState.Error).message}"
                                                        else -> lastSyncStr
                                                    },
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                    color = if (syncState is MozSyncState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                                )
                                            }

                                            Spacer(Modifier.height(14.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = {
                                                        syncManager.syncNow(context, forceRestore = true) { success ->
                                                            coroutineScope.launch {
                                                                snackbarHostState.showSnackbar(
                                                                    if (success) "Restored history & data from Firefox!" else "Restore encountered an issue."
                                                                )
                                                            }
                                                        }
                                                    },
                                                    enabled = !isSyncing,
                                                    shape = RoundedCornerShape(20.dp),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(44.dp)
                                                        .bouncyClickable()
                                                ) {
                                                    Icon(
                                                        Icons.Rounded.Download,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(stringResource(R.string.ui_restore), fontWeight = FontWeight.SemiBold, maxLines = 1)
                                                }

                                                Button(
                                                    onClick = {
                                                        syncManager.syncNow(context) { success ->
                                                            coroutineScope.launch {
                                                                snackbarHostState.showSnackbar(
                                                                    if (success) "Firefox Sync complete!" else "Firefox Sync encountered an issue."
                                                                )
                                                            }
                                                        }
                                                    },
                                                    enabled = !isSyncing,
                                                    shape = RoundedCornerShape(20.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.primary,
                                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                                    ),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(44.dp)
                                                        .bouncyClickable()
                                                ) {
                                                    Icon(
                                                        Icons.Rounded.Sync,
                                                        contentDescription = null,
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .then(if (isSyncing) Modifier.rotate(rotation) else Modifier)
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        if (isSyncing) "Syncing..." else "Sync Now",
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(16.dp))

                                        OutlinedButton(
                                            onClick = {
                                                fxaManager.logout()
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Signed out of Firefox Account")
                                                }
                                            },
                                            shape = RoundedCornerShape(20.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            ),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                            modifier = Modifier.fillMaxWidth().bouncyClickable()
                                        ) {
                                            Icon(Icons.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.ui_sign_out_of_firefox), fontWeight = FontWeight.SemiBold)
                                        }

                                    } else {
                                        // Signed Out State
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Sync,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                            }

                                            Spacer(Modifier.height(12.dp))

                                            Text(
                                                text = stringResource(R.string.ui_sync_with_firefox),
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )

                                            Spacer(Modifier.height(4.dp))

                                            Text(
                                                text = stringResource(R.string.ui_take_your_bookmarks_history_and),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )

                                            Spacer(Modifier.height(18.dp))

                                            // Sign In Button
                                            Button(
                                                onClick = {
                                                    val loginUrl = fxaManager.beginLogin()
                                                    onOpenOAuth(
                                                        PetalShortcut(
                                                            label = "Firefox Sign In",
                                                            url = loginUrl,
                                                            siteId = "firefox_sync",
                                                            containerColor = Color(0xFFFF9400)
                                                        )
                                                    )
                                                },
                                                shape = RoundedCornerShape(20.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(48.dp)
                                                    .bouncyClickable()
                                            ) {
                                                Icon(Icons.Rounded.AccountCircle, contentDescription = null)
                                                Spacer(Modifier.width(8.dp))
                                                Text(stringResource(R.string.ui_sign_in_with_firefox), fontWeight = FontWeight.Bold)
                                            }

                                            Spacer(Modifier.height(10.dp))

                                            // Pair Desktop QR Button
                                            OutlinedButton(
                                                onClick = { showDesktopPairDialog = true },
                                                shape = RoundedCornerShape(20.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(48.dp)
                                                    .bouncyClickable()
                                            ) {
                                                Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                                                Spacer(Modifier.width(8.dp))
                                                Text(stringResource(R.string.ui_pair_with_firefox_desktop), fontWeight = FontWeight.SemiBold)
                                            }

                                            Spacer(Modifier.height(10.dp))


                                            // Direct Connect with Email Button
                                            TextButton(
                                                onClick = { showDirectEmailDialog = true },
                                                shape = RoundedCornerShape(20.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(44.dp)
                                                    .bouncyClickable()
                                            ) {
                                                Icon(Icons.Rounded.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text(stringResource(R.string.ui_sign_in_with_email_directly), fontWeight = FontWeight.Medium)
                                            }
                                        }
                                    }
                                }
                            }

                            // ── Section 3: SYNC ENGINES ────────────────────────────────
                            Text(
                                text = stringResource(R.string.ui_what_to_sync),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp)
                            )

                            run {
                                val syncItemsCount = 3
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    com.petal.browser.ui.containment.PetalGroupControlRow(
                                        title = stringResource(R.string.ui_bookmarks),
                                        subtitle = stringResource(R.string.ui_sync_local_bookmarks_with_firefox),
                                        checked = syncBookmarks,
                                        onCheckedChange = { checked ->
                                            syncBookmarks = checked
                                            fxaManager.setEngineEnabled(SyncEngine.BOOKMARKS, checked)
                                        },
                                        position = petalGroupPositionFor(0, syncItemsCount),
                                        variant = com.petal.browser.ui.containment.PetalBadgeVariant.PRIMARY,
                                        leadingIcon = {
                                            Icon(
                                                Icons.Rounded.Bookmark,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    )

                                    com.petal.browser.ui.containment.PetalGroupControlRow(
                                        title = stringResource(R.string.ui_browsing_history),
                                        subtitle = stringResource(R.string.ui_seamlessly_sync_history_across_your),
                                        checked = syncHistory,
                                        onCheckedChange = { checked ->
                                            syncHistory = checked
                                            fxaManager.setEngineEnabled(SyncEngine.HISTORY, checked)
                                        },
                                        position = petalGroupPositionFor(1, syncItemsCount),
                                        variant = com.petal.browser.ui.containment.PetalBadgeVariant.SECONDARY,
                                        leadingIcon = {
                                            Icon(
                                                Icons.Rounded.History,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    )

                                    com.petal.browser.ui.containment.PetalGroupControlRow(
                                        title = stringResource(R.string.ui_open_tabs),
                                        subtitle = stringResource(R.string.ui_send_and_receive_active_browser),
                                        checked = syncTabs,
                                        onCheckedChange = { checked ->
                                            syncTabs = checked
                                            fxaManager.setEngineEnabled(SyncEngine.TABS, checked)
                                        },
                                        position = petalGroupPositionFor(2, syncItemsCount),
                                        variant = com.petal.browser.ui.containment.PetalBadgeVariant.TERTIARY,
                                        leadingIcon = {
                                            Icon(
                                                Icons.Rounded.Tab,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                        }
                                    )
                                }
                            }

                            // ── Section 4: SECURITY & PRIVACY ──────────────────────────
                            Text(
                                text = stringResource(R.string.ui_security_privacy),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp)
                            )

                            run {
                                val securityItemCount = 3
                                val isLockActive = sp.getBoolean("sp_app_lock_enabled", false)

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    com.petal.browser.ui.containment.PetalGroupNavigationRow(
                                        title = stringResource(R.string.ui_app_profile_lock),
                                        subtitle = if (isLockActive) "Protection active • Fingerprint or Password" else "Require authentication on app startup",
                                        leadingIcon = {
                                            Icon(
                                                painter = androidx.compose.ui.res.painterResource(com.petal.browser.R.drawable.mobile_vibrate_filled),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary
                                            )
                                        },
                                        position = petalGroupPositionFor(0, securityItemCount),
                                        variant = com.petal.browser.ui.containment.PetalBadgeVariant.PRIMARY,
                                        onClick = { showAppLockConfigPage = true }
                                    )

                                    com.petal.browser.ui.containment.PetalGroupControlRow(
                                        title = stringResource(R.string.ui_auto_clear_data_on_exit),
                                        subtitle = stringResource(R.string.ui_automatically_purge_cache_history_and_2),
                                        checked = isClearOnExit,
                                        onCheckedChange = { checked ->
                                            isClearOnExit = checked
                                            sp.edit()
                                                .putBoolean("sp_clear_quit", checked)
                                                .putBoolean("sp_clear_on_exit", checked)
                                                .apply()
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(
                                                    if (checked) "Auto-Clear on exit enabled" else "Auto-Clear on exit disabled"
                                                )
                                            }
                                        },
                                        position = petalGroupPositionFor(1, securityItemCount),
                                        variant = com.petal.browser.ui.containment.PetalBadgeVariant.SECONDARY,
                                        leadingIcon = {
                                            Icon(
                                                painter = androidx.compose.ui.res.painterResource(com.petal.browser.R.drawable.restore_page_filled),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    )

                                    com.petal.browser.ui.containment.PetalGroupControlRow(
                                        title = stringResource(R.string.ui_https_only_mode),
                                        subtitle = if (isHttpsOnly) "Active • HTTP automatically upgraded to HTTPS" else "Disabled • Insecure connections allowed",
                                        checked = isHttpsOnly,
                                        onCheckedChange = { checked ->
                                            isHttpsOnly = checked
                                            sp.edit().putBoolean("sp_https_only", checked).apply()
                                        },
                                        position = petalGroupPositionFor(2, securityItemCount),
                                        variant = com.petal.browser.ui.containment.PetalBadgeVariant.TERTIARY,
                                        leadingIcon = {
                                            Icon(
                                                painter = androidx.compose.ui.res.painterResource(com.petal.browser.R.drawable.layers_filled),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                        }
                                    )
                                }
                            }

                            // ── Section 5: STORAGE & DATA ──────────────────────────────
                            Text(
                                text = stringResource(R.string.ui_storage_data),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp)
                            )

                            run {
                                val storageItemCount = 2
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    com.petal.browser.ui.containment.PetalHeroCard(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(44.dp)
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .background(MaterialTheme.colorScheme.secondaryContainer),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            painter = androidx.compose.ui.res.painterResource(com.petal.browser.R.drawable.database_filled),
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                    }
                                                    Column {
                                                        Text(
                                                            text = stringResource(R.string.ui_web_cache_app_storage),
                                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            text = stringResource(R.string.ui_temporary_cached_network_files_and),
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }

                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                ) {
                                                    Text(
                                                        text = cacheSizeMb,
                                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                    )
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                Button(
                                                    onClick = {
                                                        try {
                                                            com.petal.browser.unit.BrowsingDataManager.clearCache(context, null)
                                                            cacheSizeMb = "0.0 MB"
                                                            coroutineScope.launch {
                                                                snackbarHostState.showSnackbar("Temporary web cache cleared")
                                                            }
                                                        } catch (e: Exception) {
                                                            coroutineScope.launch {
                                                                snackbarHostState.showSnackbar("Failed to clear cache: ${e.message}")
                                                            }
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                                    ),
                                                    shape = RoundedCornerShape(20.dp),
                                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                                                    modifier = Modifier.bouncyClickable()
                                                ) {
                                                    Icon(
                                                        painter = androidx.compose.ui.res.painterResource(com.petal.browser.R.drawable.close_rounded),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(stringResource(R.string.ui_clear_web_cache), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                                }
                                            }
                                        }
                                    }

                                    com.petal.browser.ui.containment.PetalGroupNavigationRow(
                                        title = stringResource(R.string.ui_clear_browsing_data),
                                        subtitle = stringResource(R.string.ui_select_remove_history_cookies_web),
                                        leadingIcon = {
                                            Icon(
                                                painter = androidx.compose.ui.res.painterResource(com.petal.browser.R.drawable.reset_settings_rounded),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary
                                            )
                                        },
                                        position = petalGroupPositionFor(1, storageItemCount),
                                        variant = com.petal.browser.ui.containment.PetalBadgeVariant.TERTIARY,
                                        onClick = { showClearDataDialog = true }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showAppLockConfigPage,
                enter = slideInHorizontally(
                    initialOffsetX = { it / 3 },
                    animationSpec = tween(durationMillis = 350, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
                ) + fadeIn(animationSpec = tween(durationMillis = 350, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))),
                exit = slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(durationMillis = 350, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
                ) + scaleOut(
                    targetScale = 0.85f,
                    animationSpec = tween(durationMillis = 350, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
                )
            ) {
                com.petal.browser.compose.security.PetalAppLockConfigScreen(
                    onBack = { showAppLockConfigPage = false },
                    wrapPredictive = false
                )
            }
        }
    }

    // ── Dialogs & Sheets ───────────────────────────────────────────────────────

    // Desktop Firefox QR Pairing Dialog
    if (showDesktopPairDialog) {
        com.petal.browser.ui.containment.PetalMaterialAlertDialog(
            onDismissRequest = { showDesktopPairDialog = false },
            title = {
                Text(stringResource(R.string.ui_pair_with_firefox_desktop), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.ui_on_your_firefox_desktop_browser),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = desktopPairUrlInput,
                        onValueChange = { desktopPairUrlInput = it },
                        label = { Text(stringResource(R.string.ui_firefox_pairing_url_code)) },
                        placeholder = { Text(stringResource(R.string.ui_https_firefox_com_pair_channel)) },
                        singleLine = false,
                        maxLines = 3,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val input = desktopPairUrlInput.trim()
                        if (input.isNotBlank()) {
                            showDesktopPairDialog = false
                            fxaManager.pairWithDesktopQr(
                                pairingUrl = input,
                                onSuccess = { email ->
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Paired successfully with Desktop Firefox ($email)! Syncing data...")
                                    }
                                    PetalMozillaSyncManager.getInstance().syncNow(context)
                                },
                                onError = { err ->
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Desktop pairing error: $err")
                                    }
                                }
                            )
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.bouncyClickable()
                ) {
                    Text(stringResource(R.string.ui_connect), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDesktopPairDialog = false },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.bouncyClickable()
                ) {
                    Text(stringResource(R.string.ui_cancel), fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // Direct Email Sign In Dialog
    if (showDirectEmailDialog) {
        com.petal.browser.ui.containment.PetalMaterialAlertDialog(
            onDismissRequest = { showDirectEmailDialog = false },
            title = {
                Text(stringResource(R.string.ui_sign_in_with_firefox_email), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.ui_enter_your_firefox_account_email),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = directEmailInput,
                        onValueChange = { directEmailInput = it },
                        label = { Text(stringResource(R.string.ui_firefox_account_email)) },
                        placeholder = { Text(stringResource(R.string.ui_you_example_com)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val email = directEmailInput.trim()
                        if (email.isNotBlank() && email.contains("@")) {
                            showDirectEmailDialog = false
                            val dummyCode = "direct_" + java.util.UUID.randomUUID().toString().take(12)
                            fxaManager.completeLogin(
                                code = dummyCode,
                                email = email,
                                displayName = email.substringBefore("@")
                            )
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Signed in as $email. Syncing data...")
                            }
                            PetalMozillaSyncManager.getInstance().syncNow(context)
                        } else {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Please enter a valid email address.")
                            }
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.bouncyClickable()
                ) {
                    Text(stringResource(R.string.ui_sign_in_sync), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDirectEmailDialog = false },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.bouncyClickable()
                ) {
                    Text(stringResource(R.string.ui_cancel), fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // Edit User Name Dialog
    if (showEditNameDialog) {
        com.petal.browser.ui.containment.PetalMaterialAlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text(stringResource(R.string.ui_edit_user_name), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { if (it.length <= 15) nameInput = it },
                    label = { Text(stringResource(R.string.ui_user_name_max_15_chars)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        GoogleAccountManager.updateDisplayName(context, nameInput)
                        showEditNameDialog = false
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.bouncyClickable()
                ) {
                    Text(stringResource(R.string.ui_save), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEditNameDialog = false },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.bouncyClickable()
                ) {
                    Text(stringResource(R.string.ui_cancel), fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // Clear Browsing Data Dialog
    if (showClearDataDialog) {
        com.petal.browser.ui.components.PetalClearBrowsingDataDialog(
            onDismiss = { showClearDataDialog = false },
            onPerformClear = { cache, cookies, storage, autofill, permissions ->
                showClearDataDialog = false
                com.petal.browser.unit.BrowsingDataManager.clearBrowsingDataAsync(
                    context,
                    null,
                    cache,
                    cookies,
                    storage,
                    autofill,
                    permissions
                ) {
                    try {
                        val cacheDir = context.cacheDir
                        val bytes = cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
                        cacheSizeMb = String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
                    } catch (_: Exception) {
                        cacheSizeMb = "0.0 MB"
                    }
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Selected browsing data cleared successfully")
                    }
                }
            }
        )
    }

    // Profile Picture Crop Dialog
    if (showCropDialog && pendingCropUri != null) {
        PetalAvatarCropSheet(
            imageUri = pendingCropUri!!,
            onDismiss = { showCropDialog = false },
            onAvatarCropped = {
                showCropDialog = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Profile picture updated!")
                }
            }
        )
    }

    // Media Picker Bottom Sheet
    if (showMediaPickerSheet) {
        com.petal.browser.ui.containment.PetalSheet(
            onDismissRequest = { showMediaPickerSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = null
        ) {
            com.petal.browser.media.PetalMediaPickerBottomSheet(
                allowMultiple = false,
                acceptTypes = arrayOf("image/*"),
                onMediaSelected = { uris ->
                    showMediaPickerSheet = false
                    val uri = uris.firstOrNull()
                    if (uri != null) {
                        try {
                            val tempFile = java.io.File(context.cacheDir, "temp_avatar_crop.png")
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                java.io.FileOutputStream(tempFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            pendingCropUri = if (tempFile.exists() && tempFile.length() > 0) Uri.fromFile(tempFile) else uri
                            showCropDialog = true
                        } catch (_: Exception) {
                            pendingCropUri = uri
                            showCropDialog = true
                        }
                    }
                },
                onDismissRequest = { showMediaPickerSheet = false },
                onBrowseSystemFiles = {
                    showMediaPickerSheet = false
                    showAvatarPetalPicker = true
                }
            )
        }
    }

    if (showAvatarPetalPicker) {
        com.petal.browser.compose.file.PetalFilePickerScreen(
            mimeTypes = arrayOf("image/*", "image/png", "image/jpeg", "image/webp"),
            onDismissRequest = { showAvatarPetalPicker = false },
            onFileSelected = { file ->
                showAvatarPetalPicker = false
                try {
                    val tempFile = java.io.File(context.cacheDir, "temp_avatar_crop.png")
                    java.io.FileInputStream(file).use { input ->
                        java.io.FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                    }
                    pendingCropUri = if (tempFile.exists() && tempFile.length() > 0) Uri.fromFile(tempFile) else Uri.fromFile(file)
                    showCropDialog = true
                } catch (e: Exception) {
                    pendingCropUri = Uri.fromFile(file)
                    showCropDialog = true
                }
            },
            onBrowseSystemFallback = {
                showAvatarPetalPicker = false
                galleryLauncher.launch("image/*")
            }
        )
    }
}
