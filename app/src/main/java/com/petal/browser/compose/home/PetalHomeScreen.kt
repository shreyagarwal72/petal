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
                } catch (e: Exception) {
                    Color(0xFF4285F4)
                }
                list.add(PetalShortcut(label, url, siteId, parsedColor))
            }
            if (list.size == 5) return list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return defaultPetalShortcuts
}

fun saveHomeShortcuts(context: Context, shortcuts: List<PetalShortcut>) {
    val sp = PreferenceManager.getDefaultSharedPreferences(context)
    try {
        val array = JSONArray()
        for (s in shortcuts.take(5)) {
            val obj = JSONObject()
            obj.put("label", s.label)
            obj.put("url", s.url)
            obj.put("siteId", s.siteId)
            val argb = s.containerColor.toArgb()
            val hexColor = String.format("#%06X", 0xFFFFFF and argb)
            obj.put("color", hexColor)
            array.put(obj)
        }
        sp.edit().putString("sp_custom_home_shortcuts_json_v3", array.toString()).apply()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private val petalShapes: List<Shape> = listOf(
    RoundedCornerShape(28.dp),
    RoundedCornerShape(topStart = 28.dp, topEnd = 12.dp, bottomEnd = 28.dp, bottomStart = 12.dp),
    RoundedCornerShape(topStart = 12.dp, topEnd = 28.dp, bottomEnd = 12.dp, bottomStart = 28.dp),
    CutCornerShape(topStart = 20.dp, bottomEnd = 20.dp),
    RoundedCornerShape(24.dp),
)

// Preset options for icon and color picking
val iconPresets = listOf(
    "youtube" to "YouTube Play",
    "google" to "Google Search",
    "github" to "GitHub Code",
    "wikipedia" to "Wikipedia",
    "duckduckgo" to "Privacy Shield",
    "weather" to "Weather Sun",
    "globe" to "Globe / Web",
    "star" to "Star Icon",
    "bookmark" to "Bookmark",
    "initial" to "First Letter"
)

val colorPresets = listOf(
    Color(0xFFFF0000) to "Red",
    Color(0xFF24292E) to "Dark Gray",
    Color(0xFF43464E) to "Slate",
    Color(0xFFDE5833) to "Orange",
    Color(0xFF4285F4) to "Blue",
    Color(0xFF2E7D32) to "Green",
    Color(0xFF7B1FA2) to "Purple",
    Color(0xFF00796B) to "Teal",
    Color(0xFFF57C00) to "Amber"
)

// ── 2. Java Interop Callback Interface ──────────────────────────────────

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
}

object PetalComposeBridge {
    @JvmStatic
    fun createComposeHomeView(
        activity: ComponentActivity,
        tabCount: Int,
        handler: PetalHomeActionHandler
    ): ComposeView {
        return ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val sp = PreferenceManager.getDefaultSharedPreferences(activity)
                val fontName = sp.getString("sp_app_font", "SYSTEM") ?: "SYSTEM"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                val dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)
                val isAmoled = sp.getBoolean("sp_amoled", false)

                val appFont = remember(fontName) {
                    try { com.petal.browser.ui.theme.AppFont.valueOf(fontName) } catch (e: Exception) { com.petal.browser.ui.theme.AppFont.SYSTEM }
                }
                val colorStyle = remember(styleName) {
                    try { com.petal.browser.ui.theme.ColorStyle.valueOf(styleName) } catch (e: Exception) { com.petal.browser.ui.theme.ColorStyle.TONAL_SPOT }
                }

                val fontWidthVal = sp.getFloat("sp_font_width", 100f)
                val fontWeightVal = sp.getInt("sp_font_weight", 400)
                val fontRoundnessVal = sp.getFloat("sp_font_roundness", 0f)

                PetalExpressiveTheme(
                    dynamicColor = dynamicColor,
                    useAmoled = isAmoled,
                    appFont = appFont,
                    fontWidth = fontWidthVal,
                    fontWeight = fontWeightVal,
                    fontRoundness = fontRoundnessVal,
                    colorStyle = colorStyle,
                    paletteId = paletteId
                ) {
                    PetalHomeScreen(
                        tabCount = tabCount,
                        onSearch = { handler.onSearch(it) },
                        onOpenShortcut = { handler.onOpenUrl(it.url) },
                        onAddShortcut = { handler.onAddShortcut() },
                        onNewTab = { handler.onNewTab() },
                        onOpenBookmarks = { handler.onOpenBookmarks() },
                        onOpenHistory = { handler.onOpenHistory() },
                        onOpenDownloads = { handler.onOpenDownloads() },
                        onOpenSettings = { handler.onOpenSettings() },
                        onTabsClick = { handler.onOpenTabsOverview() }
                    )
                }
            }
        }
    }
}

