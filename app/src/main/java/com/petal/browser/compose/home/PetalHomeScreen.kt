/*
 * PetalHomeScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive home screen for Petal Browser with customizable 5-shortcut
 * bloom ring, interactive shortcut editor dialog, custom icon & color selection,
 * search bar, and top actions.
 */

package com.petal.browser.compose.home

import android.content.Context
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
    return defaultPetalShortcuts
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

val petalShapes: List<Shape> = listOf(
    RoundedCornerShape(topStart = 28.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 28.dp),
    RoundedCornerShape(topStart = 8.dp, topEnd = 28.dp, bottomStart = 28.dp, bottomEnd = 8.dp),
    CutCornerShape(16.dp),
    CircleShape,
    RoundedCornerShape(20.dp)
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
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
    var searchText by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(searchText) {
        if (searchText.trim().length >= 2) {
            com.petal.browser.unit.SearchSuggestionsManager.fetchSuggestions(searchText.trim()) { results ->
                suggestions = results.take(6)
            }
        } else {
            suggestions = emptyList()
        }
    }

    val isSearching = searchText.isNotBlank()
    val searchShapeCorner by animateFloatAsState(
        targetValue = if (isSearching) 16f else 28f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "M3ESearchShape"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(searchShapeCorner.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = if (isSearching) 6.dp else 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                TextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    placeholder = {
                        Text(
                            "Search or type web address",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (searchText.isNotBlank()) {
                                onSearch(searchText.trim())
                            }
                        }
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.weight(1f)
                )
                if (searchText.isNotBlank()) {
                    IconButton(onClick = {
                        searchText = ""
                        suggestions = emptyList()
                    }) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onSearch(searchText.trim()) }) {
                        Icon(
                            Icons.Rounded.ArrowForward,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    suggestions.forEach { suggestion ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSearch(suggestion)
                                }
                                .padding(horizontal = 18.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                Icons.Rounded.TrendingUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.Rounded.NorthWest,
                                contentDescription = "Insert",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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
        // Center 5-Petal Flower App Icon Button for customizing shortcuts
        Surface(
            onClick = onAddShortcutClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.size(budSize),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(6.dp)) {
                AsyncImage(
                    model = com.petal.browser.R.mipmap.ic_launcher_round,
                    contentDescription = "Manage shortcuts",
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
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
            Icon(Icons.Rounded.Code, contentDescription = "GitHub", tint = Color.White, modifier = Modifier.size(26.dp))
        }
        "wikipedia" -> {
            Text(
                "W",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                color = Color.White
            )
        }
        "duckduckgo" -> {
            Icon(Icons.Rounded.Shield, contentDescription = "DuckDuckGo", tint = Color.White, modifier = Modifier.size(26.dp))
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
