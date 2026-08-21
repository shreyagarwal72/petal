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
import com.petal.browser.ui.components.PetalFeatureTile
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

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            GoogleAccountManager.updateAvatarGalleryUri(context, it.toString())
        }
    }

    // Same predictive-back wiring as Settings/Downloads: without this, a back-swipe here
    // falls straight through to the Activity-level browser back logic, which knows nothing
    // about the account screen being open.
    androidx.activity.compose.BackHandler(enabled = true) {
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("User Profile & Accounts", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
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
                        text = if (profile.isSignedIn) profile.email else "Local Profile (Not signed in to Google)",
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

            // Global Google One-Click Login Switch Section
            if (isExpressiveFeatureTiles) {
                PetalFeatureTile(
                    title = "One-Click Google Single Sign-On (SSO)",
                    subtitle = "Log in to all Google sites (YouTube, Gmail, Drive, Maps) automatically in one session",
                    icon = Icons.Rounded.VpnKey,
                    container = MaterialTheme.colorScheme.surfaceContainerHigh,
                    onContainer = MaterialTheme.colorScheme.onSurface,
                    onClick = {
                        GoogleAccountManager.setGlobalGoogleLogin(context, !profile.globalGoogleLogin)
                    },
                    pillLabel = null,
                    trailing = {
                        IconSwitch(
                            checked = profile.globalGoogleLogin,
                            icon = Icons.Rounded.VpnKey,
                            onCheckedChange = { GoogleAccountManager.setGlobalGoogleLogin(context, it) }
                        )
                    }
                )

                // Google Account Status & Auth Actions — real Credential Manager sign-in
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PetalFeatureTile(
                        title = when {
                            profile.isSignedIn -> "Google Account Signed In"
                            isSigningIn -> "Signing In..."
                            else -> "Sign In with Google"
                        },
                        subtitle = when {
                            profile.isSignedIn -> profile.email
                            isSigningIn -> "Waiting for Google account picker"
                            else -> "Choose your Google account to sign in"
                        },
                        icon = Icons.Rounded.AccountCircle,
                        container = MaterialTheme.colorScheme.primaryContainer,
                        onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
                        pillLabel = if (profile.isSignedIn) null else "Sign In",
                        onClick = {
                            if (!profile.isSignedIn) {
                                startGoogleSignIn()
                            }
                        }
                    )

                    PetalFeatureTile(
                        title = "Open Google Accounts SSO Web Login",
                        subtitle = "Open Google Accounts sign-in page to log in to Google Web Services across all Google apps & sites",
                        icon = Icons.Rounded.Language,
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                        pillLabel = "Open SSO",
                        onClick = {
                            onOpenOAuth(
                                PetalShortcut(
                                    "Google Accounts SSO",
                                    "https://accounts.google.com/ServiceLogin?hl=en",
                                    "https://accounts.google.com/ServiceLogin?hl=en",
                                    Color(0xFF4285F4)
                                )
                            )
                        }
                    )

                    if (profile.isSignedIn) {
                        PetalFeatureTile(
                            title = "Sign Out of Google",
                            subtitle = "Disconnect this Google account from Petal Browser",
                            icon = Icons.Rounded.Logout,
                            container = MaterialTheme.colorScheme.errorContainer,
                            onContainer = MaterialTheme.colorScheme.onErrorContainer,
                            pillLabel = "Sign Out",
                            onClick = {
                                coroutineScope.launch {
                                    GoogleAccountManager.legacySignOut(context)
                                    snackbarHostState.showSnackbar("Signed out of Google")
                                }
                            }
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        AccountActionRow(
                            title = "One-Click Google Single Sign-On (SSO)",
                            subtitle = "Log in to all Google sites (YouTube, Gmail, Drive, Maps) automatically in one session",
                            icon = Icons.Rounded.VpnKey,
                            trailing = {
                                IconSwitch(
                                    checked = profile.globalGoogleLogin,
                                    icon = Icons.Rounded.VpnKey,
                                    onCheckedChange = { GoogleAccountManager.setGlobalGoogleLogin(context, it) }
                                )
                            },
                            onClick = {
                                GoogleAccountManager.setGlobalGoogleLogin(context, !profile.globalGoogleLogin)
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        AccountActionRow(
                            title = when {
                                profile.isSignedIn -> "Google Account Signed In"
                                isSigningIn -> "Signing In..."
                                else -> "Sign In with Google"
                            },
                            subtitle = when {
                                profile.isSignedIn -> profile.email
                                isSigningIn -> "Waiting for Google account picker"
                                else -> "Choose your Google account to sign in"
                            },
                            icon = Icons.Rounded.AccountCircle,
                            onClick = {
                                if (!profile.isSignedIn) {
                                    startGoogleSignIn()
                                }
                            }
                        )

                        if (profile.isSignedIn) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            AccountActionRow(
                                title = "Sign Out of Google",
                                subtitle = "Disconnect this Google account from Petal Browser",
                                icon = Icons.Rounded.Logout,
                                onClick = {
                                    coroutineScope.launch {
                                        GoogleAccountManager.legacySignOut(context)
                                        snackbarHostState.showSnackbar("Signed out of Google")
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

                DisposableEffect(sp) {
                    val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                        when (key) {
                            "sp_palette_id" -> currentPaletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                            "sp_amoled" -> isAmoled = sp.getBoolean("sp_amoled", false)
                            "useDynamicColor" -> useDynamic = sp.getBoolean("useDynamicColor", isDynamicColorSupported)
                        }
                    }
                    sp.registerOnSharedPreferenceChangeListener(listener)
                    onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
                }

                PetalExpressiveTheme(
                    paletteId = currentPaletteId,
                    useAmoled = isAmoled,
                    dynamicColor = useDynamic
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
