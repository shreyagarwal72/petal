/*
 * PetalOmniboxOverlay.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Chrome-style Omnibox Search Overlay in Jetpack Compose featuring:
 * 1. Debounced live Google autocomplete suggestions (suggestqueries.google.com).
 * 2. SQLite local search history suggestions with history clock icon vs search magnifying glass icon.
 * 3. Clickable query rows with diagonal insert arrow buttons (NorthWest).
 * 4. Material 3 Expressive styling with automatic theme/palette integration.
 * 5. Auto-opening soft keyboard on focus.
 */

package com.petal.browser.ui.components

import android.content.Context
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
import com.google.android.material.bottomsheet.BottomSheetDialog
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
    private var activeDialog: BottomSheetDialog? = null

    @JvmStatic
    @JvmOverloads
    fun showOmniboxOverlay(
        activity: ComponentActivity,
        initialQuery: String = "",
        onDismissCallback: (() -> Unit)? = null,
        onQuerySubmitted: (String) -> Unit
    ) {
        activity.runOnUiThread {
            try {
                activeDialog?.dismiss()
            } catch (_: Exception) {}
            activeDialog = null

            val dialog = BottomSheetDialog(activity, com.google.android.material.R.style.Theme_Design_BottomSheetDialog).apply {
                window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
            activeDialog = dialog

            val composeView = ComposeView(activity).apply {
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
                        PetalOmniboxOverlay(
                            initialQuery = initialQuery,
                            onQuerySubmitted = { query ->
                                try { dialog.dismiss() } catch (_: Exception) {}
                                activeDialog = null
                                onQuerySubmitted(query)
                            },
                            onDismiss = {
                                try { dialog.dismiss() } catch (_: Exception) {}
                                activeDialog = null
                            }
                        )
                    }
                }
            }
            dialog.setContentView(composeView)
            dialog.setOnDismissListener {
                if (activeDialog == dialog) {
                    activeDialog = null
                }
                onDismissCallback?.invoke()
            }
            dialog.show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalOmniboxOverlay(
    initialQuery: String = "",
    onQuerySubmitted: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var queryState by remember { mutableStateOf(TextFieldValue(initialQuery)) }
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

    // Debounced search query handler
    LaunchedEffect(queryState.text) {
        val currentText = queryState.text.trim()
        if (currentText.isEmpty()) {
            // Show recent history when empty
            val recentHistory = localHistoryList.take(6).map { OmniboxSuggestion(it, isHistory = true) }
            suggestions = recentHistory
        } else {
            // Instant local matching first
            val localMatches = localHistoryList
                .filter { it.contains(currentText, ignoreCase = true) }
                .take(3)
                .map { OmniboxSuggestion(it, isHistory = true) }

            suggestions = localMatches

            // 250ms Debounce for live Google suggest queries
            delay(250)
            SearchSuggestionsManager.fetchSuggestions(currentText) { googleResults ->
                val combined = mutableListOf<OmniboxSuggestion>()
                combined.addAll(localMatches)

                googleResults.forEach { res ->
                    if (combined.none { it.query.equals(res, ignoreCase = true) }) {
                        combined.add(OmniboxSuggestion(res, isHistory = false))
                    }
                }
                suggestions = combined
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Drag handle bar
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(12.dp))

            // Chrome-style Omnibox Search TextField
            OutlinedTextField(
                value = queryState,
                onValueChange = { queryState = it },
                placeholder = {
                    Text(
                        text = "Search or type URL",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary
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
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Close overlay",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .focusRequester(focusRequester)
            )

            Spacer(Modifier.height(12.dp))

            // Suggestions List
            if (suggestions.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    items(suggestions, key = { it.query }) { item ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onQuerySubmitted(item.query) }
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

                                Spacer(Modifier.width(14.dp))

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
