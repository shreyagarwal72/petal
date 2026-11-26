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

import com.petal.browser.ui.components.PetalSpring
import com.petal.browser.ui.theme.PetalText
import com.petal.browser.ui.theme.ExpressiveButtonShape
import com.petal.browser.ui.components.scrollMotionBlur

import android.content.Context
import android.net.Uri
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import com.petal.browser.ui.kit.TextField
import com.petal.browser.ui.kit.IconButton
import com.petal.browser.ui.kit.FilledTonalButton
import com.petal.browser.ui.kit.Button
import androidx.compose.runtime.*
import com.petal.browser.ui.glass.captureGlassWallpaper
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.petal.browser.ui.theme.PetalShapeGroup
import com.petal.browser.ui.theme.toSafeShadowShape
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.petal.browser.account.PetalUserProfileManager
import com.petal.browser.account.PetalUserProfile
import com.petal.browser.profile.PetalProfileAvatar
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
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
import com.petal.browser.ui.components.entrance
import com.petal.browser.ui.components.homeLaunchEntrance
import com.petal.browser.ui.containment.PetalFloatingToolbar
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.PetalHeroCard
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalConnectedShape
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.PetalMaterialShapes
import com.petal.browser.ui.theme.mediumIncreased
import com.petal.browser.ui.theme.toShape
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

// ── 1. Data model & Persistence ───────────────────────────────────────────

const val PREF_FETCH_SHORTCUT_FAVICONS = "sp_fetch_shortcut_favicons"

@Immutable
data class PetalShortcut(
    val label: String,
    val url: String,
    val siteId: String,
    val containerColor: Color,
    val contentColor: Color = Color.White,
    val shapeName: String? = null,
    val id: String = java.util.UUID.randomUUID().toString(),
)

data class ShortcutUrlValidation(
    val isValid: Boolean,
    val normalizedUrl: String? = null,
    val errorRes: Int? = null,
)

fun validateShortcutUrl(rawUrl: String): ShortcutUrlValidation {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty()) {
        return ShortcutUrlValidation(isValid = false, errorRes = R.string.ui_shortcut_url_required)
    }
    if (trimmed.any { it.isWhitespace() }) {
        return ShortcutUrlValidation(isValid = false, errorRes = R.string.ui_shortcut_url_invalid)
    }
    val hasScheme = trimmed.contains("://")
    if (hasScheme && !trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
        return ShortcutUrlValidation(isValid = false, errorRes = R.string.ui_shortcut_url_invalid)
    }
    if (!hasScheme && (trimmed.startsWith("javascript:", ignoreCase = true) || trimmed.startsWith("data:", ignoreCase = true))) {
        return ShortcutUrlValidation(isValid = false, errorRes = R.string.ui_shortcut_url_invalid)
    }
    val candidate = if (hasScheme) trimmed else "https://$trimmed"
    val host = try {
        java.net.URI(candidate).host
    } catch (_: Throwable) {
        null
    }
    val isHostValid = !host.isNullOrBlank() && (host.contains('.') || host.equals("localhost", ignoreCase = true))
    return if (isHostValid) {
        ShortcutUrlValidation(isValid = true, normalizedUrl = candidate, errorRes = null)
    } else {
        ShortcutUrlValidation(isValid = false, errorRes = R.string.ui_shortcut_url_invalid)
    }
}

@Composable
fun rememberFetchShortcutFaviconsEnabled(): Boolean {
    val context = LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    var enabled by remember(sp) { mutableStateOf(sp.getBoolean(PREF_FETCH_SHORTCUT_FAVICONS, true)) }
    DisposableEffect(sp) {
        enabled = sp.getBoolean(PREF_FETCH_SHORTCUT_FAVICONS, true)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == null || key == PREF_FETCH_SHORTCUT_FAVICONS) {
                enabled = prefs.getBoolean(PREF_FETCH_SHORTCUT_FAVICONS, true)
            }
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return enabled
}

private fun stableLegacyShortcutId(index: Int, label: String, url: String): String {
    val seed = "petal_shortcut:$index:$label:$url"
    return java.util.UUID.nameUUIDFromBytes(seed.toByteArray(Charsets.UTF_8)).toString()
}

fun loadRemovedShortcutUrls(context: Context): Set<String> {
    val sp = PreferenceManager.getDefaultSharedPreferences(context)
    val key = com.petal.browser.profile.PetalProfileManager.getScopedKey("sp_removed_home_shortcut_urls_v1")
    return sp.getStringSet(key, emptySet()) ?: emptySet()
}

fun saveRemovedShortcutUrls(context: Context, set: Set<String>) {
    val key = com.petal.browser.profile.PetalProfileManager.getScopedKey("sp_removed_home_shortcut_urls_v1")
    PreferenceManager.getDefaultSharedPreferences(context)
        .edit()
        .putStringSet(key, set)
        .apply()
}

internal fun parseShortcutHexColor(colorStr: String?): Color {
    if (colorStr.isNullOrBlank()) return Color(0xFF4285F4)
    val hex = colorStr.trim().removePrefix("#")
    return try {
        when (hex.length) {
            6 -> Color((0xFF000000L or hex.toLong(16)).toInt())
            8 -> Color(hex.toLong(16).toInt())
            else -> Color(0xFF4285F4)
        }
    } catch (_: Throwable) {
        Color(0xFF4285F4)
    }
}

fun loadHomeShortcutsFromPrefs(
    sp: android.content.SharedPreferences,
    scopedKey: String,
    defaultFallbackKey: String? = "sp_custom_home_shortcuts_json_v3",
): List<PetalShortcut> {
    val jsonStr = sp.getString(scopedKey, null) ?: defaultFallbackKey?.let { sp.getString(it, null) }
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
                val shapeName = if (obj.has("shapeName")) obj.optString("shapeName") else null
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: stableLegacyShortcutId(i, label, url)
                val parsedColor = parseShortcutHexColor(colorStr)
                list.add(PetalShortcut(label = label, url = url, siteId = siteId, containerColor = parsedColor, shapeName = shapeName, id = id))
            }
            return list
        } catch (_: Throwable) { }
    }
    return emptyList()
}

fun saveHomeShortcutsToPrefs(
    sp: android.content.SharedPreferences,
    scopedKey: String,
    list: List<PetalShortcut>,
) {
    val array = JSONArray()
    for (item in list) {
        val obj = JSONObject()
        obj.put("id", item.id)
        obj.put("label", item.label)
        obj.put("url", item.url)
        obj.put("siteId", item.siteId)
        val argb = item.containerColor.toArgb()
        obj.put("color", String.format("#%08X", argb))
        if (item.shapeName != null) {
            obj.put("shapeName", item.shapeName)
        }
        array.put(obj)
    }
    sp.edit().putString(scopedKey, array.toString()).apply()
}

