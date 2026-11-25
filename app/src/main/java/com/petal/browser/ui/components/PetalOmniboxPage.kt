/*
 * PetalOmniboxPage.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Chrome-style full-screen Omnibox search page in Jetpack Compose. Replaces
 * the legacy AlertDialog-based search dialog (dialog_search.xml + AdapterSearch)
 * AND the previous bottom-sheet PetalOmniboxOverlay, unifying every entry point
 * (address bar tap, home page search box, incognito home, search widget) behind
 * a single full-page surface that mounts directly into MainActivity's
 * contentFrame - the same architecture as Downloads/History/Account/Settings -
 * so it gets the same predictive-back gesture handling and "last page" underlay
 * preview (InstallerX-Revived style) as those screens instead of a dialog window.
 *
 * Features:
 * 1. Debounced live autocomplete suggestions (Google / DuckDuckGo / Bing, per
 *    the user's sp_search_engine preference), matching the old dialog's engine support.
 * 2. SQLite local search/browsing history suggestions with a history clock icon
 *    vs a search magnifying-glass icon.
 * 3. Clickable query rows with diagonal insert arrow buttons (NorthWest) that
 *    copy the suggestion into the field without submitting it, Chrome-style.
 * 4. Voice search entry point (mic icon) reusing PetalVoiceSearchBridge.
 * 5. Material 3 Expressive styling with automatic theme/palette integration.
 * 6. Auto-opening soft keyboard on open, full predictive-back support.
 */

package com.petal.browser.ui.components

import com.petal.browser.ui.theme.ExpressiveButtonShape
import com.petal.browser.ui.theme.PetalText
import com.petal.browser.ui.theme.layout.petalFlingBehavior

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusWeak
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import com.petal.browser.ui.kit.TextField
import com.petal.browser.ui.kit.IconButton
import com.petal.browser.ui.kit.FilledTonalButton
import com.petal.browser.ui.kit.Button
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.database.HistoryBookmarkRepository
import com.petal.browser.database.Record
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.theme.*
import com.petal.browser.unit.SearchSuggestionsManager
import kotlinx.coroutines.delay
import org.json.JSONArray
import java.net.URI
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

/**
 * Commands reachable from the address bar by typing "/" as the very first character.
 * The host decides how each one is executed (see the `onCommand` parameter).
 */
enum class PetalOmniboxCommand {
    TABS, NEW_TAB, NEW_INCOGNITO_TAB, HISTORY, BOOKMARKS, DOWNLOADS, SETTINGS, READER_MODE
}

private class SlashCommandSpec(
    val command: PetalOmniboxCommand,
    /** Untranslated token typed after the slash, e.g. "history". */
    val trigger: String,
    /** Extra untranslated words that also match, e.g. "recent". */
    val keywords: List<String>,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val role: PetalGroupRole,
)

@Composable
private fun rememberSlashCommandSpecs(): List<SlashCommandSpec> = listOf(
    SlashCommandSpec(PetalOmniboxCommand.TABS, "tabs", listOf("tab", "switcher"),
        stringResource(R.string.ui_slash_tabs), stringResource(R.string.ui_slash_tabs_desc), Icons.Rounded.Layers, PetalGroupRole.Primary),
    SlashCommandSpec(PetalOmniboxCommand.NEW_TAB, "new", listOf("newtab", "tab", "add"),
        stringResource(R.string.ui_slash_new_tab), stringResource(R.string.ui_slash_new_tab_desc), Icons.Rounded.Add, PetalGroupRole.Primary),
    SlashCommandSpec(PetalOmniboxCommand.NEW_INCOGNITO_TAB, "incognito", listOf("private", "newincognito"),
        stringResource(R.string.ui_slash_incognito), stringResource(R.string.ui_slash_incognito_desc), Icons.Rounded.VisibilityOff, PetalGroupRole.Tertiary),
    SlashCommandSpec(PetalOmniboxCommand.HISTORY, "history", listOf("recent", "visited"),
        stringResource(R.string.ui_slash_history), stringResource(R.string.ui_slash_history_desc), Icons.Rounded.History, PetalGroupRole.Secondary),
    SlashCommandSpec(PetalOmniboxCommand.BOOKMARKS, "bookmarks", listOf("saved", "favorites", "star"),
        stringResource(R.string.ui_slash_bookmarks), stringResource(R.string.ui_slash_bookmarks_desc), Icons.Rounded.Bookmark, PetalGroupRole.Secondary),
    SlashCommandSpec(PetalOmniboxCommand.DOWNLOADS, "downloads", listOf("files", "download"),
        stringResource(R.string.ui_slash_downloads), stringResource(R.string.ui_slash_downloads_desc), Icons.Rounded.Download, PetalGroupRole.Secondary),
    SlashCommandSpec(PetalOmniboxCommand.SETTINGS, "settings", listOf("preferences", "options", "setting"),
        stringResource(R.string.ui_slash_settings), stringResource(R.string.ui_slash_settings_desc), Icons.Rounded.Settings, PetalGroupRole.Secondary),
    SlashCommandSpec(PetalOmniboxCommand.READER_MODE, "reader", listOf("reading", "read"),
        stringResource(R.string.ui_slash_reader), stringResource(R.string.ui_slash_reader_desc), Icons.Rounded.AutoStories, PetalGroupRole.Primary),
)

/** Matches the text after the leading slash against a command's trigger, keywords or translated name. */
private fun filterSlashCommands(specs: List<SlashCommandSpec>, input: String): List<SlashCommandSpec> {
    val q = input.removePrefix("/").trim().lowercase()
    if (q.isEmpty()) return specs
    return specs.filter { spec ->
        spec.trigger.startsWith(q) ||
            spec.keywords.any { it.startsWith(q) } ||
            spec.name.lowercase().contains(q)
    }
}

@Composable
private fun SlashCommandCard(
    specs: List<SlashCommandSpec>,
    isGlass: Boolean,
    sheen: Float,
    onRun: (PetalOmniboxCommand) -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    // Glass with the same solid fallback the other glass surfaces use on low-end devices / transitions.
    val glass = isGlass && !com.petal.browser.motion.MotionGate.shouldSimplifyEffects()
    Surface(
        shape = shape,
        color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .entrance(index = 1)
            .then(if (glass) Modifier.liquidGlassChrome(shape = shape, enabled = true, sheenIntensity = sheen) else Modifier)
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Text(
                text = stringResource(R.string.ui_slash_title),
                style = PetalText.sectionHeader,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Column(
                modifier = Modifier
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                specs.forEach { spec -> SlashCommandRow(spec = spec, onRun = onRun) }
            }
        }
    }
}

