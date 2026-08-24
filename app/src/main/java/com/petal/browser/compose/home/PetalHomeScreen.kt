/*
 * PetalHomeScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 professional home screen for Petal Browser — Chrome/Edge-style
 * squircle shortcut grid with merged frequently-visited sites, slim top bar,
 * and existing search bar / edit dialog / persistence intact.
 *
 * MIT License — Copyright (c) 2026
 */

package com.petal.browser.compose.home

import android.content.Context
import android.net.Uri
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.petal.browser.account.AccountViewModel
import com.petal.browser.account.ProfileAvatarDisplay
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ── 1. Data model & Persistence ───────────────────────────────────────────

data class PetalShortcut(
    val label: String,
    val url: String,
    val siteId: String,
    val containerColor: Color,
    val contentColor: Color = Color.White
)

val defaultPetalShortcuts = listOf(
    PetalShortcut("YouTube", "https://www.youtube.com", "youtube", Color(0xFFFF0000)),
    PetalShortcut("GitHub", "https://github.com", "github", Color(0xFF24292E)),
    PetalShortcut("Wikipedia", "https://wikipedia.org", "wikipedia", Color(0xFF43464E)),
    PetalShortcut("DuckDuckGo", "https://duckduckgo.com", "duckduckgo", Color(0xFFDE5833)),
    PetalShortcut("Weather", "https://www.google.com/search?q=weather", "weather", Color(0xFF4285F4))
)

fun loadHomeShortcuts(context: Context): List<PetalShortcut> {
    val sp = PreferenceManager.getDefaultSharedPreferences(context)
    val jsonStr = sp.getString("sp_custom_home_shortcuts_json_v3", null)
    if (jsonStr != null) {
        try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<PetalShortcut>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val label = obj.optString("label", "Shortcut ${i + 1}")
                val url = obj.optString("url", "https://google.com")
                val siteId = obj.optString("siteId", "globe")
                val colorStr = obj.optString("color", "#4285F4")
                val parsedColor = try {
                    Color(android.graphics.Color.parseColor(colorStr))
                } catch (_: Throwable) {
                    Color(0xFF4285F4)
                }
                list.add(PetalShortcut(label, url, siteId, parsedColor))
            }
            if (list.isNotEmpty()) return list
        } catch (_: Throwable) { }
    }

    // Auto-populate from top visited sites in local history database
    val visitedShortcuts = fetchTopVisitedShortcuts(context)
    if (visitedShortcuts.isNotEmpty()) {
        val merged = (visitedShortcuts + defaultPetalShortcuts).distinctBy { it.url }.take(5)
        saveHomeShortcuts(context, merged)
        return merged
    }

    return defaultPetalShortcuts
}