// ── 3. Screen Composable ────────────────────────────────────────────────

@Composable
fun PetalHomeScreen(
    greetingName: String? = null,
    tabCount: Int = 1,
    onSearch: (String) -> Unit = {},
    onOpenShortcut: (PetalShortcut) -> Unit = {},
    onAddShortcut: () -> Unit = {},
    onNewTab: () -> Unit = {},
    onOpenBookmarks: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onTabsClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    var shortcuts by remember { mutableStateOf(loadHomeShortcuts(context)) }
    var editingSlotIndex by remember { mutableStateOf<Int?>(null) }

    var isAmoledEnabled by remember { mutableStateOf(sp.getBoolean("sp_amoled", false)) }
    var isDynamicColorEnabled by remember { mutableStateOf(sp.getBoolean("useDynamicColor", isDynamicColorSupported)) }

    val fontName = sp.getString("sp_app_font", "SYSTEM") ?: "SYSTEM"
    val fontWidthVal = sp.getFloat("sp_font_width", 100f)
    val fontWeightVal = sp.getInt("sp_font_weight", 400)
    val fontRoundnessVal = sp.getFloat("sp_font_roundness", 0f)

    val appFont = remember(fontName) {
        try { com.petal.browser.ui.theme.AppFont.valueOf(fontName) } catch (e: Exception) { com.petal.browser.ui.theme.AppFont.SYSTEM }
    }

    PetalExpressiveTheme(
        dynamicColor = isDynamicColorEnabled,
        useAmoled = isAmoledEnabled,
        appFont = appFont,
        fontWidth = fontWidthVal,
        fontWeight = fontWeightVal,
        fontRoundness = fontRoundnessVal
    ) {
        var pageLoaded by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            pageLoaded = true
        }

        val logoScale by animateFloatAsState(
            targetValue = if (pageLoaded) 1.0f else 0.4f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "logoZoomAnim"
        )

        val bloomScale by animateFloatAsState(
            targetValue = if (pageLoaded) 1.0f else 0.2f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessVeryLow
            ),
            label = "bloomZoomAnim"
        )

        val bloomAlpha by animateFloatAsState(
            targetValue = if (pageLoaded) 1.0f else 0.0f,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "bloomAlphaAnim"
        )

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(24.dp))

                // Animated Logo & Greeting Container
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = logoScale
                            scaleY = logoScale
                        }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .size(72.dp)
                                .padding(4.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                com.petal.browser.ui.components.PetalLoadingLottie(modifier = Modifier.size(54.dp))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = greeting(greetingName),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.5).sp,
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "Petal",
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                PetalSearchBar(onSearch = onSearch)

                Spacer(Modifier.height(32.dp))

                // Animated 5-Petal Bloom Ring
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = bloomScale
                            scaleY = bloomScale
                            alpha = bloomAlpha
                        }
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
                onSelectSlot = { newSlot -> editingSlotIndex = newSlot },
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
                },
                onResetSlot = {
                    val defaultShortcut = defaultPetalShortcuts[slotIndex % defaultPetalShortcuts.size]
                    val newList = shortcuts.toMutableList()
                    if (slotIndex in newList.indices) {
                        newList[slotIndex] = defaultShortcut
                    }
                    shortcuts = newList
                    saveHomeShortcuts(context, newList)
                    editingSlotIndex = null
                }
            )
        }
    }
}

