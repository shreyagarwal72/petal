/*
 * PetalTabSwitcherSheet.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Tab Switcher Overview Sheet featuring:
 * 1. Top bar: single horizontal row with 4 elements (square + New Tab button, pill segmented control for list/grid toggle, 3-dot overflow menu).
 * 2. Search bar: full-width rounded "Search your tabs" search bar.
 * 3. Empty state: centered illustration of two overlapping diagonal cards, bold headline, subtext, and New Tab action button.
 * 4. Undo Snackbar: rounded rectangle bar near bottom with message and Undo button.
 * 5. Intact tab card design and full tab management functionality.
 */

package com.petal.browser.ui.components

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.browser.AlbumController
import com.petal.browser.browser.BrowserContainer
import com.petal.browser.ui.theme.PetalExpressiveTheme

data class TabModel(
    val album: AlbumController,
    val title: String,
    val url: String,
    val isActive: Boolean
)

object PetalTabSwitcherBridge {
    @JvmStatic
    fun showTabSwitcherSheet(
        activity: ComponentActivity,
        currentAlbum: AlbumController?,
        onSelectTab: (AlbumController) -> Unit,
        onCloseTab: (AlbumController) -> Unit,
        onCloseAllTabs: () -> Unit,
        onNewTab: () -> Unit
    ) {
        try {
            val dialog = BottomSheetDialog(activity)
            // Full-screen, non-swipeable presentation: this is a dedicated tab-manager
            // screen, not a peekable/collapsible sheet, so the drag-to-dismiss gesture is
            // disabled and the sheet is forced to occupy the entire window the moment it's
            // shown (rather than the default "expanded to content height" bottom sheet).
            dialog.behavior.isDraggable = false
            dialog.behavior.skipCollapsed = true
            dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            dialog.setCancelable(true)
            dialog.setCanceledOnTouchOutside(false)
            dialog.window?.let { window ->
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
            }
            dialog.setOnShowListener {
                try {
                    val container = dialog.findViewById<android.view.View>(com.google.android.material.R.id.container)
                    container?.let { root ->
                        root.fitsSystemWindows = false
                        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets -> insets }
                    }

                    val coordinator = dialog.findViewById<android.view.View>(com.google.android.material.R.id.coordinator)
                    coordinator?.let { root ->
                        root.fitsSystemWindows = false
                        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets -> insets }
                    }

                    val bottomSheet = dialog.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
                    bottomSheet?.let { sheet ->
                        sheet.fitsSystemWindows = false
                        sheet.background = null
                        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(sheet) { _, insets -> insets }

                        val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet)
                        behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                        behavior.skipCollapsed = true
                        behavior.isDraggable = false
                        sheet.layoutParams?.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            val composeView = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    val sp = PreferenceManager.getDefaultSharedPreferences(activity)
                    val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                    val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                    val paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                    val isAmoled = sp.getBoolean("sp_amoled", false)
                    val dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)

                    val appFont = remember(fontName) {
                        com.petal.browser.ui.theme.AppFont.fromName(fontName)
                    }
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
                        val tabItems = remember {
                            mutableStateListOf<com.petal.browser.compose.tabs.PetalTabItem>().apply {
                                addAll(
                                    BrowserContainer.list().map { album: AlbumController ->
                                        val rawTitle = try { album.getTitle() } catch (_: Exception) { null }
                                        val rawUrl = try { album.getUrl() } catch (_: Exception) { null }
                                        val isIncognitoTab = (album is com.petal.browser.view.NinjaWebView) && album.isIncognito()
                                        val faviconBitmap = if (album is com.petal.browser.view.NinjaWebView) album.getFavicon() else null
                                        // Prefer the LRU-cached thumbnail (kept warm by page-load and
                                        // tab-switch hooks) so opening the switcher doesn't need a fresh
                                        // synchronous draw; only fall back to that draw on a cache miss.
                                        val previewBitmap = if (album is com.petal.browser.view.NinjaWebView) {
                                            album.getCachedPreviewBitmap() ?: album.capturePreviewBitmap()
                                        } else null

                                        val displayTitle = when {
                                            !rawTitle.isNullOrBlank() && !rawTitle.equals("about:blank", ignoreCase = true) && !rawTitle.equals("Petal Start", ignoreCase = true) -> rawTitle
                                            !rawUrl.isNullOrBlank() && !rawUrl.equals("about:blank", ignoreCase = true) && !rawUrl.startsWith("file:///android_asset/") -> rawUrl
                                            else -> "Petal Home"
                                        }
                                        val displayUrl = if (rawUrl.isNullOrBlank() || rawUrl.equals("about:blank", ignoreCase = true) || rawUrl.startsWith("file:///android_asset/")) "Petal Home" else rawUrl
                                        com.petal.browser.compose.tabs.PetalTabItem(
                                            id = album.hashCode().toString(),
                                            title = displayTitle,
                                            url = displayUrl,
                                            faviconBitmap = faviconBitmap,
                                            previewBitmap = previewBitmap,
                                            isIncognito = isIncognitoTab,
                                            isSelected = (album == currentAlbum)
                                        )
                                    }
                                )
                            }
                        }

                        com.petal.browser.compose.tabs.PetalTabGridSwitcher(
                            tabs = tabItems,
                            onTabSelect = { tabItem ->
                                try { dialog.dismiss() } catch (ignored: Exception) {}
                                val targetAlbum = BrowserContainer.list().find { it.hashCode().toString() == tabItem.id }
                                if (targetAlbum != null) {
                                    onSelectTab(targetAlbum)
                                }
                            },
                            onTabClose = { tabItem ->
                                val targetAlbum = BrowserContainer.list().find { it.hashCode().toString() == tabItem.id }
                                if (targetAlbum != null) {
                                    tabItems.removeAll { it.id == tabItem.id }
                                    com.petal.browser.unit.TabThumbnailCache.remove(tabItem.id)
                                    onCloseTab(targetAlbum)
                                    com.petal.browser.compose.incognito.PetalIncognitoSessionManager.syncIncognitoState(context)
                                    // Don't auto-dismiss when the last tab closes - like Chrome,
                                    // stay open and let PetalTabGridSwitcher show its empty-state
                                    // fallback ("You'll find your tabs here") instead of kicking
                                    // the user out of the switcher.
                                }
                            },
                            onNewTab = { isIncognito ->
                                try { dialog.dismiss() } catch (ignored: Exception) {}
                                if (isIncognito) {
                                    (activity as? BrowserActivity)?.addAlbum("Incognito Tab", "about:blank", true, true)
                                } else {
                                    onNewTab()
                                }
                            },
                            onCloseAllTabs = {
                                val count = tabItems.size
                                try { dialog.dismiss() } catch (ignored: Exception) {}
                                PetalConfirmSheetBridge.showCloseAllTabsConfirmation(activity, count) {
                                    onCloseAllTabs()
                                }
                            },
                            onOpenSettings = {
                                try { dialog.dismiss() } catch (ignored: Exception) {}
                                (activity as? BrowserActivity)?.showOverflow(null, null, 0, "", "", null, null, 0)
                            },
                            onTabVisible = { tabItem ->
                                // Live PixelCopy thumbnail refresh: the tabItems list is
                                // seeded above with a cheap synchronous snapshot so cards
                                // never render blank, then upgraded here - per card, the
                                // first time it's actually shown in the grid - with a
                                // GPU-accurate PixelCopy capture (falls back to the same
                                // software snapshot on pre-API 31 devices). The callback
                                // always lands on the main thread, so mutating the
                                // Compose-observed tabItems list directly is safe.
                                val targetAlbum = BrowserContainer.list()
                                    .find { it.hashCode().toString() == tabItem.id }
                                if (targetAlbum is com.petal.browser.view.NinjaWebView) {
                                    targetAlbum.capturePreviewBitmapAsync { bitmap ->
                                        if (bitmap != null) {
                                            val index = tabItems.indexOfFirst { it.id == tabItem.id }
                                            if (index >= 0) {
                                                tabItems[index] = tabItems[index].copy(previewBitmap = bitmap)
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
            dialog.setContentView(composeView)
            dialog.setOnShowListener {
                // Force the sheet's internal container to fill the entire window so the
                // tab manager truly occupies the full screen rather than the default
                // wrap-content bottom sheet.
                val sheetView = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                if (sheetView != null) {
                    val params = sheetView.layoutParams
                    params.height = ViewGroup.LayoutParams.MATCH_PARENT
                    sheetView.layoutParams = params
                    com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheetView).apply {
                        isDraggable = false
                        skipCollapsed = true
                        state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                    }
                }
            }
            dialog.setOnDismissListener {
                (activity as? BrowserActivity)?.apply {
                    runOnUiThread { updatePersistentBottomNav() }
                }
            }
            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun PetalTabSwitcherContent(
    tabs: MutableList<TabModel>,
    onSelectTab: (TabModel) -> Unit,
    onCloseTab: (TabModel) -> Unit,
    onCloseAllTabs: () -> Unit,
    onNewTab: () -> Unit
) {
    var isGridView by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var showOverflowMenu by remember { mutableStateOf(false) }

    var lastClosedTab by remember { mutableStateOf<TabModel?>(null) }

    val filteredTabs = remember(tabs, searchQuery) {
        if (searchQuery.isBlank()) {
            tabs
        } else {
            val q = searchQuery.trim().lowercase()
            tabs.filter { it.title.lowercase().contains(q) || it.url.lowercase().contains(q) }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (lastClosedTab != null) 70.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Top Drag Handle Indicator
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        .align(Alignment.CenterHorizontally)
                )

                // ── 1. Top Bar: Single horizontal row with four elements ──────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Far Left: Rounded square "new tab" button with + icon
                    Surface(
                        onClick = onNewTab,
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(40.dp).popIn()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = "New Tab",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Center-Left: Pill-shaped segmented control (List vs Grid)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.height(40.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // List View Toggle Button
                            Surface(
                                onClick = { isGridView = false },
                                shape = RoundedCornerShape(50),
                                color = if (!isGridView) MaterialTheme.colorScheme.surface else Color.Transparent,
                                contentColor = if (!isGridView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .height(32.dp)
                                    .padding(horizontal = 10.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.ViewList,
                                        contentDescription = "List View",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Grid View Toggle Button
                            Surface(
                                onClick = { isGridView = true },
                                shape = RoundedCornerShape(50),
                                color = if (isGridView) MaterialTheme.colorScheme.surface else Color.Transparent,
                                contentColor = if (isGridView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .height(32.dp)
                                    .padding(horizontal = 10.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.GridView,
                                        contentDescription = "Grid View",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Far Right: Three-dot overflow menu button
                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = "Menu Options",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("New Tab") },
                                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onNewTab()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Close All Tabs", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showOverflowMenu = false
                                    onCloseAllTabs()
                                }
                            )
                        }
                    }
                }

                // ── 2. Search Bar: "Search your tabs" ─────────────────────────
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Search your tabs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                Spacer(Modifier.height(4.dp))

                // ── 3. Tabs Grid/List OR Empty State ──────────────────────────
                if (filteredTabs.isEmpty()) {
                    // Empty State: Vertically and horizontally centered
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 280.dp)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Empty State Illustration: Two overlapping rounded-rectangle card shapes layered diagonally
                            PetalEmptyTabsLottie(
                                modifier = Modifier.size(120.dp)
                            )

                            Text(
                                text = if (searchQuery.isNotBlank()) "No Tabs Found" else "You'll find your tabs here",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = if (searchQuery.isNotBlank()) "No matching tabs found for \"$searchQuery\""
                                else "Open tabs to visit different pages at the same time",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                } else {
                    val columns = if (isGridView) GridCells.Fixed(2) else GridCells.Fixed(1)
                    LazyVerticalGrid(
                        columns = columns,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 440.dp)
                    ) {
                        items(filteredTabs, key = { it.album.hashCode() }) { tab ->
                            TabCard(
                                tab = tab,
                                onSelect = { onSelectTab(tab) },
                                onClose = {
                                    lastClosedTab = tab
                                    onCloseTab(tab)
                                }
                            )
                        }
                    }
                }
            }

            // ── 4. Undo Close Snackbar: Anchored near bottom ──────────────
            AnimatedVisibility(
                visible = lastClosedTab != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth().entrance()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tab closed",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(
                            onClick = {
                                lastClosedTab?.let { restoredTab ->
                                    if (!tabs.contains(restoredTab)) {
                                        tabs.add(restoredTab)
                                    }
                                    lastClosedTab = null
                                }
                            }
                        ) {
                            Text(
                                text = "Undo",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateIllustration() {
    Box(
        modifier = Modifier.size(96.dp).entrance(index = 0),
        contentAlignment = Alignment.Center
    ) {
        // Back card (tilted diagonally behind)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            modifier = Modifier
                .size(52.dp, 72.dp)
                .graphicsLayer {
                    rotationZ = -14f
                    translationX = -8f
                    translationY = -4f
                }
        ) {}

        // Front card (tilted diagonally in front)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(52.dp, 72.dp)
                .graphicsLayer {
                    rotationZ = 8f
                    translationX = 8f
                    translationY = 4f
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun TabCard(
    tab: TabModel,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    val borderColor = if (tab.isActive) MaterialTheme.colorScheme.primary else Color.Transparent
    val borderWidth = if (tab.isActive) 2.dp else 0.dp

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (tab.isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(105.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(20.dp))
            .clickable { onSelect() }
            .entrance(index = 0)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Public,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (tab.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = tab.title,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Close Tab",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = tab.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (tab.isActive) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = "Active",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
