package com.petal.browser.compose.store

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.petal.browser.R
import com.petal.browser.compose.extensions.PetalCuratedExtensionsData
import com.petal.browser.extensions.PetalExtensionManager
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.predictive.PetalContentSnapshot
import com.petal.browser.predictive.PetalPredictiveBackSurface
import com.petal.browser.predictive.PetalScreenWrapper
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.IconSwitch
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.containment.*
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.PetalMaterialShapes
import kotlinx.coroutines.launch

/**
 * PetalBrowserStoreScreen
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive Browser Store & Hub:
 * - Redesigned with Petal Containment System (PetalSettingsSection, PetalHeroCard,
 *   PetalGroupListRow, PetalConnectedButtonGroup, PetalGroupIconBadge).
 * - Tab 0: Add-ons (Curated Firefox WebExtensions from AMO with 1-tap install)
 * - Tab 1: Petal Scripts (Verified Userscripts with SHA-256 integrity verification)
 * - Tab 2: Search Engines (Curated privacy search engines with 1-tap set default)
 * - Tab 3: Filter Lists (AdBlock & Tracker Shield blocklist subscriptions)
 */
object PetalBrowserStoreBridge {
    @JvmStatic
    fun createStoreView(
        activity: ComponentActivity,
        onBackPress: () -> Unit
    ): android.view.View {
        val rootView = activity.findViewById<android.view.View>(android.R.id.content) ?: activity.window.decorView
        PetalContentSnapshot.capture(rootView)
        return ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val snapshotBitmap = remember {
                    PetalContentSnapshot.current?.asImageBitmap()
                }
                DisposableEffect(Unit) {
                    onDispose { PetalContentSnapshot.clear() }
                }
                val sp = PreferenceManager.getDefaultSharedPreferences(activity)
                val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                val dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)
                val isAmoled = sp.getBoolean("sp_amoled", false)

                val appFont = remember(fontName) { com.petal.browser.ui.theme.AppFont.fromName(fontName) }
                val colorStyle = remember(styleName) {
                    try { com.petal.browser.ui.theme.ColorStyle.valueOf(styleName) } catch (e: Exception) { com.petal.browser.ui.theme.ColorStyle.TONAL_SPOT }
                }

