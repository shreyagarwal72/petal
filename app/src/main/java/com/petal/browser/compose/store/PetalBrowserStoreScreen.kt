package com.petal.browser.compose.store

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.containment.*
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.PetalMaterialShapes
import kotlinx.coroutines.launch

/**
 * PetalBrowserStoreScreen
 * ─────────────────────────────────────────────────────────────────────────
 * Complete Material 3 Expressive Browser Store & Hub:
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

                        // Store Navigation Tabs
                        PrimaryTabRow(
                            selectedTabIndex = selectedTabIndex,
                            containerColor = Color.Transparent,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) {
                            Tab(
                                selected = selectedTabIndex == 0,
                                onClick = {
                                    PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                                    selectedTabIndex = 0
                                },
                                text = { Text(stringResource(R.string.ui_store_tab_extensions), fontWeight = FontWeight.SemiBold) },
                                icon = { Icon(Icons.Rounded.Extension, null, modifier = Modifier.size(18.dp)) }
                            )
                            Tab(
                                selected = selectedTabIndex == 1,
                                onClick = {
                                    PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                                    selectedTabIndex = 1
                                },
                                text = { Text(stringResource(R.string.ui_store_tab_scripts), fontWeight = FontWeight.SemiBold) },
                                icon = { Icon(Icons.Rounded.Code, null, modifier = Modifier.size(18.dp)) }
                            )
                            Tab(
                                selected = selectedTabIndex == 2,
                                onClick = {
                                    PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                                    selectedTabIndex = 2
                                },
                                text = { Text(stringResource(R.string.ui_store_tab_search), fontWeight = FontWeight.SemiBold) },
                                icon = { Icon(Icons.Rounded.Search, null, modifier = Modifier.size(18.dp)) }
                            )
                            Tab(
                                selected = selectedTabIndex == 3,
                                onClick = {
                                    PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                                    selectedTabIndex = 3
                                },
                                text = { Text(stringResource(R.string.ui_store_tab_filters), fontWeight = FontWeight.SemiBold) },
                                icon = { Icon(Icons.Rounded.Shield, null, modifier = Modifier.size(18.dp)) }
                            )
                        }

                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource(R.string.ui_store_search_hint)) },
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Rounded.Clear, null)
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        )

                        // Content Pages
                        when (selectedTabIndex) {
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
    val filteredAddons = remember(searchQuery, selectedCategory) {
        PetalStoreCatalog.storeAddons.filter { addon ->
            val matchCategory = (selectedCategory == PetalStoreCatalog.AddonCategory.ALL || addon.category == selectedCategory)
            val matchQuery = searchQuery.isBlank() ||
                addon.name.contains(searchQuery, ignoreCase = true) ||
                addon.description.contains(searchQuery, ignoreCase = true)
            matchCategory && matchQuery
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Category Pills
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(PetalStoreCatalog.AddonCategory.values()) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { onSelectCategory(category) },
                    label = { Text(category.displayName) },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(filteredAddons, key = { _, it -> it.id }) { index, addon ->
                val isInstalled = installedExtensions.any {
                    it.id.equals(addon.id, ignoreCase = true) ||
                    it.name.equals(addon.name, ignoreCase = true) ||
                    (it.amoListingUrl != null && it.amoListingUrl.contains(addon.amoSlug, ignoreCase = true))
                }
                val iconUrl = PetalCuratedExtensionsData.getAmoIconUrl(addon.amoSlug) ?: PetalCuratedExtensionsData.getAmoIconUrl(addon.id)
                val visual = PetalCuratedExtensionsData.getVisual(addon.amoSlug)

                PetalGroupListRow(
                    position = petalGroupPositionFor(index, filteredAddons.size),
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(addon.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.tertiaryContainer) {
                                Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Icon(Icons.Rounded.Star, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(11.dp))
                                    Text(addon.rating.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                }
                            }
                        }
                        Text(addon.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                    trailing = {
                        if (isInstalled) {
                            FilledTonalButton(onClick = {}, enabled = false, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                                Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.ui_store_installed))
                            }
                        } else {
                            Button(
                                onClick = { onInstall(addon) },
                                enabled = !isBusy,
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                            ) {
                                Text(stringResource(R.string.ui_store_install), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                )
            }
        }
    }
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

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).clip(PetalMaterialShapes.Flower.toShape()).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ui_store_verified_by_petal), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.ui_store_scripts_catalog_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(filteredScripts, key = { _, it -> it.id }) { index, script ->
                val isInstalled = installedScriptIds.contains(script.id)
                val isVerified = remember(script.id) { script.verifyIntegrity() }

                PetalGroupListRow(
                    position = petalGroupPositionFor(index, filteredScripts.size),
                    onClick = { onToggleScript(script, !isInstalled) },
                    leading = {
                        Box(
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Javascript, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(26.dp))
                        }
                    },
                    content = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(script.name, fontWeight = FontWeight.SemiBold)
                            if (isVerified) {
                                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                                    Text("SHA-256", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                        Text(script.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                    trailing = {
                        Switch(
                            checked = isInstalled,
                            onCheckedChange = { onToggleScript(script, it) }
                        )
                    }
                )
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

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).clip(PetalMaterialShapes.Bun.toShape()).background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(24.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Privacy Search Hub", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.ui_store_search_catalog_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(filteredEngines, key = { _, it -> it.id }) { index, engine ->
                val isDefault = defaultEngineIndex == engine.id

                PetalGroupListRow(
                    position = petalGroupPositionFor(index, filteredEngines.size),
                    selected = isDefault,
                    onClick = { onSetDefaultEngine(engine) },
                    leading = {
                        Box(
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if (isDefault) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.TravelExplore, null, tint = if (isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        }
                    },
                    content = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(engine.name, fontWeight = FontWeight.SemiBold)
                            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text("Privacy: ${engine.privacyScore}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        Text(engine.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
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

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).clip(PetalMaterialShapes.Cookie6Sided.toShape()).background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(24.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Petal Shield Filters", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.ui_store_filter_catalog_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(filteredLists, key = { _, it -> it.id }) { index, filter ->
                val isSubscribed = subscribedFilterIds.contains(filter.id)

                PetalGroupListRow(
                    position = petalGroupPositionFor(index, filteredLists.size),
                    onClick = { onToggleFilterList(filter, !isSubscribed) },
                    leading = {
                        Box(
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.FilterList, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                    },
                    content = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(filter.name, fontWeight = FontWeight.SemiBold)
                            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                                Text(filter.ruleCount, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        Text(filter.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                    trailing = {
                        Switch(
                            checked = isSubscribed,
                            onCheckedChange = { onToggleFilterList(filter, it) }
                        )
                    }
                )
            }
        }
    }
}
