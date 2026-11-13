/*
 * PetalPlacesSyncBridge.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Firefox Sync only talks to Mozilla's Places databases. Petal keeps bookmarks/history in its
 * own SQLite tables, so this bridge reconciles the two:
 *
 *   before sync :  push Petal  -> Places   (new local items; items deleted locally)
 *   sync        :  Mozilla's engine (encrypted, signed, versioned)
 *   after sync  :  pull Places -> Petal    (new remote items; items deleted remotely)
 *
 * Bookmarks use a 3-way merge keyed by URL against the snapshot taken at the end of the last
 * successful reconcile, so deletions propagate in both directions without tombstones.
 * Reading-list items stay local-only (Firefox Sync has no equivalent).
 */
package com.petal.browser.account.mozilla

import android.content.Context
import android.util.Log
import com.petal.browser.database.HistoryBookmarkContract
import com.petal.browser.database.HistoryBookmarkRepository
import com.petal.browser.database.Record
import mozilla.components.concept.storage.BookmarkNode
import mozilla.components.concept.storage.BookmarkNodeType
import mozilla.components.concept.storage.BookmarkRoot
import mozilla.components.concept.storage.PageObservation
import mozilla.components.concept.storage.PageVisit
import mozilla.components.concept.storage.RedirectSource
import mozilla.components.concept.storage.VisitType
import mozilla.components.browser.storage.sync.Tab
import mozilla.components.browser.storage.sync.TabEntry
import org.json.JSONArray

