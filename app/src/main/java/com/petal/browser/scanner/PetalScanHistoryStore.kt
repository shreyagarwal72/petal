package com.petal.browser.scanner

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Historical record of a scanned barcode or QR code.
 */
data class ScanHistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val rawValue: String,
    val formatName: String,
    val title: String,
    val subtitle: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
)

/**
 * Opt-in persistent store for scanned code history, stored securely on internal storage.
 */
object PetalScanHistoryStore {

    private const val FILE_NAME = "petal_scan_history.json"
    private const val MAX_HISTORY_ITEMS = 200

    @Synchronized
    fun getHistory(context: Context): List<ScanHistoryEntry> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()

        return try {
            val content = file.readText()
            val array = JSONArray(content)
            val list = mutableListOf<ScanHistoryEntry>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ScanHistoryEntry(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        rawValue = obj.optString("rawValue", ""),
                        formatName = obj.optString("formatName", "QR_CODE"),
                        title = obj.optString("title", ""),
                        subtitle = obj.optString("subtitle", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        isPinned = obj.optBoolean("isPinned", false)
                    )
                )
            }
            // Pinned items first, then descending by timestamp
            list.sortedWith(compareByDescending<ScanHistoryEntry> { it.isPinned }.thenByDescending { it.timestamp })
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun addHistory(context: Context, result: PetalScanResult) {
        if (!PetalScannerSettings.isHistoryEnabled(context)) return

        val existing = getHistory(context).toMutableList()
        // Deduplicate recent identical scans within 5 seconds unless pinned
        val now = System.currentTimeMillis()
        val isDuplicate = existing.take(3).any {
            it.rawValue == result.rawValue && (now - it.timestamp) < 5000L
        }
        if (isDuplicate) return

        val title = when (val p = result.payload) {
            is ScanPayload.Url -> p.host
            is ScanPayload.Email -> p.address
            is ScanPayload.Phone -> p.number
            is ScanPayload.Sms -> "SMS: ${p.number}"
            is ScanPayload.Wifi -> "Wi-Fi: ${p.ssid}"
            is ScanPayload.Contact -> p.name
            is ScanPayload.Calendar -> p.title
            is ScanPayload.Otp -> "${p.issuer ?: "OTP"}: ${p.account ?: ""}".trimEnd(':', ' ')
            is ScanPayload.Product -> "Product: ${p.code}"
            is ScanPayload.Geo -> "Coordinates: ${p.latitude}, ${p.longitude}"
            is ScanPayload.Text -> p.content.take(40).replace("\n", " ")
        }

        val subtitle = when (val p = result.payload) {
            is ScanPayload.Url -> p.displayUrl
            is ScanPayload.Email -> p.subject ?: p.address
            is ScanPayload.Phone -> "Phone Number"
            is ScanPayload.Sms -> p.message ?: ""
            is ScanPayload.Wifi -> "Security: ${p.authType}"
            is ScanPayload.Contact -> listOfNotNull(p.phone, p.email, p.org).joinToString(" • ")
            is ScanPayload.Calendar -> p.location ?: ""
            is ScanPayload.Otp -> "Secret: ••••••"
            is ScanPayload.Product -> p.format
            is ScanPayload.Geo -> p.query ?: ""
            is ScanPayload.Text -> result.formatName
        }

        val newEntry = ScanHistoryEntry(
            rawValue = result.rawValue,
            formatName = result.formatName,
            title = title,
            subtitle = subtitle,
            timestamp = now,
            isPinned = false
        )

        existing.add(0, newEntry)

        // Trim unpinned items beyond limit
        val pinned = existing.filter { it.isPinned }
        val unpinned = existing.filter { !it.isPinned }.take(MAX_HISTORY_ITEMS - pinned.size)
        val combined = pinned + unpinned

        saveHistoryList(context, combined)
    }

    @Synchronized
    fun deleteHistory(context: Context, id: String) {
        val existing = getHistory(context).filter { it.id != id }
        saveHistoryList(context, existing)
    }

    @Synchronized
    fun togglePin(context: Context, id: String) {
        val existing = getHistory(context).map {
            if (it.id == id) it.copy(isPinned = !it.isPinned) else it
        }
        saveHistoryList(context, existing)
    }

    @Synchronized
    fun clearAll(context: Context) {
        // Retain pinned items on clear all
        val existing = getHistory(context).filter { it.isPinned }
        saveHistoryList(context, existing)
    }

    fun search(context: Context, query: String): List<ScanHistoryEntry> {
        val clean = query.trim().lowercase()
        if (clean.isEmpty()) return getHistory(context)
        return getHistory(context).filter {
            it.title.lowercase().contains(clean) ||
                it.subtitle.lowercase().contains(clean) ||
                it.rawValue.lowercase().contains(clean)
        }
    }

    private fun saveHistoryList(context: Context, list: List<ScanHistoryEntry>) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            val array = JSONArray()
            for (entry in list) {
                val obj = JSONObject().apply {
                    put("id", entry.id)
                    put("rawValue", entry.rawValue)
                    put("formatName", entry.formatName)
                    put("title", entry.title)
                    put("subtitle", entry.subtitle)
                    put("timestamp", entry.timestamp)
                    put("isPinned", entry.isPinned)
                }
                array.put(obj)
            }
            file.writeText(array.toString())
        } catch (_: Exception) {}
    }
}
