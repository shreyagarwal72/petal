package com.petal.browser.account

import android.net.Uri
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
import com.petal.browser.ui.components.PetalFeatureTile
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported

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
            else -> {
                val presetEmoji = getPresetEmoji(profile.avatarPresetId)
                if (presetEmoji != null) {
                    Text(
                        text = presetEmoji,
                        fontSize = (sizeDp * 0.45).sp
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

fun getPresetEmoji(presetId: String): String? {
    return when (presetId) {
        "petal_flower" -> "🌸"
        "cosmic_star" -> "⭐"
        "cyber_shield" -> "🛡️"
        "rocket_boost" -> "🚀"
        "ocean_wave" -> "🌊"
        "ninja_cat" -> "🐱"
        "sparkle" -> "✨"
        "bot_avatar" -> "🤖"
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

    LaunchedEffect(Unit) {
        GoogleAccountManager.checkAndSyncGoogleAccount(context)
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
                                    Text(
                                        text = getPresetEmoji(presetId) ?: "🌸",
                                        fontSize = 24.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Global Google One-Click Login Switch Section (PetalFeatureTile styled)
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
                    Switch(
                        checked = profile.globalGoogleLogin,
                        onCheckedChange = { GoogleAccountManager.setGlobalGoogleLogin(context, it) }
                    )
                }
            )

            // Google Account Status & Auth Actions — Gramly-style feature tiles
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                PetalFeatureTile(
                    title = if (profile.isSignedIn) "Google Account Signed In" else "Sign In with Google",
                    subtitle = if (profile.isSignedIn) profile.email else "Connect your Google account for cross-device session access",
                    icon = Icons.Rounded.AccountCircle,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
                    pillLabel = if (profile.isSignedIn) null else "Sign In",
                    onClick = {
                        if (!profile.isSignedIn) {
                            onOpenOAuth(PetalShortcut("Google Login", "https://accounts.google.com/ServiceLogin", "google", Color(0xFF4285F4)))
                        }
                    }
                )

                PetalFeatureTile(
                    title = "Add Another Google Session",
                    subtitle = "Sign in with an additional Google account",
                    icon = Icons.Rounded.Add,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                    pillLabel = "Add",
                    onClick = {
                        onOpenOAuth(PetalShortcut("Add Google Session", "https://accounts.google.com/AddSession", "google", Color(0xFF4285F4)))
                    }
                )

                if (profile.isSignedIn) {
                    PetalFeatureTile(
                        title = "Sign Out of Google",
                        subtitle = "Disconnect session & clear account cookies",
                        icon = Icons.Rounded.Logout,
                        container = MaterialTheme.colorScheme.errorContainer,
                        onContainer = MaterialTheme.colorScheme.onErrorContainer,
                        pillLabel = "Sign Out",
                        onClick = { GoogleAccountManager.signOut(context) }
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
                    onValueChange = { nameInput = it },
                    label = { Text("User Name") },
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
                val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity)
                val currentPaletteId = remember {
                    sp.getString("sp_petal_palette_id", defaultPaletteId) ?: defaultPaletteId
                }
                val isAmoled = remember { sp.getBoolean("sp_petal_amoled_mode", false) }
                val useDynamic = remember { isDynamicColorSupported }

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
