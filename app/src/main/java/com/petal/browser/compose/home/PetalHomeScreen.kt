/*
 * PetalHomeScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive home screen for Petal Browser with customizable 5-shortcut
 * bloom ring, interactive shortcut editor dialog, custom icon & color selection,
 * search bar, and top actions.
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
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
        if (first) {
            moveTo(x, y)
            first = false
        } else {
            lineTo(x, y)
        }
    }
    close()
}

val ScallopShape: Shape = com.petal.browser.ui.components.ScallopedShape(lobes = 8, depth = 0.16f)

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

val petalShapes: List<Shape> = listOf(
    FlowerShape,
    ScallopShape,
    CloverShape,
    StarburstShape,
    ArchShape
)

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
                onOpenAccountSync = { handler.onOpenAccountSync() }
            )
        }
    }
}

// ── 3. Compose View Host Extension ────────────────────────────────────────

fun ComposeView.setupExpressiveHomeScreen(
    activity: ComponentActivity,
    onSearch: (String) -> Unit,
    onOpenShortcutUrl: (String) -> Unit,
    onOpenAccountSync: () -> Unit
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
                onOpenAccountSync = onOpenAccountSync
            )
        }
    }
}

// ── 3. Main Petal Home Screen Composable ──────────────────────────────────

@Composable
fun PetalHomeScreen(
    accountViewModel: AccountViewModel,
    onSearch: (String) -> Unit,
    onOpenShortcutUrl: (String) -> Unit,
    onOpenAccountSync: () -> Unit
) {
    val context = LocalContext.current
    var shortcuts by remember { mutableStateOf(loadHomeShortcuts(context)) }
    var editingSlotIndex by remember { mutableStateOf<Int?>(null) }

    val profile = accountViewModel.profileState
    val isSignedIn = profile.isSignedIn
    val greetingName = profile.displayName

    val onOpenShortcut: (PetalShortcut) -> Unit = { shortcut ->
        onOpenShortcutUrl(shortcut.url)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Bar Profile / Sync Action Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onOpenAccountSync,
                        modifier = Modifier.size(44.dp)
                    ) {
                        ProfileAvatarDisplay(profile = profile, sizeDp = 36)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Centered App Logo & Greeting Container
                val nameDisplay = remember(greetingName) {
                    val name = greetingName?.trim()?.take(15) ?: ""
                    if (name.isNotEmpty()) name else "Explorer"
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .size(76.dp)
                                .padding(4.dp)
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        onOpenShortcutUrl("petal://settings?category=api_integrations")
                                    }
                                )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                com.petal.browser.ui.components.PetalLoadingLottie(modifier = Modifier.size(56.dp))
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        val fullGreetingText = "${getTimeGreeting()}, $nameDisplay"
                        val fontSize = when {
                            fullGreetingText.length > 24 -> 20.sp
                            fullGreetingText.length > 18 -> 24.sp
                            else -> 28.sp
                        }

                        Text(
                            text = fullGreetingText,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = fontSize,
                                letterSpacing = (-0.4).sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                PetalSearchBar(onSearch = onSearch)

                Spacer(Modifier.height(24.dp))

                // 5-Petal Bloom Shortcuts Ring (No animations, clean static layout)
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PetalBloom(
                        shortcuts = shortcuts,
                        onOpenShortcut = onOpenShortcut,
                        onAddShortcutClick = { editingSlotIndex = 0 },
                        onEditShortcutSlot = { index -> editingSlotIndex = index }
                    )
                }

                val autoVisitedSites = remember { fetchTopVisitedShortcuts(context).take(8) }
                if (autoVisitedSites.isNotEmpty()) {
                    Spacer(Modifier.height(28.dp))

                    Text(
                        text = "Frequently Visited",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        autoVisitedSites.forEachIndexed { idx, site ->
                            val m3Shape = petalShapes[idx % petalShapes.size]
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(68.dp)
                                    .clickable { onOpenShortcutUrl(site.url) }
                            ) {
                                Surface(
                                    shape = m3Shape,
                                    color = site.containerColor,
                                    modifier = Modifier.size(54.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        SiteBrandIcon(siteId = site.siteId, label = site.label)
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = site.label,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(96.dp))
            }
        }

        // Edit Shortcut Dialog
        editingSlotIndex?.let { slotIndex ->
            EditShortcutDialog(
                slotIndex = slotIndex,
                currentShortcut = shortcuts.getOrElse(slotIndex) { defaultPetalShortcuts[slotIndex % defaultPetalShortcuts.size] },
                onDismiss = { editingSlotIndex = null },
                onSave = { updatedShortcut ->
                    val newList = shortcuts.toMutableList()
                    if (slotIndex in newList.indices) {
                        newList[slotIndex] = updatedShortcut
                    } else {
                        newList.add(updatedShortcut)
                    }
                    shortcuts = newList
                    saveHomeShortcuts(context, newList)
                    editingSlotIndex = null
                }
            )
        }
    }
}

private fun getTimeGreeting(): String {
    return when (java.time.LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
}

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

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onSearch("") },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
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
                    contentDescription = "AI Web Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val voiceLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
                    val spokenText = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                    if (!spokenText.isNullOrBlank()) {
                        onSearch(spokenText)
                    }
                }
            }
            IconButton(onClick = {
                try {
                    val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak to search...")
                    }
                    voiceLauncher.launch(intent)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Voice search is not supported on this device", android.widget.Toast.LENGTH_SHORT).show()
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PetalBloom(
    shortcuts: List<PetalShortcut>,
    onOpenShortcut: (PetalShortcut) -> Unit,
    onAddShortcutClick: () -> Unit,
    onEditShortcutSlot: (Int) -> Unit
) {
    val petalSize = 64.dp
    val budSize = 56.dp
    val ringRadius = 112.dp

    RadialLayout(
        radius = ringRadius,
        modifier = Modifier
            .fillMaxWidth()
            .height(ringRadius * 2 + petalSize + 32.dp),
    ) {
        // Center 5-Petal Flower App Icon (Non-clickable)
        Surface(
            shape = FlowerShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.size(budSize),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(6.dp)) {
                AsyncImage(
                    model = com.petal.browser.R.mipmap.ic_launcher_round,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(FlowerShape)
                )
            }
        }

        // 5 Customizable Bloom Ring Shortcuts
        shortcuts.take(5).forEachIndexed { index, shortcut ->
            val shape = petalShapes[index % petalShapes.size]

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.width(72.dp)
            ) {
                Surface(
                    shape = shape,
                    color = shortcut.containerColor,
                    contentColor = shortcut.contentColor,
                    modifier = Modifier
                        .size(petalSize)
                        .combinedClickable(
                            onClick = { onOpenShortcut(shortcut) },
                            onLongClick = { onEditShortcutSlot(index) }
                        )
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        SiteBrandIcon(siteId = shortcut.siteId, label = shortcut.label)
                    }
                }
                Text(
                    text = shortcut.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

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

@Composable
private fun EditShortcutDialog(
    slotIndex: Int,
    currentShortcut: PetalShortcut,
    onDismiss: () -> Unit,
    onSave: (PetalShortcut) -> Unit
) {
    var nameText by remember(slotIndex, currentShortcut) { mutableStateOf(currentShortcut.label) }
    var urlText by remember(slotIndex, currentShortcut) { mutableStateOf(currentShortcut.url) }
    var selectedSiteId by remember(slotIndex, currentShortcut) { mutableStateOf(currentShortcut.siteId) }
    var selectedColor by remember(slotIndex, currentShortcut) { mutableStateOf(currentShortcut.containerColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Shortcut ${slotIndex + 1}") },
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
                    modifier = Modifier.fillMaxWidth()
                )

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
                    Surface(
                        shape = petalShapes[slotIndex % petalShapes.size],
                        color = selectedColor,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            SiteBrandIcon(siteId = selectedSiteId, label = nameText.ifBlank { "S" })
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

                // Choose Icon Style
                Text("Select Icon Style", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableIcons.forEach { (id, name) ->
                        FilterChip(
                            selected = (selectedSiteId == id),
                            onClick = { selectedSiteId = id },
                            label = { Text(name) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(selectedColor, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    SiteBrandIcon(siteId = id, label = name)
                                }
                            }
                        )
                    }
                }

                // Choose Palette Color
                Text("Select Container Color", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    availableColors.forEach { (color, colorName) ->
                        Surface(
                            onClick = { selectedColor = color },
                            shape = CircleShape,
                            color = color,
                            border = if (selectedColor == color) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.size(40.dp)
                        ) {
                            if (selectedColor == color) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = colorName,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
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
                    val finalUrl = if (urlText.isNotBlank() && !urlText.startsWith("http://") && !urlText.startsWith("https://")) {
                        "https://$urlText"
                    } else urlText.ifBlank { "https://google.com" }

                    onSave(
                        PetalShortcut(
                            label = nameText.ifBlank { "Shortcut ${slotIndex + 1}" },
                            url = finalUrl,
                            siteId = selectedSiteId,
                            containerColor = selectedColor
                        )
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RadialLayout(
    radius: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val width = constraints.maxWidth
        val height = radius.roundToPx() * 2 + (placeables.firstOrNull()?.height ?: 0)
        layout(width, height) {
            if (placeables.isEmpty()) return@layout
            val centerX = width / 2
            val centerY = height / 2
            val center = placeables.first()
            center.placeRelative(centerX - center.width / 2, centerY - center.height / 2)

            val petals = placeables.drop(1)
            val step = 360f / petals.size.coerceAtLeast(1)
            petals.forEachIndexed { i, placeable ->
                val angleDeg = -90f + step * i
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val x = centerX + (radius.roundToPx() * cos(angleRad)).roundToInt() - placeable.width / 2
                val y = centerY + (radius.roundToPx() * sin(angleRad)).roundToInt() - placeable.height / 2
                placeable.placeRelative(x, y)
            }
        }
    }
}