fun fetchTopVisitedShortcuts(context: Context): List<PetalShortcut> {
    val list = mutableListOf<PetalShortcut>()
    try {
        val action = com.petal.browser.database.RecordAction(context)
        action.open(false)
        val records = action.listHistory(context)
        action.close()

        val paletteColors = listOf(
            Color(0xFF4285F4), Color(0xFF34A853), Color(0xFFEA4335),
            Color(0xFFFBBC05), Color(0xFF9C27B0), Color(0xFF00BCD4)
        )

        // Group history records by domain host to find top visited sites
        val topSites = records
            .filter { !it.url.isNullOrBlank() && !it.url.startsWith("about:") && !it.url.startsWith("petal://") }
            .groupBy {
                try { Uri.parse(it.url).host ?: it.url } catch (e: Exception) { it.url }
            }
            .entries
            .sortedByDescending { it.value.size }
            .take(10)

        topSites.forEachIndexed { idx, entry ->
            val host = entry.key
            val firstRecord = entry.value.first()
            var rawLabel = firstRecord.title
            if (rawLabel.isNullOrBlank() || rawLabel.length > 25 || rawLabel.contains("http")) {
                rawLabel = host.removePrefix("www.").substringBefore(".")
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }

            val siteId = when {
                host.contains("youtube") -> "youtube"
                host.contains("github") -> "github"
                host.contains("wikipedia") -> "wikipedia"
                host.contains("duckduckgo") -> "duckduckgo"
                host.contains("google") -> "google"
                else -> "globe"
            }

            val color = paletteColors[idx % paletteColors.size]
            list.add(PetalShortcut(label = rawLabel, url = firstRecord.url, siteId = siteId, containerColor = color))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

fun saveHomeShortcuts(context: Context, list: List<PetalShortcut>) {
    val array = JSONArray()
    for (item in list) {
        val obj = JSONObject()
        obj.put("label", item.label)
        obj.put("url", item.url)
        obj.put("siteId", item.siteId)
        val argb = item.containerColor.toArgb()
        obj.put("color", String.format("#%08X", argb))
        array.put(obj)
    }
    PreferenceManager.getDefaultSharedPreferences(context)
        .edit()
        .putString("sp_custom_home_shortcuts_json_v3", array.toString())
        .apply()
}

val availableColors = listOf(
    Color(0xFFFF0000) to "YouTube Red",
    Color(0xFF4285F4) to "Google Blue",
    Color(0xFF34A853) to "Emerald",
    Color(0xFFFBBC05) to "Amber Gold",
    Color(0xFFEA4335) to "Crimson",
    Color(0xFF9C27B0) to "Violet",
    Color(0xFF00BCD4) to "Cyan Oceanic",
    Color(0xFF24292E) to "GitHub Black",
    Color(0xFFDE5833) to "DuckDuckGo Orange",
    Color(0xFF673AB7) to "Deep Purple"
)

val availableIcons = listOf(
    "youtube" to "YouTube",
    "google" to "Search",
    "github" to "Code",
    "wikipedia" to "Wikipedia (W)",
    "duckduckgo" to "Shield",
    "weather" to "Weather Sun",
    "globe" to "Globe",
    "star" to "Star",
    "heart" to "Heart",
    "bookmark" to "Bookmark",
    "lock" to "Lock"
)

// ── Shapes kept as public API — referenced by other screens (e.g. PetalDownloadManagerScreen) ──

val FlowerShape: Shape = GenericShape { size, _ ->
    val cx = size.width / 2f
    val cy = size.height / 2f
    val maxR = Math.min(cx, cy)
    val petals = 5
    var first = true
    for (i in 0..360 step 2) {
        val rad = Math.toRadians(i.toDouble())
        val r = maxR * (0.81f + 0.19f * Math.cos(petals * rad - Math.PI / 2).toFloat())
        val x = (cx + r * Math.cos(rad)).toFloat()
        val y = (cy + r * Math.sin(rad)).toFloat()
        if (first) { moveTo(x, y); first = false } else lineTo(x, y)
    }
    close()
}

val CloverShape: Shape = GenericShape { size, _ ->
    val cx = size.width / 2f
    val cy = size.height / 2f
    val maxR = Math.min(cx, cy)
    val lobes = 4
    var first = true
    for (i in 0..360 step 2) {
        val rad = Math.toRadians(i.toDouble())
        val r = maxR * (0.72f + 0.28f * Math.sin(lobes * rad).toFloat())
        val x = (cx + r * Math.cos(rad)).toFloat()
        val y = (cy + r * Math.sin(rad)).toFloat()
        if (first) { moveTo(x, y); first = false } else lineTo(x, y)
    }
    close()
}

val StarburstShape: Shape = GenericShape { size, _ ->
    val cx = size.width / 2f
    val cy = size.height / 2f
    val maxR = Math.min(cx, cy)
    val innerR = maxR * 0.68f
    val points = 8
    var first = true
    for (i in 0 until points * 2) {
        val rad = Math.toRadians((i * 360.0 / (points * 2)))
        val r = if (i % 2 == 0) maxR else innerR
        val x = (cx + r * Math.cos(rad)).toFloat()
        val y = (cy + r * Math.sin(rad)).toFloat()
        if (first) { moveTo(x, y); first = false } else lineTo(x, y)
    }
    close()
}

val ArchShape: Shape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    val r = w / 2f
    moveTo(0f, h)
    lineTo(0f, r)
    arcTo(
        rect = androidx.compose.ui.geometry.Rect(0f, 0f, w, w),
        startAngleDegrees = 180f,
        sweepAngleDegrees = 180f,
        forceMoveTo = false
    )
    lineTo(w, h)
    close()
}

// Kept for EditShortcutDialog live preview — shape preserved but bloom ring removed from screen.
val PetalContainerShape: Shape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(w * 0.5f, h)
    cubicTo(
        w * 0.05f, h * 0.64f,
        w * 0.10f, h * 0.08f,
        w * 0.5f, 0f
    )
    cubicTo(
        w * 0.90f, h * 0.08f,
        w * 0.95f, h * 0.64f,
        w * 0.5f, h
    )
    close()
}

// ── 2. Java Interop Callback Interface & Bridge ───────────────────────────

interface PetalHomeActionHandler {
    fun onSearch(query: String)
    fun onOpenUrl(url: String)
    fun onAddShortcut()
    fun onNewTab()
    fun onOpenBookmarks()
    fun onOpenHistory()
    fun onOpenDownloads()
    fun onOpenSettings()
    fun onOpenTabsOverview()
    fun onOpenAccountSync()
}

object PetalComposeBridge {
    @JvmStatic
    fun createComposeHomeView(
        activity: ComponentActivity,
        tabCount: Int,
        handler: PetalHomeActionHandler
    ): ComposeView {
        return ComposeView(activity).apply {
            setupExpressiveHomeScreen(
                activity = activity,
                onSearch = { query -> handler.onSearch(query) },
                onOpenShortcutUrl = { url -> handler.onOpenUrl(url) },
                onOpenAccountSync = { handler.onOpenAccountSync() },
                onOpenBookmarks = { handler.onOpenBookmarks() },
                onOpenHistory = { handler.onOpenHistory() },
                onOpenDownloads = { handler.onOpenDownloads() },
                onNewTab = { handler.onNewTab() }
            )
        }
    }
}

// ── 3. Compose View Host Extension ────────────────────────────────────────

fun ComposeView.setupExpressiveHomeScreen(
    activity: ComponentActivity,
    onSearch: (String) -> Unit,
    onOpenShortcutUrl: (String) -> Unit,
    onOpenAccountSync: () -> Unit,
    onOpenBookmarks: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onNewTab: () -> Unit = {}
) {
    setViewTreeLifecycleOwner(activity)
    setViewTreeViewModelStoreOwner(activity)
    setViewTreeSavedStateRegistryOwner(activity)
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

    setContent {
        val accountViewModel = viewModel<AccountViewModel>(activity)
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
            PetalHomeScreen(
                accountViewModel = accountViewModel,
                onSearch = onSearch,
                onOpenShortcutUrl = onOpenShortcutUrl,
                onOpenAccountSync = onOpenAccountSync,
                onOpenBookmarksAction = onOpenBookmarks,
                onOpenHistoryAction = onOpenHistory,
                onOpenDownloadsAction = onOpenDownloads,
                onNewTabAction = onNewTab
            )
        }
    }
}

