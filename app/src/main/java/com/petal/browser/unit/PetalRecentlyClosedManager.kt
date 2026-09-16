package com.petal.browser.unit

import com.petal.browser.compose.tabs.PetalTabItem

/**
 * Manages recently closed tabs for instant restoration ("Undo Close")
 * and historical recently closed tab browsing.
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
    private const val MAX_RECENTLY_CLOSED = 30
    private val closedTabs = mutableListOf<ClosedTabRecord>()

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
        // Do not store incognito tabs in recently closed history for privacy
        if (isIncognito) return

        val cleanUrl = url?.trim() ?: ""
        if (cleanUrl.isEmpty() || cleanUrl.equals("about:blank", ignoreCase = true)) {
            return
        }

        // Avoid exact duplicate at the top
        if (closedTabs.isNotEmpty() && closedTabs[0].url.equals(cleanUrl, ignoreCase = true)) {
            closedTabs.removeAt(0)
        }

        val safeId = if (id.isNullOrBlank()) "tab_${System.currentTimeMillis()}" else id
        val safeTitle = if (title.isNullOrBlank() || title == "Petal Home") cleanUrl else title

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
        return if (closedTabs.isNotEmpty()) closedTabs.removeAt(0) else null
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
        return if (index >= 0) closedTabs.removeAt(index) else null
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
    }
}
