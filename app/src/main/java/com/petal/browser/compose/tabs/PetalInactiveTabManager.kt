package com.petal.browser.compose.tabs

import android.content.Context
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.TimeUnit

/**
 * Data model for an Inactive / Archived Tab in Petal Browser.
 */
data class PetalInactiveTab(
    val id: String,
    val title: String,
    val url: String,
    val lastAccessedTimestamp: Long,
    val isIncognito: Boolean = false,
    val isGroup: Boolean = false,
    val groupId: String? = null,
    val groupTitle: String? = null,
    val groupColorHex: String? = null,
    val isDuplicateArchive: Boolean = false
)

/**
 * Singleton manager handling Inactive Tabs, Duplicate Tab Archival, and Inactivity Timers.
 */
object PetalInactiveTabManager {
    private const val PREFS_NAME = "petal_inactive_tabs_prefs"
    private const val KEY_INACTIVE_TABS_JSON = "inactive_tabs_json"
    private const val KEY_TAB_ACCESS_MAP_JSON = "tab_access_map_json"

    const val PREF_INACTIVE_DAYS_THRESHOLD = "sp_inactive_days_threshold" // "never", "7", "14", "21"
    const val PREF_ARCHIVE_DUPLICATES = "sp_archive_duplicate_tabs" // Boolean
    const val PREF_AUTO_CLOSE_INACTIVE_3_MONTHS = "sp_auto_close_inactive_3_months" // Boolean

    private val gson = Gson()
    private val inactiveTabsList = mutableListOf<PetalInactiveTab>()
    private val tabAccessMap = mutableMapOf<String, Long>()
    private var isInitialized = false

    @Synchronized
    fun init(context: Context) {
        if (isInitialized) return
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Load access map
        val accessJson = sp.getString(KEY_TAB_ACCESS_MAP_JSON, null)
        if (!accessJson.isNullOrBlank()) {
            try {
                val mapType = object : TypeToken<Map<String, Long>>() {}.type
                val loadedMap: Map<String, Long> = gson.fromJson(accessJson, mapType) ?: emptyMap()
                tabAccessMap.clear()
                tabAccessMap.putAll(loadedMap)
            } catch (_: Exception) {}
        }

        // Load inactive tabs
        val inactiveJson = sp.getString(KEY_INACTIVE_TABS_JSON, null)
        if (!inactiveJson.isNullOrBlank()) {
            try {
                val listType = object : TypeToken<List<PetalInactiveTab>>() {}.type
                val loadedList: List<PetalInactiveTab> = gson.fromJson(inactiveJson, listType) ?: emptyList()
                inactiveTabsList.clear()
                inactiveTabsList.addAll(loadedList)
            } catch (_: Exception) {}
        }

        isInitialized = true
    }

    @Synchronized
    private fun persist(context: Context) {
        try {
            val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val inactiveJson = gson.toJson(inactiveTabsList)
            val accessJson = gson.toJson(tabAccessMap)
            sp.edit()
                .putString(KEY_INACTIVE_TABS_JSON, inactiveJson)
                .putString(KEY_TAB_ACCESS_MAP_JSON, accessJson)
                .apply()
        } catch (_: Exception) {}
    }

    @Synchronized
    fun recordTabAccess(context: Context, tabId: String) {
        init(context)
        tabAccessMap[tabId] = System.currentTimeMillis()
        persist(context)
    }

    @Synchronized
    fun getTabLastAccess(context: Context, tabId: String): Long {
        init(context)
        return tabAccessMap[tabId] ?: System.currentTimeMillis()
    }

    @Synchronized
    fun getInactiveTabs(context: Context): List<PetalInactiveTab> {
        init(context)
        return inactiveTabsList.toList()
    }

    @Synchronized
    fun getInactiveCount(context: Context): Int {
        init(context)
        return inactiveTabsList.size
    }

    @Synchronized
    fun getThresholdDays(context: Context): Int {
        val defSp = PreferenceManager.getDefaultSharedPreferences(context)
        val value = defSp.getString(PREF_INACTIVE_DAYS_THRESHOLD, "21") ?: "21"
        return when (value) {
            "never" -> 0
            "7" -> 7
            "14" -> 14
            "21" -> 21
            else -> 21
        }
    }

    @Synchronized
    fun isArchiveDuplicatesEnabled(context: Context): Boolean {
        val defSp = PreferenceManager.getDefaultSharedPreferences(context)
        return defSp.getBoolean(PREF_ARCHIVE_DUPLICATES, true)
    }

    @Synchronized
    fun isAutoClose3MonthsEnabled(context: Context): Boolean {
        val defSp = PreferenceManager.getDefaultSharedPreferences(context)
        return defSp.getBoolean(PREF_AUTO_CLOSE_INACTIVE_3_MONTHS, true)
    }

