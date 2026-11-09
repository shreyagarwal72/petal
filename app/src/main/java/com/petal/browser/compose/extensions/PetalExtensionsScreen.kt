/*
 * PetalExtensionsScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Official Firefox/Fenix-grade Extensions Manager for Petal Browser.
 *
 * Features:
 *  - Pure Firefox WebExtensions architecture: replaced legacy Petal faux-extensions.
 *  - 3-Tab layout: Installed Add-ons, Explore Curated Catalog, Tweaks & Developer Mode.
 *  - Comprehensive Extension Details Sheet with granular permissions inspector,
 *    "Run in Private Windows" toggle, and options page navigation.
 *  - Power-user tweaks: Local .XPI package installer, Custom AMO Collection loader,
 *    and developer manifest inspector.
 *  - Responsive Material 3 Expressive and Liquid Glass design tokens.
 */

package com.petal.browser.compose.extensions

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import com.petal.browser.ui.kit.TextField
import com.petal.browser.ui.kit.IconButton
import com.petal.browser.ui.kit.TextButton
import com.petal.browser.ui.kit.FilledTonalButton
import com.petal.browser.ui.kit.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.petal.browser.R
import com.petal.browser.extensions.PetalBuiltInExtensionManager
import com.petal.browser.extensions.PetalExtensionManager
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.components.*
import com.petal.browser.ui.containment.*
import com.petal.browser.ui.theme.ExperimentalMaterial3ExpressiveApi
import com.petal.browser.ui.theme.ExpressiveButtonShape
import com.petal.browser.ui.theme.PetalExpressiveTheme
import kotlinx.coroutines.launch
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension

object PetalExtensionsBridge {
    @JvmStatic
    fun createPopupView(
        activity: ComponentActivity,
        popup: PetalExtensionManager.PendingPopup,
        onDismiss: () -> Unit
    ): android.view.View {
        val rootView = activity.findViewById<android.view.View>(android.R.id.content) ?: activity.window.decorView
        com.petal.browser.predictive.PetalContentSnapshot.capture(rootView)
        return ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val sp = PreferenceManager.getDefaultSharedPreferences(activity)
                val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                val dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)
                val isAmoled = sp.getBoolean("sp_amoled", false)

                val appFont = remember(fontName) { com.petal.browser.ui.theme.AppFont.fromName(fontName) }
                val colorStyle = remember(styleName) {
                    try { com.petal.browser.ui.theme.ColorStyle.valueOf(styleName) } catch (_: Exception) { com.petal.browser.ui.theme.ColorStyle.TONAL_SPOT }
                }

                PetalExpressiveTheme(
                    dynamicColor = dynamicColor,
                    useAmoled = isAmoled,
                    appFont = appFont,
                    colorStyle = colorStyle,
                    paletteId = paletteId
                ) {
                    PetalExtensionPopupScreen(
                        popup = popup,
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }

    @JvmStatic
    fun createExtensionsView(
        activity: ComponentActivity,
        onBackPress: () -> Unit
    ): android.view.View {
        val rootView = activity.findViewById<android.view.View>(android.R.id.content) ?: activity.window.decorView
        com.petal.browser.predictive.PetalContentSnapshot.capture(rootView)
        return ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val snapshotBitmap = remember {
                    com.petal.browser.predictive.PetalContentSnapshot.current?.asImageBitmap()
                }
                DisposableEffect(Unit) {
                    onDispose { com.petal.browser.predictive.PetalContentSnapshot.clear() }
                }
                val sp = PreferenceManager.getDefaultSharedPreferences(activity)
                val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                val dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)
                val isAmoled = sp.getBoolean("sp_amoled", false)

                val appFont = remember(fontName) { com.petal.browser.ui.theme.AppFont.fromName(fontName) }
                val colorStyle = remember(styleName) {
                    try { com.petal.browser.ui.theme.ColorStyle.valueOf(styleName) } catch (_: Exception) { com.petal.browser.ui.theme.ColorStyle.TONAL_SPOT }
                }

                PetalExpressiveTheme(
                    dynamicColor = dynamicColor,
                    useAmoled = isAmoled,
                    appFont = appFont,
                    colorStyle = colorStyle,
                    paletteId = paletteId
                ) {
                    PetalExtensionsScreen(
                        backgroundSnapshot = snapshotBitmap,
                        onDismiss = onBackPress
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PetalExtensionsScreen(
    backgroundSnapshot: androidx.compose.ui.graphics.ImageBitmap? = null,
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = remember(context) { PetalHapticEngine.getInstance(context) }
    val extensions by PetalExtensionManager.extensions.collectAsStateWithLifecycle()
    val activeTabActions by PetalExtensionManager.activeTabActionStates.collectAsStateWithLifecycle()

    val firefoxExtensions = remember(extensions) {
        extensions.filter { !PetalBuiltInExtensionManager.isBuiltIn(it.id) }
    }
    val pendingPrompt by PetalExtensionManager.pendingPrompt.collectAsStateWithLifecycle()
    val pendingPopup by PetalExtensionManager.pendingPopup.collectAsStateWithLifecycle()
    val busy by PetalExtensionManager.busy.collectAsStateWithLifecycle()
    val lastError by PetalExtensionManager.lastError.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0: Installed, 1: Explore, 2: Tweaks
    var showAddSheet by remember { mutableStateOf(false) }
    var detailExtensionId by remember { mutableStateOf<String?>(null) }
    var showXpiPicker by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        PetalExtensionManager.attach(context)
        PetalExtensionManager.refresh()
    }

    LaunchedEffect(lastError) {
        val message = lastError
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
        }
    }

    val systemXpiPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            PetalExtensionManager.installFromContentUri(context, it) { success, msg ->
                if (success) {
                    coroutineScope.launch { snackbarHostState.showSnackbar(msg ?: "Extension installed") }
                }
            }
        }
    }

