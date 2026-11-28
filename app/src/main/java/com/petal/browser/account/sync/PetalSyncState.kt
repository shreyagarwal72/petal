package com.petal.browser.account.sync

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Per-category record of changes that need to travel between devices, keyed by item (a URL for bookmarks
 * and history, origin+username for passwords). Each entry is the time of the latest change and whether the
 * item is present or deleted afterwards; the most recent change wins when devices merge.
 *
 * Only changes are stored, never the items, so the record stays small. Entries older than [RETENTION_MS]
 * are dropped.
 */
internal class SyncCategoryState(val map: HashMap<String, Pair<Long, Boolean>> = HashMap()) {

    fun put(key: String, ts: Long, present: Boolean) { map[key] = ts to present }

    fun isDeleted(key: String): Boolean = map[key]?.second == false

    /** Higher timestamp wins; on a tie the item stays (adding never loses to deleting). */
    fun mergeFrom(other: SyncCategoryState) {
        for ((k, v) in other.map) {
            val mine = map[k]
            if (mine == null || v.first > mine.first || (v.first == mine.first && v.second && !mine.second)) map[k] = v
        }
    }

    fun prune(cutoff: Long) { map.entries.removeAll { it.value.first < cutoff } }

    fun toJson(): JSONObject {
        val o = JSONObject()
        for ((k, v) in map) o.put(k, JSONArray().put(v.first).put(if (v.second) 1 else 0))
        return o
    }

    companion object {
        const val RETENTION_MS = 90L * 24 * 60 * 60 * 1000

        fun fromJson(o: JSONObject?): SyncCategoryState {
            val s = SyncCategoryState()
            if (o == null) return s
            val it = o.keys()
            while (it.hasNext()) {
                val k = it.next()
                val a = o.optJSONArray(k) ?: continue
                s.map[k] = a.optLong(0, 0L) to (a.optInt(1, 1) == 1)
            }
            return s
        }
    }
}

/** The three change records together. */
internal class SyncState(
    val bookmarks: SyncCategoryState = SyncCategoryState(),
    val history: SyncCategoryState = SyncCategoryState(),
    val passwords: SyncCategoryState = SyncCategoryState(),
    /** Reading-list flag per bookmark URL: present = on the list. */
    val reading: SyncCategoryState = SyncCategoryState(),
    val shortcuts: SyncCategoryState = SyncCategoryState(),
    /** Site rules, keyed "saved:domain", "trusted:domain" or "protected:domain". */
    val rules: SyncCategoryState = SyncCategoryState()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("b", bookmarks.toJson()).put("h", history.toJson()).put("p", passwords.toJson())
        .put("r", reading.toJson()).put("s", shortcuts.toJson()).put("x", rules.toJson())

    fun prune(now: Long) {
        val cutoff = now - SyncCategoryState.RETENTION_MS
        bookmarks.prune(cutoff); history.prune(cutoff); passwords.prune(cutoff)
        reading.prune(cutoff); shortcuts.prune(cutoff); rules.prune(cutoff)
    }

    companion object {
        fun fromJson(o: JSONObject?): SyncState = SyncState(
            SyncCategoryState.fromJson(o?.optJSONObject("b")),
            SyncCategoryState.fromJson(o?.optJSONObject("h")),
            SyncCategoryState.fromJson(o?.optJSONObject("p")),
            SyncCategoryState.fromJson(o?.optJSONObject("r")),
            SyncCategoryState.fromJson(o?.optJSONObject("s")),
            SyncCategoryState.fromJson(o?.optJSONObject("x"))
        )
    }
}

/**
 * What this device held at the end of its last successful sync. Comparing it with what the device holds
 * now is how local deletions are noticed, wherever in the app they happened (including logins saved or
 * removed by the web engine itself), so no individual delete code path has to be instrumented.
 */
internal class SyncBase(
    val bookmarks: Set<String>,
    val history: Set<String>,
    val passwords: Set<String>,
    val reading: Set<String> = emptySet(),
    val shortcuts: Set<String> = emptySet(),
    val rules: Set<String> = emptySet()
)

internal object PetalSyncFiles {
    private const val BASE_FILE = "petal_sync_base.json"
    private const val STATE_FILE = "petal_sync_state.json"
    private const val TITLES_FILE = "petal_sync_titles.json"

    private fun base(c: Context) = File(c.applicationContext.filesDir, BASE_FILE)
    private fun state(c: Context) = File(c.applicationContext.filesDir, STATE_FILE)

    fun loadBase(c: Context): SyncBase? {
        val f = base(c)
        if (!f.exists()) return null
        return try {
            val o = JSONObject(f.readText())
            SyncBase(
                strings(o.optJSONArray("b")), strings(o.optJSONArray("h")), strings(o.optJSONArray("p")),
                strings(o.optJSONArray("r")), strings(o.optJSONArray("s")), strings(o.optJSONArray("x"))
            )
        } catch (_: Exception) { null }
    }

    fun saveBase(c: Context, b: SyncBase) {
        val o = JSONObject()
            .put("b", JSONArray(b.bookmarks.toList()))
            .put("h", JSONArray(b.history.toList()))
            .put("p", JSONArray(b.passwords.toList()))
            .put("r", JSONArray(b.reading.toList()))
            .put("s", JSONArray(b.shortcuts.toList()))
            .put("x", JSONArray(b.rules.toList()))
        base(c).writeText(o.toString())
    }

    fun loadState(c: Context): SyncState {
        val f = state(c)
        if (!f.exists()) return SyncState()
        return try { SyncState.fromJson(JSONObject(f.readText())) } catch (_: Exception) { SyncState() }
    }

    fun saveState(c: Context, s: SyncState) { state(c).writeText(s.toJson().toString()) }

    /** Last known bookmark title per URL and when it last changed here (0 = unknown). */
    fun loadTitles(c: Context): HashMap<String, Pair<String, Long>> {
        val out = HashMap<String, Pair<String, Long>>()
        val f = File(c.applicationContext.filesDir, TITLES_FILE)
        if (!f.exists()) return out
        try {
            val o = JSONObject(f.readText())
            val it = o.keys()
            while (it.hasNext()) {
                val k = it.next()
                val a = o.optJSONArray(k) ?: continue
                out[k] = a.optString(0, "") to a.optLong(1, 0L)
            }
        } catch (_: Exception) {}
        return out
    }

    fun saveTitles(c: Context, titles: Map<String, Pair<String, Long>>) {
        val o = JSONObject()
        for ((k, v) in titles) o.put(k, JSONArray().put(v.first).put(v.second))
        File(c.applicationContext.filesDir, TITLES_FILE).writeText(o.toString())
    }

    fun clearAll(c: Context) {
        base(c).delete(); state(c).delete()
        File(c.applicationContext.filesDir, TITLES_FILE).delete()
    }

    private fun strings(a: JSONArray?): Set<String> {
        val out = HashSet<String>()
        if (a != null) for (i in 0 until a.length()) out.add(a.optString(i))
        return out
    }
}
