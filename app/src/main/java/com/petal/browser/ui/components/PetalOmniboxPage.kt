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

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
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
import com.petal.browser.ui.theme.*
import com.petal.browser.unit.SearchSuggestionsManager
import kotlinx.coroutines.delay

data class OmniboxSuggestion(
    val query: String,
    val isHistory: Boolean
)

object PetalOmniboxBridge {

    /**
     * Builds the full-screen omnibox page as a plain [ComposeView] meant to be mounted
     * into BrowserActivity's `contentFrame`, exactly like PetalDownloadBridge /
     * PetalHistoryBridge / PetalAccountSyncBridge. [onBackPress] is invoked both on the
     * back button/gesture and after a successful query submission's caller decides to
     * dismiss - callers are expected to call `showAlbum(currentAlbumController)` there
     * to return to the underlying page, matching every other full-screen surface.
     */
    @JvmStatic
    @JvmOverloads
    fun createOmniboxView(
        activity: ComponentActivity,
        initialQuery: String = "",
        onBackPress: () -> Unit,
        onQuerySubmitted: (String) -> Unit
    ): ComposeView {
        return ComposeView(activity).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val sp = PreferenceManager.getDefaultSharedPreferences(activity)
                val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                val isAmoled = sp.getBoolean("sp_amoled", false)
                val dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)

                val appFont = remember(fontName) {
                    try { AppFont.valueOf(fontName) } catch (e: Exception) { AppFont.GS_FLEX }
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
                        activity = activity,
                        initialQuery = initialQuery,
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
    activity: ComponentActivity,
    initialQuery: String = "",
    onQuerySubmitted: (String) -> Unit,
    onBackPress: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val cleanedInitialQuery = remember(initialQuery) {
        val trimmed = initialQuery.trim()
        if (trimmed.equals("about:blank", ignoreCase = true) || trimmed.startsWith("about:", ignoreCase = true)) {
            ""
        } else {
            initialQuery
        }
    }
    var queryState by remember {
        mutableStateOf(TextFieldValue(cleanedInitialQuery, androidx.compose.ui.text.TextRange(cleanedInitialQuery.length)))
    }
    var suggestions by remember { mutableStateOf<List<OmniboxSuggestion>>(emptyList()) }
    val focusRequester = remember { FocusRequester() }

    // Fetch local search/browsing history from SQLite database
    val localHistoryList = remember {
        val list = mutableListOf<String>()
        try {
            val action = RecordAction(context)
            action.open(false)
            val records: List<Record> = action.listHistory(context)
            action.close()
            records.forEach { r ->
                if (!r.title.isNullOrBlank()) list.add(r.title)
                if (!r.url.isNullOrBlank() && !r.url.startsWith("about:") && !r.url.startsWith("petal://")) {
                    list.add(r.url)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list.distinct()
    }

    // Debounced search query handler - respects the user's chosen suggestion engine,
    // same as the old dialog_search AdapterSearch flow (sp_search_engine: 0 Google,
    // 1 DuckDuckGo, 2 Bing), and the sp_enable_live_suggestions master toggle.
    LaunchedEffect(queryState.text) {
        val currentText = queryState.text.trim()
        if (currentText.isEmpty()) {
            suggestions = localHistoryList.take(8).map { OmniboxSuggestion(it, isHistory = true) }
        } else {
            val localMatches = localHistoryList
                .filter { it.contains(currentText, ignoreCase = true) }
                .take(3)
                .map { OmniboxSuggestion(it, isHistory = true) }

            suggestions = localMatches

            val liveSuggestionsEnabled = sp.getBoolean("sp_enable_live_suggestions", true)
            if (liveSuggestionsEnabled) {
                delay(250) // debounce
                val searchEngine = sp.getString("sp_search_engine", "0")
                val fetch: (String, SearchSuggestionsManager.SuggestionCallback) -> Unit = when (searchEngine) {
                    "1" -> SearchSuggestionsManager::fetchDuckDuckGoSuggestions
                    "3" -> SearchSuggestionsManager::fetchBingSuggestions
                    else -> SearchSuggestionsManager::fetchSuggestions // 0: Google, 2: Brave, 4: Ecosia
                }
                fetch(currentText) { engineResults ->
                    val combined = mutableListOf<OmniboxSuggestion>()
                    combined.addAll(localMatches)
                    engineResults.forEach { res ->
                        if (combined.none { it.query.equals(res, ignoreCase = true) }) {
                            combined.add(OmniboxSuggestion(res, isHistory = false))
                        }
                    }
                    suggestions = combined
                }
            }
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
    com.petal.browser.predictive.PetalScreenWrapper {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0)
        ) { innerPadding ->
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Background drawn first with matchParentSize so it never intercepts touches
                M3ExpressiveVariableBackground(
                    modifier = Modifier.matchParentSize(),
                    pageSeed = "omnibox_page"
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                    // NOTE: no .imePadding() here on purpose. BrowserActivity's
                    // ViewCompat.setOnApplyWindowInsetsListener on R.id.main (the parent
                    // of this ComposeView's host contentFrame) already applies
                    // bottom padding equal to the keyboard height whenever the IME is
                    // visible. Adding .imePadding() again here double-counts that inset,
                    // squeezing this weighted suggestions LazyColumn down to zero height
                    // and making the suggestions list disappear behind the keyboard.
                ) {
                    // Chrome-style search field row pinned to top with status bar padding
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBackPress) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedTextField(
                            value = queryState,
                            onValueChange = { queryState = it },
                            placeholder = {
                                Text(
                                    text = "Search Google or type URL",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (queryState.text.isNotEmpty()) {
                                        IconButton(onClick = { queryState = TextFieldValue("") }) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Clear text",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        IconButton(onClick = {
                                            PetalVoiceSearchBridge.showVoiceSearchSheet(activity) { result ->
                                                if (result.isNotBlank()) onQuerySubmitted(result.trim())
                                            }
                                        }) {
                                            Icon(
                                                imageVector = Icons.Rounded.Mic,
                                                contentDescription = "Voice search",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                if (queryState.text.isNotBlank()) {
                                    onQuerySubmitted(queryState.text.trim())
                                }
                            }),
                            shape = RoundedCornerShape(50),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                        )
                    }

                    // Suggestions List - fills the rest of the page, resizing with the keyboard.
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        items(
                            items = suggestions,
                            key = { item -> "${if (item.isHistory) "h" else "s"}_${item.query}" }
                        ) { item ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        val trimmed = item.query.trim()
                                        if (trimmed.isNotEmpty()) {
                                            onQuerySubmitted(trimmed)
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // History clock icon vs Search magnifying glass icon
                                    Icon(
                                        imageVector = if (item.isHistory) Icons.Rounded.History else Icons.Rounded.Search,
                                        contentDescription = null,
                                        tint = if (item.isHistory) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )

                                    Spacer(Modifier.width(20.dp))

                                    Text(
                                        text = item.query,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )

                                    // Clickable diagonal NorthWest insert arrow button
                                    IconButton(
                                        onClick = {
                                            queryState = TextFieldValue(
                                                text = item.query,
                                                selection = androidx.compose.ui.text.TextRange(item.query.length)
                                            )
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.NorthWest,
                                            contentDescription = "Insert query into omnibox",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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
}
}