// ── 4. Main Petal Home Screen Composable ──────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PetalHomeScreen(
    accountViewModel: AccountViewModel,
    onSearch: (String) -> Unit,
    onOpenShortcutUrl: (String) -> Unit,
    onOpenAccountSync: () -> Unit,
    onOpenBookmarksAction: () -> Unit = {},
    onOpenHistoryAction: () -> Unit = {},
    onOpenDownloadsAction: () -> Unit = {},
    onNewTabAction: () -> Unit = {}
) {
    var context = LocalContext.current
    var shortcuts by remember { mutableStateOf(loadHomeShortcuts(context)) }
    var editingSlotIndex by remember { mutableStateOf<Int?>(null) }
    var isAddingNewShortcut by remember { mutableStateOf(false) }

    val profile = accountViewModel.profileState

    // Merge saved shortcuts + auto-visited, deduplicated by URL
    val autoVisitedSites = remember { fetchTopVisitedShortcuts(context).take(8) }
    val mergedItems: List<Pair<PetalShortcut, Boolean>> = remember(shortcuts, autoVisitedSites) {
        val savedUrls = shortcuts.map { it.url }.toSet()
        val extraVisited = autoVisitedSites.filter { it.url !in savedUrls }
        // true = user-editable saved shortcut; false = auto-visited (read-only)
        shortcuts.map { it to true } + extraVisited.map { it to false }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // ── Layer 0: living Material 3 Expressive background ───────────
            // Same shared component every other screen already uses
            // (History, Settings, Downloads, Delete, Account Sync, Omnibox) —
            // "home_page" gives Home its own random layout, consistent with
            // how those screens each pass their own pageSeed.
            com.petal.browser.ui.components.M3ExpressiveVariableBackground(
                modifier = Modifier.fillMaxSize(),
                pageSeed = "home_page"
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Cap the content column width on large screens/tablets so the
                // hero search bar and grid don't stretch edge-to-edge.
                Column(
                    modifier = Modifier.widthIn(max = 640.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ── Slim Top Bar: petal mark + wordmark left, profile right ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(FlowerShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Petal",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = onOpenAccountSync,
                            modifier = Modifier.size(44.dp)
                        ) {
                            ProfileAvatarDisplay(profile = profile, sizeDp = 36)
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // ── Greeting Tagline (replaces removed quick shortcuts row) ──
                    PetalGreetingTagline(profile = profile)

                    Spacer(Modifier.height(18.dp))

                    // ── Hero Search Bar (moved down into former quick-actions slot) ──
                    PetalSearchBar(onSearch = onSearch)

                    Spacer(Modifier.height(26.dp))

                    // ── Shortcuts Grid (4 columns, squircle tiles) ──────────
                    PetalShortcutGrid(
                        items = mergedItems,
                        onOpenShortcut = { shortcut -> onOpenShortcutUrl(shortcut.url) },
                        onEditShortcutSlot = { index -> editingSlotIndex = index },
                        onAddShortcutClick = { isAddingNewShortcut = true }
                    )

                    Spacer(Modifier.height(96.dp))
                }
            }
        }

        // ── Create New Shortcut Dialog ────────────────────────────────────
        if (isAddingNewShortcut) {
            EditShortcutDialog(
                dialogTitle = "Add Shortcut",
                initialName = "",
                initialUrl = "",
                initialColor = Color(0xFF4285F4),
                onDismiss = { isAddingNewShortcut = false },
                onSave = { newShortcut ->
                    val newList = shortcuts.toMutableList()
                    newList.add(newShortcut)
                    shortcuts = newList
                    saveHomeShortcuts(context, newList)
                    isAddingNewShortcut = false
                },
                onDelete = null
            )
        }

        // ── Edit Existing Shortcut Dialog ─────────────────────────────────
        editingSlotIndex?.let { slotIndex ->
            val current = shortcuts.getOrNull(slotIndex)
            if (current != null) {
                EditShortcutDialog(
                    dialogTitle = "Edit Shortcut",
                    initialName = current.label,
                    initialUrl = current.url,
                    initialColor = current.containerColor,
                    onDismiss = { editingSlotIndex = null },
                    onSave = { updatedShortcut ->
                        val newList = shortcuts.toMutableList()
                        if (slotIndex in newList.indices) {
                            newList[slotIndex] = updatedShortcut
                        }
                        shortcuts = newList
                        saveHomeShortcuts(context, newList)
                        editingSlotIndex = null
                    },
                    onDelete = {
                        val newList = shortcuts.toMutableList()
                        if (slotIndex in newList.indices) {
                            newList.removeAt(slotIndex)
                        }
                        shortcuts = newList
                        saveHomeShortcuts(context, newList)
                        editingSlotIndex = null
                    }
                )
            }
        }
    }
}

// ── 5. Shortcut Grid ──────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PetalShortcutGrid(
    items: List<Pair<PetalShortcut, Boolean>>, // shortcut to isEditable
    onOpenShortcut: (PetalShortcut) -> Unit,
    onEditShortcutSlot: (Int) -> Unit,
    onAddShortcutClick: () -> Unit
) {
    // Calculate number of rows so we can give the non-scrollable grid a fixed height.
    // Grid is 4 columns: items + 1 "add" tile.
    val totalCells = items.size + 1
    val rows = (totalCells + 3) / 4 // ceiling division
    val tileSize = 60.dp
    val labelHeight = 32.dp
    val verticalSpacing = 16.dp
    val gridHeight = (tileSize + labelHeight + verticalSpacing) * rows + verticalSpacing

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier
            .fillMaxWidth()
            .height(gridHeight),
        userScrollEnabled = false, // parent Column handles scrolling
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing)
    ) {
        itemsIndexed(items) { index, (shortcut, isEditable) ->
            ShortcutTile(
                shortcut = shortcut,
                isEditable = isEditable,
                onClick = { onOpenShortcut(shortcut) },
                onLongClick = { if (isEditable) onEditShortcutSlot(index) }
            )
        }

        // "+" Add tile at end
        item {
            AddShortcutTile(onClick = onAddShortcutClick)
        }
    }
}

