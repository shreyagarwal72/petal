package com.petal.browser.account.mozilla

import android.content.Context
import com.petal.browser.database.HistoryBookmarkContract
import com.petal.browser.database.HistoryBookmarkRepository
import com.petal.browser.database.Record
import mozilla.components.browser.storage.sync.PlacesBookmarksStorage
import mozilla.components.browser.storage.sync.PlacesHistoryStorage
import mozilla.components.concept.storage.BookmarkNode
import mozilla.components.concept.storage.BookmarkNodeType
import mozilla.appservices.places.BookmarkRoot
import mozilla.components.concept.storage.PageObservation
import mozilla.components.concept.storage.PageVisit
import mozilla.components.concept.storage.VisitType

/**
 * Keeps Petal's own bookmark/history database and Mozilla's Places storage (the store the official sync
 * engines read and write) in step. Petal's UI keeps using its database; Places is the sync mirror.
 *
 * Bookmarks use a three-way merge against the set of URLs seen at the last reconcile, so a bookmark deleted
 * on either side is deleted on the other instead of being resurrected. History is add-only.
 */
internal class PetalPlacesBridge(
    private val context: Context,
    val history: PlacesHistoryStorage,
    val bookmarks: PlacesBookmarksStorage
) {
    private val state = context.getSharedPreferences("petal_fx_sync_state", Context.MODE_PRIVATE)

    private fun key(url: String) = url.trim()

    private fun flatten(node: BookmarkNode?, out: MutableMap<String, String>) {
        if (node == null) return
        if (node.type == BookmarkNodeType.ITEM && !node.url.isNullOrBlank()) out[key(node.url!!)] = node.guid
        node.children?.forEach { flatten(it, out) }
    }

    suspend fun reconcileBookmarks() {
        val repo = HistoryBookmarkRepository(context)
        val localRecords = try {
            repo.open(false); repo.listBookmark(context, false, 0)
        } catch (_: Throwable) { emptyList() } finally { try { repo.close() } catch (_: Throwable) {} }
        val local = LinkedHashMap<String, Record>()
        localRecords.forEach { r -> r.url?.let { if (it.startsWith("http")) local[key(it)] = r } }

        val remote = LinkedHashMap<String, String>()
        flatten(bookmarks.getTree(BookmarkRoot.Root.id, true).getOrNull(), remote)
        val last = state.getStringSet("bookmark_snapshot", emptySet()) ?: emptySet()
        val firstRun = !state.contains("bookmark_snapshot")

        val finalUrls = HashSet<String>()
        val write = HistoryBookmarkRepository(context)
        try {
            write.open(true)
            for ((url, rec) in local) {
                if (url in remote) { finalUrls += url; continue }
                if (!firstRun && url in last) {
                    write.deleteURL(rec.url, HistoryBookmarkContract.TABLE_BOOKMARK) // deleted on another device
                } else {
                    try {
                        bookmarks.addItem(BookmarkRoot.Mobile.id, url, rec.title?.ifBlank { url } ?: url)
                        finalUrls += url
                    } catch (_: Throwable) {}
                }
            }
            for ((url, guid) in remote) {
                if (url in local) continue
                if (!firstRun && url in last) {
                    try { bookmarks.deleteNode(guid) } catch (_: Throwable) {} // deleted in Petal
                } else {
                    val title = findTitle(bookmarks.getTree(BookmarkRoot.Root.id, true).getOrNull(), guid) ?: url
                    write.addBookmark(Record(title, url, System.currentTimeMillis(), 0L))
                    finalUrls += url
                }
            }
        } finally { try { write.close() } catch (_: Throwable) {} }
        state.edit().putStringSet("bookmark_snapshot", finalUrls).apply()
    }

    private fun findTitle(node: BookmarkNode?, guid: String): String? {
        if (node == null) return null
        if (node.guid == guid) return node.title
        node.children?.forEach { c -> findTitle(c, guid)?.let { return it } }
        return null
    }

    /** Remote history into Petal's database (recent visits only, de-duplicated by URL). */
    suspend fun pullHistory() {
        val since = System.currentTimeMillis() - 90L * 24 * 3600 * 1000
        val visits = history.getDetailedVisits(since, Long.MAX_VALUE).sortedByDescending { it.visitTime }.take(500)
        if (visits.isEmpty()) return
        val repo = HistoryBookmarkRepository(context)
        try {
            repo.open(true)
            val known = repo.listHistory(context).mapNotNull { it.url?.let(::key) }.toHashSet()
            for (v in visits) {
                val url = key(v.url)
                if (!url.startsWith("http") || url in known) continue
                repo.addHistory(Record(v.title?.ifBlank { url } ?: url, url, v.visitTime, 0L))
                known += url
            }
        } finally { try { repo.close() } catch (_: Throwable) {} }
    }

    suspend fun mirrorVisit(url: String, title: String) {
        if (!url.startsWith("http")) return
        history.recordVisit(url, PageVisit(VisitType.LINK))
        if (title.isNotBlank()) history.recordObservation(url, PageObservation(title = title))
    }
}
