package com.petal.browser.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.theme.PetalText
import com.petal.browser.R
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor

/** Recent searches kept per screen in SharedPreferences. Newest first, capped at [MAX]. */
object PetalRecentSearches {
    const val MAX = 10
    private const val FILE = "petal_recent_searches"

    fun load(context: Context, screenKey: String): List<String> =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(screenKey, "")
            .orEmpty()
            .split('\n')
            .filter { it.isNotBlank() }

    private fun save(context: Context, screenKey: String, list: List<String>) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(screenKey, list.joinToString("\n")).apply()
    }

    /** Returns the new list. Does nothing (returns [current]) for blank input or in incognito. */
    fun add(context: Context, screenKey: String, current: List<String>, query: String, incognito: Boolean): List<String> {
        val q = query.trim().replace('\n', ' ')
        if (q.isEmpty() || incognito) return current
        val next = (listOf(q) + current.filterNot { it.equals(q, ignoreCase = true) }).take(MAX)
        save(context, screenKey, next)
        return next
    }

    fun remove(context: Context, screenKey: String, current: List<String>, query: String): List<String> {
        val next = current.filterNot { it == query }
        save(context, screenKey, next)
        return next
    }

    fun clear(context: Context, screenKey: String) = save(context, screenKey, emptyList())
}

/** The collapsed search bar: a pill that opens [PetalSearchOverlay] when tapped. Shows the live query if any. */
@Composable
fun PetalSearchPill(
    query: String,
    placeholder: String,
    onClick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = isLiquidGlassEnabled("sp_liquid_glass_address_bar") || isLiquidGlassEnabled("sp_liquid_glass_containments")
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .liquidGlassChrome(shape = shape, enabled = glass, sheenIntensity = 0.45f)
            .background(
                if (glass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest,
                shape
            )
            .clickable(onClickLabel = placeholder, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = query.ifEmpty { placeholder },
            style = MaterialTheme.typography.bodyLarge,
            color = if (query.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = onClear) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ui_clear))
            }
        }
    }
}

/**
 * Full-screen search view. Place it as the LAST child of the screen's root Box so it draws on top.
 * Shows recent searches when the query is empty; the results themselves stay in the screen behind
 * and update live, so submitting just collapses the overlay and keeps the query.
 */
@Composable
fun PetalSearchOverlay(
    visible: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    recents: List<String>,
    resultCount: Int,
    onSubmit: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecents: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(PetalSpring.calm()) + scaleIn(PetalSpring.calm(), initialScale = 0.94f),
        exit = fadeOut(PetalSpring.calm()) + scaleOut(PetalSpring.calm(), targetScale = 0.94f),
        modifier = modifier,
    ) {
        val focus = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        val focusManager = LocalFocusManager.current
        LaunchedEffect(Unit) { focus.requestFocus() }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { keyboard?.hide(); focusManager.clearFocus(); onCollapse() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.ui_search_back))
                    }
                    TextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.weight(1f).focusRequester(focus),
                        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ui_clear))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboard?.hide(); focusManager.clearFocus(); onSubmit(query)
                        }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                    )
                }

                if (query.isNotBlank()) {
                    // Results are live behind; give a clear way to get to them.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { keyboard?.hide(); focusManager.clearFocus(); onSubmit(query) }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = stringResource(R.string.ui_search_show_results, resultCount),
                            style = PetalText.rowTitle,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else if (recents.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.ui_search_recent),
                            style = PetalText.sectionHeader,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onClearRecents) { Text(stringResource(R.string.ui_search_clear_recents), style = PetalText.button) }
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(recents, key = { it }) { recent ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onQueryChange(recent); keyboard?.hide(); focusManager.clearFocus(); onSubmit(recent) }
                                    .padding(start = 20.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                                Text(
                                    text = recent,
                                    style = PetalText.listItemTitle,
                                    modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                                )
                                IconButton(onClick = { onRemoveRecent(recent) }) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.ui_search_remove_recent, recent),
                                        modifier = Modifier.size(18.dp),
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
