/*
 * PetalOmniboxPage.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Chrome-style full-screen Omnibox search page in Jetpack Compose. Replaces
 * the legacy AlertDialog-based search dialog (dialog_search.xml + AdapterSearch)
 * AND the previous bottom-sheet PetalOmniboxOverlay, unifying every entry point
 * (address bar tap, home page search box, incognito home, search widget) behind
 * a single full-page surface that mounts directly into BrowserActivity's
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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.CenterFocusWeak
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
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
import com.petal.browser.database.Record
import com.petal.browser.database.RecordAction
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.theme.*
import com.petal.browser.unit.SearchSuggestionsManager
import kotlinx.coroutines.delay
import org.json.JSONArray
import java.net.URI
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

enum class SuggestionSource { RECENT_SEARCH, BROWSING_HISTORY, SEARCH_ENGINE }

data class OmniboxSuggestion(
    val query: String,
    val isHistory: Boolean,
    val source: SuggestionSource = SuggestionSource.SEARCH_ENGINE
)

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

object PetalOmniboxBridge {

    /**
     * Builds the full-screen omnibox page as a plain [ComposeView] meant to be mounted
     * into BrowserActivity's `contentFrame`, supporting Chrome-style quick-action cards
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
        onQuerySubmitted: (String) -> Unit
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
                val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                val isAmoled = sp.getBoolean("sp_amoled", false)
                val dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)

                val appFont = remember(fontName) {
                    AppFont.fromName(fontName)
                }
                val colorStyle = remember(styleName) {
                    try { ColorStyle.valueOf(styleName) } catch (e: Exception) { ColorStyle.TONAL_SPOT }
                }

                PetalExpressiveTheme(
                    dynamicColor = dynamicColor,
                    useAmoled = isAmoled,
                    appFont = appFont,
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
                        onQuerySubmitted = { query ->
                            onQuerySubmitted(query)
                            onBackPress()
                        },
                        onBackPress = onBackPress
                    )
                }
            }
        }
    }
}

@Composable
fun PetalOmniboxPage(
    backgroundSnapshot: androidx.compose.ui.graphics.ImageBitmap? = null,
    activity: ComponentActivity,
    initialQuery: String = "",
    pageTitle: String = "",
    pageUrl: String = "",
    favicon: Bitmap? = null,
    isIncognito: Boolean = false,
    onQuerySubmitted: (String) -> Unit,
    onBackPress: () -> Unit
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
        val listKey = if (isIncognito) "sp_incognito_search_history_list" else "sp_search_history_list"
        val legacyKey = if (isIncognito) "sp_incognito_search_history_queries" else "sp_search_history_queries"
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

    fun submitSearch(query: String, saveToHistory: Boolean = false) {
        val normalized = query.trim()
        if (normalized.isBlank()) return
        try {
            keyboardController?.hide()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            val windowToken = (context as? android.app.Activity)?.window?.decorView?.windowToken
            if (imm != null && windowToken != null) {
                imm.hideSoftInputFromWindow(windowToken, 0)
            }
        } catch (_: Throwable) {}
        if (saveToHistory && !isIncognito) {
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
        onQuerySubmitted(normalized)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val cleanedInitialQuery = remember(initialQuery) {
        val trimmed = initialQuery.trim()
        if (trimmed.equals("about:blank", ignoreCase = true) || trimmed.startsWith("about:", ignoreCase = true)) {
            ""
        } else {
            initialQuery
        }
    }
    var queryState by remember {
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
            emptyList<String>()
        } else {
            val list = mutableListOf<String>()
            try {
                val action = RecordAction(context)
                action.open(false)
                val records: List<Record> = action.listHistory(context)
                action.close()
                // Sort by time descending so most-recently-visited titles appear first
                records.sortedByDescending { it.time }.forEach { r ->
                    if (!r.title.isNullOrBlank()) list.add(r.title)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            list.distinct()
        }
    }

    // Debounced search query handler with robust long multi-word query support
    LaunchedEffect(queryState.text, removedSuggestions, isIncognito) {
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
                .filter { !removedSuggestions.contains(it) && !recentSearchSet.contains(it.lowercase()) }
                .take(8 - recentSearchItems.size)
                .map { OmniboxSuggestion(it, isHistory = true, source = SuggestionSource.BROWSING_HISTORY) }

            suggestions = recentSearchItems + browsingHistoryItems
        } else {
            val queryTokens = currentText.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }
            val savedMatches = savedQueries
                .filter { query ->
                    val lower = query.lowercase()
                    (lower.contains(currentText.lowercase()) || queryTokens.all { lower.contains(it) }) && !removedSuggestions.contains(query)
                }
                .take(4)
                .map { OmniboxSuggestion(it, isHistory = true, source = SuggestionSource.RECENT_SEARCH) }

            val localMatches = localHistoryList
                .filter { title ->
                    val lower = title.lowercase()
                    (lower.contains(currentText.lowercase()) || queryTokens.all { lower.contains(it) }) &&
                            !removedSuggestions.contains(title) &&
                            savedMatches.none { it.query.equals(title, ignoreCase = true) }
                }
                .take(4 - savedMatches.size)
                .map { OmniboxSuggestion(it, isHistory = true, source = SuggestionSource.BROWSING_HISTORY) }

            val historyMatches = savedMatches + localMatches
            suggestions = historyMatches

            val liveSuggestionsEnabled = true
            if (liveSuggestionsEnabled) {
                delay(200)
                val searchEngine = sp.getString("sp_search_engine", "0")
                SearchSuggestionsManager.fetchSuggestionsForEngine(searchEngine, currentText) { engineResults ->
                    val combined = mutableListOf<OmniboxSuggestion>()
                    combined.addAll(historyMatches)
                    engineResults.forEach { res ->
                        val trimmedRes = res.trim()
                        if (trimmedRes.isNotEmpty() && !removedSuggestions.contains(trimmedRes) && combined.none { it.query.equals(trimmedRes, ignoreCase = true) }) {
                            combined.add(OmniboxSuggestion(trimmedRes, isHistory = false, source = SuggestionSource.SEARCH_ENGINE))
                        }
                    }
                    // For longer searches, ensure the exact query is presented as an actionable direct search row if not already present
                    if (currentText.length >= 2 && !removedSuggestions.contains(currentText) && combined.none { it.query.equals(currentText, ignoreCase = true) }) {
                        combined.add(0, OmniboxSuggestion(currentText, isHistory = false, source = SuggestionSource.SEARCH_ENGINE))
                    }
                    suggestions = combined
                }
            } else {
                if (currentText.length >= 2 && !removedSuggestions.contains(currentText) && historyMatches.none { it.query.equals(currentText, ignoreCase = true) }) {
                    suggestions = listOf(OmniboxSuggestion(currentText, isHistory = false, source = SuggestionSource.SEARCH_ENGINE)) + historyMatches
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
                        isClipboardUrl = com.petal.browser.unit.BrowserUnit.isURL(text)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        try {
            focusRequester.requestFocus()
            keyboardController?.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    com.petal.browser.predictive.PetalPredictiveBackSurface(
        enabled = true,
        onBack = { onBackPress() },
    ) {
        com.petal.browser.predictive.PetalScreenWrapper(backgroundSnapshot = backgroundSnapshot) {
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
                    M3ExpressiveVariableBackground(
                        modifier = Modifier.matchParentSize(),
                        pageSeed = "omnibox_page"
                    )

                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Search field row pinned to top with status bar padding
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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

                            OutlinedTextField(
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
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
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
                                                com.petal.browser.lens.PetalLensBridge.showLensBottomSheet(activity)
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
                                    if (queryState.text.isNotBlank()) {
                                        submitSearch(queryState.text.trim(), saveToHistory = true)
                                    }
                                }),
                                shape = RoundedCornerShape(50),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    unfocusedContainerColor = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .liquidGlassChrome(shape = RoundedCornerShape(50), enabled = isGlass, sheenIntensity = sheen)
                                    .focusRequester(focusRequester)
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
                                    .liquidGlassChrome(shape = qaShape, enabled = isGlass, sheenIntensity = sheen)
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
                                                    .clip(CircleShape)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
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
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (pageDomain.isNotBlank()) pageDomain else cleanPageUrl,
                                                style = MaterialTheme.typography.bodySmall,
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
                                                    com.petal.browser.view.PetalToast.show(context, "Link copied to clipboard")
                                                }
                                            },
                                            modifier = Modifier.size(36.dp)
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
                                                com.petal.browser.view.PetalToast.show(context, "Link copied to clipboard")
                                            },
                                            modifier = Modifier.size(36.dp)
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
                                            com.petal.browser.haptics.PetalHapticEngine.getInstance(context).play(
                                                com.petal.browser.haptics.PetalHapticEngine.Pattern.CLICK,
                                                0.55f
                                            )
                                        } catch (_: Throwable) {}
                                        submitSearch(clip, saveToHistory = !isClipboardUrl)
                                    },
                                    shape = pasteShape,
                                    color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .liquidGlassChrome(shape = pasteShape, enabled = isGlass, sheenIntensity = sheen)
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
                                                .clip(CircleShape)
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
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = clip,
                                                style = MaterialTheme.typography.bodySmall,
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
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                                )

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(siteShortcuts) { shortcut ->
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
                                                .liquidGlassChrome(shape = shortcutShape, enabled = isGlass, sheenIntensity = sheen)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    coil.compose.SubcomposeAsyncImage(
                                                        model = faviconUrl,
                                                        contentDescription = shortcut.title,
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .clip(CircleShape),
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
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
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
                                    .animateContentSize(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                    .liquidGlassChrome(shape = heroShape, enabled = isGlass, sheenIntensity = sheen)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.ui_search_suggestions),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                                    )

                                    // Build a flat list interleaving section header entries and suggestion row entries.
                                    // `remember` is valid here — we are inside the Column composable scope.
                                    val listEntries = remember(suggestions) {
                                        buildList {
                                            var lastSrc: SuggestionSource? = null
                                            suggestions.forEach { item ->
                                                if (item.source != lastSrc) {
                                                    lastSrc = item.source
                                                    add("header_${item.source.name}" to item.source)
                                                }
                                                add("item_${item.source.name}_${item.query}" to item)
                                            }
                                        }
                                    }

                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f, fill = false),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        verticalArrangement = Arrangement.spacedBy(1.5.dp)
                                    ) {
                                        itemsIndexed(
                                            items = listEntries,
                                            key = { _, entry -> entry.first }
                                        ) { entryIndex, entry ->
                                            when (val payload = entry.second) {
                                                is SuggestionSource -> {
                                                    // Section header
                                                    val headerLabel = when (payload) {
                                                        SuggestionSource.RECENT_SEARCH -> "Recent Searches"
                                                        SuggestionSource.BROWSING_HISTORY -> "From Browsing History"
                                                        SuggestionSource.SEARCH_ENGINE -> stringResource(R.string.ui_search_suggestions)
                                                    }
                                                    Text(
                                                        text = headerLabel,
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                                                        modifier = Modifier.padding(
                                                            start = 16.dp,
                                                            end = 16.dp,
                                                            top = if (entryIndex == 0) 2.dp else 10.dp,
                                                            bottom = 2.dp
                                                        )
                                                    )
                                                }
                                                is OmniboxSuggestion -> {
                                                    // Suggestion row index within `suggestions` for group shape
                                                    val itemIndex = suggestions.indexOf(payload)

                                                    // For URLs, keep end-visible if single line, but allow multi-line wrapped text for long search queries & titles like Brave
                                                    val isUrl = payload.query.startsWith("http") ||
                                                            (payload.query.contains(".") && !payload.query.contains(" "))
                                                    val displayText = if (isUrl) smartEllipsize(payload.query, true, maxChars = 80) else payload.query

                                                    // Icon and tint per source group
                                                    val badgeIcon = when (payload.source) {
                                                        SuggestionSource.RECENT_SEARCH -> Icons.Filled.History
                                                        SuggestionSource.BROWSING_HISTORY -> Icons.Filled.History
                                                        SuggestionSource.SEARCH_ENGINE -> Icons.Filled.Search
                                                    }
                                                    val badgeContainer = when (payload.source) {
                                                        SuggestionSource.RECENT_SEARCH -> MaterialTheme.colorScheme.secondaryContainer
                                                        SuggestionSource.BROWSING_HISTORY -> MaterialTheme.colorScheme.tertiaryContainer
                                                        SuggestionSource.SEARCH_ENGINE -> MaterialTheme.colorScheme.primaryContainer
                                                    }
                                                    val badgeTint = when (payload.source) {
                                                        SuggestionSource.RECENT_SEARCH -> MaterialTheme.colorScheme.onSecondaryContainer
                                                        SuggestionSource.BROWSING_HISTORY -> MaterialTheme.colorScheme.onTertiaryContainer
                                                        SuggestionSource.SEARCH_ENGINE -> MaterialTheme.colorScheme.onPrimaryContainer
                                                    }

                                                    com.petal.browser.ui.containment.PetalGroupListRow(
                                                        position = com.petal.browser.ui.containment.petalGroupPositionFor(itemIndex, suggestions.size),
                                                        minHeight = 48.dp,
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                        onClick = {
                                                            val trimmed = payload.query.trim()
                                                            if (trimmed.isNotEmpty()) submitSearch(trimmed)
                                                        },
                                                        onLongClick = {
                                                            try {
                                                                com.petal.browser.haptics.PetalHapticEngine.getInstance(context).play(com.petal.browser.haptics.PetalHapticEngine.Pattern.HEAVY_CLICK, 0.9f)
                                                            } catch (ignored: Exception) {}
                                                            suggestionToRemove = payload
                                                        },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            // Rows fade in/out and slide smoothly into their
                                                            // new position as `suggestions` is replaced on
                                                            // every keystroke, instead of the list just
                                                            // popping to its new contents.
                                                            .animateItem(
                                                                fadeInSpec = tween(220),
                                                                fadeOutSpec = tween(150),
                                                                placementSpec = spring(
                                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                                    stiffness = Spring.StiffnessMediumLow
                                                                )
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
                                                            // Compact Brave-style bodyMedium (13.5sp) with 2-line wrapped text support
                                                            Text(
                                                                displayText,
                                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                                    fontSize = 13.5.sp,
                                                                    lineHeight = 17.sp
                                                                ),
                                                                fontWeight = FontWeight.Medium,
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                                maxLines = 2,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        },
                                                        trailing = {
                                                            IconButton(
                                                                onClick = { queryState = TextFieldValue(text = payload.query, selection = TextRange(payload.query.length)) },
                                                                modifier = Modifier.size(36.dp),
                                                            ) {
                                                                Icon(Icons.Filled.NorthWest, contentDescription = stringResource(R.string.ui_insert_query_into_omnibox), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
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
        }
    }

    if (suggestionToRemove != null) {
        val item = suggestionToRemove!!
        com.petal.browser.ui.containment.PetalMaterialAlertDialog(
            onDismissRequest = { suggestionToRemove = null },
            shape = RoundedCornerShape(28.dp),
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
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.ui_remove_from_search_suggestions, item.query, if (item.isHistory) "This will also delete it from your browsing history." else ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetQuery = item.query
                        suggestionToRemove = null
                        val newSet = (removedSuggestions + targetQuery).toSet()
                        removedSuggestions = newSet
                        sp.edit().putStringSet("sp_removed_suggestions", newSet).apply()

                        // Remove from persistent search history list as well
                        val listKey = if (isIncognito) "sp_incognito_search_history_list" else "sp_search_history_list"
                        val updatedList = getSavedSearchHistory().filterNot { it.equals(targetQuery, ignoreCase = true) }
                        sp.edit().putString(listKey, JSONArray(updatedList).toString()).apply()

                        suggestions = suggestions.filter { !it.query.equals(targetQuery, ignoreCase = true) }

                        if (item.isHistory) {
                            Thread {
                                try {
                                    val action = RecordAction(context)
                                    action.open(true)
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
                                val restoredSet = (removedSuggestions - targetQuery).toSet()
                                removedSuggestions = restoredSet
                                sp.edit().putStringSet("sp_removed_suggestions", restoredSet).apply()
                                if (item.isHistory) {
                                    val currentHistory = getSavedSearchHistory().toMutableList()
                                    if (currentHistory.none { it.equals(targetQuery, ignoreCase = true) }) {
                                        currentHistory.add(0, targetQuery)
                                        sp.edit().putString(listKey, JSONArray(currentHistory).toString()).apply()
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
                    Text(stringResource(R.string.ui_remove), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { suggestionToRemove = null },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.ui_cancel))
                }
            }
        )
    }
}