@Composable
private fun SlashCommandRow(spec: SlashCommandSpec, onRun: (PetalOmniboxCommand) -> Unit) {
    val colors = petalRoleColors(spec.role)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onRun(spec.command) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.container),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = spec.icon,
                contentDescription = null,
                tint = colors.onContainer,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = spec.name,
                style = PetalText.rowTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = spec.description,
                style = PetalText.rowSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "/" + spec.trigger,
            style = PetalText.badge,
            color = colors.solid,
            maxLines = 1
        )
    }
}

enum class SuggestionSource { RECENT_SEARCH, BROWSING_HISTORY, SEARCH_ENGINE, SWITCH_TO_TAB, BOOKMARK }

data class OmniboxSuggestion(
    val query: String,
    val isHistory: Boolean,
    val source: SuggestionSource = SuggestionSource.SEARCH_ENGINE,
    val targetUrl: String? = null,
    val tabIndex: Int = -1
)

private sealed interface OmniboxListEntry {
    data class Header(val source: SuggestionSource) : OmniboxListEntry
    data class Item(
        val suggestion: OmniboxSuggestion,
        val indexInGroup: Int,
        val groupSize: Int
    ) : OmniboxListEntry
}

/**
 * Firefox-style smart ellipsis:
 * - URLs get start-ellipsis (…end/of/path) so the unique suffix stays visible.
 * - Plain search queries get end-ellipsis (beginning of intent stays visible).
 */
private fun smartEllipsize(text: String, isUrl: Boolean, maxChars: Int = 48): String {
    if (text.length <= maxChars) return text
    return if (isUrl) {
        "\u2026" + text.takeLast(maxChars)
    } else {
        text.take(maxChars) + "\u2026"
    }
}

private data class TopSiteShortcut(
    val title: String,
    val url: String,
    val icon: ImageVector
)

private data class LocalHistoryItem(
    val title: String,
    val url: String,
    val time: Long
)

object PetalOmniboxBridge {

    /**
     * Builds the full-screen omnibox page as a plain [ComposeView] meant to be mounted
     * into MainActivity's `contentFrame`, supporting Chrome-style quick-action cards
     * and site shortcuts.
     */
    @JvmStatic
    @JvmOverloads
    fun createOmniboxView(
        activity: ComponentActivity,
        initialQuery: String = "",
        pageTitle: String = "",
        pageUrl: String = "",
        favicon: Bitmap? = null,
        isIncognito: Boolean = false,
        onBackPress: () -> Unit,
        onQuerySubmitted: (String) -> Unit,
        // Called when a predictive swipe-back finishes (its own animation already played).
        // Defaults to onBackPress, which is what taps use (they may animate the close).
        onBackGesture: (() -> Unit)? = null
    ): ComposeView {
        val rootView = activity.findViewById<android.view.View>(android.R.id.content) ?: activity.window.decorView
        com.petal.browser.predictive.PetalContentSnapshot.capture(rootView)
        return ComposeView(activity).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val snapshotBitmap = remember { com.petal.browser.predictive.PetalContentSnapshot.current?.asImageBitmap() }
                DisposableEffect(Unit) {
                    onDispose {
                        com.petal.browser.predictive.PetalContentSnapshot.clear()
                    }
                }
                val sp = PreferenceManager.getDefaultSharedPreferences(activity)
                val fontState = com.petal.browser.ui.theme.rememberFontState()
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                val isAmoled = sp.getBoolean("sp_amoled", false)
                val dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)

                val colorStyle = remember(styleName) {
                    try { ColorStyle.valueOf(styleName) } catch (e: Exception) { ColorStyle.TONAL_SPOT }
                }

                PetalExpressiveTheme(
                    dynamicColor = dynamicColor,
                    useAmoled = isAmoled,
                    fontState = fontState,
                    colorStyle = colorStyle,
                    paletteId = paletteId
                ) {
                    PetalOmniboxPage(
                        backgroundSnapshot = snapshotBitmap,
                        activity = activity,
                        initialQuery = initialQuery,
                        pageTitle = pageTitle,
                        pageUrl = pageUrl,
                        favicon = favicon,
                        isIncognito = isIncognito,
                        // The submit handler already navigates via showAlbum(tab, url)
                        // (or showAlbum(tab) for empty input). Calling onBackPress() here
                        // ran a second showAlbum(tab) with no URL while the tab still
                        // reported about:blank, which stopped the just-started load and
                        // rebuilt the surface, leaving a black page after every search.
                        onQuerySubmitted = { query ->
                            onQuerySubmitted(query)
                        },
                        onBackPress = { onBackPress() },
                        onBackGesture = onBackGesture
                    )
                }
            }
        }
    }
}

