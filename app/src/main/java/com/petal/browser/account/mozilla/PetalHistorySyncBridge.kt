package com.petal.browser.account.mozilla

import android.content.Context
import com.petal.browser.database.Record
import com.petal.browser.database.RecordAction
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class MozHistoryVisit(
    val date: Long,
    val type: Int = 1
)

data class MozHistoryItem(
    val guid: String,
    val url: String,
    val title: String,
    val visits: List<MozHistoryVisit> = emptyList(),
    val sortindex: Int = 0
)

class PetalHistorySyncBridge {

    fun exportToBsoRecords(context: Context, maxRecords: Int = 100): List<BsoRecord> {
        val action = RecordAction(context)
        val historyList = try {
            action.open(false)
            action.listHistory(context)
        } catch (e: Exception) {
            emptyList<Record>()
        } finally {
            try { action.close() } catch (_: Exception) {}
        }

        return historyList.take(maxRecords).map { item ->
            val guid = UUID.nameUUIDFromBytes(item.url.toByteArray()).toString().replace("-", "").take(12)
            val visitsArr = JSONArray().apply {
                val visitObj = JSONObject().apply {
                    put("date", if (item.time > 0) item.time * 1000L else System.currentTimeMillis() * 1000L)
                    put("type", 1)
                }
                put(visitObj)
            }

            val payload = JSONObject().apply {
                put("id", guid)
                put("histUri", item.url)
                put("title", item.title ?: item.url)
                put("visits", visitsArr)
            }

            BsoRecord(
                id = guid,
                modified = System.currentTimeMillis() / 1000.0,
                payload = payload.toString()
            )
        }
    }

    fun parseBsoRecords(bsoList: List<BsoRecord>): List<MozHistoryItem> {
        val items = mutableListOf<MozHistoryItem>()
        for (bso in bsoList) {
            try {
                val json = JSONObject(bso.payload)
                if (json.optBoolean("deleted", false)) continue

                val url = json.optString("histUri", json.optString("url", ""))
                if (url.isBlank() || url.startsWith("about:") || url.startsWith("petal://")) continue

                val title = json.optString("title", url)
                val id = json.optString("id", bso.id)
                val sortindex = json.optInt("sortindex", 0)

                val visits = mutableListOf<MozHistoryVisit>()
                val visitsArr = json.optJSONArray("visits")
                if (visitsArr != null) {
                    for (i in 0 until visitsArr.length()) {
                        val vObj = visitsArr.getJSONObject(i)
                        val dateMicro = vObj.optLong("date", System.currentTimeMillis() * 1000L)
                        val type = vObj.optInt("type", 1)
                        visits.add(MozHistoryVisit(date = dateMicro / 1000L, type = type))
                    }
                }

                items.add(
                    MozHistoryItem(
                        guid = id,
                        url = url,
                        title = title,
                        visits = visits,
                        sortindex = sortindex
                    )
                )
            } catch (_: Exception) {}
        }
        return items
    }

    fun importToDatabase(context: Context, items: List<MozHistoryItem>) {
        if (items.isEmpty()) return
        val action = RecordAction(context)
        try {
            action.open(true)
            val existingHistory = action.listHistory(context)
            val existingUrls = existingHistory.mapNotNull { it.url?.trim()?.lowercase() }.toSet()

            for (item in items) {
                val normUrl = item.url.trim().lowercase()
                if (!existingUrls.contains(normUrl)) {
                    val visitTime = item.visits.maxOfOrNull { it.date } ?: System.currentTimeMillis()
                    val record = Record().apply {
                        title = item.title.ifBlank { item.url }
                        url = item.url
                        time = visitTime
                    }
                    action.addHistory(record)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { action.close() } catch (_: Exception) {}
        }
    }
}
