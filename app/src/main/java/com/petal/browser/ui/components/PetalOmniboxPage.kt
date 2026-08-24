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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.petal.browser.ui.theme.*
import com.petal.browser.unit.SearchSuggestionsManager
import kotlinx.coroutines.delay
import java.net.URI

data class OmniboxSuggestion(
    val query: String,
    val isHistory: Boolean
)

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
                        pageTitle = pageTitle,
                        pageUrl = pageUrl,
                        favicon = favicon,
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
    pageTitle: String = "",
    pageUrl: String = "",
    favicon: Bitmap? = null,
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
        mutableStateOf(TextFieldValue(cleanedInitialQuery, TextRange(cleanedInitialQuery.length)))
    }
    var suggestions by remember { mutableStateOf<List<OmniboxSuggestion>>(emptyList()) }
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

    // Debounced search query handler
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
                delay(250)
                val searchEngine = sp.getString("sp_search_engine", "0")
                val fetch: (String, SearchSuggestionsManager.SuggestionCallback) -> Unit = when (searchEngine) {
                    "1" -> SearchSuggestionsManager::fetchDuckDuckGoSuggestions
                    "3" -> SearchSuggestionsManager::fetchBingSuggestions
                    else -> SearchSuggestionsManager::fetchSuggestions
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

    var copiedUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                val clipData = clipboard.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val text = clipData.getItemAt(0).text?.toString()?.trim()
                    if (!text.isNullOrBlank() && com.petal.browser.unit.BrowserUnit.isURL(text)) {
                        copiedUrl = text
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
        com.petal.browser.predictive.PetalScreenWrapper {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0)
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
                    // Background drawn first
                    M3ExpressiveVariableBackground(
                        modifier = Modifier.matchParentSize(),
                        pageSeed = "omnibox_page"
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {} // Absorb clicks inside content area
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

                        // Chrome-style Quick Actions Card when an active webpage is focused
                        if (cleanPageUrl.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                tonalElevation = 2.dp,
                                shadowElevation = 1.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
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
                                                contentDescription = "Page Favicon",
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
                                                    com.petal.browser.view.NinjaToast.show(context, "Link copied to clipboard")
                                                }
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Share,
                                                contentDescription = "Share URL",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        // 2. One-tap Copy
                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("URL", cleanPageUrl))
                                                com.petal.browser.view.NinjaToast.show(context, "Link copied to clipboard")
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ContentCopy,
                                                contentDescription = "Copy URL",
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
                                                contentDescription = "Edit URL",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Chrome-style "Link that you copied" suggestion chip
                        androidx.compose.animation.AnimatedVisibility(
                            visible = copiedUrl != null,
                            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
                        ) {
                            copiedUrl?.let { url ->
                                Surface(
                                    onClick = {
                                        onQuerySubmitted(url)
                                    },
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    tonalElevation = 3.dp,
                                    shadowElevation = 2.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Left: Globe Icon
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Language,
                                                contentDescription = "Copied Link",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Spacer(Modifier.width(12.dp))

                                        // Center: "Link that you copied" & URL subtitle
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Link that you copied",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = url,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Spacer(Modifier.width(8.dp))

                                        // Right: Eye/Preview icon
                                        IconButton(
                                            onClick = {
                                                queryState = TextFieldValue(
                                                    text = url,
                                                    selection = TextRange(url.length)
                                                )
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Visibility,
                                                contentDescription = "Preview link",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Frequently Visited Site Shortcuts Row
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "Frequently Visited",
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

                                    Surface(
                                        onClick = { onQuerySubmitted(shortcut.url) },
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        tonalElevation = 1.dp,
                                        modifier = Modifier.width(92.dp)
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

                        // Suggestions List - fills the rest of the page
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

                                        IconButton(
                                            onClick = {
                                                queryState = TextFieldValue(
                                                    text = item.query,
                                                    selection = TextRange(item.query.length)
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
