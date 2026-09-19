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

                val url = json.optString("histUri", json.optString("url", "")).trim()
                if (url.isBlank() || url.startsWith("about:", ignoreCase = true) || url.startsWith("petal://", ignoreCase = true)) continue

                val title = json.optString("title", "").ifBlank { url }
                val id = json.optString("id", bso.id)
                val sortindex = json.optInt("sortindex", 0)

                val visits = mutableListOf<MozHistoryVisit>()
                val visitsArr = json.optJSONArray("visits")
                if (visitsArr != null && visitsArr.length() > 0) {
                    for (i in 0 until visitsArr.length()) {
                        val vObj = visitsArr.optJSONObject(i)
                        if (vObj != null) {
                            var rawDate = vObj.optLong("date", 0L)
                            if (rawDate <= 0L) {
                                rawDate = (vObj.optDouble("date", 0.0) * 1000.0).toLong()
                            }
                            if (rawDate <= 0L) {
                                rawDate = (bso.modified * 1000.0).toLong()
                            }
                            // If timestamp is in microseconds (> 10^14), convert to milliseconds
                            val dateMillis = if (rawDate > 100_000_000_000_000L) {
                                rawDate / 1000L
                            } else if (rawDate > 0L) {
                                rawDate
                            } else {
                                System.currentTimeMillis()
                            }
                            val type = vObj.optInt("type", 1)
                            visits.add(MozHistoryVisit(date = dateMillis, type = type))
                        }
                    }
                }

                // If visits array was empty or missing, derive visit from record's modified timestamp or current time
                if (visits.isEmpty()) {
                    val modMillis = if (bso.modified > 0) (bso.modified * 1000.0).toLong() else System.currentTimeMillis()
                    visits.add(MozHistoryVisit(date = modMillis, type = 1))
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
            val existingUrlMap = mutableMapOf<String, Record>()
            for (rec in existingHistory) {
                val u = rec.url?.trim()?.lowercase()
                if (!u.isNullOrEmpty()) {
                    existingUrlMap[u] = rec
                }
            }

            for (item in items) {
                val normUrl = item.url.trim().lowercase()
                if (normUrl.isBlank() || normUrl.startsWith("about:") || normUrl.startsWith("petal://")) continue

                val visitTime = item.visits.maxOfOrNull { it.date } ?: System.currentTimeMillis()
                val existingRecord = existingUrlMap[normUrl]

                if (existingRecord == null) {
                    val record = Record().apply {
                        title = item.title.ifBlank { item.url }
                        url = item.url
                        time = visitTime
                    }
                    action.addHistory(record)
                    existingUrlMap[normUrl] = record
                } else if (visitTime > existingRecord.time) {
                    // Update to more recent visit time
                    action.deleteHistory(existingRecord)
                    val updatedRecord = Record().apply {
                        title = item.title.ifBlank { existingRecord.title ?: item.url }
                        url = item.url
                        time = visitTime
                    }
                    action.addHistory(updatedRecord)
                    existingUrlMap[normUrl] = updatedRecord
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { action.close() } catch (_: Exception) {}
        }
    }
}
