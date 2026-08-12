/*
 * PetalHomeScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive home screen for Petal Browser with Stride UI components,
 * Stride Floating Bottom Navigation Bar, Chrome Android-style Live Tab Switcher Badge,
 * IconSwitch toggles with persistent SharedPreferences, AMOLED dark mode, and Material You dynamic colors.
 */

package com.petal.browser.compose.home

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.petal.browser.ui.components.IconSwitch
import com.petal.browser.ui.components.PetalBottomNavBar
import com.petal.browser.ui.components.PetalNavTab
import com.petal.browser.ui.theme.PetalExpressiveTheme
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ── 1. Data model ───────────────────────────────────────────────────────

data class PetalShortcut(
    val label: String,
    val url: String,
    val letter: String = label.take(1).uppercase(),
)

val defaultPetalShortcuts = listOf(
    PetalShortcut("Wiki", "https://wikipedia.org"),
    PetalShortcut("GitHub", "https://github.com"),
    PetalShortcut("DuckDuckGo", "https://duckduckgo.com"),
    PetalShortcut("Reddit", "https://reddit.com"),
    PetalShortcut("News", "https://news.ycombinator.com"),
)

private val petalShapes: List<Shape> = listOf(
    RoundedCornerShape(28.dp),
    RoundedCornerShape(topStart = 28.dp, topEnd = 12.dp, bottomEnd = 28.dp, bottomStart = 12.dp),
    RoundedCornerShape(topStart = 12.dp, topEnd = 28.dp, bottomEnd = 12.dp, bottomStart = 28.dp),
    CutCornerShape(topStart = 20.dp, bottomEnd = 20.dp),
    RoundedCornerShape(24.dp),
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
                val paletteId = sp.getString("sp_palette_id", "tide") ?: "tide"
                val dynamicColor = sp.getBoolean("useDynamicColor", false)
                val isAmoled = sp.getBoolean("sp_amoled", false)

                val appFont = remember(fontName) {
                    try { com.petal.browser.ui.theme.AppFont.valueOf(fontName) } catch (e: Exception) { com.petal.browser.ui.theme.AppFont.SYSTEM }
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
    shortcuts: List<PetalShortcut> = defaultPetalShortcuts,
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

    // Persistent state loaded directly from SharedPreferences
    var isAmoledEnabled by remember { mutableStateOf(sp.getBoolean("sp_amoled", false)) }
    var isDynamicColorEnabled by remember { mutableStateOf(sp.getBoolean("useDynamicColor", true)) }
    var isAdBlockEnabled by remember { mutableStateOf(sp.getBoolean("sp_ad_block", true)) }
    var isHttpsOnlyEnabled by remember { mutableStateOf(sp.getBoolean("sp_https_only", true)) }
    var selectedNavTab by remember { mutableStateOf(PetalNavTab.HOME) }

    PetalExpressiveTheme(
        dynamicColor = isDynamicColorEnabled,
        useAmoled = isAmoledEnabled
    ) {
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
                Spacer(Modifier.height(28.dp))

                Text(
                    text = greeting(greetingName),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Petal",
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(24.dp))

                PetalSearchBar(onSearch = onSearch)

                Spacer(Modifier.height(32.dp))

                PetalBloom(
                    shortcuts = shortcuts,
                    onOpenShortcut = onOpenShortcut,
                    onAddShortcut = onAddShortcut,
                )

                Spacer(Modifier.height(96.dp))
            }
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

@Composable
private fun PetalBloom(
    shortcuts: List<PetalShortcut>,
    onOpenShortcut: (PetalShortcut) -> Unit,
    onAddShortcut: () -> Unit,
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
        Surface(
            onClick = onAddShortcut,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(budSize),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, contentDescription = "Add shortcut")
            }
        }

        shortcuts.take(6).forEachIndexed { index, shortcut ->
            val shape = petalShapes[index % petalShapes.size]
            Surface(
                onClick = { onOpenShortcut(shortcut) },
                shape = shape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(petalSize),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Text(
                        shortcut.letter,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    val labels = shortcuts.take(6).joinToString("  ·  ") { it.label }
    Text(
        labels,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.outline,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun QuickActionRow(
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        QuickAction(Icons.Rounded.Bookmarks, "Bookmarks", Modifier.weight(1f), onOpenBookmarks)
        QuickAction(Icons.Rounded.History, "History", Modifier.weight(1f), onOpenHistory)
        QuickAction(Icons.Rounded.Downloading, "Downloads", Modifier.weight(1f), onOpenDownloads)
        QuickAction(Icons.Rounded.Settings, "Settings", Modifier.weight(1f), onOpenSettings)
    }
}

@Composable
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.heightIn(min = 76.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(vertical = 10.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
