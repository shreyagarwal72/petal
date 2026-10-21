package com.petal.browser.media.playlist

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * PetalPlaylistManager
 * ─────────────────────────────────────────────────────────────────────────
 * Brave "Playlist" media vault for Petal Browser.
 * Stores audio/video items extracted from web pages for background and offline playback.
 */
data class PlaylistItem(
    val id: String,
    val title: String,
    val mediaUrl: String,
    val pageUrl: String,
    val durationSeconds: Long = 0L,
    val addedTimestamp: Long = System.currentTimeMillis()
)

object PetalPlaylistManager {
    private const val PREF_KEY_PLAYLIST = "sp_brave_playlist_items"

    @JvmStatic
    fun getItems(context: Context): List<PlaylistItem> {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val jsonStr = sp.getString(PREF_KEY_PLAYLIST, null) ?: return emptyList()
        val list = mutableListOf<PlaylistItem>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    PlaylistItem(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        mediaUrl = obj.getString("mediaUrl"),
                        pageUrl = obj.getString("pageUrl"),
                        durationSeconds = obj.optLong("durationSeconds", 0L),
                        addedTimestamp = obj.optLong("addedTimestamp", 0L)
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    @JvmStatic
    fun addItem(context: Context, title: String, mediaUrl: String, pageUrl: String): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val items = getItems(context).toMutableList()
        if (items.any { it.mediaUrl.equals(mediaUrl, ignoreCase = true) }) {
            return false // Already in playlist
        }
        val newItem = PlaylistItem(
            id = "pl_" + System.currentTimeMillis(),
            title = if (title.isBlank()) "Media Stream" else title,
            mediaUrl = mediaUrl,
            pageUrl = pageUrl
        )
        items.add(0, newItem)
        saveItems(sp, items)
        return true
    }

    @JvmStatic
    fun removeItem(context: Context, id: String) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val items = getItems(context).filter { it.id != id }
        saveItems(sp, items)
    }

    private fun saveItems(sp: SharedPreferences, items: List<PlaylistItem>) {
        val arr = JSONArray()
        for (item in items) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("title", item.title)
            obj.put("mediaUrl", item.mediaUrl)
            obj.put("pageUrl", item.pageUrl)
            obj.put("durationSeconds", item.durationSeconds)
            obj.put("addedTimestamp", item.addedTimestamp)
            arr.put(obj)
        }
        sp.edit().putString(PREF_KEY_PLAYLIST, arr.toString()).apply()
    }
}
