/*
 * PetalExtensionsScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive "Extensions" page for Petal Browser.
 *
 * Provides dedicated views for Petal Built-in Extensions and Firefox WebExtensions
 * with real icons from Mozilla AMO CDN and dedicated settings per extension.
 */

package com.petal.browser.compose.extensions

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.petal.browser.engine.gecko.PetalGeckoRuntime
import com.petal.browser.extensions.PetalBuiltInExtensionManager
import com.petal.browser.extensions.PetalExtensionManager
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.IconSwitch
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.bouncyClickable
import com.petal.browser.ui.components.entrance
import com.petal.browser.ui.theme.ExperimentalMaterial3ExpressiveApi
import com.petal.browser.ui.theme.PetalExpressiveTheme
import kotlinx.coroutines.launch
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

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
                val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity)
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
                val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity)
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
    val extensions by PetalExtensionManager.extensions.collectAsState()
    val pendingPrompt by PetalExtensionManager.pendingPrompt.collectAsState()
    val pendingPopup by PetalExtensionManager.pendingPopup.collectAsState()
    val busy by PetalExtensionManager.busy.collectAsState()
    val lastError by PetalExtensionManager.lastError.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) } // 0: Built-in, 1: Firefox Add-ons
    var showAddSheet by remember { mutableStateOf(false) }
    var detailExtensionId by remember { mutableStateOf<String?>(null) }
    var activeBuiltInSettingsSpec by remember { mutableStateOf<PetalBuiltInExtensionManager.BuiltInSpec?>(null) }

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

    com.petal.browser.predictive.PetalPredictiveBackSurface(enabled = true, onBack = onDismiss) {
        com.petal.browser.predictive.PetalScreenWrapper(backgroundSnapshot = backgroundSnapshot) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = {
                    com.petal.browser.ui.components.PetalThemedSnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.padding(16.dp)
                    )
                },
                floatingActionButton = {
                    if (selectedTabIndex == 1) {
                        ExtendedFloatingActionButton(
                            onClick = { showAddSheet = true },
                            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                            text = { Text("Install from AMO / .xpi") },
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize()) {
                    M3ExpressiveVariableBackground(modifier = Modifier.fillMaxSize(), pageSeed = "extensions_page")

                    Column(modifier = Modifier.fillMaxSize()) {
                        ExpressiveHeader(
                            title = "Extensions",
                            subtitle = if (selectedTabIndex == 0) "Petal built-in privacy and web utilities" else "${extensions.size} Firefox add-on${if (extensions.size == 1) "" else "s"} installed",
                            onBack = onDismiss
                        )

                        PrimaryTabRow(
                            selectedTabIndex = selectedTabIndex,
                            containerColor = Color.Transparent,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) {
                            Tab(
                                selected = selectedTabIndex == 0,
                                onClick = { selectedTabIndex = 0 },
                                text = { Text("Petal Built-in", fontWeight = FontWeight.SemiBold) },
                                icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            Tab(
                                selected = selectedTabIndex == 1,
                                onClick = { selectedTabIndex = 1 },
                                text = { Text("Firefox Add-ons", fontWeight = FontWeight.SemiBold) },
                                icon = { Icon(Icons.Rounded.Extension, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                        }

                        if (busy) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }

                        if (selectedTabIndex == 0) {
                            BuiltInExtensionsList(
                                innerPadding = innerPadding,
                                onOpenSettings = { spec -> activeBuiltInSettingsSpec = spec }
                            )
                        } else {
                            FirefoxAddonsList(
                                innerPadding = innerPadding,
                                extensions = extensions,
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
                                    val fallbackInstallUrl = ext.amoListingUrl ?: ext.homepageUrl
                                    PetalExtensionManager.uninstall(ext.raw)
                                    coroutineScope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Uninstalled \"$extName\"",
                                            actionLabel = if (!fallbackInstallUrl.isNullOrBlank()) "Undo" else null,
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed && !fallbackInstallUrl.isNullOrBlank()) {
                                            PetalExtensionManager.install(fallbackInstallUrl)
                                        }
                                    }
                                },
                                onAddClick = { showAddSheet = true }
                            )
                        }
                    }
                }
            }
        }
    }

    var showXpiPicker by remember { mutableStateOf(false) }
    val systemXpiPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            PetalExtensionManager.installFromContentUri(context, it) { _, _ -> }
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
            }
        )
    }

    if (showXpiPicker) {
        com.petal.browser.compose.file.PetalFilePickerScreen(
            mimeTypes = arrayOf("*/*"),
            onDismissRequest = { showXpiPicker = false },
            onFileSelected = { file ->
                showXpiPicker = false
                PetalExtensionManager.installFromContentUri(context, android.net.Uri.fromFile(file)) { _, _ -> }
            },
            onBrowseSystemFallback = {
                showXpiPicker = false
                systemXpiPicker.launch(arrayOf("*/*"))
            }
        )
    }

    detailExtensionId?.let { id ->
        val ext = extensions.find { it.id == id }
        if (ext != null) {
            ExtensionDetailSheet(
                extension = ext,
                onDismiss = { detailExtensionId = null },
                onTogglePrivate = { allowed -> PetalExtensionManager.setAllowedInPrivateBrowsing(ext.raw, allowed) },
                onUninstall = {
                    val extName = ext.name
                    val fallbackInstallUrl = ext.amoListingUrl ?: ext.homepageUrl
                    PetalExtensionManager.uninstall(ext.raw)
                    detailExtensionId = null
                    coroutineScope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "Uninstalled \"$extName\"",
                            actionLabel = if (!fallbackInstallUrl.isNullOrBlank()) "Undo" else null,
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed && !fallbackInstallUrl.isNullOrBlank()) {
                            PetalExtensionManager.install(fallbackInstallUrl)
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
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                        } catch (ignored: Exception) {}
                    }
                },
                onOpenPopup = {
                    PetalExtensionManager.triggerBrowserAction(ext.id, context)
                }
            )
        } else {
            detailExtensionId = null
        }
    }

    activeBuiltInSettingsSpec?.let { spec ->
        BuiltInExtensionSettingsDialog(
            spec = spec,
            onDismiss = { activeBuiltInSettingsSpec = null }
        )
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

@Composable
private fun BuiltInExtensionsList(
    innerPadding: PaddingValues,
    onOpenSettings: (PetalBuiltInExtensionManager.BuiltInSpec) -> Unit
) {
    val context = LocalContext.current
    val sp = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = 16.dp,
            bottom = innerPadding.calculateBottomPadding() + 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(PetalBuiltInExtensionManager.builtIns, key = { it.extensionId }) { spec ->
            var isEnabled by remember { mutableStateOf(sp.getBoolean(spec.prefKey, true)) }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (spec.prefKey) {
                                "petal_builtin_dark_webpages" -> Icons.Rounded.DarkMode
                                "petal_builtin_clean_link" -> Icons.Rounded.LinkOff
                                "petal_builtin_universal_copy" -> Icons.Rounded.ContentCopy
                                "petal_builtin_ai_blocker" -> Icons.Rounded.SmartToy
                                "petal_builtin_translate" -> Icons.Rounded.Translate
                                "petal_builtin_google_search_fixer" -> Icons.Rounded.Search
                                "petal_builtin_media_grabber" -> Icons.Rounded.VideoLibrary
                                else -> Icons.Rounded.Extension
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = spec.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = spec.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { onOpenSettings(spec) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconSwitch(
                        checked = isEnabled,
                        icon = Icons.Rounded.Check,
                        onCheckedChange = { checked ->
                            isEnabled = checked
                            sp.edit().putBoolean(spec.prefKey, checked).apply()
                            PetalBuiltInExtensionManager.setEnabled(context, spec.prefKey, checked)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FirefoxAddonsList(
    innerPadding: PaddingValues,
    extensions: List<PetalExtensionManager.InstalledExtension>,
    onToggleEnabled: (PetalExtensionManager.InstalledExtension, Boolean) -> Unit,
    onOpen: (PetalExtensionManager.InstalledExtension) -> Unit,
    onShowDetails: (PetalExtensionManager.InstalledExtension) -> Unit,
    onOpenSettings: (PetalExtensionManager.InstalledExtension) -> Unit,
    onUninstall: (PetalExtensionManager.InstalledExtension) -> Unit,
    onAddClick: () -> Unit
) {
    val context = LocalContext.current
    val busy by PetalExtensionManager.busy.collectAsState()

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = 16.dp,
            bottom = innerPadding.calculateBottomPadding() + 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: Installed Extensions
        item {
            Text(
                text = "Installed Add-ons (${extensions.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (extensions.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Rounded.ExtensionOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No Firefox add-ons installed yet",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            itemsIndexed(extensions, key = { _, ext -> ext.id }) { index, ext ->
                ExtensionRow(
                    animationIndex = index,
                    extension = ext,
                    onToggleEnabled = { enabled -> onToggleEnabled(ext, enabled) },
                    onOpen = { onOpen(ext) },
                    onShowDetails = { onShowDetails(ext) },
                    onOpenSettings = { onOpenSettings(ext) },
                    onUninstall = { onUninstall(ext) }
                )
            }
        }

        // Section 2: Recommended Firefox Add-ons
        item {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recommended for Petal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = onAddClick) {
                    Text("Browse All")
                }
            }
        }

        items(PetalExtensionManager.catalog, key = { it.id }) { entry ->
            val installedExt = extensions.find {
                it.id.equals(entry.id, ignoreCase = true) ||
                it.name.equals(entry.name, ignoreCase = true) ||
                (it.amoListingUrl != null && it.amoListingUrl.contains(entry.amoSlug, ignoreCase = true))
            }
            val isInstalled = installedExt != null
            val iconUrl = PetalCuratedExtensionsData.getAmoIconUrl(entry.amoSlug) ?: PetalCuratedExtensionsData.getAmoIconUrl(entry.id)
            val visual = PetalCuratedExtensionsData.getVisual(entry.amoSlug)

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = !busy) {
                        if (isInstalled && installedExt != null) {
                            PetalExtensionManager.triggerBrowserAction(installedExt.id, context)
                        } else {
                            PetalExtensionManager.install(entry.downloadUrl) { _, _ -> }
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(visual?.accentColor?.copy(alpha = 0.15f) ?: MaterialTheme.colorScheme.surfaceVariant),
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

                    Column(modifier = Modifier.weight(1f)) {
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
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "Installed",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
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
                    }

                    if (isInstalled) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = "Installed",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        FilledTonalIconButton(
                            onClick = { PetalExtensionManager.install(entry.downloadUrl) { _, _ -> } },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Download,
                                contentDescription = "Install",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtensionRow(
    animationIndex: Int = 0,
    extension: PetalExtensionManager.InstalledExtension,
    onToggleEnabled: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onShowDetails: () -> Unit,
    onOpenSettings: () -> Unit,
    onUninstall: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val iconUrl = remember(extension.id) {
        PetalCuratedExtensionsData.getAmoIconUrl(extension.id) ?: extension.amoListingUrl?.let { url ->
            val segs = url.trimEnd('/').split('/')
            segs.lastOrNull()?.let { PetalCuratedExtensionsData.getAmoIconUrl(it) }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .bouncyClickable(scaleDown = 0.98f, onClick = onOpen)
            .entrance(index = animationIndex)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (extension.icon != null) {
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    extension.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "v${extension.version}" + if (!extension.enabled) " · Disabled" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconSwitch(
                checked = extension.enabled,
                icon = Icons.Rounded.Check,
                onCheckedChange = onToggleEnabled
            )

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (extension.optionsPageUrl != null) {
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onOpenSettings()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Details") },
                        leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onShowDetails()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onUninstall()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BuiltInExtensionSettingsDialog(
    spec: PetalBuiltInExtensionManager.BuiltInSpec,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    when (spec.prefKey) {
        "petal_builtin_dark_webpages" -> {
            var whitelist by remember { mutableStateOf(PetalBuiltInExtensionManager.getDarkWebpagesWhitelist(context)) }
            var contrast by remember { mutableStateOf(PetalBuiltInExtensionManager.getDarkWebpagesContrast(context)) }
            var newDomain by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = onDismiss,
                icon = { Icon(Icons.Rounded.DarkMode, contentDescription = null) },
                title = { Text("Petal Dark Webpages Settings") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text("Contrast / Inversion level: $contrast%", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = contrast.toFloat(),
                            onValueChange = {
                                contrast = it.toInt()
                                PetalBuiltInExtensionManager.setDarkWebpagesContrast(context, contrast)
                            },
                            valueRange = 50f..100f,
                            steps = 10
                        )

                        Spacer(Modifier.height(16.dp))
                        Text("Whitelisted Domains (Disable Dark Mode):", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newDomain,
                                onValueChange = { newDomain = it },
                                placeholder = { Text("example.com") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = {
                                if (newDomain.isNotBlank()) {
                                    PetalBuiltInExtensionManager.addDarkWebpageWhitelist(context, newDomain)
                                    whitelist = PetalBuiltInExtensionManager.getDarkWebpagesWhitelist(context)
                                    newDomain = ""
                                }
                            }) {
                                Icon(Icons.Rounded.AddCircle, contentDescription = "Add")
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        whitelist.forEach { domain ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(domain, style = MaterialTheme.typography.bodySmall)
                                IconButton(
                                    onClick = {
                                        PetalBuiltInExtensionManager.removeDarkWebpageWhitelist(context, domain)
                                        whitelist = PetalBuiltInExtensionManager.getDarkWebpagesWhitelist(context)
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
            )
        }
        else -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = { Icon(Icons.Rounded.Extension, contentDescription = null) },
                title = { Text(spec.label) },
                text = {
                    Column {
                        Text(spec.description, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Text("Extension ID: ${spec.extensionId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text("This Petal built-in WebExtension is integrated with GeckoView content scripts and activates automatically on page navigation.", style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) { Text("OK") }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExtensionSheet(
    onDismiss: () -> Unit,
    onInstall: (String) -> Unit,
    onInstallFile: (android.net.Uri) -> Unit,
    onRequestFileImport: () -> Unit = {}
) {
    var manualUrl by remember { mutableStateOf("") }
    var showMozillaCatalogPrompt by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val busy by PetalExtensionManager.busy.collectAsState()
    val extensions by PetalExtensionManager.extensions.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                "Add Firefox Extension",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Installs compatible Firefox WebExtensions (.xpi) directly from addons.mozilla.org or disk.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = {
                    onDismiss()
                    onRequestFileImport()
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Import local .xpi file")
            }

            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = { showMozillaCatalogPrompt = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Browse Mozilla Add-ons Store")
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Install directly from URL",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = manualUrl,
                onValueChange = { manualUrl = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://addons.mozilla.org/.../addon/…") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { if (manualUrl.isNotBlank()) onInstall(manualUrl.trim()) },
                enabled = manualUrl.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Install")
            }
        }
    }

    if (showMozillaCatalogPrompt) {
        AlertDialog(
            onDismissRequest = { showMozillaCatalogPrompt = false },
            icon = { Icon(Icons.Rounded.Extension, contentDescription = null) },
            title = { Text("Find Firefox extensions") },
            text = { Text("Browse Mozilla add-ons or paste a secure .xpi download link. Petal will let GeckoView validate compatibility, signatures, permissions, and the Mozilla blocklist.") },
            confirmButton = {
                TextButton(onClick = {
                    showMozillaCatalogPrompt = false
                    (context as? com.petal.browser.activity.BrowserActivity)?.addAlbum("Firefox Add-ons", PetalExtensionManager.amoAndroidBrowseUrl, true)
                }) { Text("Open Mozilla Add-ons") }
            },
            dismissButton = { TextButton(onClick = { showMozillaCatalogPrompt = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtensionDetailSheet(
    extension: PetalExtensionManager.InstalledExtension,
    onDismiss: () -> Unit,
    onTogglePrivate: (Boolean) -> Unit,
    onUninstall: () -> Unit,
    onOpenLink: (title: String, url: String) -> Unit,
    onOpenPopup: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(extension.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Version ${extension.version}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (extension.description.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(extension.description, style = MaterialTheme.typography.bodyMedium)
            }
            if (extension.enabled && extension.supportsPopup) {
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = {
                        onDismiss()
                        onOpenPopup()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open extension")
                }
            }
            Spacer(Modifier.height(16.dp))
            DetailRow(icon = Icons.Rounded.VisibilityOff, title = "Allow in Private tabs", checked = extension.allowedInPrivateBrowsing, onCheckedChange = onTogglePrivate)
            Spacer(Modifier.height(8.dp))
            extension.amoListingUrl?.let { url ->
                DetailLinkRow(icon = Icons.Rounded.OpenInNew, title = "View on addons.mozilla.org", url = url, onOpenLink = onOpenLink)
            }
            extension.optionsPageUrl?.let { url ->
                DetailLinkRow(icon = Icons.Rounded.Settings, title = "Extension settings", url = url, onOpenLink = onOpenLink)
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onUninstall,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Remove extension")
            }
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DetailLinkRow(icon: ImageVector, title: String, url: String, onOpenLink: (title: String, url: String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenLink(title, url) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun InstallPermissionDialog(prompt: PetalExtensionManager.PendingPrompt) {
    val meta = prompt.extension.metaData
    AlertDialog(
        onDismissRequest = { prompt.respond(false) },
        icon = { Icon(Icons.Rounded.Extension, contentDescription = null) },
        title = { Text(if (prompt.isUpdate) "Update ${meta.name ?: prompt.extension.id}?" else "Add ${meta.name ?: prompt.extension.id}?") },
        text = {
            Column {
                Text(
                    if (prompt.isUpdate) "This extension needs new permissions:" else "This extension needs permission to:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                val shown = (prompt.permissions + prompt.origins).distinct().take(8)
                if (shown.isEmpty()) {
                    Text("• No special permissions", style = MaterialTheme.typography.bodySmall)
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
            TextButton(onClick = { prompt.respond(false) }) { Text("Cancel") }
        }
    )
}

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
        var popupScale by remember { mutableStateOf(1f) }

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
                                        contentDescription = "Back",
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
                                        text = "Extension Popup",
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
                                        contentDescription = "Refresh",
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
                                        contentDescription = "Zoom out",
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
                                        contentDescription = "Zoom in",
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
                                        contentDescription = "Extension Settings",
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
                                        contentDescription = "Close",
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
                                                        act.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri)))
                                                    } catch (ignored: Exception) {}
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
                                                        act.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri)))
                                                    } catch (ignored: Exception) {}
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
                    update = { geckoView ->
                        if (geckoView.session !== popup.session) {
                            geckoView.setSession(popup.session)
                        }
                        try { popup.session.setActive(true) } catch (_: Throwable) {}
                        geckoView.scaleX = popupScale
                        geckoView.scaleY = popupScale
                    },
                    onRelease = { geckoView ->
                        try { geckoView.releaseSession() } catch (_: Throwable) {}
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        pendingTextPrompt?.let { prompt ->
            AlertDialog(
                onDismissRequest = {
                    textPromptResult?.complete(prompt.dismiss())
                    pendingTextPrompt = null
                    textPromptResult = null
                },
                icon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                title = { Text(prompt.title ?: popup.extensionName) },
                text = {
                    Column {
                        if (!prompt.message.isNullOrBlank()) {
                            Text(
                                prompt.message!!,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        OutlinedTextField(
                            value = textPromptInput,
                            onValueChange = { textPromptInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            visualTransformation = if (textPromptIsPassword)
                                PasswordVisualTransformation()
                            else
                                VisualTransformation.None,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (textPromptIsPassword) KeyboardType.Password else KeyboardType.Text,
                                imeAction = ImeAction.Done
                            )
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        textPromptResult?.complete(prompt.confirm(textPromptInput))
                        pendingTextPrompt = null
                        textPromptInput = ""
                        textPromptResult = null
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        textPromptResult?.complete(prompt.dismiss())
                        pendingTextPrompt = null
                        textPromptInput = ""
                        textPromptResult = null
                    }) { Text("Cancel") }
                }
            )
        }
    }
}