fun loadHomeShortcuts(context: Context): List<PetalShortcut> {
    val sp = PreferenceManager.getDefaultSharedPreferences(context)
    val scopedKey = com.petal.browser.profile.PetalProfileManager.getScopedKey("sp_custom_home_shortcuts_json_v3")
    val fallback = if (com.petal.browser.profile.PetalProfileManager.activeProfile.isDefault) {
        "sp_custom_home_shortcuts_json_v3"
    } else null
    return loadHomeShortcutsFromPrefs(sp, scopedKey, fallback)
}

fun fetchTopVisitedShortcuts(context: Context): List<PetalShortcut> {
    val list = mutableListOf<PetalShortcut>()
    try {
        val action = com.petal.browser.database.HistoryBookmarkRepository(context)
        action.open(false)
        val records = action.listHistory(context)
        action.close()

        val paletteColors = listOf(
            Color(0xFF4285F4), Color(0xFF34A853), Color(0xFFEA4335),
            Color(0xFFFBBC05), Color(0xFF9C27B0), Color(0xFF00BCD4)
        )

        val removedUrls = loadRemovedShortcutUrls(context)

        // Group history records by domain host to find sites visited more than 3 times
        val topSites = records
            .filter { !it.url.isNullOrBlank() && !it.url.startsWith("about:") && !it.url.startsWith("petal://") && !removedUrls.contains(it.url) }
            .groupBy {
                try { Uri.parse(it.url).host ?: it.url } catch (e: Exception) { it.url }
            }
            .entries
            .filter { it.value.size > 3 }
            .sortedByDescending { it.value.size }
            .take(12)

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
            list.add(
                PetalShortcut(
                    label = rawLabel,
                    url = firstRecord.url,
                    siteId = siteId,
                    containerColor = color,
                    id = stableLegacyShortcutId(idx, rawLabel, firstRecord.url),
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

fun saveHomeShortcuts(context: Context, list: List<PetalShortcut>) {
    val scopedKey = com.petal.browser.profile.PetalProfileManager.getScopedKey("sp_custom_home_shortcuts_json_v3")
    saveHomeShortcutsToPrefs(PreferenceManager.getDefaultSharedPreferences(context), scopedKey, list)
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
                onNewTab = { handler.onNewTab() },
                onOpenTabSwitcher = { handler.onOpenTabsOverview() }
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
    onNewTab: () -> Unit = {},
    onOpenTabSwitcher: () -> Unit = {}
) {
    setViewTreeLifecycleOwner(activity)
    setViewTreeViewModelStoreOwner(activity)
    setViewTreeSavedStateRegistryOwner(activity)
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        val sp = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
        var currentPaletteId by remember { mutableStateOf(sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId) }
        var isAmoled by remember { mutableStateOf(sp.getBoolean("sp_amoled", false)) }
        var useDynamic by remember { mutableStateOf(sp.getBoolean("useDynamicColor", isDynamicColorSupported)) }
        var isExpressiveColors by remember { mutableStateOf(sp.getBoolean("sp_expressive_colors", false)) }

        DisposableEffect(sp) {
            val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                when (key) {
                    "sp_palette_id" -> currentPaletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                    "sp_amoled" -> isAmoled = sp.getBoolean("sp_amoled", false)
                    "useDynamicColor" -> useDynamic = sp.getBoolean("useDynamicColor", isDynamicColorSupported)
                    "sp_expressive_colors" -> isExpressiveColors = sp.getBoolean("sp_expressive_colors", false)
                }
            }
            sp.registerOnSharedPreferenceChangeListener(listener)
            onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
        }

        PetalExpressiveTheme(
            paletteId = currentPaletteId,
            useAmoled = isAmoled,
            dynamicColor = useDynamic,
            expressiveColors = isExpressiveColors
        ) {
            PetalHomeScreen(
                backgroundSnapshot = null,
                onSearch = onSearch,
                onOpenShortcutUrl = onOpenShortcutUrl,
                onOpenAccountSync = onOpenAccountSync,
                onOpenBookmarksAction = onOpenBookmarks,
                onOpenHistoryAction = onOpenHistory,
                onOpenDownloadsAction = onOpenDownloads,
                onNewTabAction = onNewTab,
                onOpenTabSwitcher = onOpenTabSwitcher
            )
        }
    }
}

// ── 4. Main Petal Home Screen Composable ──────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PetalHomeScreen(
    backgroundSnapshot: ImageBitmap? = null,
    profile: PetalUserProfile = PetalUserProfileManager.currentProfile,
    onSearch: (String) -> Unit = {},
    onOpenShortcutUrl: (String) -> Unit = {},
    onOpenAccountSync: () -> Unit = {},
    onOpenBookmarksAction: () -> Unit = {},
    onOpenHistoryAction: () -> Unit = {},
    onOpenDownloadsAction: () -> Unit = {},
    onNewTabAction: () -> Unit = {},
    onOpenTabSwitcher: () -> Unit = {}
) {
    var context = LocalContext.current
    var shortcuts by remember { mutableStateOf(loadHomeShortcuts(context)) }
    var removedUrls by remember { mutableStateOf(loadRemovedShortcutUrls(context)) }
    var editingItem by remember { mutableStateOf<Pair<PetalShortcut, Boolean>?>(null) }
    var isAddingNewShortcut by remember { mutableStateOf(false) }

    // Fix (Bug 3): fetchTopVisitedShortcuts() opens SQLite on the composition (main) thread,
    // blocking the first Compose frame by 100–500ms and leaving contentFrame visually black.
    // Use LaunchedEffect + Dispatchers.IO so the screen renders immediately with an empty list
    // that is then populated once the DB query completes off-thread.
    var autoVisitedSites by remember { mutableStateOf<List<PetalShortcut>>(emptyList()) }
    LaunchedEffect(removedUrls) {
        val sites = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try { fetchTopVisitedShortcuts(context).take(8) } catch (_: Throwable) { emptyList() }
        }
        autoVisitedSites = sites
    }
    val mergedItems: List<Pair<PetalShortcut, Boolean>> = remember(shortcuts, autoVisitedSites) {
        val savedUrls = shortcuts.map { it.url }.toSet()
        val extraVisited = autoVisitedSites.filter { it.url !in savedUrls }
        // true = user-editable saved shortcut; false = auto-visited
        shortcuts.map { it to true } + extraVisited.map { it to false }
    }

    val coroutineScope = rememberCoroutineScope()
    val profileSwitchProgress = remember { Animatable(1f) }
    var showProfileSwitchSheet by remember { mutableStateOf(false) }
    var showWallpaperSheet by remember { mutableStateOf(false) }

    // Dismiss any open home-screen sub-overlay on back before the gesture reaches the root
    // PredictiveBackHandler in PetalRootApp (which would animate the entire viewport).
    val hasHomeSubOverlay = editingItem != null || isAddingNewShortcut ||
        showProfileSwitchSheet || showWallpaperSheet
    androidx.activity.compose.BackHandler(enabled = hasHomeSubOverlay) {
        when {
            editingItem != null -> editingItem = null
            isAddingNewShortcut -> isAddingNewShortcut = false
            showProfileSwitchSheet -> showProfileSwitchSheet = false
            showWallpaperSheet -> showWallpaperSheet = false
        }
    }

    // Petal browser profile transition function
    fun switchProfile(targetProfileId: String) {
        if (targetProfileId == com.petal.browser.profile.PetalProfileManager.activeProfile.id) return
        coroutineScope.launch {
            // Exit: 120ms tween fade & slide down
            profileSwitchProgress.animateTo(0f, PetalSpring.quick())
            com.petal.browser.profile.PetalProfileManager.switchProfile(context, targetProfileId)
            com.petal.browser.wallpaper.PetalWallpaperManager.loadForActiveProfile(context)
            shortcuts = loadHomeShortcuts(context)
            removedUrls = loadRemovedShortcutUrls(context)
            // Entrance: 460f stiffness / 0.78f damping ratio spring bounce
            profileSwitchProgress.animateTo(1f, PetalSpring.quick())
        }
    }

    // Initialize wallpaper, profile, and tab collections managers for active profile
    LaunchedEffect(Unit) {
        com.petal.browser.profile.PetalProfileManager.init(context)
        com.petal.browser.wallpaper.PetalWallpaperManager.init(context)
        com.petal.browser.collections.PetalCollectionManager.init(context)
    }

    val glassWallpaper = com.petal.browser.ui.glass.LocalPetalGlassWallpaper.current
        ?: com.petal.browser.ui.glass.rememberPetalGlassWallpaper()
    val homeSp = remember(context) { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    var isGlassEnabled by remember { mutableStateOf(homeSp.getBoolean("sp_liquid_glass_enabled", false)) }
    var glassBgMode by remember { mutableStateOf(homeSp.getString("sp_liquid_glass_bg_mode", "STRIDE") ?: "STRIDE") }
    var glassImageUri by remember { mutableStateOf(homeSp.getString("sp_liquid_glass_bg_image_uri", "").orEmpty()) }
    var glassBgDim by remember { mutableFloatStateOf(homeSp.getFloat("sp_liquid_glass_bg_dim", 0.35f)) }
    var glassBgBlur by remember { mutableFloatStateOf(homeSp.getFloat("sp_liquid_glass_bg_blur", 16f)) }

    DisposableEffect(homeSp) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "sp_liquid_glass_enabled" -> isGlassEnabled = homeSp.getBoolean("sp_liquid_glass_enabled", false)
                "sp_liquid_glass_bg_mode" -> glassBgMode = homeSp.getString("sp_liquid_glass_bg_mode", "STRIDE") ?: "STRIDE"
                "sp_liquid_glass_bg_image_uri" -> glassImageUri = homeSp.getString("sp_liquid_glass_bg_image_uri", "").orEmpty()
                "sp_liquid_glass_bg_dim" -> glassBgDim = homeSp.getFloat("sp_liquid_glass_bg_dim", 0.35f)
                "sp_liquid_glass_bg_blur" -> glassBgBlur = homeSp.getFloat("sp_liquid_glass_bg_blur", 16f)
            }
        }
        homeSp.registerOnSharedPreferenceChangeListener(listener)
        onDispose { homeSp.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val activeWallpaperUri = com.petal.browser.wallpaper.PetalWallpaperManager.wallpaperUri

    CompositionLocalProvider(
        com.petal.browser.ui.glass.LocalPetalGlassWallpaper provides glassWallpaper
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = if (isGlassEnabled || !activeWallpaperUri.isNullOrBlank()) Color.Transparent else MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Page background (wallpaper / themed surface). It is handed to the glass header
                // layout so the header refracts the real page and the wallpaper stays full-screen
                // behind the scrolling content.
                val homePageBackground: @Composable () -> Unit = {
                // ── Layer 0: OmniBrowser Wallpaper engine (Home Wallpaper Only) ───────────

                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (!activeWallpaperUri.isNullOrBlank()) {
                        com.petal.browser.wallpaper.PetalGlassAwareWallpaperBackground(
                            wallpaperUri = activeWallpaperUri,
                            dim = com.petal.browser.wallpaper.PetalWallpaperManager.wallpaperDim,
                            blur = com.petal.browser.wallpaper.PetalWallpaperManager.wallpaperBlur,
                            glassWallpaper = glassWallpaper,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (com.petal.browser.ui.theme.SeasonalEvent.isHalloweenActive && homeSp.getBoolean("sp_seasonal_themes_enabled", true)) {
                        com.petal.browser.ui.theme.HalloweenBackground(
                            modifier = Modifier.fillMaxSize(),
                            glassWallpaper = glassWallpaper
                        )
                    } else {
                        val isAmoled = homeSp.getBoolean("sp_amoled", false)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(if (isAmoled) Color.Black else MaterialTheme.colorScheme.background)
                        )
                    }
                }

                }

                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    com.petal.browser.ui.components.PetalGlassHeaderLayout(
                        modifier = Modifier.fillMaxSize(),
                        pageBackground = homePageBackground,
                        header = { headerModifier, headerBackdrop ->
                    com.petal.browser.ui.components.ExpressiveHeader(
                        modifier = headerModifier,
                        backdrop = headerBackdrop,
                        title = "Petal",
                        subtitle = "Personal Window to the Web",
                        hasWallpaper = !activeWallpaperUri.isNullOrBlank(),
                        actions = {
                                // Wallpaper Customizer Action Button (expressive cookie)
                                com.petal.browser.ui.components.HeaderActionIcon(
                                    icon = Icons.Rounded.Palette,
                                    contentDescription = stringResource(R.string.ui_customize_wallpaper),
                                    onClick = { showWallpaperSheet = true },
                                    emphasized = true,
                                )

                                Spacer(Modifier.width(4.dp))

                                // Expressive Profile Switcher Pill Button (Issue #23)
                                val currentProfile = com.petal.browser.profile.PetalProfileManager.activeProfile
                                val isGlass = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_containments")
                                val profilePillShape = RoundedCornerShape(20.dp)
                                // Inside the real-glass header the pill is a neutral frosted chip (no Material colours).
                                val headerGlass = com.petal.browser.ui.components.LocalHeaderGlassStyle.current
                                Surface(
                                    shape = profilePillShape,
                                    color = headerGlass?.chip
                                        ?: if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                                    border = when {
                                        headerGlass != null -> BorderStroke(0.75.dp, headerGlass.content.copy(alpha = 0.16f))
                                        isGlass -> null
                                        else -> BorderStroke(1.dp, currentProfile.getComposeColor().copy(alpha = 0.5f))
                                    },
                                    modifier = Modifier
                                        .height(34.dp)
                                        .liquidGlassChrome(shape = profilePillShape, enabled = isGlass && headerGlass == null)
                                        .clickable { showProfileSwitchSheet = true }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(currentProfile.getComposeColor().copy(alpha = 0.25f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            PetalProfileAvatar(
                                                profile = currentProfile,
                                                size = 20.dp,
                                                shape = CircleShape
                                            )
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = currentProfile.name,
                                            style = PetalText.chip,
                                            color = headerGlass?.content ?: MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 84.dp)
                                        )
                                    }
                                }
                            }
                        )

                        }
                    ) { topInset ->
                        // Main home scrolling column wrapped with Petal profile switch animation
                        val homeScrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = profileSwitchProgress.value
                                    translationY = (1f - profileSwitchProgress.value) * 14.dp.toPx()
                                    val scale = 0.97f + profileSwitchProgress.value * 0.03f
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .scrollMotionBlur(homeScrollState)
                                .verticalScroll(homeScrollState)
                                .padding(start = 20.dp, end = 20.dp, top = topInset + 12.dp, bottom = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Cap the content column width on large screens/tablets so the
                            // hero search bar and grid don't stretch edge-to-edge.
                            Column(
                                modifier = Modifier.widthIn(max = 640.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
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
                                    onEditShortcutItem = { item -> editingItem = item },
                                    onAddShortcutClick = { isAddingNewShortcut = true }
                                )

                                // ── Brave News: Privacy RSS Top Stories ──────────────
                                Spacer(Modifier.height(24.dp))
                                com.petal.browser.home.news.PetalNewsSection(
                                    onArticleClick = { url -> onOpenShortcutUrl(url) },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // ── Tab Collections Section (Firefox-style persistent groups) ──
                                LaunchedEffect(Unit) {
                                    com.petal.browser.collections.PetalCollectionManager.init(context)
                                }
                                val collections = com.petal.browser.collections.PetalCollectionManager.collections
                                if (collections.isNotEmpty()) {
                                    Spacer(Modifier.height(28.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.ui_collections),
                                            style = PetalText.sheetTitle,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(R.string.ui_saved, collections.size),
                                            style = PetalText.badge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Spacer(Modifier.height(12.dp))

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        collections.forEach { col ->
                                            var isExpanded by remember { mutableStateOf(false) }
                                            PetalHeroCard(
                                                shape = RoundedCornerShape(20.dp),
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                            modifier = Modifier.weight(1f).clickable { isExpanded = !isExpanded }
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(10.dp)
                                                                    .clip(CircleShape)
                                                                    .background(
                                                                        try {
                                                                            Color(android.graphics.Color.parseColor(col.colorHex))
                                                                        } catch (_: Exception) {
                                                                            MaterialTheme.colorScheme.primary
                                                                        }
                                                                    )
                                                            )
                                                            Text(
                                                                text = col.name,
                                                                style = PetalText.cardTitle,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Text(
                                                                text = stringResource(R.string.ui_tabs_3, col.items.size),
                                                                style = PetalText.cardBody,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }

                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            IconButton(
                                                                onClick = {
                                                                    // Open all tabs in collection
                                                                    col.items.forEach { item -> onOpenShortcutUrl(item.url) }
                                                                },
                                                                modifier = Modifier.size(32.dp)
                                                            ) {
                                                                Icon(
                                                                    Icons.Rounded.OpenInNew,
                                                                    contentDescription = stringResource(R.string.ui_open_all),
                                                                    tint = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                            }
                                                            IconButton(
                                                                onClick = {
                                                                    com.petal.browser.collections.PetalCollectionManager.deleteCollection(context, col.id)
                                                                },
                                                                modifier = Modifier.size(32.dp)
                                                            ) {
                                                                Icon(
                                                                    Icons.Rounded.DeleteOutline,
                                                                    contentDescription = stringResource(R.string.ui_delete_collection),
                                                                    tint = MaterialTheme.colorScheme.error,
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                            }
                                                        }
                                                    }

                                                    androidx.compose.animation.AnimatedVisibility(
                                                        visible = isExpanded,
                                                        enter = androidx.compose.animation.expandVertically(animationSpec = PetalSpring.calm()) + androidx.compose.animation.fadeIn(),
                                                        exit = androidx.compose.animation.shrinkVertically(animationSpec = PetalSpring.calm()) + androidx.compose.animation.fadeOut()
                                                    ) {
                                                        Column {
                                                            Spacer(Modifier.height(10.dp))
                                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                                            Spacer(Modifier.height(8.dp))
                                                            col.items.forEach { tabItem ->
                                                                Row(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .clip(RoundedCornerShape(10.dp))
                                                                        .clickable { onOpenShortcutUrl(tabItem.url) }
                                                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                                ) {
                                                                    Column(modifier = Modifier.weight(1f)) {
                                                                        Text(
                                                                            text = tabItem.title.ifBlank { tabItem.url },
                                                                            style = PetalText.listItemTitle,
                                                                            color = MaterialTheme.colorScheme.onSurface,
                                                                        )
                                                                        Text(
                                                                            text = tabItem.url,
                                                                            style = PetalText.listItemMeta,
                                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                            maxLines = 1,
                                                                            overflow = TextOverflow.Ellipsis
                                                                        )
                                                                    }
                                                                    Icon(
                                                                        Icons.Rounded.ChevronRight,
                                                                        contentDescription = null,
                                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                        modifier = Modifier.size(18.dp)
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

                                Spacer(Modifier.height(96.dp))
                            }
                        }
                    }

                    }

                    // ── Profile Switcher Modal Sheet ──────────────────────────────────
                    if (showProfileSwitchSheet) {
                        com.petal.browser.ui.components.PetalProfileSwitchSheet(
                            onDismissRequest = { showProfileSwitchSheet = false },
                            onOpenAccountSettings = onOpenAccountSync,
                            onSelectProfile = { selectedId ->
                                switchProfile(selectedId)
                            }
                        )
                    }

                    // ── Wallpaper Customizer Modal Sheet ──────────────────────────────
                    if (showWallpaperSheet) {
                        com.petal.browser.ui.components.PetalWallpaperSheet(
                            onDismissRequest = { showWallpaperSheet = false }
                        )
                    }

                    // ── Create New Shortcut Dialog ────────────────────────────────────
                    if (isAddingNewShortcut) {
                        EditShortcutDialog(
                            dialogTitle = stringResource(R.string.ui_add_shortcut),
                            initialId = null,
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

                    // ── Edit / Hide Shortcut Dialog ───────────────────────────────────
                    editingItem?.let { (current, isCustom) ->
                        EditShortcutDialog(
                            dialogTitle = if (isCustom) stringResource(R.string.ui_edit_shortcut) else stringResource(R.string.ui_hide_from_home),
                            deleteLabelRes = if (isCustom) R.string.ui_delete else R.string.ui_hide,
                            initialId = current.id,
                            initialName = current.label,
                            initialUrl = current.url,
                            initialColor = current.containerColor,
                            initialShapeName = current.shapeName,
                            onDismiss = { editingItem = null },
                            onSave = { updatedShortcut ->
                                if (isCustom) {
                                    val newList = shortcuts.toMutableList()
                                    val idx = newList.indexOfFirst { it.id == current.id }
                                    if (idx != -1) {
                                        newList[idx] = updatedShortcut
                                    } else {
                                        newList.add(updatedShortcut)
                                    }
                                    shortcuts = newList
                                    saveHomeShortcuts(context, newList)
                                } else {
                                    // Convert the visited item to a custom shortcut and hide the
                                    // original auto-visited URL so editing never creates a duplicate.
                                    val newRemoved = removedUrls + current.url
                                    removedUrls = newRemoved
                                    saveRemovedShortcutUrls(context, newRemoved)
                                    val newList = shortcuts.toMutableList()
                                    newList.add(updatedShortcut)
                                    shortcuts = newList
                                    saveHomeShortcuts(context, newList)
                                }
                                editingItem = null
                            },
                            onDelete = {
                                if (isCustom) {
                                    val newList = shortcuts.toMutableList()
                                    newList.removeAll { it.id == current.id }
                                    shortcuts = newList
                                    saveHomeShortcuts(context, newList)
                                }
                                // Also blocklist URL so it does not reappear under auto-visited
                                val newRemoved = removedUrls.toMutableSet()
                                newRemoved.add(current.url)
                                removedUrls = newRemoved
                                saveRemovedShortcutUrls(context, newRemoved)
                                editingItem = null
                            }
                        )
                    }

                    // ── Built-in Crash Recovery Dialog Host (Auto / Off) ──
                    com.petal.browser.ui.components.PetalCrashRecoveryHost()
            }
        }
    }
}

// ── 5. Shortcut Grid ──────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun PetalShortcutGrid(
    items: List<Pair<PetalShortcut, Boolean>>, // shortcut to isEditable
    onOpenShortcut: (PetalShortcut) -> Unit,
    onEditShortcutItem: (Pair<PetalShortcut, Boolean>) -> Unit,
    onAddShortcutClick: () -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        maxItemsInEachRow = 4,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items.forEachIndexed { index, item ->
            val (shortcut, isEditable) = item
            key(shortcut.id) {
                Box(modifier = Modifier.width(68.dp)) {
                    ShortcutTile(
                        shortcut = shortcut,
                        isEditable = isEditable,
                        index = index,
                        onClick = { onOpenShortcut(shortcut) },
                        onLongClick = { onEditShortcutItem(item) }
                    )
                }
            }
        }

        // "+" Add tile at end
        Box(modifier = Modifier.width(68.dp)) {
            AddShortcutTile(index = items.size, onClick = onAddShortcutClick)
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
    index: Int = 0,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = PetalSpring.quick(),
        label = "shortcut_press_scale"
    )

    val fetchFaviconsEnabled = rememberFetchShortcutFaviconsEnabled()
    val faviconUrl = remember(shortcut.url, fetchFaviconsEnabled) {
        if (fetchFaviconsEnabled) getFaviconUrl(shortcut.url) else null
    }
    var isImageError by remember(shortcut.url, fetchFaviconsEnabled) { mutableStateOf(false) }

    val defaultShape = homeShortcutShape(index, shortcut.id.ifBlank { shortcut.url.ifBlank { shortcut.label } })
    val tileShape = remember(shortcut.shapeName, index, shortcut.id) {
        PetalMaterialShapes.resolveByName(shortcut.shapeName)?.toShape() ?: defaultShape
    }

    val isGlass = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_containments")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .homeLaunchEntrance(3 + index)
            .padding(vertical = 4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(60.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(elevation = if (isGlass) 0.dp else 2.dp, shape = tileShape.toSafeShadowShape(), clip = false)
                .liquidGlassChrome(shape = tileShape, enabled = isGlass)
                .clip(tileShape)
                .background(if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            if (!faviconUrl.isNullOrEmpty() && !isImageError) {
                // 0-padding full-bleed clipping to the expressive shape
                AsyncImage(
                    model = faviconUrl,
                    contentDescription = shortcut.label,
                    contentScale = ContentScale.Crop,
                    onError = { isImageError = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(tileShape)
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(tileShape)
                        .background(shortcut.containerColor.copy(alpha = 0.22f))
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
            style = PetalText.tabLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AddShortcutTile(index: Int = 0, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .homeLaunchEntrance(3 + index)
            .padding(vertical = 4.dp)
    ) {
        val isGlass = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_containments")
        val addTileShape = homeShortcutShape(index)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(60.dp)
                .liquidGlassChrome(shape = addTileShape, enabled = isGlass)
                .clip(addTileShape)
                .background(if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.ui_add_shortcut),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = stringResource(R.string.ui_add),
            style = PetalText.tabLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun homeShortcutShape(index: Int, key: Any? = null): Shape {
    val mixedKey = if (key != null) (key.hashCode() xor (index * 31)) else (index * 7919)
    return com.petal.browser.ui.theme.rememberExpressiveShape(mixedKey)
}

// ── 6. Search Bar ─────────────────────────────────────────────────────────

@Composable
private fun PetalSearchBar(onSearch: (String) -> Unit) {
    // This is a decoy bar, matching Chrome's home-screen search box behavior.
    // It never accepts typed input itself - tapping anywhere on it (including
    // the placeholder text area) hands off immediately to the real full-screen
    // omnibox (PetalOmniboxPage, opened via onSearch("") -> showOmniboxPage("")
    // in MainActivity), which is where live suggestions/history/voice/engine
    // preference all actually live. Do not reintroduce a local TextField or
    // local suggestion-fetching here - that previously duplicated and shadowed
    // the omnibox page instead of opening it.
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = PetalSpring.quick(),
        label = "search_bar_press_scale"
    )
    // Real on-screen bounds of this pill: the omnibox page grows out of / collapses back into it.
    val pillBounds = remember { arrayOfNulls<androidx.compose.ui.geometry.Rect>(1) }

    PetalFloatingToolbar(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .homeLaunchEntrance(2)
            .onGloballyPositioned { pillBounds[0] = it.boundsInWindow() }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current
            ) {
                com.petal.browser.ui.components.PetalOmniboxOrigin.mark(pillBounds[0], -1f)
                onSearch("")
            }
    ) {
            // One inner row owns all spacing. PetalFloatingToolbar adds 12dp between its direct
            // children and IconButtons default to 48dp, which made every gap in this bar too wide.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = stringResource(R.string.ui_search),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.ui_search_or_type_url_2),
                    style = PetalText.rowTitle.copy(fontWeight = FontWeight.Normal),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(2.dp))

                IconButton(
                    onClick = {
                        if (activity != null) {
                            com.petal.browser.ui.components.PetalAiSearchBridge.showAiSearchResult(activity, "")
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = stringResource(R.string.ui_petal_ai),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        if (activity != null) {
                            com.petal.browser.ui.components.PetalVoiceSearchBridge.showVoiceSearchSheet(activity) { result ->
                                if (result.isNotBlank()) onSearch(result)
                            }
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Rounded.Mic,
                        contentDescription = stringResource(R.string.ui_voice_search_2),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
    }
}

// ── 6b. Greeting Tagline ─────────────────────────────────────────────────────
// Replaces the old quick-shortcuts row (bookmarks/history/downloads/new tab).
// Shows a rotating, personalized greeting where the search bar used to sit.
// Holds a process-singleton greeting template chosen when the app starts,
// ensuring the greeting stays identical across all tabs and only changes when
// the app is restarted.

private val generalGreetingTaglines = listOf(
    "Welcome back, %s—the web is ready whenever you are.",
    "What are you curious about today, %s?",
    "Ready to discover something amazing, %s?",
    "%s, here is your personal window to the web.",
    "Search deeper and expand your world, %s.",
    "Bring your biggest ideas to life today, %s.",
    "Your next breakthrough starts right here, %s.",
    "Designed for your speed and creativity, %s.",
    "Stay inspired and keep building, %s.",
    "Good to see you again, %s—let's explore.",
    "Clear mind and a fresh tab, %s.",
    "Everything is set up and waiting for you, %s.",
    "Your digital workspace is ready, %s."
)

private val morningGreetingTaglines = listOf(
    "Good morning, %s! Start the day with a fresh perspective."
)

private val afternoonGreetingTaglines = listOf(
    "Good afternoon, %s—keep that momentum rolling."
)

private val eveningGreetingTaglines = listOf(
    "Good evening, %s—time to unwind and explore."
)

private object PetalSessionGreeting {
    private var sessionTemplate: String? = null

    fun getGreeting(username: String): String {
        return try {
            if (sessionTemplate == null) {
                val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                val timeSpecificTaglines = when {
                    hour in 4..11 -> morningGreetingTaglines
                    hour in 12..16 -> afternoonGreetingTaglines
                    else -> eveningGreetingTaglines
                }
                val pool = generalGreetingTaglines + timeSpecificTaglines
                sessionTemplate = pool.randomOrNull() ?: "Welcome back, %s—the web is ready whenever you are."
            }
            (sessionTemplate ?: "Welcome back, %s").replace("%s", username)
        } catch (_: Throwable) {
            "Welcome back, $username"
        }
    }
}

@Composable
private fun PetalGreetingTagline(profile: com.petal.browser.account.PetalUserProfile) {
    val context = LocalContext.current
    val username = profile.displayName.ifBlank { "Petal Explorer" }

    var updateWelcomeMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName
            val lastSeenVersion = sp.getString("sp_last_seen_welcome_version", "")
            if (!currentVersion.isNullOrBlank() && currentVersion != lastSeenVersion) {
                sp.edit().putString("sp_last_seen_welcome_version", currentVersion).apply()
                updateWelcomeMessage = "Welcome to Petal v$currentVersion! 🎉 Enjoy the latest updates."
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    val tagline = remember(username, updateWelcomeMessage) {
        if (!updateWelcomeMessage.isNullOrBlank()) {
            updateWelcomeMessage!!
        } else {
            PetalSessionGreeting.getGreeting(username)
        }
    }

    PetalHeroCard(
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .homeLaunchEntrance(1)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            PetalGroupIconBadge(Icons.Rounded.AutoAwesome, modifier = Modifier.size(36.dp), size = 36.dp, iconSize = 20.dp)

            Spacer(Modifier.width(14.dp))

            Text(
                text = tagline,
                style = PetalText.rowTitle,
                color = if (updateWelcomeMessage != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f)
            )
        }
    }
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
            Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.ui_youtube), tint = tint, modifier = Modifier.size(26.dp))
        }
        "google", "search" -> {
            Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.ui_google), tint = tint, modifier = Modifier.size(24.dp))
        }
        "github" -> {
            androidx.compose.foundation.Image(
                painter = painterResource(com.petal.browser.R.drawable.ic_shortcut_github),
                contentDescription = stringResource(R.string.ui_github),
                modifier = Modifier.size(26.dp)
            )
        }
        "wikipedia" -> {
            androidx.compose.foundation.Image(
                painter = painterResource(com.petal.browser.R.drawable.ic_shortcut_wikipedia),
                contentDescription = stringResource(R.string.ui_wikipedia),
                modifier = Modifier.size(26.dp)
            )
        }
        "duckduckgo" -> {
            androidx.compose.foundation.Image(
                painter = painterResource(com.petal.browser.R.drawable.ic_shortcut_duckduckgo),
                contentDescription = stringResource(R.string.ui_duckduckgo),
                modifier = Modifier.size(26.dp)
            )
        }
        "weather" -> {
            Icon(Icons.Rounded.WbSunny, contentDescription = stringResource(R.string.ui_google_weather), tint = Color(0xFFFFD54F), modifier = Modifier.size(24.dp))
        }
        "globe" -> {
            Icon(Icons.Rounded.Public, contentDescription = stringResource(R.string.ui_web), tint = tint, modifier = Modifier.size(24.dp))
        }
        "star" -> {
            Icon(Icons.Rounded.Star, contentDescription = stringResource(R.string.ui_star), tint = tint, modifier = Modifier.size(24.dp))
        }
        "heart" -> {
            Icon(Icons.Rounded.Favorite, contentDescription = stringResource(R.string.ui_heart), tint = tint, modifier = Modifier.size(24.dp))
        }
        "bookmark" -> {
            Icon(Icons.Rounded.Bookmark, contentDescription = stringResource(R.string.ui_bookmark), tint = tint, modifier = Modifier.size(24.dp))
        }
        "lock" -> {
            Icon(Icons.Rounded.Lock, contentDescription = stringResource(R.string.ui_lock), tint = tint, modifier = Modifier.size(24.dp))
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
            Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.ui_youtube), tint = Color.White, modifier = Modifier.size(28.dp))
        }
        "google", "search" -> {
            Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.ui_google), tint = Color.White, modifier = Modifier.size(26.dp))
        }
        "github" -> {
            androidx.compose.foundation.Image(
                painter = painterResource(com.petal.browser.R.drawable.ic_shortcut_github),
                contentDescription = stringResource(R.string.ui_github),
                modifier = Modifier.size(28.dp)
            )
        }
        "wikipedia" -> {
            androidx.compose.foundation.Image(
                painter = painterResource(com.petal.browser.R.drawable.ic_shortcut_wikipedia),
                contentDescription = stringResource(R.string.ui_wikipedia),
                modifier = Modifier.size(28.dp)
            )
        }
        "duckduckgo" -> {
            androidx.compose.foundation.Image(
                painter = painterResource(com.petal.browser.R.drawable.ic_shortcut_duckduckgo),
                contentDescription = stringResource(R.string.ui_duckduckgo),
                modifier = Modifier.size(28.dp)
            )
        }
        "weather" -> {
            Icon(Icons.Rounded.WbSunny, contentDescription = stringResource(R.string.ui_google_weather), tint = Color(0xFFFFD54F), modifier = Modifier.size(26.dp))
        }
        "globe" -> {
            Icon(Icons.Rounded.Public, contentDescription = stringResource(R.string.ui_web), tint = Color.White, modifier = Modifier.size(26.dp))
        }
        "star" -> {
            Icon(Icons.Rounded.Star, contentDescription = stringResource(R.string.ui_star), tint = Color.White, modifier = Modifier.size(26.dp))
        }
        "heart" -> {
            Icon(Icons.Rounded.Favorite, contentDescription = stringResource(R.string.ui_heart), tint = Color.White, modifier = Modifier.size(26.dp))
        }
        "bookmark" -> {
            Icon(Icons.Rounded.Bookmark, contentDescription = stringResource(R.string.ui_bookmark), tint = Color.White, modifier = Modifier.size(26.dp))
        }
        "lock" -> {
            Icon(Icons.Rounded.Lock, contentDescription = stringResource(R.string.ui_lock), tint = Color.White, modifier = Modifier.size(26.dp))
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

private fun Modifier.horizontalScrollEdgeFade(
    scrollState: androidx.compose.foundation.ScrollState,
    fadeWidth: Dp = 20.dp,
): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val fadePx = fadeWidth.toPx().coerceAtMost(size.width / 3f)
        if (scrollState.canScrollBackward && fadePx > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startX = 0f,
                    endX = fadePx,
                ),
                size = androidx.compose.ui.geometry.Size(fadePx, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
        if (scrollState.canScrollForward && fadePx > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startX = size.width - fadePx,
                    endX = size.width,
                ),
                topLeft = androidx.compose.ui.geometry.Offset(size.width - fadePx, 0f),
                size = androidx.compose.ui.geometry.Size(fadePx, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
    }

@Composable
private fun shapeGroupLabel(group: PetalShapeGroup): String = when (group) {
    PetalShapeGroup.BASIC -> stringResource(R.string.ui_shape_cat_basic)
    PetalShapeGroup.COOKIE -> stringResource(R.string.ui_shape_cat_cookie)
    PetalShapeGroup.DYNAMIC -> stringResource(R.string.ui_shape_cat_dynamic)
    PetalShapeGroup.ORGANIC -> stringResource(R.string.ui_shape_cat_organic)
    PetalShapeGroup.ENERGETIC -> stringResource(R.string.ui_shape_cat_energetic)
}

private fun deriveShortcutSiteId(url: String): String = try {
    val host = Uri.parse(url).host.orEmpty()
    when {
        host.contains("youtube") -> "youtube"
        host.contains("github") -> "github"
        host.contains("wikipedia") -> "wikipedia"
        host.contains("duckduckgo") -> "duckduckgo"
        host.contains("google") -> "google"
        else -> "globe"
    }
} catch (_: Throwable) {
    "globe"
}

private fun deriveShortcutName(url: String): String? = try {
    Uri.parse(url).host
        ?.removePrefix("www.")
        ?.substringBefore(".")
        ?.takeIf { it.isNotBlank() }
        ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
} catch (_: Throwable) {
    null
}

@Composable
private fun EditShortcutDialog(
    dialogTitle: String,
    deleteLabelRes: Int = R.string.ui_delete,
    initialId: String? = null,
    initialName: String,
    initialUrl: String,
    initialColor: Color,
    initialShapeName: String? = null,
    onDismiss: () -> Unit,
    onSave: (PetalShortcut) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val resolvedInitialShape = remember(initialShapeName) {
        PetalMaterialShapes.resolveByName(initialShapeName) ?: PetalMaterialShapes.Flower
    }
    var nameText by remember(initialName) { mutableStateOf(initialName) }
    var urlText by remember(initialUrl) { mutableStateOf(initialUrl) }
    var urlTouched by remember(initialUrl) { mutableStateOf(initialUrl.isNotBlank()) }
    var selectedColor by remember(initialColor) { mutableStateOf(initialColor) }
    var selectedShapeName by remember(resolvedInitialShape) { mutableStateOf(resolvedInitialShape.name) }
    var selectedGroup by remember(resolvedInitialShape) { mutableStateOf(PetalMaterialShapes.groupFor(resolvedInitialShape)) }

    val urlValidation = remember(urlText) { validateShortcutUrl(urlText) }
    val showUrlError = !urlValidation.isValid && (urlTouched || urlText.isNotBlank())

    val fetchFaviconsEnabled = rememberFetchShortcutFaviconsEnabled()
    var debouncedUrl by remember(initialUrl) { mutableStateOf(initialUrl) }
    LaunchedEffect(urlText, fetchFaviconsEnabled) {
        if (!fetchFaviconsEnabled) {
            debouncedUrl = ""
        } else {
            kotlinx.coroutines.delay(400L)
            debouncedUrl = urlText
        }
    }
    val currentFaviconUrl = remember(debouncedUrl, fetchFaviconsEnabled) {
        val debouncedValidation = validateShortcutUrl(debouncedUrl)
        if (fetchFaviconsEnabled && debouncedValidation.isValid && debouncedValidation.normalizedUrl != null) {
            getFaviconUrl(debouncedValidation.normalizedUrl)
        } else null
    }
    var isImageError by remember(currentFaviconUrl) { mutableStateOf(false) }

    val selectedShapeHolder = remember(selectedShapeName) {
        PetalMaterialShapes.resolveByName(selectedShapeName) ?: PetalMaterialShapes.Flower
    }
    val previewTileShape = selectedShapeHolder.toShape()
    val previewDerivedTitle = remember(nameText, urlValidation.normalizedUrl) {
        nameText.trim().ifBlank {
            urlValidation.normalizedUrl?.let { deriveShortcutName(it) } ?: "Shortcut"
        }
    }
    val previewSiteId = remember(urlValidation.normalizedUrl) {
        urlValidation.normalizedUrl?.let { deriveShortcutSiteId(it) } ?: "globe"
    }

    val isGlass = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_containments") &&
        !com.petal.browser.motion.MotionGate.isLowEndDevice(context)

    com.petal.browser.ui.containment.PetalMaterialAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle) },
        text = {
            // No verticalScroll here: PetalMaterialAlertDialog already scrolls its whole content, and a
            // nested vertical scroll gets infinite max height and crashes on open.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                TextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text(stringResource(R.string.ui_name)) },
                    singleLine = true,
                    glass = isGlass,
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                TextField(
                    value = urlText,
                    onValueChange = {
                        urlText = it
                        urlTouched = true
                    },
                    label = { Text(stringResource(R.string.ui_url)) },
                    singleLine = true,
                    placeholder = { Text("example.com") },
                    isError = showUrlError,
                    supportingText = if (!urlValidation.isValid && urlValidation.errorRes != null) {
                        { Text(stringResource(urlValidation.errorRes)) }
                    } else null,
                    glass = isGlass,
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Color picker swatches
                Text(
                    text = stringResource(R.string.ui_shortcut_color),
                    style = PetalText.sectionHeader,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val colorRowScrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScrollEdgeFade(colorRowScrollState)
                        .horizontalScroll(colorRowScrollState)
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableColors.forEach { (swatchColor, colorLabel) ->
                        val isColorSelected = swatchColor.toArgb() == selectedColor.toArgb()
                        val checkScale by animateFloatAsState(
                            targetValue = if (isColorSelected) 1f else 0f,
                            animationSpec = PetalSpring.playful(),
                            label = "color_check_scale"
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .then(if (isGlass) Modifier.liquidGlassChrome(CircleShape, enabled = true, sheenIntensity = 0.35f) else Modifier)
                                .border(
                                    width = if (isColorSelected) 2.5.dp else 1.dp,
                                    color = if (isColorSelected) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.28f),
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = swatchColor }
                                .semantics {
                                    contentDescription = colorLabel
                                    selected = isColorSelected
                                    role = Role.RadioButton
                                }
                        ) {
                            if (checkScale > 0.05f) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .graphicsLayer { scaleX = checkScale; scaleY = checkScale }
                                )
                            }
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.ui_material_3_expressive_shape),
                    style = PetalText.sectionHeader,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Category filter chips above the shape carousel so 35 shapes are organized cleanly
                val categoryScrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScrollEdgeFade(categoryScrollState)
                        .horizontalScroll(categoryScrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PetalShapeGroup.entries.forEach { group ->
                        val catSelected = group == selectedGroup
                        val catLabel = shapeGroupLabel(group)
                        val catShape = RoundedCornerShape(50)
                        val catContainer = when {
                            catSelected -> MaterialTheme.colorScheme.primaryContainer
                            isGlass -> com.petal.browser.ui.containment.petalGroupSurfaceColor()
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(catShape)
                                .background(catContainer, catShape)
                                .then(
                                    if (isGlass) {
                                        Modifier.liquidGlassChrome(
                                            shape = catShape,
                                            enabled = true,
                                            tintColor = if (catSelected) MaterialTheme.colorScheme.primaryContainer else Color.Unspecified,
                                            sheenIntensity = 0.35f,
                                            pressFx = true
                                        )
                                    } else Modifier
                                )
                                .border(
                                    width = if (catSelected) 1.5.dp else if (isGlass) 0.dp else 0.5.dp,
                                    color = if (catSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = catShape
                                )
                                .clickable { selectedGroup = group }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .semantics {
                                    contentDescription = catLabel
                                    selected = catSelected
                                    role = Role.Tab
                                }
                        ) {
                            Text(
                                text = catLabel,
                                style = PetalText.chip,
                                color = if (catSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Shape selector horizontal carousel drawing filled 48dp expressive shapes with left/right fade edges
                val categoryShapes = remember(selectedGroup) { PetalMaterialShapes.shapesInGroup(selectedGroup) }
                val shapeRowScrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScrollEdgeFade(shapeRowScrollState)
                        .horizontalScroll(shapeRowScrollState)
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    categoryShapes.forEach { shapeHolder ->
                        val isSelected = shapeHolder.name.equals(selectedShapeHolder.name, ignoreCase = true)
                        val shape = shapeHolder.toShape()
                        val selectionScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.06f else 0.96f,
                            animationSpec = PetalSpring.playful(),
                            label = "shape_chip_scale"
                        )
                        val checkAlpha by animateFloatAsState(
                            targetValue = if (isSelected) 1f else 0f,
                            animationSpec = PetalSpring.quick(),
                            label = "shape_check_alpha"
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .graphicsLayer {
                                    scaleX = selectionScale
                                    scaleY = selectionScale
                                }
                                .clip(shape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else if (isGlass) com.petal.browser.ui.containment.petalGroupSurfaceColor()
                                    else MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                                .then(
                                    if (isGlass) {
                                        Modifier.liquidGlassChrome(
                                            shape = shape,
                                            enabled = true,
                                            tintColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                            sheenIntensity = 0.40f,
                                            pressFx = true
                                        )
                                    } else Modifier
                                )
                                .border(
                                    width = if (isSelected) 2.5.dp else if (isGlass) 0.dp else 0.75.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outlineVariant,
                                    shape = shape
                                )
                                .clickable {
                                    selectedShapeName = shapeHolder.name
                                }
                                .semantics {
                                    contentDescription = shapeHolder.name
                                    selected = isSelected
                                    role = Role.RadioButton
                                }
                        ) {
                            if (checkAlpha > 0.05f) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .graphicsLayer {
                                            alpha = checkAlpha
                                            scaleX = checkAlpha
                                            scaleY = checkAlpha
                                        }
                                )
                            }
                        }
                    }
                }

                // Live Preview at exact home screen tile size (60.dp) with 0-padding clipping and label
                val previewRowShape = RoundedCornerShape(16.dp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlassChrome(shape = previewRowShape, enabled = isGlass)
                        .background(
                            if (isGlass) com.petal.browser.ui.containment.petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerLow,
                            previewRowShape
                        )
                        .padding(12.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(68.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(60.dp)
                                .then(
                                    if (isGlass) Modifier.liquidGlassChrome(previewTileShape, enabled = true, sheenIntensity = 0.45f)
                                    else Modifier.shadow(elevation = 2.dp, shape = previewTileShape.toSafeShadowShape(), clip = false)
                                )
                                .clip(previewTileShape)
                                .background(if (isGlass) com.petal.browser.ui.containment.petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest)
                        ) {
                            if (!currentFaviconUrl.isNullOrEmpty() && !isImageError) {
                                AsyncImage(
                                    model = currentFaviconUrl,
                                    contentDescription = previewDerivedTitle,
                                    contentScale = ContentScale.Crop,
                                    onError = { isImageError = true },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(previewTileShape)
                                )
                            } else {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(previewTileShape)
                                        .background(selectedColor.copy(alpha = 0.22f))
                                ) {
                                    SiteBrandIconTinted(
                                        siteId = previewSiteId,
                                        label = previewDerivedTitle,
                                        tint = selectedColor
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = previewDerivedTitle,
                            style = PetalText.tabLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ui_shortcut_preview),
                            style = PetalText.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = previewDerivedTitle,
                            style = PetalText.rowTitle,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = selectedShapeHolder.name,
                            style = PetalText.badge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            val onSaveAction = {
                val finalUrl = urlValidation.normalizedUrl
                if (finalUrl != null) {
                    val derivedName = nameText.trim().ifBlank {
                        deriveShortcutName(finalUrl) ?: "Shortcut"
                    }
                    onSave(
                        PetalShortcut(
                            label = derivedName,
                            url = finalUrl,
                            siteId = deriveShortcutSiteId(finalUrl),
                            containerColor = selectedColor,
                            shapeName = selectedShapeHolder.name,
                            id = initialId ?: java.util.UUID.randomUUID().toString(),
                        )
                    )
                }
            }

            if (onDelete != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onDelete,
                        shape = petalConnectedShape(0, 3),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(stringResource(deleteLabelRes), maxLines = 1)
                    }
                    FilledTonalButton(
                        onClick = onDismiss,
                        shape = petalConnectedShape(1, 3),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(stringResource(R.string.ui_cancel), maxLines = 1)
                    }
                    Button(
                        onClick = onSaveAction,
                        enabled = urlValidation.isValid,
                        shape = petalConnectedShape(2, 3),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(stringResource(R.string.ui_save), maxLines = 1)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onDismiss,
                        shape = petalConnectedShape(0, 2),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(stringResource(R.string.ui_cancel), maxLines = 1)
                    }
                    Button(
                        onClick = onSaveAction,
                        enabled = urlValidation.isValid,
                        shape = petalConnectedShape(1, 2),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(stringResource(R.string.ui_save), maxLines = 1)
                    }
                }
            }
        },
        dismissButton = null
    )
}