    /**
     * Periodically evaluate open tabs to move inactive and duplicate tabs into the archived list.
     * Returns the list of tabs that were archived and need to be removed from the active container.
     */
    @Synchronized
    fun evaluateAndArchiveInactiveTabs(
        context: Context,
        openTabs: List<PetalTabItem>
    ): List<PetalTabItem> {
        init(context)
        val now = System.currentTimeMillis()
        val thresholdDays = getThresholdDays(context)
        val archiveDuplicates = isArchiveDuplicatesEnabled(context)
        val autoClose3Months = isAutoClose3MonthsEnabled(context)

        // 1. Clean up 3-month-old inactive tabs
        if (autoClose3Months) {
            val threeMonthsMs = TimeUnit.DAYS.toMillis(90)
            inactiveTabsList.removeAll { now - it.lastAccessedTimestamp > threeMonthsMs }
        }

        val tabsToArchive = mutableListOf<PetalTabItem>()

        // 2. Duplicate detection (keep the most recently accessed copy, archive older copies)
        if (archiveDuplicates && openTabs.size > 1) {
            val urlGroups = openTabs.filter { !it.isIncognito && it.url.isNotBlank() && it.url != "about:blank" }
                .groupBy { it.url.trim().lowercase() }

            for ((_, duplicateTabs) in urlGroups) {
                if (duplicateTabs.size > 1) {
                    // Sort by last accessed descending (most recent first)
                    val sorted = duplicateTabs.sortedByDescending { getTabLastAccess(context, it.id) }
                    // Keep index 0, archive index 1..n
                    for (i in 1 until sorted.size) {
                        val duplicateTab = sorted[i]
                        if (duplicateTab !in tabsToArchive && !duplicateTab.isSelected) {
                            tabsToArchive.add(duplicateTab)
                            val inactive = PetalInactiveTab(
                                id = duplicateTab.id,
                                title = duplicateTab.title,
                                url = duplicateTab.url,
                                lastAccessedTimestamp = getTabLastAccess(context, duplicateTab.id),
                                isIncognito = duplicateTab.isIncognito,
                                isGroup = !duplicateTab.groupId.isNullOrEmpty(),
                                groupId = duplicateTab.groupId,
                                groupTitle = duplicateTab.groupTitle,
                                groupColorHex = duplicateTab.groupColorHex,
                                isDuplicateArchive = true
                            )
                            if (inactiveTabsList.none { it.id == inactive.id }) {
                                inactiveTabsList.add(0, inactive)
                            }
                        }
                    }
                }
            }
        }

        // 3. Inactivity threshold evaluation
        if (thresholdDays > 0) {
            val thresholdMs = TimeUnit.DAYS.toMillis(thresholdDays.toLong())
            for (tab in openTabs) {
                if (tab.isSelected || tab.isIncognito || tab in tabsToArchive) continue
                val lastAccess = getTabLastAccess(context, tab.id)
                if (now - lastAccess >= thresholdMs) {
                    tabsToArchive.add(tab)
                    val inactive = PetalInactiveTab(
                        id = tab.id,
                        title = tab.title,
                        url = tab.url,
                        lastAccessedTimestamp = lastAccess,
                        isIncognito = tab.isIncognito,
                        isGroup = !tab.groupId.isNullOrEmpty(),
                        groupId = tab.groupId,
                        groupTitle = tab.groupTitle,
                        groupColorHex = tab.groupColorHex,
                        isDuplicateArchive = false
                    )
                    if (inactiveTabsList.none { it.id == inactive.id }) {
                        inactiveTabsList.add(0, inactive)
                    }
                }
            }
        }

        persist(context)
        return tabsToArchive
    }

    @Synchronized
    fun restoreInactiveTab(context: Context, inactiveTab: PetalInactiveTab) {
        init(context)
        inactiveTabsList.removeAll { it.id == inactiveTab.id }
        tabAccessMap[inactiveTab.id] = System.currentTimeMillis()
        persist(context)
    }

    @Synchronized
    fun restoreAllInactiveTabs(context: Context): List<PetalInactiveTab> {
        init(context)
        val all = inactiveTabsList.toList()
        inactiveTabsList.clear()
        val now = System.currentTimeMillis()
        all.forEach { tabAccessMap[it.id] = now }
        persist(context)
        return all
    }

    @Synchronized
    fun removeInactiveTab(context: Context, inactiveTabId: String) {
        init(context)
        inactiveTabsList.removeAll { it.id == inactiveTabId }
        tabAccessMap.remove(inactiveTabId)
        persist(context)
    }

    @Synchronized
    fun clearAllInactiveTabs(context: Context) {
        init(context)
        inactiveTabsList.clear()
        persist(context)
    }
}
