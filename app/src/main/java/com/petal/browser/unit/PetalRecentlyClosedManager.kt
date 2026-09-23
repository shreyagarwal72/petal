package com.petal.browser.unit

import android.content.Context
import androidx.preference.PreferenceManager
import com.petal.browser.compose.tabs.PetalTabItem
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Manages recently closed tabs with:
 * - Configurable expiration/retention policy (1 day, 7 days, 14 days, 30 days, or never).
 * - Persistent storage across sessions via SharedPreferences JSON.
 * - Material 3 Expressive vault security: Password/PIN hash, Biometric preference, and auto-lock state.
 * - ZERO thumbnail storage: All previews are explicitly wiped from memory and disk when tabs are closed.
 */
data class ClosedTabRecord(
    val id: String,
    val title: String,
    val url: String,
    val originalIndex: Int = -1,
    val isIncognito: Boolean = false,
    val groupId: String? = null,
    val groupTitle: String? = null,
    val groupColorHex: String? = null,
    val closedTimestamp: Long = System.currentTimeMillis()
)

object PetalRecentlyClosedManager {

    private const val PREFS_KEY_RECORDS = "sp_recently_closed_records_json"
    const val PREF_RETENTION_DAYS = "sp_recently_closed_retention_days" // "1", "7", "14", "30", "never"
    const val PREF_LOCK_TYPE = "sp_recently_closed_lock_type" // "none", "biometric", "password"
    const val PREF_PASSWORD_HASH = "sp_recently_closed_pwd_hash"

    private const val MAX_RECENTLY_CLOSED = 60
    private val closedTabs = mutableListOf<ClosedTabRecord>()
    private var isInitialized = false