internal class PetalPlacesSyncBridge(
    private val context: Context,
    private val services: PetalFxaServices
) {
    private val snapPrefs get() = context.getSharedPreferences("petal_fx_sync_state", Context.MODE_PRIVATE)

    // ── Bookmarks ─────────────────────────────────────────────────────────────────────────────

    private fun localBookmarks(): Map<String, String> {
        val repo = HistoryBookmarkRepository(context)
        return try {
            repo.open(false)
            repo.listBookmark(context, false, 0)
                .filter { !it.isReadingList && !it.url.isNullOrBlank() }
                .associate { it.url!!.trim() to (it.title ?: it.url!!) }
        } finally { try { repo.close() } catch (_: Throwable) {} }
    }

    private suspend fun remoteBookmarks(): Map<String, BookmarkNode> {
        val root = services.bookmarksStorage.value.getTree(BookmarkRoot.Root.id, true) ?: return emptyMap()
        val out = LinkedHashMap<String, BookmarkNode>()
        fun walk(n: BookmarkNode) {
            if (n.type == BookmarkNodeType.ITEM) {
                val u = n.url?.trim()
                if (!u.isNullOrBlank() && !u.startsWith("place:")) out.putIfAbsent(u, n)
            }
            n.children?.forEach { walk(it) }
        }
        walk(root)
        return out
    }

    private fun loadSnapshot(): Set<String> = try {
        val arr = JSONArray(snapPrefs.getString("bookmark_snapshot", "[]"))
        (0 until arr.length()).map { arr.getString(it) }.toSet()
    } catch (_: Throwable) { emptySet() }

    private fun saveSnapshot(urls: Set<String>) {
        snapPrefs.edit().putString("bookmark_snapshot", JSONArray(urls.toList()).toString()).apply()
    }

    suspend fun pushBookmarks() {
        try {
            val local = localBookmarks()
            val remote = remoteBookmarks()
            val snap = loadSnapshot()
            val store = services.bookmarksStorage.value
            for ((url, title) in local) {
                if (url !in remote && url !in snap) store.addItem(BookmarkRoot.Mobile.id, url, title, null)
            }
            for (url in snap) {
                if (url !in local) remote[url]?.let { store.deleteNode(it.guid) }
            }
        } catch (t: Throwable) { Log.e(TAG, "pushBookmarks", t) }
    }

    suspend fun pullBookmarks() {
        try {
            val remote = remoteBookmarks()
            var local = localBookmarks()
            val snap = loadSnapshot()
            val repo = HistoryBookmarkRepository(context)
            try {
                repo.open(true)
                for ((url, node) in remote) {
                    if (url !in local && url !in snap) {
                        repo.addBookmark(Record().apply {
                            title = node.title?.takeIf { it.isNotBlank() } ?: url
                            this.url = url
                            time = node.dateAdded.takeIf { it > 0 } ?: System.currentTimeMillis()
                            iconColor = time
                        })
                    }
                }
                for (url in snap) {
                    if (url in local && url !in remote) repo.deleteBookmark(url)
                }
            } finally { try { repo.close() } catch (_: Throwable) {} }
            local = localBookmarks()
            saveSnapshot(local.keys)
        } catch (t: Throwable) { Log.e(TAG, "pullBookmarks", t) }
    }

    // ── History ───────────────────────────────────────────────────────────────────────────────

    /** Mirror a single visit to Places right when it happens (same as Fenix's history observer). */
    suspend fun mirrorVisit(url: String, title: String) {
        try {
            val h = services.historyStorage.value
            h.recordVisit(url, PageVisit(VisitType.LINK, RedirectSource.NOT_SOURCE))
            if (title.isNotBlank()) h.recordObservation(url, PageObservation(title = title))
        } catch (t: Throwable) { Log.e(TAG, "mirrorVisit", t) }
    }

    suspend fun pullHistory() {
        try {
            val since = System.currentTimeMillis() - 90L * 24 * 3600 * 1000
            val visits = services.historyStorage.value.getDetailedVisits(since, Long.MAX_VALUE)
                .sortedByDescending { it.visitTime }.take(MAX_HISTORY_IMPORT)
            val repo = HistoryBookmarkRepository(context)
            try {
                repo.open(true)
                val seen = HashSet<String>()
                for (v in visits) {
                    val url = v.url
                    if (url.isBlank() || url.startsWith("about:") || !seen.add(url)) continue
                    if (!repo.checkUrl(url, HistoryBookmarkContract.TABLE_HISTORY)) {
                        repo.addHistory(Record(v.title?.ifBlank { url } ?: url, url, v.visitTime, 0L))
                    }
                }
            } finally { try { repo.close() } catch (_: Throwable) {} }
        } catch (t: Throwable) { Log.e(TAG, "pullHistory", t) }
    }

    // ── Tabs ──────────────────────────────────────────────────────────────────────────────────

    suspend fun pushTabs(open: List<MozTabInfo>) {
        try {
            val now = System.currentTimeMillis()
            val tabs = open.map { Tab(listOf(TabEntry(it.title, it.url, it.iconUrl)), 0, now, false) }
            services.tabsStorage.value.store(tabs)
        } catch (t: Throwable) { Log.e(TAG, "pushTabs", t) }
    }

    suspend fun pullRemoteTabs(names: Map<String, FxaDevice>): List<RemoteDeviceTabs> = try {
        services.tabsStorage.value.getAll().map { (client, tabs) ->
            val dev = names[client.id]
            RemoteDeviceTabs(
                deviceId = client.id,
                deviceName = dev?.name ?: "Other device",
                deviceType = dev?.type ?: "desktop",
                lastModified = tabs.maxOfOrNull { it.lastUsed } ?: 0L,
                tabs = tabs.mapNotNull { t ->
                    val e = t.history.getOrNull(t.active) ?: t.history.lastOrNull() ?: return@mapNotNull null
                    if (e.url.isBlank() || e.url.startsWith("about:")) null
                    else MozTabInfo(e.title.ifBlank { e.url }, e.url, e.iconUrl, t.lastUsed)
                }
            )
        }.filter { it.tabs.isNotEmpty() }
    } catch (t: Throwable) { Log.e(TAG, "pullRemoteTabs", t); emptyList() }

    companion object {
        private const val TAG = "PetalPlacesBridge"
        private const val MAX_HISTORY_IMPORT = 2000
    }
}
