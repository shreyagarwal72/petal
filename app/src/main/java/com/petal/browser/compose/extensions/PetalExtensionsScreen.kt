/*
 * PetalExtensionsScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive "Extensions" page for Petal Browser.
 *
 * Petal renders pages with Mozilla GeckoView - the same engine Firefox for Android uses -
 * so this screen is a real add-ons manager for real Firefox WebExtensions (signed .xpi
 * packages from addons.mozilla.org), not a lookalike. See [PetalExtensionManager] for the
 * GeckoView WebExtensionController plumbing this UI drives.
 */

package com.petal.browser.compose.extensions

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.extensions.PetalExtensionManager
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.IconSwitch
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.theme.ExperimentalMaterial3ExpressiveApi
import com.petal.browser.ui.theme.PetalExpressiveTheme
import org.mozilla.geckoview.GeckoView

object PetalExtensionsBridge {
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

    var showAddSheet by remember { mutableStateOf(false) }
    var detailExtensionId by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { PetalExtensionManager.attach(context); PetalExtensionManager.refresh() }

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddSheet = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Add extension") },
                shape = RoundedCornerShape(20.dp)
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            M3ExpressiveVariableBackground(modifier = Modifier.fillMaxSize(), pageSeed = "extensions_page")

            Column(modifier = Modifier.fillMaxSize()) {
                ExpressiveHeader(
                    title = "Extensions",
                    subtitle = if (extensions.isEmpty()) "Firefox add-ons, powered by GeckoView" else "${extensions.size} installed",
                    onBack = onDismiss
                )
                if (busy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                if (extensions.isEmpty()) {
                    EmptyExtensionsState(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        onAddClick = { showAddSheet = true }
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 12.dp,
                            bottom = innerPadding.calculateBottomPadding() + 96.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(extensions, key = { it.id }) { ext ->
                            ExtensionRow(
                                extension = ext,
                                onToggleEnabled = { enabled ->
                                    PetalExtensionManager.setEnabled(ext.raw, enabled)
                                },
                                onOpen = {
                                    // Trigger the extension's own browser-action click, which
                                    // will invoke the popup flow in PetalExtensionManager if
                                    // the extension defines one.
                                    detailExtensionId = ext.id
                                },
                                onUninstall = {
                                    PetalExtensionManager.uninstall(ext.raw)
                                },
                                onOpenPopup = {
                                    PetalExtensionManager.triggerBrowserAction(ext.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    } // PetalScreenWrapper
    } // PetalPredictiveBackSurface

    if (showAddSheet) {
        AddExtensionSheet(
            onDismiss = { showAddSheet = false },
            onInstall = { url ->
                PetalExtensionManager.install(url) { success, _ ->
                    if (success) showAddSheet = false
                }
            },
            onInstallFile = { uri ->
                PetalExtensionManager.installFromContentUri(context, uri) { success, _ ->
                    if (success) showAddSheet = false
                }
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
                    PetalExtensionManager.uninstall(ext.raw)
                    detailExtensionId = null
                },
                onOpenLink = { linkTitle, url ->
                    // Opening a link (extension settings / AMO listing) needs to fully leave
                    // the Extensions screen, not just close this bottom sheet - the Extensions
                    // screen is a full-screen overlay presented on top of the browser content
                    // (see BrowserActivity#presentComposeScreen), so if only the sheet is
                    // dismissed here, the newly-opened tab loads invisibly behind it and it
                    // looks like tapping the link did nothing. Close the sheet, close the
                    // whole overlay (onDismiss == onBackPress from the caller), then navigate.
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
                    PetalExtensionManager.triggerBrowserAction(ext.id)
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
        ExtensionPopupDialog(
            popup = popup,
            onDismiss = { PetalExtensionManager.dismissPopup() }
        )
    }
}

@Composable
private fun EmptyExtensionsState(modifier: Modifier = Modifier, onAddClick: () -> Unit) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Extension,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "No extensions yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Petal runs on the same engine as Firefox, so real Firefox add-ons from " +
                "addons.mozilla.org install and sync here just like they would on desktop.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        FilledTonalButton(onClick = onAddClick, shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Browse recommended add-ons")
        }
    }
}

@Composable
private fun ExtensionRow(
    extension: PetalExtensionManager.InstalledExtension,
    onToggleEnabled: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onUninstall: () -> Unit,
    onOpenPopup: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onOpen() }
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
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val bmp = extension.icon
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    Icon(
                        Icons.Rounded.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
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
                    DropdownMenuItem(
                        text = { Text("Remove") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                        onClick = { showMenu = false; onUninstall() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExtensionSheet(
    onDismiss: () -> Unit,
    onInstall: (String) -> Unit,
    onInstallFile: (android.net.Uri) -> Unit
) {
    var manualUrl by remember { mutableStateOf("") }
    var showMozillaCatalogPrompt by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val busy by PetalExtensionManager.busy.collectAsState()
    val xpiPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(onInstallFile)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                "Add extension",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Installs real Firefox add-ons (.xpi) from addons.mozilla.org.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            Text(
                "Recommended",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            PetalExtensionManager.catalog.forEach { entry ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = !busy) { onInstall(entry.downloadUrl) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Extension,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text(
                                entry.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(Icons.Rounded.Download, contentDescription = "Install", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { xpiPicker.launch(arrayOf("*/*")) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Import .xpi file")
            }

            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = { showMozillaCatalogPrompt = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Find more extensions")
            }

            Spacer(Modifier.height(20.dp))
            Spacer(Modifier.height(16.dp))

            Text(
                "Install from a URL",
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
            text = { Text("Browse Mozilla Android add-ons, then paste an add-on page or download link here to install it.") },
            confirmButton = { TextButton(onClick = {
                showMozillaCatalogPrompt = false
                // foreground=true - otherwise this silently opens a background tab while the
                // Extensions overlay stays on screen, which looks like the button did nothing.
                (context as? com.petal.browser.activity.BrowserActivity)?.addAlbum("Firefox Add-ons", PetalExtensionManager.amoAndroidBrowseUrl, true)
            }) { Text("Open Mozilla Add-ons") } },
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
            // Only extensions that actually declare a popup expose the action.
            if (extension.enabled && extension.supportsPopup) {
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = {
                        // Dismiss the sheet first so the popup dialog renders on top of
                        // the main screen, not on top of the bottom sheet.
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
            // An extension's options page is a moz-extension://<id>/... URL - only GeckoView
            // understands that scheme, so it must be opened as a normal Petal (Gecko) tab
            // rather than a system ACTION_VIEW intent. onOpenLink additionally closes the
            // Extensions screen's own full-screen overlay before navigating - otherwise the
            // overlay stays on top of the newly-opened tab and the tap looks like a no-op.
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

/**
 * Renders a WebExtension browser/page-action popup (the small dropdown UI extensions like
 * uBlock Origin or Bitwarden show when their toolbar icon is tapped) inside a floating M3 card,
 * using a lightweight secondary [GeckoView] bound to the popup's own [GeckoSession].
 */
@Composable
private fun ExtensionPopupDialog(
    popup: PetalExtensionManager.PendingPopup,
    onDismiss: () -> Unit
) {
    // Look up the installed extension to get its icon for the title bar.
    val extensions by PetalExtensionManager.extensions.collectAsState()
    val extIcon = remember(popup.extensionId, extensions) {
        extensions.find { it.id == popup.extensionId }?.icon
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(min = 220.dp, max = 520.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Title bar: optional extension icon + name + close button
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Extension icon (24dp) if available, else a generic Extension icon
                    val bmp = extIcon
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                    } else {
                        Icon(
                            Icons.Rounded.Extension,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        popup.extensionName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }
                HorizontalDivider()
                AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    factory = { ctx ->
                        GeckoView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setSession(popup.session)
                        }
                    },
                    onRelease = { view -> view.releaseSession() }
                )
            }
        }
    }
}
