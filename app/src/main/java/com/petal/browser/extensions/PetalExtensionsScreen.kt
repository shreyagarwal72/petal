/*
 * PetalExtensionsScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive Add-ons Store and Extension Management screen
 * for Petal Browser.
 *
 * Features:
 *   • Live AMO v5 Remote Extension Discovery & Search
 *   • Category filter pills (All, Privacy, Tools, Themes, Media)
 *   • Direct remote .xpi download & installation into live GeckoView runtime
 *   • Installed extension management (Enable/Disable toggles, Uninstall)
 *   • Consistent M3 Expressive design system (ExpressiveHeader, FancyCircularOrbLoader,
 *     ExpressiveButtonGroup, PetalMaterialShapes, EmptyStateBlob, Liquid Glass, Predictive Back)
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.extensions

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.preference.PreferenceManager
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.petal.browser.gecko.ExtensionInstallState
import com.petal.browser.gecko.GeckoExtensionManager
import com.petal.browser.gecko.GeckoRuntimeHolder
import com.petal.browser.ui.components.EmptyStateBlob
import com.petal.browser.ui.components.ExpressiveButtonGroup
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.ExpressiveSegmentItem
import com.petal.browser.ui.components.ExpressiveToastPill
import com.petal.browser.ui.components.FancyCircularOrbLoader
import com.petal.browser.ui.components.IconSwitch
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mozilla.geckoview.WebExtension

private enum class ExtensionTab(val id: String, val title: String) {
    STORE("store", "Add-ons Store"),
    INSTALLED("installed", "Installed")
}

private data class ExtensionCategory(
    val slug: String,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val EXTENSION_CATEGORIES = listOf(
    ExtensionCategory("all", "All", Icons.Rounded.Category),
    ExtensionCategory("privacy-security", "Privacy & Security", Icons.Rounded.Security),
    ExtensionCategory("search-tools", "Search & Tools", Icons.Rounded.Search),
    ExtensionCategory("appearance", "Appearance", Icons.Rounded.Palette),
    ExtensionCategory("download-management", "Downloads & Media", Icons.Rounded.Download),
    ExtensionCategory("performance", "Performance", Icons.Rounded.Speed)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalExtensionsScreen(
    backgroundSnapshot: androidx.compose.ui.graphics.ImageBitmap? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf(ExtensionTab.STORE) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategorySlug by remember { mutableStateOf("all") }

    // Remote Store State
    var isSearching by remember { mutableStateOf(false) }
    var remoteExtensions by remember { mutableStateOf<List<RemoteExtension>>(emptyList()) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var installingXpiUrl by remember { mutableStateOf<String?>(null) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var isToastVisible by remember { mutableStateOf(false) }

    fun showToast(msg: String) {
        toastMessage = msg
        isToastVisible = true
        coroutineScope.launch {
            delay(3500)
            isToastVisible = false
        }
    }

    // Gecko Extensions State
    val geckoInstalledExtensions by GeckoExtensionManager.installedExtensions.collectAsState()
    val installState by GeckoExtensionManager.installState.collectAsState()

    // Local / Legacy Chrome extensions fallback
    var legacyExtensions by remember { mutableStateOf(PetalExtensionManager.getInstalledExtensions(context)) }

    // Initial search load
    fun loadRemoteExtensions(query: String = "", category: String = "all") {
        isSearching = true
        searchError = null
        coroutineScope.launch {
            val catParam = if (category == "all") null else category
            val result = ExtensionRepository.searchExtensions(query = query, category = catParam)
            isSearching = false
            result.onSuccess { list ->
                remoteExtensions = list
            }.onFailure { err ->
                searchError = err.message ?: "Failed to load extensions from Mozilla AMO"
            }
        }
    }

    // Initial Load & Debounced Search
    var searchJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(searchQuery, selectedCategorySlug) {
        searchJob?.cancel()
        searchJob = launch {
            if (searchQuery.isNotBlank()) {
                delay(400) // Debounce typing
            }
            loadRemoteExtensions(searchQuery, selectedCategorySlug)
        }
    }

    // React to install state changes
    LaunchedEffect(installState) {
        when (val state = installState) {
            is ExtensionInstallState.Success -> {
                installingXpiUrl = null
                showToast("Extension '${state.extension.metaData?.name ?: state.extension.id}' installed successfully!")
                GeckoExtensionManager.refreshInstalledExtensions()
            }
            is ExtensionInstallState.Error -> {
                installingXpiUrl = null
                showToast("Failed to install extension: ${state.message}")
            }
            is ExtensionInstallState.Installing -> {
                installingXpiUrl = state.xpiUrl
            }
            else -> {}
        }
    }

    // File picker for manual .xpi / .crx
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val uriStr = uri.toString()
            if (uriStr.endsWith(".xpi", ignoreCase = true) || uri.lastPathSegment?.endsWith(".xpi", ignoreCase = true) == true) {
                if (GeckoRuntimeHolder.isInitialized) {
                    GeckoExtensionManager.installRemoteExtension(uriStr) { res ->
                        if (res.isSuccess) {
                            showToast("Installed .xpi extension!")
                        } else {
                            showToast("Failed to install .xpi: ${res.exceptionOrNull()?.message}")
                        }
                    }
                } else {
                    showToast("Gecko Engine not initialized. Enable Gecko Engine in Settings.")
                }
            } else {
                val success = PetalExtensionManager.installExtensionFromUri(context, uri)
                if (success) {
                    legacyExtensions = PetalExtensionManager.getInstalledExtensions(context)
                    showToast("Extension installed successfully!")
                } else {
                    showToast("Failed to install extension.")
                }
            }
        }
    }

    com.petal.browser.predictive.PetalPredictiveBackSurface(
        enabled = true,
        onBack = onDismiss,
    ) {
        com.petal.browser.predictive.PetalScreenWrapper(backgroundSnapshot = backgroundSnapshot) {
            Scaffold(
                topBar = {
                    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
                        ExpressiveHeader(
                            title = "WebExtensions Store",
                            subtitle = if (activeTab == ExtensionTab.STORE) "Discover & Install AMO Extensions" else "${geckoInstalledExtensions.size + legacyExtensions.size} Active Extensions",
                            onBack = onDismiss,
                            actions = {
                                FilledTonalButton(
                                    onClick = { fileLauncher.launch("*/*") },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Install File", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        )

                        // Tab Switcher
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            ExpressiveButtonGroup(
                                items = listOf(
                                    ExpressiveSegmentItem(
                                        id = ExtensionTab.STORE.id,
                                        label = "Add-ons Store",
                                        icon = Icons.Rounded.ShoppingBag
                                    ),
                                    ExpressiveSegmentItem(
                                        id = ExtensionTab.INSTALLED.id,
                                        label = "Installed (${geckoInstalledExtensions.size + legacyExtensions.size})",
                                        icon = Icons.Rounded.Extension
                                    )
                                ),
                                selectedId = activeTab.id,
                                onItemSelected = { id ->
                                    activeTab = if (id == ExtensionTab.STORE.id) ExtensionTab.STORE else ExtensionTab.INSTALLED
                                    if (activeTab == ExtensionTab.INSTALLED && GeckoRuntimeHolder.isInitialized) {
                                        GeckoExtensionManager.refreshInstalledExtensions()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Search Bar (Shown on Store tab & Installed tab)
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp),
                            placeholder = {
                                Text(if (activeTab == ExtensionTab.STORE) "Search Mozilla AMO Add-ons (e.g. uBlock, Dark Reader)..." else "Filter installed extensions...")
                            },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        )

                        // Category Filter Pills (Only on Store tab)
                        if (activeTab == ExtensionTab.STORE) {
                            val categoryScrollState = rememberScrollState()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(categoryScrollState)
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                EXTENSION_CATEGORIES.forEach { category ->
                                    val isSelected = selectedCategorySlug == category.slug
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedCategorySlug = category.slug
                                        },
                                        label = {
                                            Text(
                                                category.title,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                )
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = category.icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                    }
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    M3ExpressiveVariableBackground(pageSeed = "extensions_store")

                    when (activeTab) {
                        ExtensionTab.STORE -> {
                            RenderStoreTab(
                                isSearching = isSearching,
                                searchError = searchError,
                                remoteExtensions = remoteExtensions,
                                installingXpiUrl = installingXpiUrl,
                                installedGeckoExtensions = geckoInstalledExtensions,
                                onRetry = { loadRemoteExtensions(searchQuery, selectedCategorySlug) },
                                onInstallExtension = { remoteExt ->
                                    val xpiUrl = remoteExt.xpiDownloadUrl
                                    if (xpiUrl.isNullOrBlank()) {
                                        showToast("Direct .xpi release file not available for this extension.")
                                        return@RenderStoreTab
                                    }

                                    if (!GeckoRuntimeHolder.isInitialized) {
                                        // Opt-in trigger if not yet started
                                        GeckoRuntimeHolder.enable(context)
                                    }

                                    installingXpiUrl = xpiUrl
                                    GeckoExtensionManager.installRemoteExtension(xpiUrl) { res ->
                                        installingXpiUrl = null
                                        if (res.isSuccess) {
                                            showToast("Extension '${remoteExt.title}' installed successfully!")
                                        } else {
                                            showToast("Installation error: ${res.exceptionOrNull()?.message}")
                                        }
                                    }
                                }
                            )
                        }
                        ExtensionTab.INSTALLED -> {
                            RenderInstalledTab(
                                searchQuery = searchQuery,
                                geckoExtensions = geckoInstalledExtensions,
                                legacyExtensions = legacyExtensions,
                                onToggleGecko = { ext, isEnabled ->
                                    if (isEnabled) {
                                        GeckoExtensionManager.enable(ext)
                                    } else {
                                        GeckoExtensionManager.disable(ext)
                                    }
                                },
                                onUninstallGecko = { ext ->
                                    GeckoExtensionManager.uninstall(ext) {
                                        showToast("Uninstalled extension")
                                    }
                                },
                                onToggleLegacy = { ext, isEnabled ->
                                    PetalExtensionManager.setExtensionEnabled(context, ext.id, isEnabled)
                                    legacyExtensions = PetalExtensionManager.getInstalledExtensions(context)
                                },
                                onRemoveLegacy = { ext ->
                                    PetalExtensionManager.removeExtension(context, ext.id)
                                    legacyExtensions = PetalExtensionManager.getInstalledExtensions(context)
                                    showToast("Removed extension")
                                }
                            )
                        }
                    }

                    // Floating Toast Notification
                    ExpressiveToastPill(
                        message = toastMessage ?: "",
                        isVisible = isToastVisible,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderStoreTab(
    isSearching: Boolean,
    searchError: String?,
    remoteExtensions: List<RemoteExtension>,
    installingXpiUrl: String?,
    installedGeckoExtensions: List<WebExtension>,
    onRetry: () -> Unit,
    onInstallExtension: (RemoteExtension) -> Unit
) {
    if (isSearching) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FancyCircularOrbLoader(size = 56.dp, strokeWidth = 5.dp)
                Text(
                    "Discovering Add-ons from Mozilla AMO...",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    if (searchError != null && remoteExtensions.isEmpty()) {
        EmptyStateBlob(
            imageVector = Icons.Rounded.CloudOff,
            title = "Unable to connect to AMO",
            description = searchError,
            actionText = "Retry Search",
            actionIcon = Icons.Rounded.Refresh,
            onAction = onRetry
        )
        return
    }

    if (remoteExtensions.isEmpty()) {
        EmptyStateBlob(
            imageVector = Icons.Rounded.SearchOff,
            title = "No Extensions Found",
            description = "Try searching for content blockers, themes, or developer user scripts.",
            actionText = "Reset Filters",
            actionIcon = Icons.Rounded.FilterListOff,
            onAction = onRetry
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(remoteExtensions, key = { it.id }) { ext ->
            val isInstalled = installedGeckoExtensions.any {
                it.id == ext.guid || it.metaData?.name.equals(ext.title, ignoreCase = true)
            }
            val isCurrentlyInstalling = installingXpiUrl == ext.xpiDownloadUrl

            RemoteExtensionCard(
                extension = ext,
                isInstalled = isInstalled,
                isInstalling = isCurrentlyInstalling,
                onInstallClick = { onInstallExtension(ext) }
            )
        }
    }
}

@Composable
private fun RemoteExtensionCard(
    extension: RemoteExtension,
    isInstalled: Boolean,
    isInstalling: Boolean,
    onInstallClick: () -> Unit
) {
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Icon + Title/Author + Install Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Extension Icon
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(52.dp)
                ) {
                    if (!extension.iconUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(extension.iconUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = extension.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Extension,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                // Title, Author, Rating
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = extension.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(2.dp))

                    Text(
                        text = "by ${extension.author}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (extension.rating > 0.0) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = "Rating",
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = String.format(java.util.Locale.US, "%.1f", extension.rating),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "(${extension.ratingsCount})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(6.dp))
                        }

                        if (extension.dailyUsers > 0) {
                            Icon(
                                imageVector = Icons.Rounded.People,
                                contentDescription = "Users",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = formatUserCount(extension.dailyUsers),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Install Button
                if (isInstalled) {
                    FilledTonalButton(
                        onClick = {},
                        enabled = false,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Installed", style = MaterialTheme.typography.labelMedium)
                    }
                } else if (isInstalling) {
                    FilledTonalButton(
                        onClick = {},
                        enabled = false,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Adding...", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Button(
                        onClick = onInstallClick,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Install", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }

            // Summary description
            if (extension.summary.isNotBlank()) {
                Text(
                    text = extension.summary,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Permissions / Category Badges
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = "v${extension.version}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                if (extension.permissions.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = "${extension.permissions.size} permissions",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (extension.categories.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = extension.categories.first().replace("-", " ").replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderInstalledTab(
    searchQuery: String,
    geckoExtensions: List<WebExtension>,
    legacyExtensions: List<PetalExtension>,
    onToggleGecko: (WebExtension, Boolean) -> Unit,
    onUninstallGecko: (WebExtension) -> Unit,
    onToggleLegacy: (PetalExtension, Boolean) -> Unit,
    onRemoveLegacy: (PetalExtension) -> Unit
) {
    val filteredGecko = remember(searchQuery, geckoExtensions) {
        geckoExtensions.filter { ext ->
            val name = ext.metaData?.name ?: ext.id
            val desc = ext.metaData?.description ?: ""
            searchQuery.isBlank() || name.contains(searchQuery, ignoreCase = true) || desc.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredLegacy = remember(searchQuery, legacyExtensions) {
        legacyExtensions.filter { ext ->
            searchQuery.isBlank() || ext.name.contains(searchQuery, ignoreCase = true) || ext.description.contains(searchQuery, ignoreCase = true)
        }
    }

    if (filteredGecko.isEmpty() && filteredLegacy.isEmpty()) {
        EmptyStateBlob(
            imageVector = Icons.Rounded.ExtensionOff,
            title = "No Installed Extensions",
            description = "Browse the Add-ons Store to install extensions directly into Petal Browser."
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Active GeckoView WebExtensions
        if (filteredGecko.isNotEmpty()) {
            item {
                Text(
                    text = "GeckoView WebExtensions (${filteredGecko.size})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
                )
            }

            items(filteredGecko, key = { it.id }) { ext ->
                val meta = ext.metaData
                val isEnabled = ext.flags and WebExtension.Flags.ALLOW_IN_INCOGNITO != 0 || true

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.Extension,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = meta?.name ?: ext.id,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "v${meta?.version ?: "1.0.0"} • Gecko Native",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconSwitch(
                                checked = isEnabled,
                                icon = Icons.Rounded.Extension,
                                onCheckedChange = { onToggleGecko(ext, it) }
                            )
                        }

                        if (!meta?.description.isNullOrBlank()) {
                            Text(
                                text = meta?.description ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = { onUninstallGecko(ext) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Uninstall", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        // Legacy / Bundled Extensions
        if (filteredLegacy.isNotEmpty()) {
            item {
                Text(
                    text = "Bundled Extensions & Userscripts (${filteredLegacy.size})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
                )
            }

            items(filteredLegacy, key = { it.id }) { ext ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.tertiary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.Extension,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = ext.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "v${ext.version}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconSwitch(
                                checked = ext.enabled,
                                icon = Icons.Rounded.Extension,
                                onCheckedChange = { isEnabled -> onToggleLegacy(ext, isEnabled) }
                            )
                        }

                        Text(
                            text = ext.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = { onRemoveLegacy(ext) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Remove", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatUserCount(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM users", count / 1_000_000.0)
        count >= 1_000 -> String.format(java.util.Locale.US, "%.1fk users", count / 1_000.0)
        else -> "$count users"
    }
}

/** Java Interop Bridge to open Chrome & Web Extensions sheet */
object PetalExtensionsBridge {
    @JvmStatic
    fun showExtensions(activity: ComponentActivity) {
        try {
            val dialog = BottomSheetDialog(activity)
            dialog.setOnShowListener {
                val bottomSheet = dialog.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
                bottomSheet?.let { sheet ->
                    val behavior = BottomSheetBehavior.from(sheet)
                    behavior.state = BottomSheetBehavior.STATE_EXPANDED
                    behavior.skipCollapsed = true
                }
            }

            val rootView = activity.findViewById<android.view.View>(android.R.id.content) ?: activity.window.decorView
            com.petal.browser.predictive.PetalContentSnapshot.capture(rootView)
            val composeView = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    val snapshotBitmap = remember { com.petal.browser.predictive.PetalContentSnapshot.current?.asImageBitmap() }
                    DisposableEffect(Unit) {
                        onDispose {
                            com.petal.browser.predictive.PetalContentSnapshot.clear()
                        }
                    }
                    val sp = PreferenceManager.getDefaultSharedPreferences(activity)
                    val paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                    val dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)
                    val isAmoled = sp.getBoolean("sp_amoled", false)

                    PetalExpressiveTheme(
                        dynamicColor = dynamicColor,
                        useAmoled = isAmoled,
                        paletteId = paletteId
                    ) {
                        PetalExtensionsScreen(
                            backgroundSnapshot = snapshotBitmap,
                            onDismiss = { dialog.dismiss() }
                        )
                    }
                }
            }

            dialog.setContentView(composeView)
            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
