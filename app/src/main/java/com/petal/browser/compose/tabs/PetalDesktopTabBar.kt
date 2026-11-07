package com.petal.browser.compose.tabs

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.browser.AlbumController
import com.petal.browser.browser.BrowserContainer
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported
import kotlinx.coroutines.delay

/** Callbacks from the desktop tab strip into the host activity. */
interface PetalDesktopTabBarHandler {
    fun onTabSelect(controller: AlbumController)
    fun onTabClose(controller: AlbumController)
    fun onNewTab(incognito: Boolean)
    fun currentController(): AlbumController?
}

/** Lightweight snapshot of one tab for the strip. Equality drives recomposition. */
private data class PetalDesktopTabEntry(
    val key: Int,
    val title: String,
    val favicon: Bitmap?,
    val incognito: Boolean,
    val selected: Boolean,
    val controller: AlbumController
) {
    // Controller identity is covered by [key]; keep equality cheap and stable.
    override fun equals(other: Any?): Boolean =
        other is PetalDesktopTabEntry && key == other.key && title == other.title &&
            favicon === other.favicon && incognito == other.incognito && selected == other.selected

    override fun hashCode(): Int = key * 31 + title.hashCode() + (if (selected) 1 else 0)
}

object PetalDesktopTabBarBridge {
    const val PREF_KEY = "sp_desktop_tab_bar"
    const val MODE_AUTO = "AUTO"
    const val MODE_ALWAYS = "ALWAYS"
    const val MODE_NEVER = "NEVER"

    /** Width (dp) from which Auto mode shows the strip: Material "Medium" window width class and up. */
    private const val AUTO_MIN_WIDTH_DP = 600
    const val BAR_HEIGHT_DP = 44f

    @JvmStatic
    fun getMode(context: Context): String =
        PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_KEY, MODE_AUTO) ?: MODE_AUTO

    @JvmStatic
    fun setMode(context: Context, mode: String) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(PREF_KEY, mode).apply()
    }

    /** True when the strip should be on screen for the current mode and window width. */
    @JvmStatic
    fun shouldShow(context: Context): Boolean = when (getMode(context)) {
        MODE_ALWAYS -> true
        MODE_NEVER -> false
        else -> context.resources.configuration.screenWidthDp >= AUTO_MIN_WIDTH_DP
    }

    @JvmStatic
    fun barHeightPx(context: Context): Int =
        Math.round(BAR_HEIGHT_DP * context.resources.displayMetrics.density)

    @JvmStatic
    fun bind(composeView: ComposeView, activity: ComponentActivity, handler: PetalDesktopTabBarHandler) {
        if (composeView.tag == "PetalDesktopTabBarBound") return
        composeView.tag = "PetalDesktopTabBarBound"
        composeView.apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val sp = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
                var themeCfg by remember { mutableStateOf(sp.getString("sp_theme_config", "FOLLOW_SYSTEM") ?: "FOLLOW_SYSTEM") }
                var fontName by remember { mutableStateOf(sp.getString("sp_app_font", "PETAL") ?: "PETAL") }
                var styleName by remember { mutableStateOf(sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT") }
                var paletteId by remember { mutableStateOf(sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId) }
                var dynamicColor by remember { mutableStateOf(sp.getBoolean("useDynamicColor", isDynamicColorSupported)) }
                var isAmoled by remember { mutableStateOf(sp.getBoolean("sp_amoled", false)) }
                val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()

                DisposableEffect(sp) {
                    val l = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                        when (key) {
                            "sp_theme_config" -> themeCfg = sp.getString(key, "FOLLOW_SYSTEM") ?: "FOLLOW_SYSTEM"
                            "sp_app_font" -> fontName = sp.getString(key, "PETAL") ?: "PETAL"
                            "sp_color_style" -> styleName = sp.getString(key, "TONAL_SPOT") ?: "TONAL_SPOT"
                            "sp_palette_id" -> paletteId = sp.getString(key, defaultPaletteId) ?: defaultPaletteId
                            "useDynamicColor" -> dynamicColor = sp.getBoolean(key, isDynamicColorSupported)
                            "sp_amoled" -> isAmoled = sp.getBoolean(key, false)
                        }
                    }
                    sp.registerOnSharedPreferenceChangeListener(l)
                    onDispose { sp.unregisterOnSharedPreferenceChangeListener(l) }
                }

                val darkTheme = when (themeCfg) {
                    "LIGHT" -> false
                    "DARK" -> true
                    else -> isSystemDark
                }
                val colorStyle = remember(styleName) {
                    try { ColorStyle.valueOf(styleName) } catch (e: Exception) { ColorStyle.TONAL_SPOT }
                }

                PetalExpressiveTheme(
                    darkTheme = darkTheme,
                    dynamicColor = dynamicColor,
                    useAmoled = isAmoled,
                    appFont = remember(fontName) { AppFont.fromName(fontName) },
                    colorStyle = colorStyle,
                    paletteId = paletteId
                ) {
                    PetalDesktopTabBar(handler = handler)
                }
            }
        }
    }
}

