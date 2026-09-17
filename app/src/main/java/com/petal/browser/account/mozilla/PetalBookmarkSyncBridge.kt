package com.petal.browser.account.mozilla

import android.content.Context
import com.petal.browser.database.Record
import com.petal.browser.database.RecordAction
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class MozBookmarkType {
    FOLDER, BOOKMARK, SEPARATOR
}

data class MozBookmarkItem(
    val guid: String,
    val parentGuid: String,
    val position: Long = 0L,
    val title: String,
    val url: String?,
    val type: MozBookmarkType,
    val dateAdded: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val childrenGuids: List<String> = emptyList()
)

class PetalBookmarkSyncBridge {

    fun exportToBsoRecords(context: Context): List<BsoRecord> {
        val action = RecordAction(context)
        val records = try {
            action.open(false)
            action.listBookmark(context, false, 0)
        } catch (e: Exception) {
            emptyList<Record>()
        } finally {
            try { action.close() } catch (_: Exception) {}
        }

        var pos = 0L
        return records.map { r ->
            val guid = UUID.nameUUIDFromBytes((r.url ?: r.title).toByteArray()).toString().replace("-", "").take(12)
            val payload = JSONObject().apply {
                put("id", guid)
                put("type", "bookmark")
                put("title", r.title ?: "")
                put("parentid", MOBILE_GUID)
                put("pos", pos++)
                put("bmkUri", r.url ?: "")
                put("dateAdded", if (r.time > 0) r.time else System.currentTimeMillis())
                put("lastModified", System.currentTimeMillis())
            }
            BsoRecord(
                id = guid,
                modified = System.currentTimeMillis() / 1000.0,
                payload = payload.toString()
            )
        }
    }

    fun parseBsoRecords(bsoList: List<BsoRecord>): List<MozBookmarkItem> {
        val items = mutableListOf<MozBookmarkItem>()
        for (bso in bsoList) {
            try {
                val json = JSONObject(bso.payload)
                val isDeleted = json.optBoolean("deleted", false)
                val id = json.optString("id", bso.id)
                val typeStr = json.optString("type", "bookmark")
                val type = when (typeStr.lowercase()) {
                    "folder" -> MozBookmarkType.FOLDER
                    "separator" -> MozBookmarkType.SEPARATOR
                    else -> MozBookmarkType.BOOKMARK
                }
                val title = json.optString("title", "")
                val parentId = json.optString("parentid", MOBILE_GUID)
                val url = json.optString("bmkUri", json.optString("url", "")).takeIf { it.isNotBlank() }
                val position = json.optLong("pos", 0L)
                val dateAdded = json.optLong("dateAdded", System.currentTimeMillis())
                val lastModified = (bso.modified * 1000.0).toLong()

                items.add(
                    MozBookmarkItem(
                        guid = id,
                        parentGuid = parentId,
                        position = position,
                        title = title,
                        url = url,
                        type = type,
                        dateAdded = dateAdded,
                        lastModified = lastModified,
                        isDeleted = isDeleted
                    )
                )
            } catch (_: Exception) {}
        }
        return items
    }

    fun importToDatabase(context: Context, items: List<MozBookmarkItem>) {
        if (items.isEmpty()) return
        val action = RecordAction(context)
        try {
            action.open(true)
            val existing = action.listBookmark(context, false, 0)
            val existingUrls = existing.mapNotNull { it.url?.trim()?.lowercase() }.toSet()

            for (item in items) {
                if (item.isDeleted || item.url.isNullOrBlank()) continue
                val normalizedUrl = item.url.trim().lowercase()
                if (!existingUrls.contains(normalizedUrl)) {
                    val record = Record().apply {
                        title = item.title.ifBlank { item.url }
                        url = item.url
                        time = item.dateAdded
                    }
                    action.addBookmark(record)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { action.close() } catch (_: Exception) {}
        }
    }

    companion object {
        const val MOBILE_GUID = "mobile"
        const val PLACES_ROOT_GUID = "places"
        const val MENU_GUID = "menu"
        const val TOOLBAR_GUID = "toolbar"
        const val UNFILED_GUID = "unfiled"
    }
}
