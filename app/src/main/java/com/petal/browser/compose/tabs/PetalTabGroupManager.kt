package com.petal.browser.compose.tabs

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Data model for a Tab Group in Petal Browser.
 */
data class PetalTabGroup(
    val id: String,
    val title: String,
    val colorHex: String,
    val isIncognito: Boolean = false,
    val tabIds: List<String> = emptyList(),
    val workspaceId: String? = null,
    val isCollapsed: Boolean = false
) {
    fun parseColor(): Color {
        return try {
            Color(android.graphics.Color.parseColor(colorHex))
        } catch (_: Exception) {
            Color(0xFF3B82F6) // Default primary blue
        }
    }
}

/**
 * Preset palette for Tab Group colors (Material 3 Expressive vibrant colors).
 */
val TabGroupColorPresets = listOf(
    "#3B82F6", // Blue
    "#10B981", // Emerald / Green
    "#F59E0B", // Amber / Orange
    "#EF4444", // Rose / Red
    "#8B5CF6", // Purple
    "#EC4899", // Pink
    "#06B6D4", // Cyan
    "#84CC16"  // Lime
)

/**
 * Singleton manager handling Tab Group creation, merging, persistence and updates.
 */
object PetalTabGroupManager {
    private const val PREFS_NAME = "petal_tab_groups_prefs"
    private const val KEY_GROUPS_JSON = "tab_groups_json"
    private val gson = Gson()

    private val groupsMap = mutableMapOf<String, PetalTabGroup>()
    private var isInitialized = false