private fun greeting(name: String?): String {
    val base = when (java.time.LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    return if (name.isNullOrBlank()) base else "$base, $name"
}

@Composable
private fun PetalSearchBar(onSearch: (String) -> Unit) {
    var searchText by remember { mutableStateOf("") }
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
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
    val ringRadius = 92.dp

    RadialLayout(
        radius = ringRadius,
        modifier = Modifier
            .fillMaxWidth()
            .height(ringRadius * 2 + petalSize),
    ) {
        // Center (+) Button for customizing shortcuts
        Surface(
            onClick = onAddShortcutClick,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(budSize),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, contentDescription = "Manage shortcuts")
            }
        }

        // 5 Customizable Bloom Ring Shortcuts
        shortcuts.take(5).forEachIndexed { index, shortcut ->
            val shape = petalShapes[index % petalShapes.size]
            var isPressed by remember { mutableStateOf(false) }

            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.84f else 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                finishedListener = {
                    if (isPressed) {
                        isPressed = false
                        onOpenShortcut(shortcut)
                    }
                },
                label = "petalIconAnim"
            )

            Box(modifier = Modifier.size(petalSize)) {
                Surface(
                    shape = shape,
                    color = shortcut.containerColor,
                    contentColor = shortcut.contentColor,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .combinedClickable(
                            onClick = { isPressed = true },
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
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    val labels = shortcuts.take(5).joinToString("  ·  ") { it.label }
    Text(
        labels,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.outline,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
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
    onSelectSlot: (Int) -> Unit,
    onSave: (PetalShortcut) -> Unit,
    onResetSlot: () -> Unit
) {
    var nameText by remember(slotIndex, currentShortcut) { mutableStateOf(currentShortcut.label) }
    var urlText by remember(slotIndex, currentShortcut) { mutableStateOf(currentShortcut.url) }
    var selectedSiteId by remember(slotIndex, currentShortcut) { mutableStateOf(currentShortcut.siteId) }
    var selectedColor by remember(slotIndex, currentShortcut) { mutableStateOf(currentShortcut.containerColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    "Customize Shortcut ${slotIndex + 1}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (i in 0 until 5) {
                        Surface(
                            onClick = { onSelectSlot(i) },
                            shape = CircleShape,
                            color = if (i == slotIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "${i + 1}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (i == slotIndex) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Shortcut Name Input
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("Shortcut Name") },
                    placeholder = { Text("e.g. YouTube, Google") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Shortcut URL Input
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("Website Link (URL)") },
                    placeholder = { Text("e.g. https://www.youtube.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Live Icon Preview
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = selectedColor,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            SiteBrandIcon(siteId = selectedSiteId, label = if (nameText.isBlank()) "S" else nameText)
                        }
                    }
                    Column {
                        Text(
                            text = if (nameText.isBlank()) "Shortcut Preview" else nameText,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (urlText.isBlank()) "https://..." else urlText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Icon Preset Picker
                Text("Select Icon:", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    iconPresets.forEach { (presetId, presetName) ->
                        val isSelected = selectedSiteId == presetId
                        Surface(
                            onClick = { selectedSiteId = presetId },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(selectedColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    SiteBrandIcon(siteId = presetId, label = if (nameText.isBlank()) "S" else nameText)
                                }
                                Text(
                                    presetName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Color Preset Picker
                Text("Select Color:", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    colorPresets.forEach { (colorOption, colorName) ->
                        val isSelected = selectedColor == colorOption
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colorOption)
                                .clickable { selectedColor = colorOption }
                                .then(
                                    if (isSelected) Modifier.border(
                                        3.dp,
                                        MaterialTheme.colorScheme.primary,
                                        CircleShape
                                    ) else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    var formattedUrl = urlText.trim()
                    if (formattedUrl.isNotBlank() && !formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
                        formattedUrl = "https://$formattedUrl"
                    }
                    val finalName = if (nameText.isBlank()) "Shortcut" else nameText.trim()
                    val finalUrl = if (formattedUrl.isBlank()) "https://google.com" else formattedUrl
                    onSave(
                        PetalShortcut(
                            label = finalName,
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onResetSlot) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
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
            center.place(centerX - center.width / 2, centerY - center.height / 2)

            val petals = placeables.drop(1)
            val step = 360f / petals.size.coerceAtLeast(1)
            petals.forEachIndexed { i, placeable ->
                val angleDeg = -90f + step * i
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val x = centerX + (radius.roundToPx() * cos(angleRad)).roundToInt() - placeable.width / 2
                val y = centerY + (radius.roundToPx() * sin(angleRad)).roundToInt() - placeable.height / 2
                placeable.place(x, y)
            }
        }
    }
}
