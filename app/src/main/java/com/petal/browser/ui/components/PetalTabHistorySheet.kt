package com.petal.browser.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.theme.PetalText
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petal.browser.browser.PetalTabHistoryState
import com.petal.browser.engine.gecko.PetalEngineStore
import com.petal.browser.ui.containment.PetalSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import mozilla.components.browser.icons.Icon as MozIcon
import mozilla.components.browser.icons.IconRequest

/**
 * Back/forward history popup of one tab (opened by long-pressing the Back control).
 * Lists the tab's Gecko session history newest first with favicon, title and URL; the current
 * entry is marked. Selecting an entry calls [onNavigate] with its index in Gecko's list
 * (for GeckoSession.gotoHistoryIndex).
 */
@Composable
fun PetalTabHistorySheet(
    tabId: String,
    isPrivate: Boolean,
    onNavigate: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val histories by PetalTabHistoryState.histories.collectAsStateWithLifecycle()
    val history = histories[tabId]

    PetalSheet(onDismissRequest = onDismiss, title = "Tab history") {
        if (history == null || history.entries.isEmpty()) {
            Text(
                text = "No pages in this tab's history yet",
                style = PetalText.emptyState,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)
            )
            return@PetalSheet
        }
        val size = history.entries.size
        // Newest first: displayed row i is Gecko index (size - 1 - i).
        val currentRow = size - 1 - history.currentIndex
        val listState = rememberLazyListState(
            initialFirstVisibleItemIndex = (currentRow - 1).coerceAtLeast(0)
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.heightIn(max = 460.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            itemsIndexed(history.entries.asReversed()) { row, entry ->
                val geckoIndex = size - 1 - row
                val isCurrent = geckoIndex == history.currentIndex
                val favicon by produceState<Bitmap?>(initialValue = null, entry.uri) {
                    value = loadFavicon(context, entry.uri, isPrivate)
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            com.petal.browser.haptics.PetalHapticEngine.getInstance(context).thresholdCrossed(context)
                            if (!isCurrent) onNavigate(geckoIndex)
                            onDismiss()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            val bmp = favicon
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(
                                text = entry.title.ifBlank { entry.uri },
                                style = PetalText.listItemTitle,
                                color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = entry.uri,
                                style = PetalText.listItemMeta,
                                color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isCurrent) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Current page",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun loadFavicon(
    context: android.content.Context,
    uri: String,
    isPrivate: Boolean
): Bitmap? {
    if (!uri.startsWith("http://") && !uri.startsWith("https://")) return null
    return try {
        val icons = PetalEngineStore.getBrowserIcons(context)
        val deferred: Deferred<MozIcon> = icons.loadIcon(IconRequest(url = uri, isPrivate = isPrivate))
        deferred.await().bitmap
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        null
    }
}
