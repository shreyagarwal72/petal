package com.petal.browser.collections

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * Single item inside a saved Tab Collection.
 */
data class SavedTabItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Tab Collection model representing a named group of saved research/reading tabs.
 */
data class PetalTabCollection(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val items: List<SavedTabItem> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val colorHex: String = "#3B82F6"
)

/**
 * Persistent manager for Home Screen Tab Collections (mirroring Firefox for Android Collections).
 */
object PetalCollectionManager {
    private const val PREFS_NAME = "petal_tab_collections_prefs"
    private const val KEY_COLLECTIONS_JSON = "key_tab_collections_json"
    private val gson = Gson()

    var collections by mutableStateOf<List<PetalTabCollection>>(emptyList())
        private set

    private var isInitialized = false

    @Synchronized
    fun init(context: Context) {
        if (isInitialized) return
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = sp.getString(KEY_COLLECTIONS_JSON, null)
        if (!json.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<PetalTabCollection>>() {}.type
                collections = gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        isInitialized = true
    }

    @Synchronized
    private fun persist(context: Context) {
        try {
            val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = gson.toJson(collections)
            sp.edit().putString(KEY_COLLECTIONS_JSON, json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun saveCollection(context: Context, name: String, items: List<SavedTabItem>, colorHex: String = "#3B82F6"): PetalTabCollection {
        init(context)
        val newCollection = PetalTabCollection(
            name = name.ifBlank { "Collection ${collections.size + 1}" },
            items = items,
            colorHex = colorHex
        )
        collections = listOf(newCollection) + collections
        persist(context)
        return newCollection
    }

    @Synchronized
    fun deleteCollection(context: Context, collectionId: String) {
        init(context)
        collections = collections.filterNot { it.id == collectionId }
        persist(context)
    }

    @Synchronized
    fun removeItem(context: Context, collectionId: String, itemId: String) {
        init(context)
        collections = collections.map { col ->
            if (col.id == collectionId) {
                col.copy(items = col.items.filterNot { it.id == itemId })
            } else {
                col
            }
        }.filter { it.items.isNotEmpty() }
        persist(context)
    }
}