@Composable
fun PetalOmniboxPage(
    backgroundSnapshot: androidx.compose.ui.graphics.ImageBitmap? = null,
    handleBackInternally: Boolean = true,
    activity: ComponentActivity,
    initialQuery: String = "",
    pageTitle: String = "",
    pageUrl: String = "",
    favicon: Bitmap? = null,
    isIncognito: Boolean = false,
    onQuerySubmitted: (String) -> Unit,
    onBackPress: () -> Unit,
    onBackGesture: (() -> Unit)? = null,
    /** When set, typing "/" as the first character shows a command menu that calls this. */
    onCommand: ((PetalOmniboxCommand) -> Unit)? = null
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        com.petal.browser.profile.PetalProfileManager.init(context)
        com.petal.browser.wallpaper.PetalWallpaperManager.init(context)
    }
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val isGlass = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_containments")
    val sheen = if (isGlass) sp.getFloat("sp_liquid_glass_sheen", 0.60f).coerceIn(0f, 1f) else 0.60f

    fun getSavedSearchHistory(): List<String> {
        if (isIncognito) return emptyList()
        val listKey = "sp_search_history_list"
        val legacyKey = "sp_search_history_queries"
        val raw = sp.getString(listKey, null)
        if (!raw.isNullOrBlank()) {
            return try {
                val json = JSONArray(raw)
                val result = mutableListOf<String>()
                for (i in 0 until json.length()) {
                    val s = json.optString(i)
                    if (!s.isNullOrBlank()) result.add(s)
                }
                result
            } catch (e: Exception) {
                emptyList()
            }
        }
        // Fallback and migrate from legacy StringSet if available
        val legacy = sp.getStringSet(legacyKey, null)
        if (!legacy.isNullOrEmpty()) {
            val list = legacy.toList()
            val jsonStr = JSONArray(list).toString()
            sp.edit().putString(listKey, jsonStr).remove(legacyKey).apply()
            return list
        }
        return emptyList()
    }

    fun submitSearch(query: String, saveToHistory: Boolean = true) {
        val normalized = query.trim()
        if (normalized.isBlank()) return
        val isUrl = com.petal.browser.unit.PetalBrowserUtils.isURL(normalized)
        val shouldSave = saveToHistory && !isUrl && !isIncognito
        if (shouldSave) {
            val listKey = "sp_search_history_list"
            val legacyKey = "sp_search_history_queries"
            val limit = 40
            val currentList = getSavedSearchHistory().toMutableList()
            // Remove any existing entry with the same query (case-insensitive) so it moves to top
            currentList.removeAll { it.equals(normalized, ignoreCase = true) }
            // Add latest search at index 0 (top rank)
            currentList.add(0, normalized)
            val trimmedList = currentList.take(limit)
            val jsonStr = JSONArray(trimmedList).toString()
            sp.edit()
                .putString(listKey, jsonStr)
                .remove(legacyKey)
                .apply()
        }
        try {
            keyboardController?.hide()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            val windowToken = (context as? android.app.Activity)?.window?.decorView?.windowToken
            if (imm != null && windowToken != null) {
                imm.hideSoftInputFromWindow(windowToken, 0)
            }
        } catch (_: Throwable) {}
        onQuerySubmitted(normalized)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val cleanedInitialQuery = remember(initialQuery) {
        val trimmed = initialQuery.trim()
        if (trimmed.equals("about:blank", ignoreCase = true) ||
            trimmed.startsWith("about:", ignoreCase = true) ||
            trimmed.startsWith("petal:", ignoreCase = true)
        ) {
            ""
        } else {
            initialQuery
        }
    }
    var queryState by remember(cleanedInitialQuery) {
        // Opening the address bar is an edit action: select the complete current
        // URL so typing immediately replaces it instead of appending at the end.
        mutableStateOf(TextFieldValue(cleanedInitialQuery, TextRange(0, cleanedInitialQuery.length)))
    }
    var suggestions by remember { mutableStateOf<List<OmniboxSuggestion>>(emptyList()) }
    var suggestionToRemove by remember { mutableStateOf<OmniboxSuggestion?>(null) }
    var removedSuggestions by remember {
        mutableStateOf<Set<String>>(
            sp.getStringSet("sp_removed_suggestions", null)?.toSet() ?: emptySet()
        )
    }
    val focusRequester = remember { FocusRequester() }

    // Slash commands: only a leading "/" (index 0) can trigger this; "a.com/x", "https://x/y" never do.
    val slashSpecs = rememberSlashCommandSpecs()
    val slashMatches = remember(queryState.text, slashSpecs, onCommand) {
        if (onCommand != null && queryState.text.startsWith("/")) {
            filterSlashCommands(slashSpecs, queryState.text)
        } else emptyList()
    }
    val slashUnlockTitle = stringResource(R.string.ui_slash_unlock_title)
    val slashUnlockSubtitle = stringResource(R.string.ui_slash_unlock_subtitle)
    fun runSlashCommand(command: PetalOmniboxCommand) {
        val handler = onCommand ?: return
        try { keyboardController?.hide() } catch (_: Throwable) {}
        if (command == PetalOmniboxCommand.NEW_INCOGNITO_TAB) {
            // Same gate the tab switcher uses before it opens private tabs.
            val lockOn = sp.getBoolean("sp_biometric_incognito_tab_lock", false)
            val fragmentActivity = (activity as? androidx.fragment.app.FragmentActivity)
                ?: (context as? androidx.fragment.app.FragmentActivity)
            if (lockOn && fragmentActivity != null && com.petal.browser.security.BiometricLockManager.canAuthenticate(context)) {
                com.petal.browser.security.BiometricLockManager.authenticate(
                    activity = fragmentActivity,
                    title = slashUnlockTitle,
                    subtitle = slashUnlockSubtitle,
                    onSuccess = Runnable { handler(command) },
                    onError = java.util.function.Consumer { }
                )
                return
            }
        }
        handler(command)
    }

    var currentEngineIndexStr by remember {
        mutableStateOf(sp.getString("sp_search_engine", "0") ?: "0")
    }
    val showEngineSelector = remember {
        sp.getBoolean("sp_show_search_engine_selector_in_omnibox", true)
    }
    var showEngineMenu by remember { mutableStateOf(false) }

    val allEngines = remember(context, currentEngineIndexStr) {
        allSearchEngines(context)
    }
    val currentEngine = remember(currentEngineIndexStr, allEngines) {
        val idx = currentEngineIndexStr.toIntOrNull() ?: 0
        allEngines.find { it.index == idx } ?: availableSearchEngines.first()
    }

    val openTabs = remember(isIncognito) {
        try {
            com.petal.browser.engine.gecko.PetalEngineStore.getStore(context).state.tabs.mapIndexedNotNull { index, tab ->
                if (tab.content.private == isIncognito && !com.petal.browser.engine.gecko.PetalEngineStore.isEphemeralTabId(tab.id)) {
                    val title = tab.content.title
                    val url = tab.content.url
                    if (title.isNotBlank() || url.isNotBlank()) Triple(title, url, index) else null
                } else null
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    val cleanPageUrl = remember(pageUrl) {
        val trimmed = pageUrl.trim()
        if (trimmed.equals("about:blank", ignoreCase = true) || trimmed.startsWith("about:", ignoreCase = true) || trimmed.startsWith("petal:", ignoreCase = true)) {
            ""
        } else {
            trimmed
        }
    }

    val pageDomain = remember(cleanPageUrl) {
        try {
            if (cleanPageUrl.isNotBlank()) {
                val uri = URI(cleanPageUrl)
                uri.host?.removePrefix("www.") ?: cleanPageUrl
            } else ""
        } catch (e: Exception) {
            ""
        }
    }

    val siteShortcuts = remember {
        listOf(
            TopSiteShortcut("Google", "https://www.google.com", Icons.Rounded.Search),
            TopSiteShortcut("YouTube", "https://www.youtube.com", Icons.Rounded.PlayCircle),
            TopSiteShortcut("Wikipedia", "https://www.wikipedia.org", Icons.Rounded.MenuBook),
            TopSiteShortcut("GitHub", "https://github.com", Icons.Rounded.Code),
            TopSiteShortcut("Reddit", "https://www.reddit.com", Icons.Rounded.Forum),
            TopSiteShortcut("News", "https://news.google.com", Icons.Rounded.RssFeed)
        )
    }

    // Fetch local search/browsing history from SQLite database (only when not in Incognito).
    // Records are sorted by most-recently-visited first (Firefox AwesomeBar convention).
    val localHistoryList = remember(isIncognito) {
        if (isIncognito) {
            emptyList<LocalHistoryItem>()
        } else {
            val list = mutableListOf<LocalHistoryItem>()
            try {
                val action = HistoryBookmarkRepository(context)
                action.open(false)
                val records: List<Record> = action.listHistory(context)
                action.close()
                // Sort by time descending so most-recently-visited titles/urls appear first
                records.sortedByDescending { it.time }.forEach { r ->
                    val url = r.url?.trim() ?: ""
                    val title = r.title?.trim() ?: ""
                    if (url.isNotBlank() || title.isNotBlank()) {
                        list.add(LocalHistoryItem(title = title, url = url, time = r.time))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            list.distinctBy { it.url.ifBlank { it.title } }
        }
    }

    // Fetch local bookmarks for omnibox suggestion matches
    val localBookmarksList = remember(isIncognito) {
        if (isIncognito) {
            emptyList<Record>()
        } else {
            try {
                val action = HistoryBookmarkRepository(context)
                action.open(false)
                val list = action.listBookmark(context, false, 0)
                action.close()
                list
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    // Debounced search query handler with robust long multi-word query support
    LaunchedEffect(queryState.text, removedSuggestions, isIncognito, currentEngineIndexStr) {
        val currentText = queryState.text.trim()
        val savedQueries = getSavedSearchHistory()
        if (currentText.isEmpty()) {
            // Firefox-style: Recent Searches (MRU, exact order) come first, then
            // Browsing History entries below — two visually separated groups.
            val recentSearchItems = savedQueries
                .filter { !removedSuggestions.contains(it) }
                .take(5)
                .map { OmniboxSuggestion(it, isHistory = true, source = SuggestionSource.RECENT_SEARCH) }

            val recentSearchSet = recentSearchItems.map { it.query.lowercase() }.toSet()
            val browsingHistoryItems = localHistoryList
                .filter { rec ->
                    val query = rec.title.ifBlank { rec.url }
                    !removedSuggestions.contains(query) &&
                    !removedSuggestions.contains(rec.url) &&
                    !recentSearchSet.contains(query.lowercase()) &&
                    !recentSearchSet.contains(rec.url.lowercase())
                }
                .take(8 - recentSearchItems.size)
                .map { rec ->
                    OmniboxSuggestion(
                        query = rec.title.ifBlank { rec.url },
                        isHistory = true,
                        source = SuggestionSource.BROWSING_HISTORY,
                        targetUrl = rec.url
                    )
                }

            com.petal.browser.motion.MotionGate.postAfterTransition {
                if (queryState.text.trim().isNotEmpty()) return@postAfterTransition
                suggestions = recentSearchItems + browsingHistoryItems
            }
        } else {
            val queryTokens = currentText.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }
            val lowerCurrent = currentText.lowercase()

            val matchedTabs = openTabs.filter { (title, url, _) ->
                val titleLower = title.lowercase()
                val urlLower = url.lowercase()
                (titleLower.contains(lowerCurrent) || urlLower.contains(lowerCurrent) ||
                        queryTokens.all { titleLower.contains(it) || urlLower.contains(it) }) &&
                        !removedSuggestions.contains(title) && !removedSuggestions.contains(url)
            }.take(2).map { (title, url, index) ->
                OmniboxSuggestion(
                    query = title.ifBlank { url },
                    isHistory = false,
                    source = SuggestionSource.SWITCH_TO_TAB,
                    targetUrl = url,
                    tabIndex = index
                )
            }

            val matchedBookmarks = localBookmarksList.filter { rec ->
                val title = rec.title?.lowercase() ?: ""
                val url = rec.url?.lowercase() ?: ""
                (title.contains(lowerCurrent) || url.contains(lowerCurrent) ||
                        queryTokens.all { title.contains(it) || url.contains(it) }) &&
                        !removedSuggestions.contains(rec.title ?: "") &&
                        !removedSuggestions.contains(rec.url ?: "")
            }.take(3).map { rec ->
                OmniboxSuggestion(
                    query = rec.title?.ifBlank { rec.url } ?: rec.url ?: "",
                    isHistory = false,
                    source = SuggestionSource.BOOKMARK,
                    targetUrl = rec.url
                )
            }

            val savedMatches = savedQueries
                .filter { query ->
                    val lower = query.lowercase()
                    (lower.contains(lowerCurrent) || queryTokens.all { lower.contains(it) }) && !removedSuggestions.contains(query)
                }
                .take(4)
                .map { OmniboxSuggestion(it, isHistory = true, source = SuggestionSource.RECENT_SEARCH) }

            val localMatches = localHistoryList
                .filter { rec ->
                    val titleLower = rec.title.lowercase()
                    val urlLower = rec.url.lowercase()
                    val matches = titleLower.contains(lowerCurrent) || urlLower.contains(lowerCurrent) ||
                            queryTokens.all { titleLower.contains(it) || urlLower.contains(it) }
                    val notRemoved = !removedSuggestions.contains(rec.title) && !removedSuggestions.contains(rec.url)
                    val notInSaved = savedMatches.none { it.query.equals(rec.title, ignoreCase = true) || it.query.equals(rec.url, ignoreCase = true) }
                    matches && notRemoved && notInSaved
                }
                .take(4 - savedMatches.size)
                .map { rec ->
                    OmniboxSuggestion(
                        query = rec.title.ifBlank { rec.url },
                        isHistory = true,
                        source = SuggestionSource.BROWSING_HISTORY,
                        targetUrl = rec.url
                    )
                }

            val historyMatches = savedMatches + localMatches

            val isDirectUrl = com.petal.browser.unit.PetalBrowserUtils.isURL(currentText)
            val directItem = OmniboxSuggestion(
                query = currentText,
                isHistory = false,
                source = SuggestionSource.SEARCH_ENGINE,
                targetUrl = if (isDirectUrl) {
                    if (currentText.contains("://")) currentText
                    else if (currentText.startsWith("localhost", ignoreCase = true) || currentText.startsWith("127.0.0.1")) "http://$currentText"
                    else "https://$currentText"
                } else null
            )

            val liveSuggestionsEnabled = sp.getBoolean("sp_enable_live_suggestions", true)
            val privateSuggestionsEnabled = sp.getBoolean("sp_search_suggestions_private", false)
            val shouldFetchRemote = liveSuggestionsEnabled && (!isIncognito || privateSuggestionsEnabled) && !isDirectUrl

            if (shouldFetchRemote) {
                delay(180)
                SearchSuggestionsManager.fetchSuggestionsForEngine(context, currentEngineIndexStr, currentText) { engineResults ->
                    com.petal.browser.motion.MotionGate.postAfterTransition {
                        if (queryState.text.trim() != currentText) return@postAfterTransition
                        val combined = mutableListOf<OmniboxSuggestion>()
                        if (currentText.isNotBlank() && !removedSuggestions.contains(currentText)) {
                            combined.add(directItem)
                        }
                        combined.addAll(matchedTabs)
                        combined.addAll(matchedBookmarks)
                        combined.addAll(historyMatches)
                        engineResults.forEach { res ->
                            val trimmedRes = res.trim()
                            if (trimmedRes.isNotEmpty() && !removedSuggestions.contains(trimmedRes) && combined.none { it.query.equals(trimmedRes, ignoreCase = true) }) {
                                combined.add(OmniboxSuggestion(trimmedRes, isHistory = false, source = SuggestionSource.SEARCH_ENGINE))
                            }
                        }
                        suggestions = combined
                    }
                }
            } else {
                com.petal.browser.motion.MotionGate.postAfterTransition {
                    if (queryState.text.trim() != currentText) return@postAfterTransition
                    val combined = mutableListOf<OmniboxSuggestion>()
                    if (currentText.isNotBlank() && !removedSuggestions.contains(currentText)) {
                        combined.add(directItem)
                    }
                    combined.addAll(matchedTabs)
                    combined.addAll(matchedBookmarks)
                    combined.addAll(historyMatches)
                    suggestions = combined
                }
            }
        }
    }

    var clipboardText by remember { mutableStateOf<String?>(null) }
    var isClipboardUrl by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                val clipData = clipboard.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val text = clipData.getItemAt(0).text?.toString()?.trim()
                    if (!text.isNullOrBlank()) {
                        clipboardText = text
                        isClipboardUrl = com.petal.browser.unit.PetalBrowserUtils.isURL(text)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(Unit) {
        // Start the keyboard with the page's grow animation (was 100ms) so they rise together.
        delay(30)
        try {
            focusRequester.requestFocus()
            keyboardController?.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // The root's predictive back handler poses and dismisses this page when it hosts it; standalone
    // hosts (the bridge) keep the plain handler.
    if (handleBackInternally) {
        BackHandler(enabled = true) {
            onBackPress()
        }
    }

    Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0),
                snackbarHost = { PetalThemedSnackbarHost(hostState = snackbarHostState) }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            onBackPress()
                        }
                ) {
                    // Background drawn first and captures LocalPetalGlassWallpaper
                    PetalPageBackground(
                        modifier = Modifier.matchParentSize(),
                        pageKey = "omnibox_page"
                    )

                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Search field row pinned to top with status bar padding
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .entrance(index = 0),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextField(
                                value = queryState,
                                onValueChange = { queryState = it },
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.ui_search_or_type_url, currentEngine.name),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                leadingIcon = if (showEngineSelector) {
                                    {
                                        Box {
                                            IconButton(
                                                onClick = { showEngineMenu = true },
                                                modifier = Modifier.size(38.dp)
                                            ) {
                                                val engineIcon = when (currentEngine.name.lowercase()) {
                                                    "google" -> Icons.Rounded.Search
                                                    "duckduckgo" -> Icons.Rounded.Shield
                                                    "startpage" -> Icons.Rounded.VpnKey
                                                    "brave search", "brave" -> Icons.Rounded.Security
                                                    "bing" -> Icons.Rounded.Search
                                                    "searxng", "searx" -> Icons.Rounded.ManageSearch
                                                    "qwant" -> Icons.Rounded.TravelExplore
                                                    "ecosia" -> Icons.Rounded.Eco
                                                    "torrays" -> Icons.Rounded.CloudDownload
                                                    else -> Icons.Rounded.Search
                                                }
                                                Icon(
                                                    imageVector = engineIcon,
                                                    contentDescription = stringResource(R.string.ui_search_with_tap_to_change, currentEngine.name),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            com.petal.browser.ui.containment.PetalPopupMenu(
                                                expanded = showEngineMenu,
                                                onDismissRequest = { showEngineMenu = false }
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.ui_search_engine),
                                                    style = PetalText.sectionHeader,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                                )
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                                allEngines.forEach { engine ->
                                                    val isSelected = engine.index == (currentEngineIndexStr.toIntOrNull() ?: 0)
                                                    com.petal.browser.ui.containment.PetalPopupMenuItem(
                                                        text = {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                Text(
                                                                    text = engine.name,
                                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                                )
                                                                if (isSelected) {
                                                                    Icon(
                                                                        Icons.Rounded.Check,
                                                                        contentDescription = null,
                                                                        tint = MaterialTheme.colorScheme.primary,
                                                                        modifier = Modifier.size(16.dp)
                                                                    )
                                                                }
                                                            }
                                                        },
                                                        onClick = {
                                                            currentEngineIndexStr = engine.index.toString()
                                                            sp.edit()
                                                                .putString("sp_search_engine", engine.index.toString())
                                                                .putBoolean("searchEngineSwitch", false)
                                                                .apply()
                                                            showEngineMenu = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else null,
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (queryState.text.isNotEmpty()) {
                                            IconButton(onClick = { queryState = TextFieldValue("") }) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = stringResource(R.string.ui_clear_text),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        } else {
                                            IconButton(onClick = {
                                                com.petal.browser.lens.PetalLensBridge.showPetalScannerDirectly(activity)
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Rounded.CenterFocusWeak,
                                                    contentDescription = stringResource(R.string.ui_petal_qr_scanner),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            IconButton(onClick = {
                                                PetalVoiceSearchBridge.showVoiceSearchSheet(activity) { result ->
                                                    if (result.isNotBlank()) submitSearch(result.trim(), saveToHistory = true)
                                                }
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Mic,
                                                    contentDescription = stringResource(R.string.ui_voice_search),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                },
                                maxLines = 3,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    // "/word" that matches a command runs it; a bare "/" or an unknown
                                    // "/word" falls through to the normal search / URL behaviour.
                                    val slashWord = queryState.text.removePrefix("/").trim()
                                    if (queryState.text.startsWith("/") && slashWord.isNotEmpty() && slashMatches.isNotEmpty()) {
                                        runSlashCommand(slashMatches.first().command)
                                    } else if (queryState.text.isNotBlank()) {
                                        submitSearch(queryState.text.trim(), saveToHistory = true)
                                    }
                                }),
                                shape = RoundedCornerShape(50),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    unfocusedContainerColor = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .liquidGlassChrome(shape = RoundedCornerShape(50), enabled = isGlass, sheenIntensity = sheen)
                                    .focusRequester(focusRequester), glass = false
                            )
                        }

                        // Chrome-style Quick Actions Card when an active webpage is focused and user hasn't edited the search
                        if (cleanPageUrl.isNotBlank() && queryState.text.trim() == cleanPageUrl) {
                            val qaShape = RoundedCornerShape(20.dp)
                            Surface(
                                shape = qaShape,
                                color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                                    .entrance(index = 1)
                                    .liquidGlassChrome(shape = qaShape, enabled = isGlass, sheenIntensity = sheen, pressFx = true)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Favicon + Title & Domain
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (favicon != null) {
                                            Image(
                                                bitmap = favicon.asImageBitmap(),
                                                contentDescription = stringResource(R.string.ui_page_favicon),
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(ExpressiveButtonShape)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(ExpressiveButtonShape)
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Language,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        Spacer(Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (pageTitle.isNotBlank()) pageTitle else (pageDomain.ifBlank { cleanPageUrl }),
                                                style = PetalText.cardTitle,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (pageDomain.isNotBlank()) pageDomain else cleanPageUrl,
                                                style = PetalText.listItemMeta,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Spacer(Modifier.width(8.dp))

                                    // 3 Quick Action Icons: Native Share, One-tap Copy, Edit Pencil
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 1. Native Share
                                        IconButton(
                                            onClick = {
                                                try {
                                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_TEXT, cleanPageUrl)
                                                        if (pageTitle.isNotBlank()) {
                                                            putExtra(Intent.EXTRA_SUBJECT, pageTitle)
                                                        }
                                                    }
                                                    activity.startActivity(Intent.createChooser(shareIntent, "Share Webpage"))
                                                } catch (e: Exception) {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    clipboard.setPrimaryClip(ClipData.newPlainText("URL", cleanPageUrl))
                                                    com.petal.browser.view.PetalToast.show(context, context.getString(R.string.pref_custom_tabs_copied))
                                                }
                                            },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Share,
                                                contentDescription = stringResource(R.string.ui_share_url),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        // 2. One-tap Copy
                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("URL", cleanPageUrl))
                                                com.petal.browser.view.PetalToast.show(context, context.getString(R.string.pref_custom_tabs_copied))
                                            },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ContentCopy,
                                                contentDescription = stringResource(R.string.ui_copy_url),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        // 3. Edit Pencil
                                        IconButton(
                                            onClick = {
                                                queryState = TextFieldValue(
                                                    text = cleanPageUrl,
                                                    selection = TextRange(cleanPageUrl.length)
                                                )
                                                try {
                                                    focusRequester.requestFocus()
                                                    keyboardController?.show()
                                                } catch (e: Exception) {}
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Edit,
                                                contentDescription = stringResource(R.string.ui_edit_url),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Smart Paste & Search Action Pill (Detects URLs and text queries from clipboard when query is empty)
                        androidx.compose.animation.AnimatedVisibility(
                            visible = clipboardText != null && queryState.text.isEmpty(),
                            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
                        ) {
                            clipboardText?.let { clip ->
                                val pasteShape = RoundedCornerShape(24.dp)
                                Surface(
                                    onClick = {
                                        try {
                                        } catch (_: Throwable) {}
                                        submitSearch(clip, saveToHistory = !isClipboardUrl)
                                    },
                                    shape = pasteShape,
                                    color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .entrance(index = 2)
                                        .liquidGlassChrome(shape = pasteShape, enabled = isGlass, sheenIntensity = sheen, pressFx = true)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Left: Globe or Search Icon
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(ExpressiveButtonShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isClipboardUrl) Icons.Rounded.Language else Icons.Rounded.Search,
                                                contentDescription = if (isClipboardUrl) "Open Link" else "Search Clipboard",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Spacer(Modifier.width(12.dp))

                                        // Center: Action Title & Content snippet
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (isClipboardUrl) "Link from clipboard" else "Search from clipboard",
                                                style = PetalText.cardTitle,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = clip,
                                                style = PetalText.listItemMeta,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Spacer(Modifier.width(8.dp))

                                        // Right: Insert/Edit into input field
                                        IconButton(
                                            onClick = {
                                                queryState = TextFieldValue(
                                                    text = clip,
                                                    selection = TextRange(clip.length)
                                                )
                                                try {
                                                    focusRequester.requestFocus()
                                                    keyboardController?.show()
                                                } catch (_: Exception) {}
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.NorthWest,
                                                contentDescription = stringResource(R.string.ui_insert_into_search),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }


                        // Frequently Visited Site Shortcuts Row (Only show when query hasn't been edited)
                        if (cleanPageUrl.isNotBlank() && queryState.text.trim() == cleanPageUrl) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.ui_frequently_visited),
                                    style = PetalText.sectionHeader,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                                )

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    itemsIndexed(siteShortcuts, key = { index, shortcut -> "${index}_${shortcut.url.ifEmpty { shortcut.title }}" }) { _, shortcut ->
                                        val faviconUrl = remember(shortcut.url) {
                                            com.petal.browser.database.FaviconHelper.getGoogleFaviconUrl(shortcut.url)
                                        }

                                        val shortcutShape = RoundedCornerShape(16.dp)
                                        Surface(
                                            onClick = { submitSearch(shortcut.url) },
                                            shape = shortcutShape,
                                            color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainer,
                                            tonalElevation = if (isGlass) 0.dp else 1.dp,
                                            modifier = Modifier
                                                .width(92.dp)
                                                .liquidGlassChrome(shape = shortcutShape, enabled = isGlass, sheenIntensity = sheen, pressFx = true)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(ExpressiveButtonShape)
                                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    coil.compose.SubcomposeAsyncImage(
                                                        model = faviconUrl,
                                                        contentDescription = shortcut.title,
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .clip(ExpressiveButtonShape),
                                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                                        loading = {
                                                            Icon(
                                                                imageVector = shortcut.icon,
                                                                contentDescription = shortcut.title,
                                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        },
                                                        error = {
                                                            Icon(
                                                                imageVector = shortcut.icon,
                                                                contentDescription = shortcut.title,
                                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    )
                                                }

                                                Spacer(Modifier.height(4.dp))

                                                Text(
                                                    text = shortcut.title,
                                                    style = PetalText.caption,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Slash commands sit above the suggestions and only appear for a leading "/".
                        AnimatedVisibility(
                            visible = slashMatches.isNotEmpty(),
                            enter = fadeIn(animationSpec = PetalSpring.calm()) +
                                slideInVertically(animationSpec = PetalSpring.calm()) { -it / 5 },
                            exit = fadeOut(animationSpec = PetalSpring.calm()) +
                                slideOutVertically(animationSpec = PetalSpring.calm()) { -it / 5 },
                        ) {
                            SlashCommandCard(
                                specs = slashMatches,
                                isGlass = isGlass,
                                sheen = sheen,
                                onRun = { runSlashCommand(it) }
                            )
                        }

                        // Suggestions List - fills the remaining space above the keyboard with Material 3 Expressive containment
                        if (suggestions.isNotEmpty()) {
                            val heroShape = com.petal.browser.ui.containment.PetalContainmentShapes.Hero
                            Surface(
                                shape = heroShape,
                                color = if (isGlass) com.petal.browser.ui.containment.petalGroupSurfaceColor().copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .liquidGlassChrome(shape = heroShape, enabled = isGlass, sheenIntensity = sheen)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.ui_search_suggestions),
                                        style = PetalText.sectionHeader,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                                    )

                                    // Build a flat list interleaving section header entries and suggestion row entries.
                                    // Grouped by source with strictly unique composite index keys to prevent Compose collision crashes.
                                    val listEntries = remember(suggestions) {
                                        buildList {
                                            val grouped = suggestions.groupBy { it.source }
                                            var globalIndex = 0
                                            grouped.forEach { (source, items) ->
                                                add("header_${source.name}_${globalIndex++}" to OmniboxListEntry.Header(source))
                                                items.forEachIndexed { idx, item ->
                                                    add("item_${source.name}_${globalIndex++}_${idx}_${item.query}_${item.targetUrl ?: ""}" to OmniboxListEntry.Item(item, idx, items.size))
                                                }
                                            }
                                        }
                                    }

                                    LazyColumn(
                                        flingBehavior = petalFlingBehavior(),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f, fill = false),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        verticalArrangement = Arrangement.spacedBy(1.5.dp)
                                    ) {
                                        items(
                                            items = listEntries,
                                            key = { it.first }
                                        ) { entry ->
                                            when (val row = entry.second) {
                                                is OmniboxListEntry.Header -> {
                                                    // Section header
                                                    val headerLabel = when (row.source) {
                                                        SuggestionSource.SWITCH_TO_TAB -> stringResource(R.string.ui_switch_to_tab)
                                                        SuggestionSource.RECENT_SEARCH -> stringResource(R.string.ui_recent_searches)
                                                        SuggestionSource.BROWSING_HISTORY -> stringResource(R.string.ui_from_browsing_history)
                                                        SuggestionSource.SEARCH_ENGINE -> stringResource(R.string.ui_search_suggestions)
                                                        SuggestionSource.BOOKMARK -> stringResource(R.string.ui_bookmarks)
                                                    }
                                                    Text(
                                                        text = headerLabel,
                                                        style = PetalText.sectionHeader,
                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                                                        modifier = Modifier
                                                            .animateItem(
                                                                fadeInSpec = PetalSpring.calm(),
                                                                fadeOutSpec = PetalSpring.calm(),
                                                                placementSpec = PetalSpring.calm()
                                                            )
                                                            .padding(
                                                                start = 16.dp,
                                                                end = 16.dp,
                                                                top = 10.dp,
                                                                bottom = 2.dp
                                                            )
                                                    )
                                                }
                                                is OmniboxListEntry.Item -> {
                                                    val payload = row.suggestion
                                                    val isDirectEngineNav = payload.targetUrl != null && payload.source == SuggestionSource.SEARCH_ENGINE

                                                    // For URLs, keep end-visible if single line, but allow multi-line wrapped text for long search queries & titles like Brave
                                                    val isUrl = payload.query.startsWith("http") ||
                                                            (payload.query.contains(".") && !payload.query.contains(" "))
                                                    val displayText = if (isUrl) smartEllipsize(payload.query, true, maxChars = 80) else payload.query

                                                    // Icon and tint per source group
                                                    val badgeIcon = when {
                                                        isDirectEngineNav -> Icons.Rounded.Language
                                                        payload.source == SuggestionSource.SWITCH_TO_TAB -> Icons.Rounded.Tab
                                                        payload.source == SuggestionSource.BOOKMARK -> Icons.Rounded.Bookmark
                                                        payload.source == SuggestionSource.RECENT_SEARCH -> Icons.Rounded.History
                                                        payload.source == SuggestionSource.BROWSING_HISTORY -> Icons.Rounded.History
                                                        else -> Icons.Rounded.Search
                                                    }
                                                    val badgeContainer = when (payload.source) {
                                                        SuggestionSource.SWITCH_TO_TAB -> MaterialTheme.colorScheme.tertiaryContainer
                                                        SuggestionSource.BOOKMARK -> MaterialTheme.colorScheme.primaryContainer
                                                        SuggestionSource.RECENT_SEARCH -> MaterialTheme.colorScheme.secondaryContainer
                                                        SuggestionSource.BROWSING_HISTORY -> MaterialTheme.colorScheme.tertiaryContainer
                                                        SuggestionSource.SEARCH_ENGINE -> MaterialTheme.colorScheme.primaryContainer
                                                    }
                                                    val badgeTint = when (payload.source) {
                                                        SuggestionSource.SWITCH_TO_TAB -> MaterialTheme.colorScheme.onTertiaryContainer
                                                        SuggestionSource.BOOKMARK -> MaterialTheme.colorScheme.onPrimaryContainer
                                                        SuggestionSource.RECENT_SEARCH -> MaterialTheme.colorScheme.onSecondaryContainer
                                                        SuggestionSource.BROWSING_HISTORY -> MaterialTheme.colorScheme.onTertiaryContainer
                                                        SuggestionSource.SEARCH_ENGINE -> MaterialTheme.colorScheme.onPrimaryContainer
                                                    }

                                                    com.petal.browser.ui.containment.PetalGroupListRow(
                                                        position = com.petal.browser.ui.containment.petalGroupPositionFor(row.indexInGroup, row.groupSize),
                                                        minHeight = 48.dp,
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                        onClick = {
                                                            if (payload.source == SuggestionSource.SWITCH_TO_TAB && payload.tabIndex >= 0) {
                                                                try {
                                                                    val target = com.petal.browser.engine.gecko.PetalEngineStore.getStore(context).state.tabs[payload.tabIndex]
                                                                    com.petal.browser.engine.gecko.PetalEngineStore.getStore(context).dispatch(
                                                                        mozilla.components.browser.state.action.TabListAction.SelectTabAction(target.id)
                                                                    )
                                                                    onBackPress()
                                                                } catch (_: Exception) {
                                                                    val trimmed = payload.targetUrl?.trim() ?: payload.query.trim()
                                                                    if (trimmed.isNotEmpty()) submitSearch(trimmed, saveToHistory = false)
                                                                }
                                                            } else if (!payload.targetUrl.isNullOrBlank()) {
                                                                submitSearch(payload.targetUrl.trim(), saveToHistory = false)
                                                            } else {
                                                                val trimmed = payload.query.trim()
                                                                if (trimmed.isNotEmpty()) submitSearch(trimmed, saveToHistory = true)
                                                            }
                                                        },
                                                        onLongClick = {
                                                            try {
                                                                com.petal.browser.haptics.PetalHapticEngine.getInstance(context).thresholdCrossed(context)
                                                            } catch (ignored: Exception) {}
                                                            suggestionToRemove = payload
                                                        },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .animateItem(
                                                                fadeInSpec = PetalSpring.calm(),
                                                                fadeOutSpec = PetalSpring.calm(),
                                                                placementSpec = PetalSpring.calm()
                                                            ),
                                                        leading = {
                                                            com.petal.browser.ui.containment.PetalGroupIconBadge(
                                                                icon = badgeIcon,
                                                                container = badgeContainer,
                                                                tint = badgeTint,
                                                                size = 36.dp,
                                                                iconSize = 18.dp
                                                            )
                                                        },
                                                        content = {
                                                            Column {
                                                                Text(
                                                                    displayText,
                                                                    style = PetalText.listItemTitle,
                                                                    color = MaterialTheme.colorScheme.onSurface
                                                                )
                                                                if (!payload.targetUrl.isNullOrBlank() && (payload.source == SuggestionSource.SWITCH_TO_TAB || payload.source == SuggestionSource.BOOKMARK || payload.source == SuggestionSource.BROWSING_HISTORY)) {
                                                                    Text(
                                                                        text = smartEllipsize(payload.targetUrl, true, maxChars = 60),
                                                                        style = PetalText.listItemMeta,
                                                                        color = MaterialTheme.colorScheme.primary,
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis
                                                                    )
                                                                } else if (isDirectEngineNav && !payload.targetUrl.isNullOrBlank()) {
                                                                    Text(
                                                                        text = smartEllipsize(payload.targetUrl, true, maxChars = 60),
                                                                        style = PetalText.listItemMeta,
                                                                        color = MaterialTheme.colorScheme.primary,
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis
                                                                    )
                                                                } else if (payload.source == SuggestionSource.SEARCH_ENGINE && payload.query.equals(queryState.text.trim(), ignoreCase = true)) {
                                                                    Text(
                                                                        text = stringResource(R.string.ui_search_with, currentEngine.name),
                                                                        style = PetalText.listItemMeta,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                    )
                                                                }
                                                            }
                                                        },
                                                        trailing = {
                                                            val insertText = if (!payload.targetUrl.isNullOrBlank() && (payload.source == SuggestionSource.BROWSING_HISTORY || payload.source == SuggestionSource.BOOKMARK || payload.source == SuggestionSource.SWITCH_TO_TAB || isDirectEngineNav)) {
                                                                payload.targetUrl
                                                            } else {
                                                                payload.query
                                                            }
                                                            IconButton(
                                                                onClick = { queryState = TextFieldValue(text = insertText, selection = TextRange(insertText.length)) },
                                                                modifier = Modifier.size(36.dp),
                                                            ) {
                                                                Icon(Icons.Rounded.NorthWest, contentDescription = stringResource(R.string.ui_insert_query_into_omnibox), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                                            }
                                                        },
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
            }

    if (suggestionToRemove != null) {
        val item = suggestionToRemove!!
        com.petal.browser.ui.containment.PetalMaterialAlertDialog(
            onDismissRequest = { suggestionToRemove = null },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = {
                Icon(
                    imageVector = if (item.isHistory) Icons.Rounded.DeleteSweep else Icons.Rounded.RemoveCircleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.ui_remove_suggestion),
                    style = PetalText.dialogTitle,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.ui_remove_from_search_suggestions, item.query, if (item.isHistory) " This will also delete it from your browsing history." else ""),
                    style = PetalText.dialogBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetQuery = item.query
                        val targetUrl = item.targetUrl
                        suggestionToRemove = null
                        val newSet = (removedSuggestions + targetQuery + (targetUrl ?: "")).filter { it.isNotBlank() }.toSet()
                        removedSuggestions = newSet
                        sp.edit().putStringSet("sp_removed_suggestions", newSet).apply()

                        // Remove from persistent search history list as well
                        if (!isIncognito && (item.source == SuggestionSource.RECENT_SEARCH || item.targetUrl == null)) {
                            val listKey = "sp_search_history_list"
                            val updatedList = getSavedSearchHistory().filterNot { it.equals(targetQuery, ignoreCase = true) }
                            sp.edit().putString(listKey, JSONArray(updatedList).toString()).apply()
                        }

                        suggestions = suggestions.filter {
                            !it.query.equals(targetQuery, ignoreCase = true) &&
                                (targetUrl.isNullOrBlank() || !it.targetUrl.equals(targetUrl, ignoreCase = true))
                        }

                        if (item.isHistory) {
                            Thread {
                                try {
                                    val action = HistoryBookmarkRepository(context)
                                    action.open(true)
                                    if (!targetUrl.isNullOrBlank()) {
                                        action.deleteURL(targetUrl, com.petal.browser.database.HistoryBookmarkContract.TABLE_HISTORY)
                                    }
                                    action.deleteHistoryByTitle(targetQuery)
                                    action.close()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }.start()
                        }

                        coroutineScope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "Removed \"$targetQuery\"",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                val restoredSet = (removedSuggestions - targetQuery - (targetUrl ?: "")).toSet()
                                removedSuggestions = restoredSet
                                sp.edit().putStringSet("sp_removed_suggestions", restoredSet).apply()
                                if (item.source == SuggestionSource.RECENT_SEARCH && !isIncognito) {
                                    val currentHistory = getSavedSearchHistory().toMutableList()
                                    if (currentHistory.none { it.equals(targetQuery, ignoreCase = true) }) {
                                        currentHistory.add(0, targetQuery)
                                        sp.edit().putString("sp_search_history_list", JSONArray(currentHistory).toString()).apply()
                                    }
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.ui_remove), style = PetalText.button)
                }
            },
            dismissButton = {
                FilledTonalButton(
                    onClick = { suggestionToRemove = null },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.ui_cancel), style = PetalText.button)
                }
            }
        )
    }
}