    val hasSubOverlay = showAddSheet || detailExtensionId != null || showXpiPicker
    val handleDismiss: () -> Unit = {
        when {
            showAddSheet -> showAddSheet = false
            detailExtensionId != null -> detailExtensionId = null
            showXpiPicker -> showXpiPicker = false
            else -> onDismiss()
        }
    }

    if (hasSubOverlay) {
        androidx.activity.compose.BackHandler(enabled = true) {
            handleDismiss()
        }
    }

    com.petal.browser.predictive.PetalPredictiveBackSurface(
        enabled = !hasSubOverlay,
        onBack = onDismiss
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            com.petal.browser.predictive.PetalScreenWrapper(
                isBehind = false,
                backgroundSnapshot = backgroundSnapshot,
                modifier = Modifier.fillMaxSize()
            ) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = {
                        com.petal.browser.ui.containment.PetalSnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.padding(16.dp)
                        )
                    },
                    floatingActionButton = {
                        if (selectedTabIndex == 0) {
                            PetalLiquidButton(
                                onClick = { showAddSheet = true },
                                variant = PetalButtonVariant.Filled,
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.ui_install_from_amo_xpi))
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        M3ExpressiveVariableBackground(modifier = Modifier.fillMaxSize(), pageSeed = "extensions_page")

                        Column(modifier = Modifier.fillMaxSize()) {
                            ExpressiveHeader(
                                title = stringResource(R.string.ui_extensions),
                                subtitle = when (selectedTabIndex) {
                                    0 -> "${firefoxExtensions.size} Firefox add-on${if (firefoxExtensions.size == 1) "" else "s"} installed"
                                    1 -> "Explore curated add-ons from Mozilla AMO"
                                    else -> "Developer tools, local XPI & custom collections"
                                },
                                onBack = { if (hasSubOverlay) handleDismiss() else onDismiss() },
                                actions = {
                                    FilledTonalIconButton(
                                        shape = ExpressiveButtonShape,
                                        onClick = {
                                            haptic.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.7f)
                                            (context as? com.petal.browser.activity.BrowserActivity)?.addAlbum(
                                                "Firefox Add-ons",
                                                PetalExtensionManager.amoAndroidBrowseUrl,
                                                true
                                            )
                                        },
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    ) {
                                        Icon(Icons.Rounded.Storefront, contentDescription = stringResource(R.string.ui_find_firefox_extensions))
                                    }
                                }
                            )

                            // 3-Tab Firefox Parity Bar
                            PrimaryTabRow(
                                selectedTabIndex = selectedTabIndex,
                                containerColor = Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Tab(
                                    selected = selectedTabIndex == 0,
                                    onClick = {
                                        haptic.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.6f)
                                        selectedTabIndex = 0
                                    },
                                    text = { Text("Installed (${firefoxExtensions.size})", fontWeight = FontWeight.SemiBold) },
                                    icon = { Icon(Icons.Rounded.Extension, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                )
                                Tab(
                                    selected = selectedTabIndex == 1,
                                    onClick = {
                                        haptic.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.6f)
                                        selectedTabIndex = 1
                                    },
                                    text = { Text("Explore", fontWeight = FontWeight.SemiBold) },
                                    icon = { Icon(Icons.Rounded.Storefront, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                )
                                Tab(
                                    selected = selectedTabIndex == 2,
                                    onClick = {
                                        haptic.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.6f)
                                        selectedTabIndex = 2
                                    },
                                    text = { Text("Tweaks", fontWeight = FontWeight.SemiBold) },
                                    icon = { Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                )
                            }

                            if (busy) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }

                            when (selectedTabIndex) {
                                0 -> InstalledExtensionsTab(
                                    innerPadding = innerPadding,
                                    extensions = firefoxExtensions,
                                    activeTabActions = activeTabActions,
                                    onToggleEnabled = { ext, enabled ->
                                        PetalExtensionManager.setEnabled(ext.raw, enabled)
                                    },
                                    onOpen = { ext ->
                                        PetalExtensionManager.triggerBrowserAction(ext.id, context)
                                    },
                                    onShowDetails = { ext ->
                                        detailExtensionId = ext.id
                                    },
                                    onOpenSettings = { ext ->
                                        PetalExtensionManager.openOptionsPage(ext.id, context)
                                    },
                                    onUninstall = { ext ->
                                        val extName = ext.name
                                        val fallbackUrl = ext.amoListingUrl ?: ext.homepageUrl
                                        PetalExtensionManager.uninstall(ext.raw)
                                        coroutineScope.launch {
                                            val res = snackbarHostState.showSnackbar(
                                                message = "Uninstalled \"$extName\"",
                                                actionLabel = if (!fallbackUrl.isNullOrBlank()) "Undo" else null,
                                                duration = SnackbarDuration.Short
                                            )
                                            if (res == SnackbarResult.ActionPerformed && !fallbackUrl.isNullOrBlank()) {
                                                PetalExtensionManager.install(fallbackUrl)
                                            }
                                        }
                                    },
                                    onExploreClick = { selectedTabIndex = 1 }
                                )
                                1 -> ExploreExtensionsTab(
                                    innerPadding = innerPadding,
                                    installedExtensions = firefoxExtensions
                                )
                                2 -> TweaksExtensionsTab(
                                    innerPadding = innerPadding,
                                    installedExtensions = firefoxExtensions,
                                    onRequestFileImport = { showXpiPicker = true }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        AddExtensionSheet(
            onDismiss = { showAddSheet = false },
            onInstall = { url ->
                showAddSheet = false
                PetalExtensionManager.install(url) { _, _ -> }
            },
            onInstallFile = { uri ->
                showAddSheet = false
                PetalExtensionManager.installFromContentUri(context, uri) { _, _ -> }
            },
            onRequestFileImport = {
                showAddSheet = false
                showXpiPicker = true
            },
            onOpenStore = {
                showAddSheet = false
                selectedTabIndex = 1
            }
        )
    }

    if (showXpiPicker) {
        com.petal.browser.compose.file.PetalFilePickerScreen(
            mimeTypes = arrayOf("*/*"),
            onDismissRequest = { showXpiPicker = false },
            onFileSelected = { file ->
                showXpiPicker = false
                PetalExtensionManager.installFromContentUri(context, Uri.fromFile(file)) { _, _ -> }
            },
            onBrowseSystemFallback = {
                showXpiPicker = false
                systemXpiPicker.launch(arrayOf("*/*"))
            }
        )
    }

    detailExtensionId?.let { id ->
        val ext = firefoxExtensions.find { it.id == id }
        if (ext != null) {
            ExtensionDetailSheet(
                extension = ext,
                onDismiss = { detailExtensionId = null },
                onTogglePrivate = { allowed -> PetalExtensionManager.setAllowedInPrivateBrowsing(ext.raw, allowed) },
                onToggleEnabled = { enabled -> PetalExtensionManager.setEnabled(ext.raw, enabled) },
                onOpenSettings = {
                    detailExtensionId = null
                    onDismiss()
                    PetalExtensionManager.openOptionsPage(ext.id, context)
                },
                onUninstall = {
                    val extName = ext.name
                    val fallbackUrl = ext.amoListingUrl ?: ext.homepageUrl
                    PetalExtensionManager.uninstall(ext.raw)
                    detailExtensionId = null
                    coroutineScope.launch {
                        val res = snackbarHostState.showSnackbar(
                            message = "Uninstalled \"$extName\"",
                            actionLabel = if (!fallbackUrl.isNullOrBlank()) "Undo" else null,
                            duration = SnackbarDuration.Short
                        )
                        if (res == SnackbarResult.ActionPerformed && !fallbackUrl.isNullOrBlank()) {
                            PetalExtensionManager.install(fallbackUrl)
                        }
                    }
                },
                onOpenLink = { linkTitle, url ->
                    detailExtensionId = null
                    onDismiss()
                    val activity = context as? com.petal.browser.activity.BrowserActivity
                    if (activity != null) {
                        activity.addAlbum(linkTitle, url, true)
                    } else {
                        try {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (ignored: Exception) {}
                    }
                },
                onOpenPopup = {
                    detailExtensionId = null
                    PetalExtensionManager.triggerBrowserAction(ext.id, context)
                }
            )
        } else {
            detailExtensionId = null
        }
    }

    pendingPrompt?.let { prompt ->
        InstallPermissionDialog(prompt = prompt)
    }

    pendingPopup?.let { popup ->
        PetalExtensionPopupScreen(
            popup = popup,
            onDismiss = {
                PetalExtensionManager.dismissPopup()
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TAB 0: Installed Extensions Page
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InstalledExtensionsTab(
    innerPadding: PaddingValues,
    extensions: List<PetalExtensionManager.InstalledExtension>,
    activeTabActions: Map<String, PetalExtensionManager.ExtensionActionState>,
    onToggleEnabled: (PetalExtensionManager.InstalledExtension, Boolean) -> Unit,
    onOpen: (PetalExtensionManager.InstalledExtension) -> Unit,
    onShowDetails: (PetalExtensionManager.InstalledExtension) -> Unit,
    onOpenSettings: (PetalExtensionManager.InstalledExtension) -> Unit,
    onUninstall: (PetalExtensionManager.InstalledExtension) -> Unit,
    onExploreClick: () -> Unit
) {
    val addonsListState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = addonsListState,
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (extensions.isEmpty()) {
                item {
                    PetalHeroCard(
                        shape = PetalContainmentShapes.HeroInner,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(28.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ExtensionOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Text(
                                text = stringResource(R.string.ui_no_firefox_add_ons_installed),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Install extensions to block ads, protect privacy, customize webpages, and sync credentials just like on desktop Firefox.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            FilledTonalButton(
                                onClick = onExploreClick,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Storefront, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Explore Recommended Add-ons")
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(extensions, key = { _, ext -> ext.id }) { index, ext ->
                    val actionState = activeTabActions[ext.id]
                    ExtensionRow(
                        animationIndex = index,
                        position = petalGroupPositionFor(index, extensions.size),
                        extension = ext,
                        actionState = actionState,
                        onToggleEnabled = { enabled -> onToggleEnabled(ext, enabled) },
                        onOpen = { onOpen(ext) },
                        onShowDetails = { onShowDetails(ext) },
                        onOpenSettings = { onOpenSettings(ext) },
                        onUninstall = { onUninstall(ext) }
                    )
                }
            }
        }

        PetalExpressiveScrollBar(
            listState = addonsListState,
            modifier = Modifier.align(Alignment.CenterEnd),
            dragLabelProvider = { index ->
                extensions.getOrNull(index)?.name?.let { com.petal.browser.ui.components.extractFastScrollGlyph(it) }
            }
        )
    }
}

@Composable
private fun ExtensionRow(
    animationIndex: Int = 0,
    position: PetalGroupPosition = PetalGroupPosition.SINGLE,
    extension: PetalExtensionManager.InstalledExtension,
    actionState: PetalExtensionManager.ExtensionActionState? = null,
    onToggleEnabled: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onShowDetails: () -> Unit,
    onOpenSettings: () -> Unit,
    onUninstall: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptic = remember(context) { PetalHapticEngine.getInstance(context) }
    val iconUrl = remember(extension.id) {
        PetalCuratedExtensionsData.getAmoIconUrl(extension.id) ?: extension.amoListingUrl?.let { url ->
            val segs = url.trimEnd('/').split('/')
            segs.lastOrNull()?.let { PetalCuratedExtensionsData.getAmoIconUrl(it) }
        }
    }

    PetalGroupListRow(
        position = position,
        onClick = onShowDetails,
        modifier = Modifier.entrance(index = animationIndex),
        leading = {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                val actionIcon = actionState?.icon
                if (actionIcon != null) {
                    Image(
                        bitmap = actionIcon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(30.dp)
                    )
                } else if (extension.icon != null) {
                    Image(
                        bitmap = extension.icon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(30.dp)
                    )
                } else if (!iconUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(iconUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = extension.name,
                        modifier = Modifier.size(30.dp)
                    )
                } else {
                    Icon(
                        Icons.Rounded.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
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
                    extension.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                actionState?.badgeText?.takeIf { it.isNotBlank() }?.let { badge ->
                    val bg = actionState.badgeBackgroundColor?.takeIf { it != 0 }?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
                    val fg = actionState.badgeTextColor?.takeIf { it != 0 }?.let { Color(it) } ?: MaterialTheme.colorScheme.onPrimary
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(bg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(badge, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = fg)
                    }
                }
            }
            Text(
                "v${extension.version}" + if (!extension.enabled) " · Disabled" else if (extension.allowedInPrivateBrowsing) " · Private allowed" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconSwitch(
                    checked = extension.enabled,
                    icon = Icons.Rounded.Check,
                    onCheckedChange = onToggleEnabled
                )

                Box {
                    IconButton(
                        onClick = {
                            haptic.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.6f)
                            showMenu = true
                        }
                    ) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.ui_more_options))
                    }
                    PetalPopupMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (extension.optionsPageUrl != null) {
                            PetalPopupMenuItem(
                                text = stringResource(R.string.ui_settings),
                                leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onOpenSettings()
                                }
                            )
                        }
                        if (extension.supportsPopup) {
                            PetalPopupMenuItem(
                                text = stringResource(R.string.ui_open_extension),
                                leadingIcon = { Icon(Icons.Rounded.OpenInBrowser, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onOpen()
                                }
                            )
                        }
                        PetalPopupMenuItem(
                            text = stringResource(R.string.ui_details),
                            leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onShowDetails()
                            }
                        )
                        PetalPopupMenuItem(
                            text = stringResource(R.string.ui_remove_extension),
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onUninstall()
                            }
                        )
                    }
                }
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// TAB 1: Explore / Recommended Catalog Page
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExploreExtensionsTab(
    innerPadding: PaddingValues,
    installedExtensions: List<PetalExtensionManager.InstalledExtension>
) {
    val context = LocalContext.current
    val haptic = remember(context) { PetalHapticEngine.getInstance(context) }
    val busy by PetalExtensionManager.busy.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    val catalog = PetalExtensionManager.catalog

    val filtered = remember(searchQuery) {
        if (searchQuery.isBlank()) catalog else {
            val q = searchQuery.trim().lowercase()
            catalog.filter { it.name.lowercase().contains(q) || it.description.lowercase().contains(q) }
        }
    }
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Search Input
            item {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search verified Firefox add-ons...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }

            // AMO Directory Callout
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            (context as? com.petal.browser.activity.BrowserActivity)?.addAlbum(
                                "Firefox Add-ons",
                                PetalExtensionManager.amoAndroidBrowseUrl,
                                true
                            )
                        }
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Public, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Browse Mozilla Add-ons (AMO)", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text("Explore thousands of extensions on addons.mozilla.org", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }

            item {
                PetalSectionLabel("Curated Add-ons (${filtered.size})")
            }

            itemsIndexed(filtered, key = { _, it -> it.id }) { index, entry ->
                val installedExt = installedExtensions.find {
                    it.id.equals(entry.id, ignoreCase = true) ||
                    it.name.equals(entry.name, ignoreCase = true) ||
                    (it.amoListingUrl != null && it.amoListingUrl.contains(entry.amoSlug, ignoreCase = true))
                }
                val isInstalled = installedExt != null
                val iconUrl = PetalCuratedExtensionsData.getAmoIconUrl(entry.amoSlug) ?: PetalCuratedExtensionsData.getAmoIconUrl(entry.id)
                val visual = PetalCuratedExtensionsData.getVisual(entry.amoSlug)

                PetalGroupListRow(
                    position = petalGroupPositionFor(index, filtered.size),
                    onClick = {
                        if (!busy) {
                            if (isInstalled && installedExt != null) {
                                PetalExtensionManager.triggerBrowserAction(installedExt.id, context)
                            } else {
                                haptic.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.7f)
                                PetalExtensionManager.install(entry.downloadUrl) { _, _ -> }
                            }
                        }
                    },
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
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
                                    contentDescription = entry.name,
                                    modifier = Modifier.size(30.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = visual?.icon ?: Icons.Rounded.Extension,
                                    contentDescription = null,
                                    tint = visual?.accentColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
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
                                entry.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isInstalled) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ) {
                                    Text(
                                        text = stringResource(R.string.ui_installed),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            entry.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    trailing = {
                        if (isInstalled) {
                            Icon(Icons.Rounded.CheckCircle, "Installed", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        } else {
                            FilledTonalIconButton(
                                shape = ExpressiveButtonShape,
                                onClick = {
                                    haptic.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.7f)
                                    PetalExtensionManager.install(entry.downloadUrl) { _, _ -> }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Rounded.Download, "Install", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                )
            }
        }

        PetalExpressiveScrollBar(
            listState = listState,
            modifier = Modifier.align(Alignment.CenterEnd),
            dragLabelProvider = { index ->
                filtered.getOrNull(index)?.name?.let { com.petal.browser.ui.components.extractFastScrollGlyph(it) }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TAB 2: Tweaks, Local XPI & Developer Tools Page
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TweaksExtensionsTab(
    innerPadding: PaddingValues,
    installedExtensions: List<PetalExtensionManager.InstalledExtension>,
    onRequestFileImport: () -> Unit
) {
    val context = LocalContext.current
    val haptic = remember(context) { PetalHapticEngine.getInstance(context) }
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    var isDevMode by remember { mutableStateOf(sp.getBoolean("sp_extension_developer_mode", false)) }
    var manifestInspectExt by remember { mutableStateOf<PetalExtensionManager.InstalledExtension?>(null) }
    var showCustomCollectionDialog by remember { mutableStateOf(false) }
    var directUrlInput by remember { mutableStateOf("") }
    val busy by PetalExtensionManager.busy.collectAsStateWithLifecycle()

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = 16.dp,
            bottom = innerPadding.calculateBottomPadding() + 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Section 1: Package & File Loader
        item {
            PetalSectionLabel("Local Package Loader")
        }

        item {
            PetalHeroCard(shape = PetalContainmentShapes.HeroInner, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Install from Local File (.XPI)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "Import unlisted, developer, or self-hosted Firefox add-ons (.xpi) directly from device storage or downloads.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilledTonalButton(
                        onClick = {
                            haptic.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.7f)
                            onRequestFileImport()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Select .XPI File")
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    Text(
                        text = "Install from Direct URL",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    TextField(
                        value = directUrlInput,
                        onValueChange = { directUrlInput = it },
                        placeholder = { Text("https://.../extension.xpi") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (directUrlInput.isNotBlank()) {
                                haptic.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.7f)
                                PetalExtensionManager.install(directUrlInput.trim()) { _, _ -> }
                                directUrlInput = ""
                            }
                        },
                        enabled = directUrlInput.isNotBlank() && !busy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Install Package")
                    }
                }
            }
        }

        // Section 2: Custom AMO Collection
        item {
            PetalSectionLabel("Custom AMO Add-on Collection")
        }

        item {
            PetalHeroCard(shape = PetalContainmentShapes.HeroInner, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Mozilla Firefox Add-on Collection",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "Configure your Mozilla Account User ID and custom collection slug to browse and install any add-on from your personal collection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilledTonalButton(
                        onClick = {
                            haptic.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.7f)
                            showCustomCollectionDialog = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.CollectionsBookmark, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Configure Custom Collection")
                    }
                }
            }
        }

        // Section 3: Developer Mode & Manifest Inspection
        item {
            PetalSectionLabel("Developer & Diagnostics")
        }

        item {
            PetalHeroCard(shape = PetalContainmentShapes.HeroInner, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "WebExtension Developer Mode",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Enable manifest inspector and advanced diagnostic tools",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconSwitch(
                            checked = isDevMode,
                            icon = Icons.Rounded.Code,
                            onCheckedChange = { checked ->
                                isDevMode = checked
                                sp.edit().putBoolean("sp_extension_developer_mode", checked).apply()
                            }
                        )
                    }

                    if (isDevMode && installedExtensions.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                        Text(
                            text = "Inspect Extension Manifest",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        installedExtensions.forEach { ext ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { manifestInspectExt = ext }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(ext.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Custom Collection Dialog
    if (showCustomCollectionDialog) {
        val dialogScope = rememberCoroutineScope()
        var userId by remember { mutableStateOf(sp.getString("sp_amo_collection_user_id", "") ?: "") }
        var collectionName by remember { mutableStateOf(sp.getString("sp_amo_collection_name", "") ?: "") }
        var isLoadingCollection by remember { mutableStateOf(false) }
        var collectionError by remember { mutableStateOf<String?>(null) }
        var fetchedAddons by remember { mutableStateOf<List<PetalExtensionManager.CatalogEntry>?>(null) }

        PetalMaterialAlertDialog(
            onDismissRequest = { showCustomCollectionDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CollectionsBookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("AMO Add-on Collection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Enter your Mozilla Add-ons (AMO) account User ID and collection slug to install your favorite extensions in 1 tap.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    TextField(
                        value = userId,
                        onValueChange = { userId = it },
                        label = { Text("User ID (e.g. 12345678)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    TextField(
                        value = collectionName,
                        onValueChange = { collectionName = it },
                        label = { Text("Collection Slug (e.g. my-extensions)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (collectionError != null) {
                        Text(collectionError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    if (isLoadingCollection) {
                        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
                            PetalCircularWavyProgressIndicator(modifier = Modifier.size(24.dp), size = 24.dp)
                        }
                    }

                    fetchedAddons?.let { list ->
                        Text("Found ${list.size} add-ons in collection:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        list.forEach { addon ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(addon.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                        if (addon.description.isNotBlank()) {
                                            Text(addon.description, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    FilledTonalButton(
                                        onClick = { PetalExtensionManager.install(addon.downloadUrl) { _, _ -> } },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Install", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        sp.edit()
                            .putString("sp_amo_collection_user_id", userId.trim())
                            .putString("sp_amo_collection_name", collectionName.trim())
                            .apply()

                        isLoadingCollection = true
                        collectionError = null
                        dialogScope.launch {
                            val res = PetalExtensionManager.fetchCustomCollection(userId, collectionName)
                            isLoadingCollection = false
                            res.fold(
                                onSuccess = { list ->
                                    if (list.isEmpty()) collectionError = "Collection is empty or private."
                                    else fetchedAddons = list
                                },
                                onFailure = { err ->
                                    collectionError = err.message ?: "Failed to fetch collection"
                                }
                            )
                        }
                    },
                    enabled = userId.isNotBlank() && collectionName.isNotBlank() && !isLoadingCollection
                ) {
                    Text("Load Collection")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomCollectionDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Manifest Inspector Dialog
    manifestInspectExt?.let { ext ->
        val manifestJson = remember(ext.id) {
            PetalExtensionManager.getManifest(ext.raw)?.toString(2) ?: "Manifest could not be decoded."
        }
        PetalMaterialAlertDialog(
            onDismissRequest = { manifestInspectExt = null },
            title = {
                Text("${ext.name} manifest.json", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = manifestJson,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { manifestInspectExt = null }) {
                    Text("Close")
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EXTENSION DETAIL SHEET: Firefox-grade permissions, options & private browsing
// ─────────────────────────────────────────────────────────────────────────────

data class PermissionItem(
    val title: String,
    val description: String,
    val icon: ImageVector = Icons.Rounded.Security
)

private fun extractExtensionPermissions(extension: WebExtension): List<PermissionItem> {
    val result = mutableListOf<PermissionItem>()
    val seen = mutableSetOf<String>()

    fun add(title: String, desc: String, icon: ImageVector) {
        if (seen.add(title)) {
            result.add(PermissionItem(title, desc, icon))
        }
    }

    val rawList = mutableListOf<String>()

    try {
        val manifest = PetalExtensionManager.getManifest(extension)
        manifest?.optJSONArray("permissions")?.let { arr ->
            for (i in 0 until arr.length()) {
                val p = arr.optString(i)
                if (p.isNotBlank()) rawList.add(p)
            }
        }
        manifest?.optJSONArray("optional_permissions")?.let { arr ->
            for (i in 0 until arr.length()) {
                val p = arr.optString(i)
                if (p.isNotBlank()) rawList.add(p)
            }
        }
    } catch (_: Exception) {}

    for (perm in rawList.distinct()) {
        when {
            perm == "<all_urls>" || perm == "*://*/*" || perm == "http://*/*" || perm == "https://*/*" ->
                add("Access all website data", "Can access, read, and modify content on all websites you visit", Icons.Rounded.Language)
            perm.startsWith("*://*.") || perm.startsWith("https://*.") || perm.startsWith("http://*.") -> {
                val domain = perm.substringAfter("://*.").substringBefore("/*")
                add("Website data for $domain", "Can access and modify content on $domain and subdomains", Icons.Rounded.Public)
            }
            perm.startsWith("*://") || perm.startsWith("https://") || perm.startsWith("http://") -> {
                val domain = perm.substringAfter("://").substringBefore("/*")
                add("Website data for $domain", "Can access content on $domain", Icons.Rounded.Public)
            }
            perm == "webRequest" || perm == "webRequestBlocking" ->
                add("Web requests", "Can intercept, filter, or block network traffic", Icons.Rounded.AltRoute)
            perm == "tabs" ->
                add("Browser tabs", "Can access tab URLs, titles, and track tab switching", Icons.Rounded.Tab)
            perm == "storage" || perm == "unlimitedStorage" ->
                add("Local storage", "Can store client-side settings, rules, and cache locally", Icons.Rounded.Storage)
            perm == "cookies" ->
                add("Cookies", "Can read, set, or modify website cookies", Icons.Rounded.Cookie)
            perm == "notifications" ->
                add("Notifications", "Can display system alerts and push notifications", Icons.Rounded.Notifications)
            perm == "downloads" || perm == "downloads.open" ->
                add("Downloads", "Can manage and trigger file downloads", Icons.Rounded.Download)
            perm == "contextMenus" ->
                add("Context menus", "Can add custom items to link and selection menus", Icons.Rounded.MenuOpen)
            perm == "activeTab" || perm == "scripting" ->
                add("Active tab content", "Can run scripts and modify elements on the currently active tab", Icons.Rounded.Code)
            perm == "alarms" ->
                add("Background alarms", "Can run periodic tasks in the background", Icons.Rounded.Alarm)
            perm == "clipboardRead" || perm == "clipboardWrite" ->
                add("Clipboard", "Can read from or write to your system clipboard", Icons.Rounded.ContentPaste)
            perm == "privacy" ->
                add("Privacy settings", "Can control browser privacy protections", Icons.Rounded.Shield)
            perm == "proxy" ->
                add("Proxy management", "Can route connections through a custom proxy", Icons.Rounded.VpnLock)
            perm.isNotBlank() && !perm.startsWith("moz-") ->
                add(perm, "Feature permission declared in extension manifest", Icons.Rounded.Security)
        }
    }
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtensionDetailSheet(
    extension: PetalExtensionManager.InstalledExtension,
    onDismiss: () -> Unit,
    onTogglePrivate: (Boolean) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onUninstall: () -> Unit,
    onOpenLink: (title: String, url: String) -> Unit,
    onOpenPopup: () -> Unit
) {
    val context = LocalContext.current
    val haptic = remember(context) { PetalHapticEngine.getInstance(context) }
    var showUninstallConfirm by remember { mutableStateOf(false) }

    val permissions = remember(extension.raw) {
        extractExtensionPermissions(extension.raw)
    }

    PetalSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Icon, Title, Version, Enabled Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (extension.icon != null) {
                        Image(
                            bitmap = extension.icon.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Extension,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = extension.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Version ${extension.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconSwitch(
                    checked = extension.enabled,
                    icon = Icons.Rounded.Check,
                    onCheckedChange = onToggleEnabled
                )
            }

            // Description
            if (extension.description.isNotBlank()) {
                Text(
                    text = extension.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Action Buttons: Settings (Options Page) & Open Popup
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (extension.optionsPageUrl != null) {
                    FilledTonalButton(
                        onClick = {
                            haptic.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.7f)
                            onOpenSettings()
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ui_settings))
                    }
                }

                if (extension.enabled && extension.supportsPopup) {
                    FilledTonalButton(
                        onClick = {
                            haptic.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.7f)
                            onDismiss()
                            onOpenPopup()
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open Popup")
                    }
                }
            }

            // Private Browsing Setting
            PetalHeroCard(shape = PetalContainmentShapes.HeroInner, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(
                                text = "Run in private windows",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Allow this add-on to execute in private/incognito tabs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconSwitch(
                        checked = extension.allowedInPrivateBrowsing,
                        icon = Icons.Rounded.Check,
                        onCheckedChange = onTogglePrivate
                    )
                }
            }

            // Permissions Inspector Card
            PetalHeroCard(shape = PetalContainmentShapes.HeroInner, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text(
                            text = "Granted Permissions (${permissions.size})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    if (permissions.isEmpty()) {
                        Text(
                            text = "This add-on does not require any special host or API permissions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        permissions.forEach { perm ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(perm.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(perm.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                                    Text(perm.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // Links: AMO & Homepage
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                extension.amoListingUrl?.let { url ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenLink(extension.name, url) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("View on addons.mozilla.org", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.primary)
                            Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                extension.homepageUrl?.let { url ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenLink("${extension.name} Homepage", url) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Visit Official Homepage", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
                            Icon(Icons.Rounded.Language, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Destructive Uninstall Button
            PetalLiquidButton(
                onClick = { showUninstallConfirm = true },
                variant = PetalButtonVariant.Destructive,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ui_remove_extension))
            }
        }
    }

    if (showUninstallConfirm) {
        PetalMaterialAlertDialog(
            onDismissRequest = { showUninstallConfirm = false },
            icon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Remove ${extension.name}?") },
            text = { Text("This will completely remove the add-on and all its saved settings from Petal Browser.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUninstallConfirm = false
                        onUninstall()
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUninstallConfirm = false }) {
                    Text(stringResource(R.string.ui_cancel))
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DIALOGS: Add Extension Sheet & Permission Confirmation
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExtensionSheet(
    onDismiss: () -> Unit,
    onInstall: (String) -> Unit,
    onInstallFile: (Uri) -> Unit,
    onRequestFileImport: () -> Unit = {},
    onOpenStore: () -> Unit = {}
) {
    var manualUrl by remember { mutableStateOf("") }
    val context = LocalContext.current
    val busy by PetalExtensionManager.busy.collectAsStateWithLifecycle()
    val btnShape = RoundedCornerShape(16.dp)

    PetalSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.ui_add_firefox_extension),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.ui_installs_compatible_firefox_webextensi),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PetalLiquidButton(
                onClick = onOpenStore,
                variant = PetalButtonVariant.Filled,
                shape = btnShape,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Storefront, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Explore Recommended Catalog", fontWeight = FontWeight.Bold)
            }

            PetalLiquidButton(
                onClick = {
                    onDismiss()
                    onRequestFileImport()
                },
                enabled = !busy,
                variant = PetalButtonVariant.Tonal,
                shape = btnShape,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ui_import_local_xpi_file))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            Text(
                stringResource(R.string.ui_install_directly_from_url),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            TextField(
                value = manualUrl,
                onValueChange = { manualUrl = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.ui_https_addons_mozilla_org_addon)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            Button(
                onClick = { if (manualUrl.isNotBlank()) onInstall(manualUrl.trim()) },
                enabled = manualUrl.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = btnShape
            ) {
                if (busy) {
                    PetalCircularWavyProgressIndicator(size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.ui_install_2))
            }
        }
    }
}

@Composable
internal fun InstallPermissionDialog(prompt: PetalExtensionManager.PendingPrompt) {
    val meta = prompt.extension.metaData
    val displayName = meta?.name ?: prompt.extension.id
    PetalMaterialAlertDialog(
        onDismissRequest = { prompt.respond(false) },
        icon = { Icon(Icons.Rounded.Extension, contentDescription = null) },
        title = { Text(if (prompt.isUpdate) "Update $displayName?" else "Add $displayName?") },
        text = {
            Column {
                Text(
                    if (prompt.isUpdate) "This extension needs new permissions:" else "This extension needs permission to:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                val shown = (prompt.permissions + prompt.origins).distinct().take(8)
                if (shown.isEmpty()) {
                    Text(stringResource(R.string.ui_no_special_permissions), style = MaterialTheme.typography.bodySmall)
                } else {
                    shown.forEach { p ->
                        Text("• $p", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { prompt.respond(true) }) { Text(if (prompt.isUpdate) "Update" else "Add") }
        },
        dismissButton = {
            TextButton(onClick = { prompt.respond(false) }) { Text(stringResource(R.string.ui_cancel)) }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// EXTENSION POPUP SCREEN
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PetalExtensionPopupScreen(
    popup: PetalExtensionManager.PendingPopup,
    onDismiss: () -> Unit
) {
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    val context = LocalContext.current
    val hostActivity = context as? ComponentActivity

    key(popup.session) {
        var popupScale by remember { mutableFloatStateOf(1f) }

        var pendingTextPrompt by remember {
            mutableStateOf<GeckoSession.PromptDelegate.TextPrompt?>(null)
        }
        var textPromptInput by remember { mutableStateOf("") }
        var textPromptIsPassword by remember { mutableStateOf(false) }
        var textPromptResult by remember {
            mutableStateOf<GeckoResult<GeckoSession.PromptDelegate.PromptResponse>?>(null)
        }

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    shadowElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                IconButton(
                                    onClick = {
                                        try {
                                            popup.session.goBack()
                                        } catch (_: Throwable) {
                                            onDismiss()
                                        }
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = stringResource(R.string.ui_back),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Extension,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column(modifier = Modifier.padding(end = 4.dp)) {
                                    Text(
                                        text = popup.extensionName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = stringResource(R.string.ui_extension_popup),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        try { popup.session.reload() } catch (_: Throwable) {}
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = stringResource(R.string.ui_refresh),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { popupScale = (popupScale - 0.15f).coerceAtLeast(0.4f) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ZoomOut,
                                        contentDescription = stringResource(R.string.ui_zoom_out),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                Surface(
                                    onClick = { popupScale = 1f },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                    modifier = Modifier.widthIn(min = 38.dp)
                                ) {
                                    Text(
                                        text = "${(popupScale * 100).toInt()}%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { popupScale = (popupScale + 0.15f).coerceAtMost(4f) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ZoomIn,
                                        contentDescription = stringResource(R.string.ui_zoom_in),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        onDismiss()
                                        PetalExtensionManager.openOptionsPage(popup.extensionId, context)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Settings,
                                        contentDescription = stringResource(R.string.ui_extension_settings),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.ui_close),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .navigationBarsPadding()
                    .clipToBounds()
            ) {
                AndroidView(
                    factory = { ctx ->
                        GeckoView(ctx).apply {
                            try {
                                com.petal.browser.ui.glass.GeckoGlassBackend.applyBackend(this, ctx)
                            } catch (_: Throwable) {}
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            isClickable = true
                            isFocusable = true
                            isFocusableInTouchMode = true

                            try {
                                popup.session.setActive(true)
                            } catch (_: Throwable) {}

                            popup.session.contentDelegate = object : GeckoSession.ContentDelegate {
                                override fun onCloseRequest(session: GeckoSession) {
                                    (ctx as? ComponentActivity)?.runOnUiThread { onDismiss() }
                                }
                            }

                            popup.session.promptDelegate = object : GeckoSession.PromptDelegate {
                                override fun onAlertPrompt(
                                    session: GeckoSession,
                                    prompt: GeckoSession.PromptDelegate.AlertPrompt
                                ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
                                    GeckoResult.fromValue(prompt.dismiss())

                                override fun onButtonPrompt(
                                    session: GeckoSession,
                                    prompt: GeckoSession.PromptDelegate.ButtonPrompt
                                ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
                                    GeckoResult.fromValue(prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE))

                                override fun onTextPrompt(
                                    session: GeckoSession,
                                    prompt: GeckoSession.PromptDelegate.TextPrompt
                                ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                                    val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                                    val lowerMsg = (prompt.message ?: "").lowercase()
                                    val isPass = lowerMsg.contains("password") ||
                                        lowerMsg.contains("pin") ||
                                        lowerMsg.contains("passphrase") ||
                                        lowerMsg.contains("master") ||
                                        lowerMsg.contains("unlock") ||
                                        lowerMsg.contains("secret")
                                    (ctx as? ComponentActivity)?.runOnUiThread {
                                        pendingTextPrompt = prompt
                                        textPromptInput = prompt.defaultValue ?: ""
                                        textPromptIsPassword = isPass
                                        textPromptResult = result
                                    }
                                    return result
                                }

                                override fun onSharePrompt(
                                    session: GeckoSession,
                                    prompt: GeckoSession.PromptDelegate.SharePrompt
                                ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
                                    GeckoResult.fromValue(prompt.confirm(GeckoSession.PromptDelegate.SharePrompt.Result.SUCCESS))
                            }

                            popup.session.navigationDelegate = object : GeckoSession.NavigationDelegate {
                                override fun onLoadRequest(
                                    session: GeckoSession,
                                    request: GeckoSession.NavigationDelegate.LoadRequest
                                ): GeckoResult<AllowOrDeny>? {
                                    val uri = request.uri ?: return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                                    if (uri.startsWith("moz-extension://", true) ||
                                        uri.startsWith("resource://", true) ||
                                        uri.startsWith("about:", true) ||
                                        uri.startsWith("blob:", true) ||
                                        uri.startsWith("data:", true) ||
                                        uri.startsWith("javascript:", true)
                                    ) {
                                        return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                                    }
                                    if (uri.startsWith("http://", true) || uri.startsWith("https://", true)) {
                                        hostActivity?.let { act ->
                                            act.runOnUiThread {
                                                onDismiss()
                                                val browserActivity = act as? com.petal.browser.activity.BrowserActivity
                                                if (browserActivity != null) {
                                                    browserActivity.addAlbum(null, uri, true)
                                                } else {
                                                    try {
                                                        act.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(uri)))
                                                    } catch (_: Exception) {}
                                                }
                                            }
                                        }
                                        return GeckoResult.fromValue(AllowOrDeny.DENY)
                                    }
                                    return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                                }

                                override fun onNewSession(
                                    session: GeckoSession,
                                    uri: String
                                ): GeckoResult<GeckoSession>? {
                                    if (uri.isNotEmpty()) {
                                        hostActivity?.let { act ->
                                            act.runOnUiThread {
                                                onDismiss()
                                                val browserActivity = act as? com.petal.browser.activity.BrowserActivity
                                                if (browserActivity != null) {
                                                    browserActivity.addAlbum(null, uri, true)
                                                } else {
                                                    try {
                                                        act.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(uri)))
                                                    } catch (_: Exception) {}
                                                }
                                            }
                                        }
                                    }
                                    return null
                                }
                            }

                            setSession(popup.session)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = popupScale
                            scaleY = popupScale
                        }
                )
            }
        }

        pendingTextPrompt?.let { prompt ->
            PetalMaterialAlertDialog(
                onDismissRequest = {
                    textPromptResult?.complete(prompt.dismiss())
                    pendingTextPrompt = null
                    textPromptResult = null
                },
                title = { Text(prompt.title ?: "Extension Prompt") },
                text = {
                    Column {
                        if (!prompt.message.isNullOrBlank()) {
                            Text(prompt.message!!, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                        }
                        TextField(
                            value = textPromptInput,
                            onValueChange = { textPromptInput = it },
                            singleLine = true,
                            visualTransformation = if (textPromptIsPassword) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = if (textPromptIsPassword) androidx.compose.ui.text.input.KeyboardType.Password else androidx.compose.ui.text.input.KeyboardType.Text,
                                imeAction = androidx.compose.ui.text.input.ImeAction.Done
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        textPromptResult?.complete(prompt.confirm(textPromptInput))
                        pendingTextPrompt = null
                        textPromptResult = null
                    }) {
                        Text("Submit")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        textPromptResult?.complete(prompt.dismiss())
                        pendingTextPrompt = null
                        textPromptResult = null
                    }) {
                        Text(stringResource(R.string.ui_cancel))
                    }
                }
            )
        }
    }
}