    @Synchronized
    fun init(context: Context) {
        if (isInitialized) return
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = sp.getString(KEY_GROUPS_JSON, null)
        if (!json.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<PetalTabGroup>>() {}.type
                val loadedList: List<PetalTabGroup> = gson.fromJson(json, type) ?: emptyList()
                groupsMap.clear()
                loadedList.forEach { groupsMap[it.id] = it }
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
            val json = gson.toJson(groupsMap.values.toList())
            sp.edit().putString(KEY_GROUPS_JSON, json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun getAllGroups(context: Context): List<PetalTabGroup> {
        init(context)
        return groupsMap.values.toList()
    }

    @Synchronized
    fun getGroup(context: Context, groupId: String): PetalTabGroup? {
        init(context)
        return groupsMap[groupId]
    }

    @Synchronized
    fun findGroupByTabId(context: Context, tabId: String): PetalTabGroup? {
        init(context)
        return groupsMap.values.find { it.tabIds.contains(tabId) }
    }

    private fun syncGroupMetadata(context: Context, group: PetalTabGroup) {
        group.tabIds.forEach { tid ->
            com.petal.browser.browser.PetalTabMetadataStore.setGroup(
                context = context,
                tabId = tid,
                groupId = group.id,
                groupTitle = group.title,
                groupColorHex = group.colorHex
            )
        }
    }

    private fun clearGroupMetadata(context: Context, tabIds: Collection<String>) {
        tabIds.forEach { tid ->
            com.petal.browser.browser.PetalTabMetadataStore.setGroup(context, tid, null, null, null)
        }
    }

    @androidx.annotation.VisibleForTesting
    @Synchronized
    fun resetForTest() {
        groupsMap.clear()
        isInitialized = false
    }

    @Synchronized
    fun createGroupWithTabs(
        context: Context,
        tabA: PetalTabItem,
        tabB: PetalTabItem,
        title: String? = null
    ): PetalTabGroup {
        init(context)
        if (tabA.id == tabB.id) {
            return createGroupWithTab(context, tabA, title)
        }
        // Strict privacy: Incognito tabs cannot be merged into persistent tab groups
        if (tabA.isIncognito || tabB.isIncognito) {
            return findGroupByTabId(context, tabA.id)
                ?: findGroupByTabId(context, tabB.id)
                ?: PetalTabGroup(id = "", title = "", colorHex = "#3B82F6", tabIds = emptyList())
        }
        val existingGroupA = findGroupByTabId(context, tabA.id)
        val existingGroupB = findGroupByTabId(context, tabB.id)

        if (existingGroupA != null && existingGroupB != null && existingGroupA.id == existingGroupB.id) {
            return existingGroupA
        }

        if (existingGroupA != null) {
            existingGroupB?.let { gb -> removeTabFromGroup(context, gb.id, tabB.id) }
            val updatedTabs = (existingGroupA.tabIds + tabB.id).distinct()
            val updatedGroup = existingGroupA.copy(tabIds = updatedTabs)
            groupsMap[updatedGroup.id] = updatedGroup
            syncGroupMetadata(context, updatedGroup)
            persist(context)
            return updatedGroup
        }

        if (existingGroupB != null) {
            val updatedTabs = (existingGroupB.tabIds + tabA.id).distinct()
            val updatedGroup = existingGroupB.copy(tabIds = updatedTabs)
            groupsMap[updatedGroup.id] = updatedGroup
            syncGroupMetadata(context, updatedGroup)
            persist(context)
            return updatedGroup
        }

        // Neither tab is in a group -> create a new group
        val newGroupId = "group_${System.currentTimeMillis()}"
        val colorIndex = (groupsMap.size) % TabGroupColorPresets.size
        val colorHex = TabGroupColorPresets[colorIndex]
        val groupTitle = title?.takeIf { it.isNotBlank() } ?: "Group ${groupsMap.size + 1}"

        val newGroup = PetalTabGroup(
            id = newGroupId,
            title = groupTitle,
            colorHex = colorHex,
            isIncognito = false,
            tabIds = listOf(tabA.id, tabB.id).distinct()
        )
        groupsMap[newGroupId] = newGroup
        syncGroupMetadata(context, newGroup)
        persist(context)
        return newGroup
    }

    @Synchronized
    fun createGroupWithTab(
        context: Context,
        tab: PetalTabItem,
        title: String? = null
    ): PetalTabGroup {
        init(context)
        if (tab.isIncognito) {
            return PetalTabGroup(id = "", title = "", colorHex = "#3B82F6", isIncognito = true, tabIds = emptyList())
        }
        val existing = findGroupByTabId(context, tab.id)
        if (existing != null) {
            syncGroupMetadata(context, existing)
            return existing
        }
        val newGroupId = "group_${System.currentTimeMillis()}_${groupsMap.size}"
        val colorHex = TabGroupColorPresets[groupsMap.size % TabGroupColorPresets.size]
        val groupTitle = title?.takeIf { it.isNotBlank() } ?: "Group ${groupsMap.size + 1}"
        val group = PetalTabGroup(
            id = newGroupId,
            title = groupTitle,
            colorHex = colorHex,
            isIncognito = false,
            tabIds = listOf(tab.id)
        )
        groupsMap[newGroupId] = group
        syncGroupMetadata(context, group)
        persist(context)
        return group
    }

    @Synchronized
    fun ensureGroupWithTab(
        context: Context,
        groupId: String,
        groupTitle: String?,
        groupColorHex: String?,
        tabId: String
    ): PetalTabGroup {
        init(context)
        val existing = groupsMap[groupId]
        if (existing != null) {
            val updated = existing.copy(tabIds = (existing.tabIds + tabId).distinct())
            groupsMap[groupId] = updated
            syncGroupMetadata(context, updated)
            persist(context)
            return updated
        }
        val group = PetalTabGroup(
            id = groupId,
            title = groupTitle?.takeIf { it.isNotBlank() } ?: "Group ${groupsMap.size + 1}",
            colorHex = groupColorHex?.takeIf { it.isNotBlank() } ?: TabGroupColorPresets[groupsMap.size % TabGroupColorPresets.size],
            isIncognito = false,
            tabIds = listOf(tabId)
        )
        groupsMap[groupId] = group
        syncGroupMetadata(context, group)
        persist(context)
        return group
    }

    @Synchronized
    fun addTabToGroup(context: Context, groupId: String, tabId: String): PetalTabGroup? {
        init(context)
        val isIncognito = try {
            com.petal.browser.engine.gecko.PetalEngineStore.getStore(context).state.tabs.find { it.id == tabId }?.content?.private == true
        } catch (_: Throwable) { false }
        if (isIncognito) return null

        val group = groupsMap[groupId] ?: return null
        // Detach from any previous group first to avoid orphan membership across groups
        groupsMap.values.filter { it.id != groupId && it.tabIds.contains(tabId) }.forEach { oldGroup ->
            val cleaned = oldGroup.tabIds.filter { it != tabId }
            if (cleaned.isEmpty()) groupsMap.remove(oldGroup.id)
            else groupsMap[oldGroup.id] = oldGroup.copy(tabIds = cleaned)
        }
        val updatedTabs = (group.tabIds + tabId).distinct()
        val updatedGroup = group.copy(tabIds = updatedTabs)
        groupsMap[groupId] = updatedGroup
        syncGroupMetadata(context, updatedGroup)
        persist(context)
        return updatedGroup
    }

    @Synchronized
    fun removeTabFromGroup(context: Context, groupId: String, tabId: String): PetalTabGroup? {
        init(context)
        val group = groupsMap[groupId] ?: return null
        val updatedTabs = group.tabIds.filter { it != tabId }
        com.petal.browser.browser.PetalTabMetadataStore.setGroup(context, tabId, null, null, null)
        if (updatedTabs.isEmpty()) {
            groupsMap.remove(groupId)
            persist(context)
            return null
        } else {
            val updatedGroup = group.copy(tabIds = updatedTabs)
            groupsMap[groupId] = updatedGroup
            persist(context)
            return updatedGroup
        }
    }

    @Synchronized
    fun updateGroup(context: Context, group: PetalTabGroup) {
        init(context)
        groupsMap[group.id] = group
        syncGroupMetadata(context, group)
        persist(context)
    }

    @Synchronized
    fun toggleCollapse(context: Context, groupId: String): Boolean {
        init(context)
        val group = groupsMap[groupId] ?: return false
        val newState = !group.isCollapsed
        groupsMap[groupId] = group.copy(isCollapsed = newState)
        persist(context)
        return newState
    }

    @Synchronized
    fun setGroupWorkspace(context: Context, groupId: String, workspaceId: String?) {
        init(context)
        val group = groupsMap[groupId] ?: return
        groupsMap[groupId] = group.copy(workspaceId = workspaceId)
        group.tabIds.forEach { tabId ->
            com.petal.browser.browser.PetalTabMetadataStore.setWorkspace(context, tabId, workspaceId)
        }
        persist(context)
    }

    @Synchronized
    fun isGroupCollapsed(context: Context, groupId: String): Boolean {
        init(context)
        return groupsMap[groupId]?.isCollapsed ?: false
    }

    @Synchronized
    fun deleteGroup(context: Context, groupId: String) {
        init(context)
        val removed = groupsMap.remove(groupId)
        if (removed != null) {
            clearGroupMetadata(context, removed.tabIds)
        }
        persist(context)
    }

    @Synchronized
    fun autoGroupByDomain(context: Context, tabs: List<PetalTabItem>): Int {
        init(context)
        val regularTabs = tabs.filter { !it.isIncognito && it.url.isNotBlank() && !it.url.startsWith("about:") }
        val domainMap = mutableMapOf<String, MutableList<PetalTabItem>>()
        
        for (tab in regularTabs) {
            val domain = com.petal.browser.unit.PetalAndroidUtils.domain(tab.url)
            if (!domain.isNullOrBlank() && domain != "null" && domain != "localhost") {
                domainMap.getOrPut(domain) { mutableListOf() }.add(tab)
            }
        }

        var groupsCreatedOrUpdated = 0
        for ((domain, tabList) in domainMap) {
            if (tabList.size >= 2) {
                val existingGroup = groupsMap.values.find { it.title.equals(domain, ignoreCase = true) && !it.isIncognito }
                val tabIds = tabList.map { it.id }
                if (existingGroup != null) {
                    val merged = (existingGroup.tabIds + tabIds).distinct()
                    if (merged.size != existingGroup.tabIds.size) {
                        val updated = existingGroup.copy(tabIds = merged)
                        groupsMap[existingGroup.id] = updated
                        syncGroupMetadata(context, updated)
                        groupsCreatedOrUpdated++
                    }
                } else {
                    val newGroupId = "group_${System.currentTimeMillis()}_${groupsMap.size}"
                    val colorIndex = (groupsMap.size) % TabGroupColorPresets.size
                    val colorHex = TabGroupColorPresets[colorIndex]
                    val cleanTitle = domain.removePrefix("www.").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    val newGroup = PetalTabGroup(
                        id = newGroupId,
                        title = cleanTitle,
                        colorHex = colorHex,
                        isIncognito = false,
                        tabIds = tabIds.distinct()
                    )
                    groupsMap[newGroupId] = newGroup
                    syncGroupMetadata(context, newGroup)
                    groupsCreatedOrUpdated++
                }
            }
        }
        if (groupsCreatedOrUpdated > 0) {
            persist(context)
        }
        return groupsCreatedOrUpdated
    }

    @Synchronized
    @JvmOverloads
    fun syncWithOpenTabs(
        context: Context,
        currentOpenTabIds: Set<String>,
        protectedTabIds: Set<String> = emptySet()
    ) {
        init(context)
        // Guard against cold-start / pre-restore empty emissions wiping saved groups
        if (currentOpenTabIds.isEmpty()) return
        val retainIds = currentOpenTabIds + protectedTabIds
        var changed = false
        val groupKeys = groupsMap.keys.toList()
        for (gid in groupKeys) {
            val group = groupsMap[gid] ?: continue
            val remainingTabs = group.tabIds.filter { retainIds.contains(it) }
            if (remainingTabs.isEmpty()) {
                groupsMap.remove(gid)
                changed = true
            } else if (remainingTabs.size != group.tabIds.size) {
                groupsMap[gid] = group.copy(tabIds = remainingTabs)
                changed = true
            }
        }
        if (changed) {
            persist(context)
        }
    }
}
