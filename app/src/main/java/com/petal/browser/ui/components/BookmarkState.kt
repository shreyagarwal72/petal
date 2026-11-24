package com.petal.browser.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.petal.browser.database.HistoryBookmarkContract
import com.petal.browser.database.HistoryBookmarkRepository
import com.petal.browser.database.Record
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bookmark lookups and writes for the address bar, always off the main thread.
 * [version] is bumped by [HistoryBookmarkRepository] on every bookmark write from anywhere in the
 * app, so any bookmark icon that reads it re-queries and stays in sync.
 */
object BookmarkChanges {
    var version by mutableIntStateOf(0)
        private set

    @JvmStatic
    fun notifyChanged() { version++ }

    suspend fun isBookmarked(context: Context, url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val repo = HistoryBookmarkRepository(context)
            repo.open(false)
            try { repo.checkBookmark(url) } finally { repo.close() }
        } catch (_: Exception) { false }
    }

    /** Toggles and returns the NEW state, or null on failure. */
    suspend fun toggle(context: Context, url: String, title: String, currentlyBookmarked: Boolean): Boolean? =
        withContext(Dispatchers.IO) {
            try {
                val repo = HistoryBookmarkRepository(context)
                repo.open(true)
                try {
                    if (currentlyBookmarked) {
                        repo.deleteURL(url, HistoryBookmarkContract.TABLE_BOOKMARK)
                        false
                    } else {
                        repo.addBookmark(Record(title.ifBlank { url }, url, 0L, 0))
                        true
                    }
                } finally { repo.close() }
            } catch (_: Exception) { null }
        }
}

/** Observable bookmark state for [url]; initial value false until the background query returns. */
@Composable
fun rememberIsBookmarked(url: String): State<Boolean> {
    val context = LocalContext.current
    val version = BookmarkChanges.version
    return produceState(initialValue = false, url, version) {
        value = BookmarkChanges.isBookmarked(context, url)
    }
}
