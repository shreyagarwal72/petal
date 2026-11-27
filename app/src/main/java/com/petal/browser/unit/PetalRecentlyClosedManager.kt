package com.petal.browser.unit

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.preference.PreferenceManager
import com.petal.browser.compose.tabs.PetalTabItem
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Manages recently closed tabs with:
 * - Configurable expiration/retention policy (1 day, 7 days, 14 days, 30 days, or never).
 * - Persistent storage across sessions via SharedPreferences JSON written off the UI thread.
 * - Optional serialized Gecko engine session state (for back/forward history restoration).
 * - Material 3 Expressive vault security: Password/PIN hash, Biometric preference, and auto-lock state.
 * - ZERO thumbnail storage: All previews are explicitly excluded.
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
    val closedTimestamp: Long = System.currentTimeMillis(),
    val engineStateJson: String? = null,
    val workspaceId: String? = null,
    val isPinned: Boolean = false,
    val isPinnedGlobal: Boolean = true,
    val parentId: String? = null
)

object PetalRecentlyClosedManager {

    private const val PREFS_KEY_RECORDS = "sp_recently_closed_records_json"
    const val PREF_RETENTION_DAYS = "sp_recently_closed_retention_days" // "1", "7", "14", "30", "never"
    const val PREF_LOCK_TYPE = "sp_recently_closed_lock_type" // "none", "biometric", "password"
    const val PREF_PASSWORD_HASH = "sp_recently_closed_pwd_hash"

    private const val MAX_RECENTLY_CLOSED = 60
    private val closedTabs = mutableListOf<ClosedTabRecord>()
    private var isInitialized = false
    @Volatile
    private var appContext: Context? = null
    private val ioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "petal-recently-closed-io").apply { isDaemon = true }
    }

    @JvmStatic
    fun isHomeOrBlankUrl(url: String?): Boolean {
        val clean = url?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return clean.isEmpty() ||
            clean == "about:blank" ||
            clean == "about:home" ||
            clean == "petal://home" ||
            clean == "petal://start" ||
            clean.contains("petal_home.html") ||
            clean.startsWith("file:///android_asset/")
    }

    @Synchronized
    fun init(context: Context) {
        appContext = context.applicationContext ?: context
        if (isInitialized) return
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val json = sp.getString(PREFS_KEY_RECORDS, null)
        if (!json.isNullOrBlank()) {
            try {
                val array = JSONArray(json)
                closedTabs.clear()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val recUrl = obj.optString("url")
                    val recIncognito = obj.optBoolean("isIncognito", false)
                    if (recIncognito || isHomeOrBlankUrl(recUrl)) continue
                    closedTabs.add(
                        ClosedTabRecord(
                            id = obj.optString("id"),
                            title = obj.optString("title"),
                            url = recUrl,
                            originalIndex = obj.optInt("originalIndex", -1),
                            isIncognito = false,
                            groupId = obj.optString("groupId").takeIf { it.isNotEmpty() },
                            groupTitle = obj.optString("groupTitle").takeIf { it.isNotEmpty() },
                            groupColorHex = obj.optString("groupColorHex").takeIf { it.isNotEmpty() },
                            closedTimestamp = obj.optLong("closedTimestamp", System.currentTimeMillis()),
                            engineStateJson = obj.optString("engineStateJson").takeIf { it.isNotEmpty() },
                            workspaceId = obj.optString("workspaceId").takeIf { it.isNotEmpty() },
                            isPinned = obj.optBoolean("isPinned", false),
                            isPinnedGlobal = obj.optBoolean("isPinnedGlobal", true),
                            parentId = obj.optString("parentId").takeIf { it.isNotEmpty() }
                        )
                    )
                }
            } catch (_: Exception) {}
        }
        pruneExpiredTabs(context)
        isInitialized = true
    }

    private fun resolveContext(context: Context?): Context? {
        return context?.applicationContext ?: appContext ?: try {
            com.petal.browser.PetalApplication.instance
        } catch (_: Exception) {
            null
        }
    }

    private fun persist(context: Context?) {
        val ctx = resolveContext(context) ?: return
        val snapshot = closedTabs.toList()
        ioExecutor.execute {
            try {
                val array = JSONArray()
                snapshot.forEach { rec ->
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
                        rec.engineStateJson?.let { put("engineStateJson", it) }
                        rec.workspaceId?.let { put("workspaceId", it) }
                        if (rec.isPinned) put("isPinned", true)
                        if (!rec.isPinnedGlobal) put("isPinnedGlobal", false)
                        rec.parentId?.let { put("parentId", it) }
                    }
                    array.put(obj)
                }
                PreferenceManager.getDefaultSharedPreferences(ctx)
                    .edit()
                    .putString(PREFS_KEY_RECORDS, array.toString())
                    .apply()
            } catch (_: Exception) {}
        }
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
        groupColorHex: String? = null,
        engineStateJson: String? = null,
        workspaceId: String? = null,
        isPinned: Boolean = false,
        isPinnedGlobal: Boolean = true,
        parentId: String? = null
    ) {
        // Strict privacy: Do not store incognito tabs in recently closed history
        if (isIncognito) return

        val cleanUrl = url?.trim() ?: ""
        if (isHomeOrBlankUrl(cleanUrl)) {
            return
        }

        val safeId = if (id.isNullOrBlank()) "closed_${System.currentTimeMillis()}" else id
        // Deduplicate by URL and ID anywhere in the list so restored/re-closed entries move cleanly to top
        closedTabs.removeAll {
            it.url.equals(cleanUrl, ignoreCase = true) || (!id.isNullOrBlank() && it.id == id)
        }

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
            closedTimestamp = System.currentTimeMillis(),
            engineStateJson = engineStateJson?.takeIf { it.isNotBlank() },
            workspaceId = workspaceId,
            isPinned = isPinned,
            isPinnedGlobal = isPinnedGlobal,
            parentId = parentId
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
            groupColorHex = tabItem.groupColorHex,
            workspaceId = tabItem.workspaceId,
            isPinned = tabItem.isPinned,
            isPinnedGlobal = tabItem.isPinnedGlobal
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

    @VisibleForTesting
    @Synchronized
    fun resetForTest() {
        closedTabs.clear()
        isInitialized = false
        appContext = null
    }

    // --- Vault Security & Passcode Helpers ---

    fun getLockType(context: Context): String {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREF_LOCK_TYPE, "none") ?: "none"
    }

    fun setLockType(context: Context, type: String) {
        val editor = PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(PREF_LOCK_TYPE, type)
        if (type != "password") {
            editor.remove(PREF_PASSWORD_HASH)
        }
        editor.apply()
    }

    fun setPassword(context: Context, plainText: String) {
        if (plainText.isBlank()) return
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
        if (storedHash.isBlank() || plainText.isBlank()) return false
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