private fun resolveEntries(handler: PetalDesktopTabBarHandler): List<PetalDesktopTabEntry> {
    val list = try { BrowserContainer.list() } catch (_: Exception) { return emptyList() }
    val selected = try { handler.currentController() } catch (_: Exception) { null }
    return list.map { album ->
        val rawTitle = try { album.title } catch (_: Exception) { null }
        val rawUrl = try { album.url } catch (_: Exception) { null }
        val title = when {
            !rawTitle.isNullOrBlank() && !rawTitle.equals("about:blank", true) &&
                !rawTitle.equals("Petal Start", true) && !rawTitle.equals("Petal Home", true) -> rawTitle
            !rawUrl.isNullOrBlank() && !rawUrl.equals("about:blank", true) &&
                !rawUrl.startsWith("file:///android_asset/") && !rawUrl.equals("petal://home", true) -> rawUrl
            else -> "Petal Home"
        }
        val favicon = when (album) {
            is com.petal.browser.view.PetalGeckoView -> album.getFavicon()
            is com.petal.browser.browser.PetalTabViewController -> album.getFavicon()
            is com.petal.browser.browser.PlaceholderAlbumController -> album.getFavicon()
            else -> null
        }
        PetalDesktopTabEntry(
            key = System.identityHashCode(album),
            title = title,
            favicon = favicon,
            incognito = try { album.isIncognito() } catch (_: Exception) { false },
            selected = album === selected,
            controller = album
        )
    }
}

@Composable
fun PetalDesktopTabBar(handler: PetalDesktopTabBarHandler, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(resolveEntries(handler)) }

    // Poll the tab registry: titles, favicons and selection change from many places in the app.
    // Only a changed snapshot triggers recomposition (see PetalDesktopTabEntry.equals).
    LaunchedEffect(Unit) {
        while (true) {
            val fresh = resolveEntries(handler)
            if (fresh != entries) entries = fresh
            delay(350)
        }
    }

    val listState: LazyListState = rememberLazyListState()
    val selectedIndex = entries.indexOfFirst { it.selected }
    LaunchedEffect(selectedIndex, entries.size) {
        if (selectedIndex >= 0) listState.animateScrollToItem(selectedIndex)
    }

    val haptic: () -> Unit = {
        try { PetalHapticEngine.getInstance(context).playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.85f) } catch (_: Exception) {}
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(PetalDesktopTabBarBridge.BAR_HEIGHT_DP.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f, fill = false),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(entries, key = { it.key }) { entry ->
                PetalDesktopTabChip(
                    entry = entry,
                    modifier = Modifier.animateItem(
                        fadeInSpec = tween(220, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)),
                        placementSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
                        fadeOutSpec = tween(150)
                    ),
                    onSelect = { haptic(); handler.onTabSelect(entry.controller) },
                    onClose = { haptic(); handler.onTabClose(entry.controller) }
                )
            }
        }
        Box(
            modifier = Modifier
                .padding(start = 6.dp)
                .size(32.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = androidx.compose.material3.ripple()
                ) { haptic(); handler.onNewTab(false) }
                .semantics { contentDescription = "New tab" },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun PetalDesktopTabChip(
    entry: PetalDesktopTabEntry,
    modifier: Modifier,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (entry.selected) {
            if (entry.incognito) scheme.tertiaryContainer else scheme.secondaryContainer
        } else scheme.surfaceContainerHigh.copy(alpha = 0.0f),
        animationSpec = spring(stiffness = Spring.StiffnessMedium), label = "tabChipColor"
    )
    val content = if (entry.selected) {
        if (entry.incognito) scheme.onTertiaryContainer else scheme.onSecondaryContainer
    } else scheme.onSurfaceVariant
    val corner by animateDpAsState(if (entry.selected) 16.dp else 10.dp, spring(dampingRatio = 0.7f), label = "tabChipCorner")
    val widthDp by animateDpAsState(if (entry.selected) 200.dp else 150.dp, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "tabChipWidth")

    Row(
        modifier = modifier
            .width(widthDp)
            .height(36.dp)
            .clip(RoundedCornerShape(corner))
            .background(container)
            .clickable(onClick = onSelect)
            .padding(start = 10.dp, end = 4.dp)
            .semantics { contentDescription = entry.title },
        verticalAlignment = Alignment.CenterVertically
    ) {
        val fav = entry.favicon
        if (fav != null && !fav.isRecycled) {
            Image(
                bitmap = fav.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(16.dp).clip(RoundedCornerShape(4.dp))
            )
        } else {
            Icon(Icons.Rounded.Public, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        }
        Text(
            text = entry.title,
            color = content,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .clickable(onClick = onClose)
                .semantics { contentDescription = "Close tab" },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Close, contentDescription = null, tint = content, modifier = Modifier.size(15.dp))
        }
    }
}
