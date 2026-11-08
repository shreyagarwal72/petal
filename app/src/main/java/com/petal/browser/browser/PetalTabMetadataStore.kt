package com.petal.browser.browser

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Data model for tab sidecar metadata (tab groups, custom titles, Zen workspaces, and pinned state).
 */
data class PetalTabMetadata(
    val tabId: String,
    val tabGroupId: String? = null,
    val tabGroupTitle: String? = null,
    val tabGroupColorHex: String? = null,
    val customTitle: String? = null,
    val workspaceId: String? = null,
    val isPinned: Boolean = false,
    val isPinnedGlobal: Boolean = true
)

/**
 * Sidecar metadata store keyed by tabId.
 * Persists tab group id/title/color and custom titles across app restarts,
 * keeping BrowserStore lean while retaining rich tab categorization.
 */
object PetalTabMetadataStore {
    private const val TAG = "PetalTabMetadataStore"
    private const val PREFS_NAME = "petal_tab_metadata_sidecar"
    private const val KEY_METADATA_JSON = "tab_metadata_map"
    private val gson = Gson()

    private val cache = mutableMapOf<String, PetalTabMetadata>()
    private var isLoaded = false

    @Synchronized
    private fun ensureLoaded(context: Context) {
        if (isLoaded) return
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = sp.getString(KEY_METADATA_JSON, null)
        if (!json.isNullOrBlank()) {
            try {
                val type = object : TypeToken<Map<String, PetalTabMetadata>>() {}.type
                val loaded: Map<String, PetalTabMetadata>? = gson.fromJson(json, type)
                cache.clear()
                if (loaded != null) {
                    cache.putAll(loaded)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load tab metadata sidecar", e)
            }
        }
        isLoaded = true
    }

    @Synchronized
    private fun persist(context: Context) {
        try {
            val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = gson.toJson(cache)
            sp.edit().putString(KEY_METADATA_JSON, json).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist tab metadata sidecar", e)
        }
    }

    @JvmStatic
    @Synchronized
    fun get(context: Context, tabId: String): PetalTabMetadata? {
        if (tabId.isBlank()) return null
        ensureLoaded(context)
        return cache[tabId]
    }

    @JvmStatic
    @Synchronized
    fun put(context: Context, metadata: PetalTabMetadata) {
        if (metadata.tabId.isBlank()) return
        ensureLoaded(context)
        cache[metadata.tabId] = metadata
        persist(context)
    }

    @JvmStatic
    @Synchronized
    fun setGroup(
        context: Context,
        tabId: String,
        groupId: String?,
        groupTitle: String?,
        groupColorHex: String?
    ) {
        if (tabId.isBlank()) return
        ensureLoaded(context)
        val existing = cache[tabId]
        val updated = existing?.copy(
            tabGroupId = groupId,
            tabGroupTitle = groupTitle,
            tabGroupColorHex = groupColorHex
        ) ?: PetalTabMetadata(
            tabId = tabId,
            tabGroupId = groupId,
            tabGroupTitle = groupTitle,
            tabGroupColorHex = groupColorHex
        )
        cache[tabId] = updated
        persist(context)
    }

    @JvmStatic
    @Synchronized
    fun setCustomTitle(context: Context, tabId: String, customTitle: String?) {
        if (tabId.isBlank()) return
        ensureLoaded(context)
        val existing = cache[tabId]
        val updated = existing?.copy(customTitle = customTitle)
            ?: PetalTabMetadata(tabId = tabId, customTitle = customTitle)
        cache[tabId] = updated
        persist(context)
    }

    @JvmStatic
    @Synchronized
    fun setWorkspace(context: Context, tabId: String, workspaceId: String?) {
        if (tabId.isBlank()) return
        ensureLoaded(context)
        val existing = cache[tabId]
        val updated = existing?.copy(workspaceId = workspaceId)
            ?: PetalTabMetadata(tabId = tabId, workspaceId = workspaceId)
        cache[tabId] = updated
        persist(context)
    }

    @JvmStatic
    @Synchronized
    fun setPinned(context: Context, tabId: String, isPinned: Boolean, isGlobal: Boolean = true) {
        if (tabId.isBlank()) return
        ensureLoaded(context)
        val existing = cache[tabId]
        val updated = existing?.copy(isPinned = isPinned, isPinnedGlobal = isGlobal)
            ?: PetalTabMetadata(tabId = tabId, isPinned = isPinned, isPinnedGlobal = isGlobal)
        cache[tabId] = updated
        persist(context)
    }

    @JvmStatic
    @Synchronized
    fun reassignWorkspace(context: Context, fromWorkspaceId: String, toWorkspaceId: String) {
        ensureLoaded(context)
        var changed = false
        for ((tabId, meta) in cache) {
            if (meta.workspaceId == fromWorkspaceId) {
                cache[tabId] = meta.copy(workspaceId = toWorkspaceId)
                changed = true
            }
        }
        if (changed) {
            persist(context)
        }
    }

    @JvmStatic
    @Synchronized
    fun getWorkspaceId(context: Context, tabId: String): String {
        ensureLoaded(context)
        return cache[tabId]?.workspaceId ?: com.petal.browser.compose.tabs.PetalWorkspaceManager.DEFAULT_WORKSPACE_ID
    }

    @JvmStatic
    @Synchronized
    fun isPinned(context: Context, tabId: String): Boolean {
        ensureLoaded(context)
        return cache[tabId]?.isPinned ?: false
    }

    @JvmStatic
    @Synchronized
    fun isPinnedGlobal(context: Context, tabId: String): Boolean {
        ensureLoaded(context)
        return cache[tabId]?.isPinnedGlobal ?: true
    }

    @JvmStatic
    @Synchronized
    fun remove(context: Context, tabId: String) {
        if (tabId.isBlank()) return
        ensureLoaded(context)
        if (cache.remove(tabId) != null) {
            persist(context)
        }
    }

    /**
     * Prunes metadata for tabs that no longer exist in the store.
     */
    @JvmStatic
    @Synchronized
    fun prune(context: Context, activeTabIds: Collection<String>) {
        ensureLoaded(context)
        val idsSet = activeTabIds.toSet()
        val beforeSize = cache.size
        cache.keys.retainAll(idsSet)
        if (cache.size != beforeSize) {
            persist(context)
        }
    }

    @JvmStatic
    @Synchronized
    fun clear(context: Context) {
        ensureLoaded(context)
        cache.clear()
        persist(context)
    }

    @JvmStatic
    @Synchronized
    fun getAll(context: Context): Map<String, PetalTabMetadata> {
        ensureLoaded(context)
        return HashMap(cache)
    }

    @JvmStatic
    @Synchronized
    fun putAll(context: Context, entries: Map<String, PetalTabMetadata>) {
        ensureLoaded(context)
        cache.putAll(entries)
        persist(context)
    }

    @JvmStatic
    @Synchronized
    fun exportJson(context: Context): String {
        ensureLoaded(context)
        return gson.toJson(cache)
    }

    @JvmStatic
    @Synchronized
    fun importJson(context: Context, json: String?) {
        if (json.isNullOrBlank()) return
        ensureLoaded(context)
        try {
            val type = object : TypeToken<Map<String, PetalTabMetadata>>() {}.type
            val imported: Map<String, PetalTabMetadata>? = gson.fromJson(json, type)
            if (imported != null) {
                cache.putAll(imported)
                persist(context)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to import tab metadata sidecar", e)
        }
    }
}