fun getFaviconUrl(url: String): String? {
    if (url.isBlank()) return null
    val cleanUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
    return try {
        val host = Uri.parse(cleanUrl).host
        if (!host.isNullOrBlank()) {
            "https://www.google.com/s2/favicons?domain=$host&sz=128"
        } else null
    } catch (_: Throwable) {
        null
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutTile(
    shortcut: PetalShortcut,
    isEditable: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "shortcut_press_scale"
    )

    val faviconUrl = remember(shortcut.url) { getFaviconUrl(shortcut.url) }
    var isImageError by remember(shortcut.url) { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(60.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(elevation = 3.dp, shape = RoundedCornerShape(20.dp), clip = false)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            if (!faviconUrl.isNullOrEmpty() && !isImageError) {
                AsyncImage(
                    model = faviconUrl,
                    contentDescription = shortcut.label,
                    contentScale = ContentScale.Fit,
                    onError = { isImageError = true },
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(shortcut.containerColor.copy(alpha = 0.18f))
                ) {
                    SiteBrandIconTinted(
                        siteId = shortcut.siteId,
                        label = shortcut.label,
                        tint = shortcut.containerColor
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = shortcut.label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AddShortcutTile(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Add shortcut",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = "Add",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ── 6. Search Bar ─────────────────────────────────────────────────────────

@Composable
private fun PetalSearchBar(onSearch: (String) -> Unit) {
    // This is a decoy bar, matching Chrome's home-screen search box behavior.
    // It never accepts typed input itself - tapping anywhere on it (including
    // the placeholder text area) hands off immediately to the real full-screen
    // omnibox (PetalOmniboxPage, opened via onSearch("") -> showOmniboxPage("")
    // in BrowserActivity), which is where live suggestions/history/voice/engine
    // preference all actually live. Do not reintroduce a local TextField or
    // local suggestion-fetching here - that previously duplicated and shadowed
    // the omnibox page instead of opening it.
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "search_bar_press_scale"
    )

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current
            ) { onSearch("") },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 18.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Search or type web address",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = {
                if (activity != null) {
                    com.petal.browser.ui.components.PetalAiSearchBridge.showAiSearchResult(activity, "")
                }
            }) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = "Petal AI",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = {
                if (activity != null) {
                    com.petal.browser.ui.components.PetalVoiceSearchBridge.showVoiceSearchSheet(activity) { result ->
                        if (result.isNotBlank()) onSearch(result)
                    }
                }
            }) {
                Icon(
                    Icons.Rounded.Mic,
                    contentDescription = "Voice Search",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ── 6b. Greeting Tagline ─────────────────────────────────────────────────────
// Replaces the old quick-shortcuts row (bookmarks/history/downloads/new tab).
// Shows a rotating, personalized greeting where the search bar used to sit.
// A random line is picked once per composition of the home screen, which in
// practice means a fresh line each time the app process is (re)started —
// normal recomposition/navigation within the same session does not reroll it.

private val generalGreetingTaglines = listOf(
    "Welcome back, %s—the web is ready whenever you are.",
    "What are you curious about today, %s?",
    "Ready to discover something amazing, %s?",
    "%s, here is your personal window to the entire web.",
    "Search deeper and expand your world, %s.",
    "Bring your biggest ideas to life today, %s.",
    "Your next great breakthrough starts right here, %s.",
    "Designed for your speed and creativity, %s.",
    "Stay inspired and keep building, %s.",
    "Good to see you again, %s; let's make things happen.",
    "Clear mind and a fresh tab, %s—what's on your agenda?",
    "Everything is set up and waiting for you, %s.",
    "Your digital workspace is primed and ready, %s."
)

private val morningGreetingTaglines = listOf(
    "Good morning, %s! Start the day with a fresh perspective."
)

private val afternoonGreetingTaglines = listOf(
    "Good afternoon, %s—keep that momentum rolling."
)

private val eveningGreetingTaglines = listOf(
    "Good evening, %s; time to unwind and explore something fun."
)

@Composable
private fun PetalGreetingTagline(profile: com.petal.browser.account.GoogleUserProfile) {
    val context = LocalContext.current
    val username = profile.displayName.ifBlank { "Petal Explorer" }

    var updateWelcomeMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            val currentVersion = context.packageManager.getPackageInfo(context.getPackageName(), 0).versionName
            val lastSeenVersion = sp.getString("sp_last_seen_welcome_version", "")
            if (!currentVersion.isNullOrBlank() && currentVersion != lastSeenVersion) {
                sp.edit().putString("sp_last_seen_welcome_version", currentVersion).apply()
                updateWelcomeMessage = "Welcome to Petal v$currentVersion! 🎉 Enjoy the latest updates."
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val tagline = remember(username, updateWelcomeMessage) {
        if (!updateWelcomeMessage.isNullOrBlank()) {
            updateWelcomeMessage!!
        } else {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val timeSpecificTaglines = when {
                hour in 4..11 -> morningGreetingTaglines
                hour in 12..16 -> afternoonGreetingTaglines
                else -> eveningGreetingTaglines
            }
            val pool = generalGreetingTaglines + timeSpecificTaglines
            pool.random().format(username)
        }
    }

    Text(
        text = tagline,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
        color = if (updateWelcomeMessage != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    )
}

// ── 7. Site Brand Icons ────────────────────────────────────────────────────

/**
 * Tinted variant used inside the grid tiles — icon uses the brand color as tint
 * rather than white, so it reads well on the neutral surfaceContainerHigh tile bg.
 */
@Composable
private fun SiteBrandIconTinted(siteId: String, label: String, tint: Color) {
    when (siteId) {
        "youtube" -> {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "YouTube", tint = tint, modifier = Modifier.size(26.dp))
        }
        "google", "search" -> {
            Icon(Icons.Rounded.Search, contentDescription = "Google", tint = tint, modifier = Modifier.size(24.dp))
        }
        "github" -> {
            androidx.compose.foundation.Image(
                painter = painterResource(com.petal.browser.R.drawable.ic_shortcut_github),
                contentDescription = "GitHub",
                modifier = Modifier.size(26.dp)
            )
        }
        "wikipedia" -> {
            androidx.compose.foundation.Image(
                painter = painterResource(com.petal.browser.R.drawable.ic_shortcut_wikipedia),
                contentDescription = "Wikipedia",
                modifier = Modifier.size(26.dp)
            )
        }
        "duckduckgo" -> {
            androidx.compose.foundation.Image(
                painter = painterResource(com.petal.browser.R.drawable.ic_shortcut_duckduckgo),
                contentDescription = "DuckDuckGo",
                modifier = Modifier.size(26.dp)
            )
        }
        "weather" -> {
            Icon(Icons.Rounded.WbSunny, contentDescription = "Google Weather", tint = Color(0xFFFFD54F), modifier = Modifier.size(24.dp))
        }
        "globe" -> {
            Icon(Icons.Rounded.Public, contentDescription = "Web", tint = tint, modifier = Modifier.size(24.dp))
        }
        "star" -> {
            Icon(Icons.Rounded.Star, contentDescription = "Star", tint = tint, modifier = Modifier.size(24.dp))
        }
        "heart" -> {
            Icon(Icons.Rounded.Favorite, contentDescription = "Heart", tint = tint, modifier = Modifier.size(24.dp))
        }
        "bookmark" -> {
            Icon(Icons.Rounded.Bookmark, contentDescription = "Bookmark", tint = tint, modifier = Modifier.size(24.dp))
        }
        "lock" -> {
            Icon(Icons.Rounded.Lock, contentDescription = "Lock", tint = tint, modifier = Modifier.size(24.dp))
        }
        else -> {
            Text(
                label.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = tint
            )
        }
    }
}

/**
 * White-icon variant preserved for the EditShortcutDialog live preview surface
 * which still uses PetalContainerShape with a solid brand-color background.
 */
@Composable
private fun SiteBrandIcon(siteId: String, label: String) {
    when (siteId) {
        "youtube" -> {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "YouTube", tint = Color.White, modifier = Modifier.size(28.dp))
        }
        "google", "search" -> {
            Icon(Icons.Rounded.Search, contentDescription = "Google", tint = Color.White, modifier = Modifier.size(26.dp))
        }
        "github" -> {
            androidx.compose.foundation.Image(
                painter = painterResource(com.petal.browser.R.drawable.ic_shortcut_github),
                contentDescription = "GitHub",
                modifier = Modifier.size(28.dp)
            )
        }
        "wikipedia" -> {
            androidx.compose.foundation.Image(
                painter = painterResource(com.petal.browser.R.drawable.ic_shortcut_wikipedia),
                contentDescription = "Wikipedia",
                modifier = Modifier.size(28.dp)
            )
        }
        "duckduckgo" -> {
            androidx.compose.foundation.Image(
                painter = painterResource(com.petal.browser.R.drawable.ic_shortcut_duckduckgo),
                contentDescription = "DuckDuckGo",
                modifier = Modifier.size(28.dp)
            )
        }
        "weather" -> {
            Icon(Icons.Rounded.WbSunny, contentDescription = "Google Weather", tint = Color(0xFFFFD54F), modifier = Modifier.size(26.dp))
        }
        "globe" -> {
            Icon(Icons.Rounded.Public, contentDescription = "Web", tint = Color.White, modifier = Modifier.size(26.dp))
        }
        "star" -> {
            Icon(Icons.Rounded.Star, contentDescription = "Star", tint = Color.White, modifier = Modifier.size(26.dp))
        }
        "heart" -> {
            Icon(Icons.Rounded.Favorite, contentDescription = "Heart", tint = Color.White, modifier = Modifier.size(26.dp))
        }
        "bookmark" -> {
            Icon(Icons.Rounded.Bookmark, contentDescription = "Bookmark", tint = Color.White, modifier = Modifier.size(26.dp))
        }
        "lock" -> {
            Icon(Icons.Rounded.Lock, contentDescription = "Lock", tint = Color.White, modifier = Modifier.size(26.dp))
        }
        else -> {
            Text(
                label.take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
        }
    }
}

// ── 8. Edit Shortcut Dialog ───────────────────────────────────────────────

@Composable
private fun EditShortcutDialog(
    dialogTitle: String,
    initialName: String,
    initialUrl: String,
    initialColor: Color,
    onDismiss: () -> Unit,
    onSave: (PetalShortcut) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var nameText by remember(initialName) { mutableStateOf(initialName) }
    var urlText by remember(initialUrl) { mutableStateOf(initialUrl) }

    val currentFaviconUrl = remember(urlText) { getFaviconUrl(urlText) }
    var isImageError by remember(urlText) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("URL") },
                    singleLine = true,
                    placeholder = { Text("example.com") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Live Preview with automatic thumbnail
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerLow,
                            RoundedCornerShape(16.dp)
                        )
                        .padding(12.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .shadow(elevation = 2.dp, shape = RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        if (!currentFaviconUrl.isNullOrEmpty() && !isImageError) {
                            AsyncImage(
                                model = currentFaviconUrl,
                                contentDescription = nameText,
                                contentScale = ContentScale.Fit,
                                onError = { isImageError = true },
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                        } else {
                            Text(
                                nameText.ifBlank { "S" }.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Column {
                        Text("Live Preview", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        Text(
                            nameText.ifBlank { "Shortcut" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalUrl = if (urlText.isNotBlank() && !urlText.startsWith("http://") && !urlText.startsWith("https://")) {
                        "https://$urlText"
                    } else urlText.ifBlank { "https://google.com" }

                    val derivedName = if (nameText.isNotBlank()) {
                        nameText
                    } else {
                        try {
                            Uri.parse(finalUrl).host?.removePrefix("www.")?.substringBefore(".")
                                ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                                ?: "Shortcut"
                        } catch (_: Throwable) {
                            "Shortcut"
                        }
                    }

                    val siteId = try {
                        val host = Uri.parse(finalUrl).host ?: ""
                        when {
                            host.contains("youtube") -> "youtube"
                            host.contains("github") -> "github"
                            host.contains("wikipedia") -> "wikipedia"
                            host.contains("duckduckgo") -> "duckduckgo"
                            host.contains("google") -> "google"
                            else -> "globe"
                        }
                    } catch (_: Throwable) { "globe" }

                    onSave(
                        PetalShortcut(
                            label = derivedName,
                            url = finalUrl,
                            siteId = siteId,
                            containerColor = initialColor
                        )
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