    @Synchronized
    fun init(context: Context) {
        if (isInitialized) return
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val json = sp.getString(PREFS_KEY_RECORDS, null)
        if (!json.isNullOrBlank()) {
            try {
                val array = JSONArray(json)
                closedTabs.clear()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    closedTabs.add(
                        ClosedTabRecord(
                            id = obj.optString("id"),
                            title = obj.optString("title"),
                            url = obj.optString("url"),
                            originalIndex = obj.optInt("originalIndex", -1),
                            isIncognito = obj.optBoolean("isIncognito", false),
                            groupId = obj.optString("groupId").takeIf { it.isNotEmpty() },
                            groupTitle = obj.optString("groupTitle").takeIf { it.isNotEmpty() },
                            groupColorHex = obj.optString("groupColorHex").takeIf { it.isNotEmpty() },
                            closedTimestamp = obj.optLong("closedTimestamp", System.currentTimeMillis())
                        )
                    )
                }
            } catch (_: Exception) {}
        }
        pruneExpiredTabs(context)
        isInitialized = true
    }

    private fun persist(context: Context?) {
        val ctx = context ?: try {
            com.petal.browser.PetalApplication.instance
        } catch (_: Exception) { null } ?: return

        try {
            val array = JSONArray()
            closedTabs.forEach { rec ->
                val obj = JSONObject().apply {
                    put("id", rec.id)
                    put("title", rec.title)
                    put("url", rec.url)
                    put("originalIndex", rec.originalIndex)
                    put("isIncognito", rec.isIncognito)
                    rec.groupId?.let { put("groupId", it) }
                    rec.groupTitle?.let { put("groupTitle", it) }
                    rec.groupColorHex?.let { put("groupColorHex", it) }
                    put("closedTimestamp", rec.closedTimestamp)
                }
                array.put(obj)
            }
            PreferenceManager.getDefaultSharedPreferences(ctx)
                .edit()
                .putString(PREFS_KEY_RECORDS, array.toString())
                .apply()
        } catch (_: Exception) {}
    }

    /**
     * Enforces the retention policy: removes records older than the configured threshold.
     */
    @Synchronized
    fun pruneExpiredTabs(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val retentionDays = sp.getString(PREF_RETENTION_DAYS, "7") ?: "7"
        if (retentionDays == "never") return

        val days = retentionDays.toLongOrNull() ?: 7L
        val cutoffTime = System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L)
        val removed = closedTabs.removeAll { it.closedTimestamp < cutoffTime }
        if (removed) {
            persist(context)
        }
    }

    @JvmStatic
    @JvmOverloads
    @Synchronized
    fun pushClosedTab(
        id: String?,
        title: String?,
        url: String?,
        originalIndex: Int = -1,
        isIncognito: Boolean = false,
        groupId: String? = null,
        groupTitle: String? = null,
        groupColorHex: String? = null
    ) {
        // Strict privacy: Do not store incognito tabs in recently closed history
        if (isIncognito) return

        val cleanUrl = url?.trim() ?: ""
        if (cleanUrl.isEmpty() || cleanUrl.equals("about:blank", ignoreCase = true) || cleanUrl.equals("petal://home", ignoreCase = true)) {
            return
        }

        // Wipe thumbnail cache immediately and completely
        if (!id.isNullOrBlank()) {
            TabThumbnailCache.remove(id)
        }

        // Avoid exact duplicate at the top
        if (closedTabs.isNotEmpty() && closedTabs[0].url.equals(cleanUrl, ignoreCase = true)) {
            closedTabs.removeAt(0)
        }

        val safeId = if (id.isNullOrBlank()) "closed_${System.currentTimeMillis()}" else id
        val safeTitle = if (title.isNullOrBlank() || title == "Petal Home" || title == "Petal Start") cleanUrl else title

        val record = ClosedTabRecord(
            id = safeId,
            title = safeTitle,
            url = cleanUrl,
            originalIndex = originalIndex,
            isIncognito = false,
            groupId = groupId,
            groupTitle = groupTitle,
            groupColorHex = groupColorHex,
            closedTimestamp = System.currentTimeMillis()
        )

        closedTabs.add(0, record)
        if (closedTabs.size > MAX_RECENTLY_CLOSED) {
            closedTabs.removeAt(closedTabs.size - 1)
        }
        persist(null)
    }

    @JvmStatic
    @JvmOverloads
    @Synchronized
    fun pushClosedTabItem(tabItem: PetalTabItem, originalIndex: Int = -1) {
        pushClosedTab(
            id = tabItem.id,
            title = tabItem.title,
            url = tabItem.url,
            originalIndex = originalIndex,
            isIncognito = tabItem.isIncognito,
            groupId = tabItem.groupId,
            groupTitle = tabItem.groupTitle,
            groupColorHex = tabItem.groupColorHex
        )
    }

    @JvmStatic
    @Synchronized
    fun popLastClosedTab(): ClosedTabRecord? {
        val rec = if (closedTabs.isNotEmpty()) closedTabs.removeAt(0) else null
        if (rec != null) persist(null)
        return rec
    }

    @JvmStatic
    @Synchronized
    fun peekLastClosedTab(): ClosedTabRecord? {
        return closedTabs.firstOrNull()
    }

    @JvmStatic
    @Synchronized
    fun removeClosedTab(id: String): ClosedTabRecord? {
        val index = closedTabs.indexOfFirst { it.id == id }
        val rec = if (index >= 0) closedTabs.removeAt(index) else null
        if (rec != null) persist(null)
        return rec
    }

    @JvmStatic
    @Synchronized
    fun getRecentlyClosedTabs(): List<ClosedTabRecord> {
        return closedTabs.toList()
    }

    @JvmStatic
    @Synchronized
    fun clear() {
        closedTabs.clear()
        persist(null)
    }

    // --- Vault Security & Passcode Helpers ---

    fun getLockType(context: Context): String {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREF_LOCK_TYPE, "none") ?: "none"
    }

    fun setLockType(context: Context, type: String) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(PREF_LOCK_TYPE, type)
            .apply()
    }

    fun setPassword(context: Context, plainText: String) {
        val hash = hashString(plainText)
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(PREF_PASSWORD_HASH, hash)
            .putString(PREF_LOCK_TYPE, "password")
            .apply()
    }

    fun verifyPassword(context: Context, plainText: String): Boolean {
        val storedHash = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREF_PASSWORD_HASH, "") ?: ""
        if (storedHash.isBlank()) return true
        return hashString(plainText) == storedHash
    }

    fun hasPasswordConfigured(context: Context): Boolean {
        val stored = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREF_PASSWORD_HASH, "")
        return !stored.isNullOrBlank()
    }

    /**
     * Resets the vault lock by removing the stored passcode hash, reverting lock type to 'none',
     * and erasing all closed tabs records for security.
     */
    @JvmStatic
    @Synchronized
    fun resetLockAndClearData(context: Context) {
        closedTabs.clear()
        persist(context)
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .remove(PREF_PASSWORD_HASH)
            .putString(PREF_LOCK_TYPE, "none")
            .apply()
    }

    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