                PetalExpressiveTheme(
                    dynamicColor = dynamicColor,
                    useAmoled = isAmoled,
                    appFont = appFont,
                    colorStyle = colorStyle,
                    paletteId = paletteId
                ) {
                    PetalBrowserStoreScreen(
                        onDismiss = onBackPress,
                        backgroundSnapshot = snapshotBitmap
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalBrowserStoreScreen(
    onDismiss: () -> Unit,
    backgroundSnapshot: androidx.compose.ui.graphics.ImageBitmap? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val installedExtensions by PetalExtensionManager.extensions.collectAsState()
    val isExtensionBusy by PetalExtensionManager.busy.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(PetalStoreCatalog.AddonCategory.ALL) }

    var installedScriptIds by remember { mutableStateOf(PetalStoreCatalog.getInstalledScriptIds(context)) }
    var subscribedFilterIds by remember { mutableStateOf(PetalStoreCatalog.getSubscribedFilterListIds(context)) }
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var currentDefaultEngineIndex by remember {
        mutableIntStateOf(sp.getString("sp_search_engine", "0")?.toIntOrNull() ?: 0)
    }

    LaunchedEffect(Unit) {
        PetalExtensionManager.attach(context)
        PetalExtensionManager.refresh()
    }

    val tabItems = remember {
        listOf(
            PetalConnectedButtonItem("Add-ons", Icons.Rounded.Extension),
            PetalConnectedButtonItem("Scripts", Icons.Rounded.Code),
            PetalConnectedButtonItem("Search", Icons.Rounded.Search),
            PetalConnectedButtonItem("Filters", Icons.Rounded.Shield)
        )
    }

    PetalPredictiveBackSurface(enabled = true, onBack = onDismiss) {
        PetalScreenWrapper(backgroundSnapshot = backgroundSnapshot) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = {
                    PetalSnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    M3ExpressiveVariableBackground(modifier = Modifier.fillMaxSize(), pageSeed = "petal_browser_store")

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Expressive Header
                        ExpressiveHeader(
                            title = stringResource(R.string.ui_browser_store_title),
                            subtitle = stringResource(R.string.ui_browser_store_subtitle),
                            onBack = onDismiss
                        )

                        // Containment Navigation: Connected Button Group
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            PetalConnectedButtonGroup(
                                items = tabItems,
                                selectedIndex = selectedTabIndex,
                                onSelect = { index ->
                                    PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                                    selectedTabIndex = index
                                }
                            )
                        }

                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource(R.string.ui_store_search_hint)) },
                            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.primary) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Rounded.Close, null)
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        )

                        // Content Pages Area with weight(1f) to ensure full layout reach
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            AnimatedContent(
                                targetState = selectedTabIndex,
                                transitionSpec = {
                                    fadeIn() togetherWith fadeOut()
                                },
                                label = "StoreTabContentTransition"
                            ) { tabIndex ->
                                when (tabIndex) {
                                    0 -> StoreExtensionsPage(
                                        searchQuery = searchQuery,
                                        selectedCategory = selectedCategory,
                                        onSelectCategory = { selectedCategory = it },
                                        installedExtensions = installedExtensions,
                                        isBusy = isExtensionBusy,
                                        onInstall = { addon ->
                                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.HEAVY_CLICK, 0.8f)
                                            PetalExtensionManager.install(addon.downloadUrl) { success, _ ->
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        if (success) "Added ${addon.name} to Petal" else "Failed to install ${addon.name}"
                                                    )
                                                }
                                            }
                                        }
                                    )
                                    1 -> StoreScriptsPage(
                                        searchQuery = searchQuery,
                                        installedScriptIds = installedScriptIds,
                                        onToggleScript = { script, install ->
                                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                                            PetalStoreCatalog.setScriptInstalled(context, script.id, install)
                                            installedScriptIds = PetalStoreCatalog.getInstalledScriptIds(context)
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(
                                                    if (install) "Enabled \"${script.name}\"" else "Disabled \"${script.name}\""
                                                )
                                            }
                                        }
                                    )
                                    2 -> StoreSearchEnginesPage(
                                        searchQuery = searchQuery,
                                        defaultEngineIndex = currentDefaultEngineIndex,
                                        onSetDefaultEngine = { engine ->
                                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.6f)
                                            sp.edit().putString("sp_search_engine", engine.id.toString()).apply()
                                            currentDefaultEngineIndex = engine.id
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Set default search engine to ${engine.name}")
                                            }
                                        }
                                    )
                                    3 -> StoreFilterListsPage(
                                        searchQuery = searchQuery,
                                        subscribedFilterIds = subscribedFilterIds,
                                        onToggleFilterList = { filter, subscribe ->
                                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                                            PetalStoreCatalog.setFilterListSubscribed(context, filter.id, subscribe)
                                            subscribedFilterIds = PetalStoreCatalog.getSubscribedFilterListIds(context)
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(
                                                    if (subscribe) "Subscribed to ${filter.name}" else "Unsubscribed from ${filter.name}"
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
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Subpage 1: Extensions / Add-ons
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StoreExtensionsPage(
    searchQuery: String,
    selectedCategory: PetalStoreCatalog.AddonCategory,
    onSelectCategory: (PetalStoreCatalog.AddonCategory) -> Unit,
    installedExtensions: List<PetalExtensionManager.InstalledExtension>,
    isBusy: Boolean,
    onInstall: (PetalStoreCatalog.StoreAddon) -> Unit
) {
    val isSearching = searchQuery.isNotBlank()
    val allAddons = remember { PetalStoreCatalog.storeAddons }

    // When searching or specific category picked, use flattened list
    val isCategorizedView = !isSearching && selectedCategory == PetalStoreCatalog.AddonCategory.ALL

    // Group addons by category for structured sections
    val categorySections = remember(allAddons) {
        listOf(
            Triple(PetalStoreCatalog.AddonCategory.BLOCKERS, "Ad Blockers & Filters", Icons.Rounded.Shield),
            Triple(PetalStoreCatalog.AddonCategory.PRIVACY, "Privacy & Security", Icons.Rounded.Security),
            Triple(PetalStoreCatalog.AddonCategory.MEDIA, "Media & Streaming", Icons.Rounded.PlayCircle),
            Triple(PetalStoreCatalog.AddonCategory.TOOLS, "Utilities & Productivity", Icons.Rounded.Construction)
        )
    }

    val filteredAddons = remember(searchQuery, selectedCategory) {
        allAddons.filter { addon ->
            val matchCategory = (selectedCategory == PetalStoreCatalog.AddonCategory.ALL || addon.category == selectedCategory)
            val matchQuery = searchQuery.isBlank() ||
                addon.name.contains(searchQuery, ignoreCase = true) ||
                addon.description.contains(searchQuery, ignoreCase = true)
            matchCategory && matchQuery
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Category Filter Chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(PetalStoreCatalog.AddonCategory.values()) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { onSelectCategory(category) },
                    label = { Text(category.displayName, fontWeight = if (selectedCategory == category) FontWeight.Bold else FontWeight.Medium) },
                    shape = RoundedCornerShape(14.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Intro Card when browsing all categories without search
            if (isCategorizedView) {
                item {
                    PetalHeroCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            PetalGroupIconBadge(
                                icon = Icons.Rounded.Extension,
                                variant = PetalBadgeVariant.PRIMARY,
                                size = 48.dp,
                                iconSize = 26.dp,
                                shape = PetalMaterialShapes.Flower.toShape()
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Firefox WebExtensions",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Curated add-ons verified for Petal GeckoView with 1-tap installation.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Render each categorized PetalSettingsSection
                categorySections.forEach { (category, title, icon) ->
                    val addonsInCategory = allAddons.filter { it.category == category }
                    if (addonsInCategory.isNotEmpty()) {
                        item {
                            PetalSettingsSection(
                                title = title,
                                icon = icon
                            ) {
                                addonsInCategory.forEachIndexed { index, addon ->
                                    val isInstalled = installedExtensions.any {
                                        it.id.equals(addon.id, ignoreCase = true) ||
                                        it.name.equals(addon.name, ignoreCase = true) ||
                                        (it.amoListingUrl != null && it.amoListingUrl.contains(addon.amoSlug, ignoreCase = true))
                                    }
                                    val position = petalGroupPositionFor(index, addonsInCategory.size)
                                    StoreAddonRow(
                                        addon = addon,
                                        position = position,
                                        isInstalled = isInstalled,
                                        isBusy = isBusy,
                                        onInstall = onInstall
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Flattened search or filtered category view
                item {
                    PetalSettingsSection(
                        title = if (isSearching) "Search Results (${filteredAddons.size})" else selectedCategory.displayName,
                        icon = if (isSearching) Icons.Rounded.Search else Icons.Rounded.Extension
                    ) {
                        if (filteredAddons.isEmpty()) {
                            StoreEmptyStateCard(
                                message = "No matching add-ons found",
                                icon = Icons.Rounded.ExtensionOff
                            )
                        } else {
                            filteredAddons.forEachIndexed { index, addon ->
                                val isInstalled = installedExtensions.any {
                                    it.id.equals(addon.id, ignoreCase = true) ||
                                    it.name.equals(addon.name, ignoreCase = true) ||
                                    (it.amoListingUrl != null && it.amoListingUrl.contains(addon.amoSlug, ignoreCase = true))
                                }
                                val position = petalGroupPositionFor(index, filteredAddons.size)
                                StoreAddonRow(
                                    addon = addon,
                                    position = position,
                                    isInstalled = isInstalled,
                                    isBusy = isBusy,
                                    onInstall = onInstall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreAddonRow(
    addon: PetalStoreCatalog.StoreAddon,
    position: PetalGroupPosition,
    isInstalled: Boolean,
    isBusy: Boolean,
    onInstall: (PetalStoreCatalog.StoreAddon) -> Unit
) {
    val iconUrl = PetalCuratedExtensionsData.getAmoIconUrl(addon.amoSlug) ?: PetalCuratedExtensionsData.getAmoIconUrl(addon.id)
    val visual = PetalCuratedExtensionsData.getVisual(addon.amoSlug)

    PetalGroupListRow(
        position = position,
        onClick = { if (!isInstalled && !isBusy) onInstall(addon) },
        leading = {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                if (!iconUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(iconUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = addon.name,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Icon(
                        imageVector = visual?.icon ?: Icons.Rounded.Extension,
                        contentDescription = null,
                        tint = visual?.accentColor ?: MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = addon.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(Icons.Rounded.Star, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(11.dp))
                        Text(addon.rating.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
            Text(
                text = addon.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailing = {
            if (isInstalled) {
                FilledTonalButton(
                    onClick = {},
                    enabled = false,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.ui_store_installed), fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = { onInstall(addon) },
                    enabled = !isBusy,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(stringResource(R.string.ui_store_install), fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Subpage 2: Petal Scripts ("Toppings")
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StoreScriptsPage(
    searchQuery: String,
    installedScriptIds: Set<String>,
    onToggleScript: (PetalStoreCatalog.StoreScript, Boolean) -> Unit
) {
    val filteredScripts = remember(searchQuery) {
        PetalStoreCatalog.storeScripts.filter { script ->
            searchQuery.isBlank() ||
                script.name.contains(searchQuery, ignoreCase = true) ||
                script.description.contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            PetalHeroCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    PetalGroupIconBadge(
                        icon = Icons.Rounded.Verified,
                        variant = PetalBadgeVariant.SECONDARY,
                        size = 48.dp,
                        iconSize = 26.dp,
                        shape = PetalMaterialShapes.Flower.toShape()
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.ui_store_verified_by_petal),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = stringResource(R.string.ui_store_scripts_catalog_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            PetalSettingsSection(
                title = "Verified Userscripts (${filteredScripts.size})",
                icon = Icons.Rounded.Code
            ) {
                if (filteredScripts.isEmpty()) {
                    StoreEmptyStateCard(
                        message = "No matching scripts found",
                        icon = Icons.Rounded.CodeOff
                    )
                } else {
                    filteredScripts.forEachIndexed { index, script ->
                        val isInstalled = installedScriptIds.contains(script.id)
                        val isVerified = remember(script.id) { script.verifyIntegrity() }
                        val position = petalGroupPositionFor(index, filteredScripts.size)

                        PetalGroupListRow(
                            position = position,
                            onClick = { onToggleScript(script, !isInstalled) },
                            leading = {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isInstalled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.Javascript,
                                        contentDescription = null,
                                        tint = if (isInstalled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            },
                            content = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = script.name,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (isVerified) {
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = "SHA-256",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = script.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailing = {
                                IconSwitch(
                                    checked = isInstalled,
                                    icon = Icons.Rounded.Code,
                                    onCheckedChange = { onToggleScript(script, it) }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Subpage 3: Search Engines
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StoreSearchEnginesPage(
    searchQuery: String,
    defaultEngineIndex: Int,
    onSetDefaultEngine: (PetalStoreCatalog.StoreSearchEngine) -> Unit
) {
    val filteredEngines = remember(searchQuery) {
        PetalStoreCatalog.storeSearchEngines.filter { engine ->
            searchQuery.isBlank() ||
                engine.name.contains(searchQuery, ignoreCase = true) ||
                engine.description.contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            PetalHeroCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    PetalGroupIconBadge(
                        icon = Icons.Rounded.Search,
                        variant = PetalBadgeVariant.TERTIARY,
                        size = 48.dp,
                        iconSize = 26.dp,
                        shape = PetalMaterialShapes.Bun.toShape()
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Privacy Search Hub",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = stringResource(R.string.ui_store_search_catalog_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            PetalSettingsSection(
                title = "Independent Search Engines",
                icon = Icons.Rounded.TravelExplore
            ) {
                if (filteredEngines.isEmpty()) {
                    StoreEmptyStateCard(
                        message = "No matching search engines found",
                        icon = Icons.Rounded.SearchOff
                    )
                } else {
                    filteredEngines.forEachIndexed { index, engine ->
                        val isDefault = defaultEngineIndex == engine.id
                        val position = petalGroupPositionFor(index, filteredEngines.size)

                        PetalGroupListRow(
                            position = position,
                            selected = isDefault,
                            onClick = { onSetDefaultEngine(engine) },
                            leading = {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isDefault) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.TravelExplore,
                                        contentDescription = null,
                                        tint = if (isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            },
                            content = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = engine.name,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Text(
                                            text = "Privacy: ${engine.privacyScore}",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = engine.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailing = {
                                RadioButton(
                                    selected = isDefault,
                                    onClick = { onSetDefaultEngine(engine) }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Subpage 4: Filter Lists
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StoreFilterListsPage(
    searchQuery: String,
    subscribedFilterIds: Set<String>,
    onToggleFilterList: (PetalStoreCatalog.StoreFilterList, Boolean) -> Unit
) {
    val filteredLists = remember(searchQuery) {
        PetalStoreCatalog.storeFilterLists.filter { list ->
            searchQuery.isBlank() ||
                list.name.contains(searchQuery, ignoreCase = true) ||
                list.description.contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            PetalHeroCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    PetalGroupIconBadge(
                        icon = Icons.Rounded.Shield,
                        variant = PetalBadgeVariant.SURFACE_TONAL,
                        size = 48.dp,
                        iconSize = 26.dp,
                        shape = PetalMaterialShapes.Cookie6Sided.toShape()
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Petal Shield Filters",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.ui_store_filter_catalog_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            PetalSettingsSection(
                title = "Blocklist Subscriptions (${filteredLists.size})",
                icon = Icons.Rounded.FilterList
            ) {
                if (filteredLists.isEmpty()) {
                    StoreEmptyStateCard(
                        message = "No matching filter lists found",
                        icon = Icons.Rounded.FilterListOff
                    )
                } else {
                    filteredLists.forEachIndexed { index, filter ->
                        val isSubscribed = subscribedFilterIds.contains(filter.id)
                        val position = petalGroupPositionFor(index, filteredLists.size)

                        PetalGroupListRow(
                            position = position,
                            onClick = { onToggleFilterList(filter, !isSubscribed) },
                            leading = {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isSubscribed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.FilterList,
                                        contentDescription = null,
                                        tint = if (isSubscribed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            },
                            content = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = filter.name,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.surfaceContainerLowest
                                    ) {
                                        Text(
                                            text = filter.ruleCount,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = filter.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailing = {
                                IconSwitch(
                                    checked = isSubscribed,
                                    icon = Icons.Rounded.Shield,
                                    onCheckedChange = { onToggleFilterList(filter, it) }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Clean Material 3 Expressive empty state card for store pages
 */
@Composable
private fun StoreEmptyStateCard(
    message: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        shape = petalGroupShape(PetalGroupPosition.SINGLE),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
