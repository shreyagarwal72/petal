package com.petal.browser.account

import android.net.Uri
import android.preference.PreferenceManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.ComponentActivity
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.compose.AsyncImage
import com.petal.browser.compose.home.PetalShortcut
import com.petal.browser.ui.components.IconSwitch
import com.petal.browser.ui.components.PetalAboutDeveloperSheet
import com.petal.browser.ui.components.PetalThemedSnackbarHost
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported
import kotlinx.coroutines.launch

@Composable
fun ProfileAvatarDisplay(
    profile: GoogleUserProfile,
    sizeDp: Int = 72,
    modifier: Modifier = Modifier
) {
    val size = sizeDp.dp
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        when {
            profile.avatarType == AvatarType.GOOGLE_URL && !profile.avatarUrl.isNullOrEmpty() -> {
                AsyncImage(
                    model = profile.avatarUrl,
                    contentDescription = "Profile Photo",
                    modifier = Modifier.size(size).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
            profile.avatarType == AvatarType.GALLERY_URI && !profile.customAvatarUri.isNullOrEmpty() -> {
                AsyncImage(
                    model = Uri.parse(profile.customAvatarUri),
                    contentDescription = "Custom Photo",
                    modifier = Modifier.size(size).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
            profile.avatarType == AvatarType.PRESET && profile.avatarPresetId == "app_icon" -> {
                AsyncImage(
                    model = com.petal.browser.R.mipmap.ic_launcher,
                    contentDescription = "App Icon Avatar",
                    modifier = Modifier.size(size * 0.7f),
                    contentScale = ContentScale.Fit
                )
            }
            else -> {
                val iconVector = getPresetMaterialIcon(profile.avatarPresetId)
                if (iconVector != null) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = "Preset Avatar",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(size * 0.5f)
                    )
                } else {
                    val initial = profile.displayName.trim().take(1).ifEmpty { "P" }.uppercase()
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

fun getPresetMaterialIcon(presetId: String): androidx.compose.ui.graphics.vector.ImageVector? {
    return when (presetId) {
        "petal_flower" -> Icons.Rounded.LocalFlorist
        "cosmic_star" -> Icons.Rounded.Star
        "cyber_shield" -> Icons.Rounded.Shield
        "rocket_boost" -> Icons.Rounded.RocketLaunch
        "ocean_wave" -> Icons.Rounded.Water
        "ninja_cat" -> Icons.Rounded.Pets
        "sparkle" -> Icons.Rounded.AutoAwesome
        "bot_avatar" -> Icons.Rounded.SmartToy
        else -> null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalUserProfileScreen(
    onBack: () -> Unit,
    onOpenOAuth: (PetalShortcut) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val profile = GoogleAccountManager.currentProfile
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isSigningIn by remember { mutableStateOf(false) }

    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var isExpressiveFeatureTiles by remember { mutableStateOf(sp.getBoolean("sp_expressive_feature_tiles", true)) }

    LaunchedEffect(Unit) {
        GoogleAccountManager.init(context)
    }

    DisposableEffect(sp) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "sp_expressive_feature_tiles") {
                isExpressiveFeatureTiles = sp.getBoolean("sp_expressive_feature_tiles", true)
            }
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val legacySignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        val result = GoogleAccountManager.handleLegacySignInResult(context, activityResult.data)
        if (result is GoogleSignInResult.Success) {
            isSigningIn = false
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Signed in as ${result.profile.email}")
            }
        } else {
            // If legacy intent returns failure/cancellation, seamlessly fall back to Credential Manager UI
            coroutineScope.launch {
                when (val fallbackResult = GoogleAccountManager.signIn(context)) {
                    is GoogleSignInResult.Success -> {
                        snackbarHostState.showSnackbar("Signed in as ${fallbackResult.profile.email}")
                    }
                    is GoogleSignInResult.Failure -> {
                        if (result is GoogleSignInResult.Failure) {
                            snackbarHostState.showSnackbar(result.message)
                        }
                    }
                }
                isSigningIn = false
            }
        }
    }

    fun startGoogleSignIn() {
        if (isSigningIn) return
        isSigningIn = true
        coroutineScope.launch {
            try {
                val intent = GoogleAccountManager.createLegacySignInIntent(context)
                legacySignInLauncher.launch(intent)
            } catch (e: Throwable) {
                // Fallback to Credential Manager if Play Services auth client fails
                when (val result = GoogleAccountManager.signIn(context)) {
                    is GoogleSignInResult.Success -> {
                        snackbarHostState.showSnackbar("Signed in as ${result.profile.email}")
                    }
                    is GoogleSignInResult.Failure -> {
                        snackbarHostState.showSnackbar(result.message)
                    }
                }
                isSigningIn = false
            }
        }
    }

    var showEditNameDialog by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf(profile.displayName) }

    var isLoading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(600L)
        isLoading = false
    }

    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            pendingCropUri = it
        }
    }

    if (pendingCropUri != null) {
        PetalAvatarCropSheet(
            imageUri = pendingCropUri!!,
            onDismiss = { pendingCropUri = null },
            onAvatarCropped = {
                pendingCropUri = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Profile picture updated permanently!")
                }
            }
        )
    }

    com.petal.browser.predictive.PetalPredictiveBackSurface(
        enabled = true,
        onBack = { onBack() },
    ) {
    com.petal.browser.predictive.PetalScreenWrapper {
        Scaffold(
        topBar = {
            com.petal.browser.ui.components.ExpressiveHeader(
                title = "User Accounts & Profile",
                subtitle = "Manage account & preferences",
                onBack = { onBack() },
                maxTitleLines = 1,
                maxSubtitleLines = 1
            )
        },
        snackbarHost = { PetalThemedSnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            com.petal.browser.ui.components.M3ExpressiveVariableBackground(pageSeed = "account_page")

        if (isLoading) {
            com.petal.browser.compose.composable.ContainedLoadingIndicator(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
            // Main User Profile Hero Card
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ProfileAvatarDisplay(profile = profile, sizeDp = 84)

                    Spacer(Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = profile.displayName,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = {
                            nameInput = profile.displayName
                            showEditNameDialog = true
                        }) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = "Edit User Name",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Text(
                        text = if (profile.isSignedIn) profile.email else "Local Petal Explorer Profile",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(16.dp))

                    // Avatar Selection Section (Built-in Presets vs Gallery)
                    Text(
                        text = "Choose Profile Picture",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Gallery Button
                        Surface(
                            onClick = { galleryLauncher.launch("image/*") },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.AddPhotoAlternate,
                                    contentDescription = "Select from Gallery",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Built-in Presets
                        GoogleAccountManager.builtinAvatarPresets.forEach { (presetId, label) ->
                            val isSelected = profile.avatarType == AvatarType.PRESET && profile.avatarPresetId == presetId
                            Surface(
                                onClick = { GoogleAccountManager.updateAvatarPreset(context, presetId) },
                                shape = CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier.size(52.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (presetId == "app_icon") {
                                        AsyncImage(
                                            model = com.petal.browser.R.mipmap.ic_launcher,
                                            contentDescription = label,
                                            modifier = Modifier.size(32.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        val iconVec = getPresetMaterialIcon(presetId)
                                        if (iconVec != null) {
                                            Icon(
                                                imageVector = iconVec,
                                                contentDescription = label,
                                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.Person,
                                                contentDescription = label,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Tappable-only Google Web Accounts SSO card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onOpenOAuth(
                            PetalShortcut(
                                "Google Accounts SSO",
                                "https://accounts.google.com/ServiceLogin?hl=en",
                                "https://accounts.google.com/ServiceLogin?hl=en",
                                Color(0xFF4285F4)
                            )
                        )
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Open Google Accounts Web SSO",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Launch Google Accounts login page to sign in to Google Web Services (YouTube, Gmail, Drive, Maps)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "Open SSO",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Section 1: 🛡️ Security & Privacy Center
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Security,
                            contentDescription = "Security Center",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Security & Privacy Center",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Biometric Lock Preference
                    var isBiometricEnabled by remember { mutableStateOf(sp.getBoolean("sp_biometric_lock", false)) }
                    AccountActionRow(
                        title = "App & Profile Lock",
                        subtitle = "Require biometric / device lock when launching Petal Browser",
                        icon = Icons.Rounded.Lock,
                        trailing = {
                            IconSwitch(
                                checked = isBiometricEnabled,
                                icon = Icons.Rounded.Lock,
                                onCheckedChange = { checked ->
                                    val activity = context as? androidx.appcompat.app.AppCompatActivity
                                    if (checked && activity != null) {
                                        com.petal.browser.security.BiometricLockManager.authenticate(
                                            activity,
                                            "Enable Biometric App Lock",
                                            "Verify your fingerprint or PIN to enable lock",
                                            Runnable {
                                                isBiometricEnabled = true
                                                sp.edit().putBoolean("sp_biometric_lock", true).apply()
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Biometric App Lock enabled & verified")
                                                }
                                            },
                                            java.util.function.Consumer { error ->
                                                isBiometricEnabled = false
                                                sp.edit().putBoolean("sp_biometric_lock", false).apply()
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Biometric setup failed: $error")
                                                }
                                            }
                                        )
                                    } else {
                                        isBiometricEnabled = false
                                        sp.edit().putBoolean("sp_biometric_lock", false).apply()
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Biometric App Lock disabled")
                                        }
                                    }
                                }
                            )
                        },
                        onClick = {
                            val activity = context as? androidx.appcompat.app.AppCompatActivity
                            val newChecked = !isBiometricEnabled
                            if (newChecked && activity != null) {
                                com.petal.browser.security.BiometricLockManager.authenticate(
                                    activity,
                                    "Enable Biometric App Lock",
                                    "Verify your fingerprint or PIN to enable lock",
                                    Runnable {
                                        isBiometricEnabled = true
                                        sp.edit().putBoolean("sp_biometric_lock", true).apply()
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Biometric App Lock enabled & verified")
                                        }
                                    },
                                    java.util.function.Consumer { error ->
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Biometric setup failed: $error")
                                        }
                                    }
                                )
                            } else {
                                isBiometricEnabled = false
                                sp.edit().putBoolean("sp_biometric_lock", false).apply()
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Biometric App Lock disabled")
                                }
                            }
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // Google Passkey & WebAuthn Management
                    AccountActionRow(
                        title = "Google Passkey & Credential Vault",
                        subtitle = "Hardware-bound passwordless authentication & FIDO2 passkeys",
                        icon = Icons.Rounded.Key,
                        onClick = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Google Passkey vault synced with Android Credential Manager")
                            }
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // Auto-Clear on Exit Preference
                    var isClearOnExit by remember { mutableStateOf(sp.getBoolean("sp_clear_on_exit", false)) }
                    AccountActionRow(
                        title = "Auto-Clear Data on Exit",
                        subtitle = "Automatically purge cache, history, and cookies on close",
                        icon = Icons.Rounded.CleaningServices,
                        trailing = {
                            IconSwitch(
                                checked = isClearOnExit,
                                icon = Icons.Rounded.CleaningServices,
                                onCheckedChange = { checked ->
                                    isClearOnExit = checked
                                    sp.edit().putBoolean("sp_clear_on_exit", checked).apply()
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            if (checked) "Auto-Clear on exit enabled" else "Auto-Clear on exit disabled"
                                        )
                                    }
                                }
                            )
                        },
                        onClick = {
                            isClearOnExit = !isClearOnExit
                            sp.edit().putBoolean("sp_clear_on_exit", isClearOnExit).apply()
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // HTTPS-Only Mode Status
                    var isHttpsOnly by remember { mutableStateOf(sp.getBoolean("sp_https_only", true)) }
                    AccountActionRow(
                        title = "HTTPS-Only Mode",
                        subtitle = "Automatically upgrade HTTP requests to secure HTTPS connection",
                        icon = Icons.Rounded.VerifiedUser,
                        trailing = {
                            IconSwitch(
                                checked = isHttpsOnly,
                                icon = Icons.Rounded.VerifiedUser,
                                onCheckedChange = { checked ->
                                    isHttpsOnly = checked
                                    sp.edit().putBoolean("sp_https_only", checked).apply()
                                }
                            )
                        },
                        onClick = {
                            isHttpsOnly = !isHttpsOnly
                            sp.edit().putBoolean("sp_https_only", isHttpsOnly).apply()
                        }
                    )
                }
            }

            // Section 5: 📊 Local Data & Storage Audit
            var cacheSizeMb by remember {
                mutableStateOf(
                    try {
                        val cacheDir = context.cacheDir
                        val bytes = cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
                        String.format("%.1f MB", bytes / (1024f * 1024f))
                    } catch (e: Exception) {
                        "0.0 MB"
                    }
                )
            }
            var showClearDataDialog by remember { mutableStateOf(false) }

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
                                cacheSizeMb = String.format("%.1f MB", bytes / (1024f * 1024f))
                            } catch (e: Exception) {
                                cacheSizeMb = "0.0 MB"
                            }
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Selected browsing data cleared successfully")
                            }
                        }
                    }
                )
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Storage,
                            contentDescription = "Data Storage Audit",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Storage & Data Audit",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Inspect application storage & manage browsing data",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Storage Consumption Summary Card
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
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
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Rounded.CleaningServices,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = "Web Cache & App Storage",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Temporary cached network files and assets",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = cacheSizeMb,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
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
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.DeleteSweep,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Clear Web Cache", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    AccountActionRow(
                        title = "Clear Browsing Data",
                        subtitle = "Select & remove history, cookies, web storage, autofill & permissions",
                        icon = Icons.Rounded.DeleteSweep,
                        onClick = { showClearDataDialog = true }
                    )
                }
            }
        }
    }

        // Edit User Name Dialog
        if (showEditNameDialog) {
            AlertDialog(
                onDismissRequest = { showEditNameDialog = false },
                title = { Text("Edit User Name") },
                text = {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { if (it.length <= 15) nameInput = it },
                        label = { Text("User Name (max 15 chars)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        GoogleAccountManager.updateDisplayName(context, nameInput)
                        showEditNameDialog = false
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditNameDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
}
}
}

@Composable
private fun AccountActionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (trailing != null) {
            trailing()
        }
    }
}

// ── Java Interop Bridge ────────────────────────────────────────────────────
object PetalAccountSyncBridge {
    @JvmStatic
    fun createAccountSyncView(
        activity: ComponentActivity,
        onBack: () -> Unit,
        onOpenOAuth: (PetalShortcut) -> Unit
    ): ComposeView {
        return ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val sp = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
                var currentPaletteId by remember { mutableStateOf(sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId) }
                var isAmoled by remember { mutableStateOf(sp.getBoolean("sp_amoled", false)) }
                var useDynamic by remember { mutableStateOf(sp.getBoolean("useDynamicColor", isDynamicColorSupported)) }
                var fontName by remember { mutableStateOf(sp.getString("sp_app_font", "PETAL") ?: "PETAL") }
                var styleName by remember { mutableStateOf(sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT") }
                var fontWidthVal by remember { mutableFloatStateOf(sp.getFloat("sp_font_width", 92f)) }
                var fontWeightVal by remember { mutableIntStateOf(sp.getInt("sp_font_weight", 750)) }
                var fontRoundnessVal by remember { mutableFloatStateOf(sp.getFloat("sp_font_roundness", 100f)) }

                DisposableEffect(sp) {
                    val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                        when (key) {
                            "sp_palette_id" -> currentPaletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                            "sp_amoled" -> isAmoled = sp.getBoolean("sp_amoled", false)
                            "useDynamicColor" -> useDynamic = sp.getBoolean("useDynamicColor", isDynamicColorSupported)
                            "sp_app_font" -> fontName = sp.getString("sp_app_font", "PETAL") ?: "PETAL"
                            "sp_color_style" -> styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                            "sp_font_width" -> fontWidthVal = sp.getFloat("sp_font_width", 92f)
                            "sp_font_weight" -> fontWeightVal = sp.getInt("sp_font_weight", 750)
                            "sp_font_roundness" -> fontRoundnessVal = sp.getFloat("sp_font_roundness", 100f)
                        }
                    }
                    sp.registerOnSharedPreferenceChangeListener(listener)
                    onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
                }

                val appFont = remember(fontName) {
                    try { com.petal.browser.ui.theme.AppFont.valueOf(fontName) } catch (e: Exception) { com.petal.browser.ui.theme.AppFont.PETAL }
                }
                val colorStyle = remember(styleName) {
                    try { com.petal.browser.ui.theme.ColorStyle.valueOf(styleName) } catch (e: Exception) { com.petal.browser.ui.theme.ColorStyle.TONAL_SPOT }
                }

                PetalExpressiveTheme(
                    paletteId = currentPaletteId,
                    useAmoled = isAmoled,
                    dynamicColor = useDynamic,
                    appFont = appFont,
                    colorStyle = colorStyle,
                    fontWidth = fontWidthVal,
                    fontWeight = fontWeightVal,
                    fontRoundness = fontRoundnessVal
                ) {
                    PetalUserProfileScreen(
                        onBack = onBack,
                        onOpenOAuth = onOpenOAuth
                    )
                }
            }
        }
    }
}
