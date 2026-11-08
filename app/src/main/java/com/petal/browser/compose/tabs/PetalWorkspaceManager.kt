package com.petal.browser.compose.tabs

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.petal.browser.browser.PetalTabMetadataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Data model for a Zen Browser-style Workspace.
 */
data class PetalWorkspace(
    val id: String,
    val name: String,
    val iconName: String,
    val colorHex: String,
    val order: Int = 0,
    val isDefault: Boolean = false
) {
    fun parseColor(): Color = try {
        Color(android.graphics.Color.parseColor(colorHex))
    } catch (_: Exception) {
        Color(0xFF3B82F6)
    }
}

/**
 * Curated preset accent colors for Zen Workspaces (Material 3 Expressive palette).
 */
val WorkspaceColorPresets = listOf(
    "#3B82F6", // Blue
    "#10B981", // Emerald
    "#8B5CF6", // Violet
    "#F59E0B", // Amber
    "#F43F5E", // Rose
    "#06B6D4", // Cyan
    "#6366F1", // Indigo
    "#EC4899"  // Pink
)

/**
 * Curated Material vector icons for Zen Workspaces.
 */
val WorkspaceIconPresets = listOf(
    "Home",
    "Work",
    "Code",
    "MenuBook",
    "Favorite",
    "Star",
    "ShoppingCart",
    "Explore",
    "School",
    "SportsEsports",
    "Palette",
    "Lightbulb"
)

/**
 * PetalWorkspaceManager
 * ─────────────────────────────────────────────────────────────────────────
 * Zen Browser-style workspace manager handling workspace creation, editing,
 * reordering, active context filtering, and persistence.
 */
object PetalWorkspaceManager {
    const val DEFAULT_WORKSPACE_ID = "ws_default"

    private const val PREFS_NAME = "petal_workspaces_prefs"
    private const val KEY_WORKSPACES_JSON = "workspaces_json"
    private const val KEY_ACTIVE_WORKSPACE_ID = "active_workspace_id"
    private val gson = Gson()

    private val defaultWorkspaces = listOf(
        PetalWorkspace(
            id = DEFAULT_WORKSPACE_ID,
            name = "General",
            iconName = "Home",
            colorHex = "#3B82F6",
            order = 0,
            isDefault = true
        ),
        PetalWorkspace(
            id = "ws_personal",
            name = "Personal",
            iconName = "Favorite",
            colorHex = "#EC4899",
            order = 1
        ),
        PetalWorkspace(
            id = "ws_work",
            name = "Work",
            iconName = "Work",
            colorHex = "#10B981",
            order = 2
        ),
        PetalWorkspace(
            id = "ws_research",
            name = "Research",
            iconName = "MenuBook",
            colorHex = "#8B5CF6",
            order = 3
        )
    )

    private val _workspaces = MutableStateFlow<List<PetalWorkspace>>(defaultWorkspaces)
    val workspaces: StateFlow<List<PetalWorkspace>> = _workspaces.asStateFlow()

    private val _activeWorkspaceId = MutableStateFlow(DEFAULT_WORKSPACE_ID)
    val activeWorkspaceId: StateFlow<String> = _activeWorkspaceId.asStateFlow()

    @Volatile
    private var isInitialized = false

    @Synchronized
    fun init(context: Context) {
        if (isInitialized) return
        val appContext = context.applicationContext
        val sp = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val json = sp.getString(KEY_WORKSPACES_JSON, null)
        val loadedList = if (!json.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<PetalWorkspace>>() {}.type
                gson.fromJson<List<PetalWorkspace>>(json, type) ?: defaultWorkspaces
            } catch (_: Exception) {
                defaultWorkspaces
            }
        } else {
            defaultWorkspaces
        }

        // Ensure default workspace always exists
        val sanitizedList = if (loadedList.none { it.id == DEFAULT_WORKSPACE_ID }) {
            listOf(defaultWorkspaces.first()) + loadedList
        } else {
            loadedList
        }.sortedBy { it.order }

        _workspaces.value = sanitizedList

        val savedActiveId = sp.getString(KEY_ACTIVE_WORKSPACE_ID, DEFAULT_WORKSPACE_ID) ?: DEFAULT_WORKSPACE_ID
        _activeWorkspaceId.value = if (sanitizedList.any { it.id == savedActiveId }) {
            savedActiveId
        } else {
            DEFAULT_WORKSPACE_ID
        }

        isInitialized = true
    }

    @Synchronized
    private fun persist(context: Context) {
        val appContext = context.applicationContext
        val sp = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(_workspaces.value)
        sp.edit()
            .putString(KEY_WORKSPACES_JSON, json)
            .putString(KEY_ACTIVE_WORKSPACE_ID, _activeWorkspaceId.value)
            .apply()
    }

    fun getActiveWorkspace(context: Context): PetalWorkspace {
        init(context)
        val id = _activeWorkspaceId.value
        return _workspaces.value.firstOrNull { it.id == id } ?: _workspaces.value.first()
    }

    fun setActiveWorkspace(context: Context, id: String) {
        init(context)
        if (_workspaces.value.any { it.id == id }) {
            _activeWorkspaceId.value = id
            val appContext = context.applicationContext
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_ACTIVE_WORKSPACE_ID, id).apply()
        }
    }

    @Synchronized
    fun createWorkspace(
        context: Context,
        name: String,
        iconName: String,
        colorHex: String
    ): PetalWorkspace {
        init(context)
        val cleanName = name.trim().ifBlank { "Workspace" }
        val newOrder = (_workspaces.value.maxOfOrNull { it.order } ?: 0) + 1
        val newWs = PetalWorkspace(
            id = "ws_${UUID.randomUUID().toString().take(8)}",
            name = cleanName,
            iconName = iconName,
            colorHex = colorHex,
            order = newOrder,
            isDefault = false
        )
        val updated = _workspaces.value + newWs
        _workspaces.value = updated.sortedBy { it.order }
        persist(context)
        return newWs
    }

    @Synchronized
    fun updateWorkspace(
        context: Context,
        workspaceId: String,
        name: String,
        iconName: String,
        colorHex: String
    ) {
        init(context)
        val cleanName = name.trim().ifBlank { "Workspace" }
        val updated = _workspaces.value.map { ws ->
            if (ws.id == workspaceId) {
                ws.copy(name = cleanName, iconName = iconName, colorHex = colorHex)
            } else {
                ws
            }
        }
        _workspaces.value = updated.sortedBy { it.order }
        persist(context)
    }

    @Synchronized
    fun deleteWorkspace(context: Context, workspaceId: String) {
        init(context)
        if (workspaceId == DEFAULT_WORKSPACE_ID) return // Cannot delete default workspace
        val currentList = _workspaces.value
        val target = currentList.firstOrNull { it.id == workspaceId } ?: return

        // Reassign all tabs in deleted workspace to default workspace
        PetalTabMetadataStore.reassignWorkspace(context, workspaceId, DEFAULT_WORKSPACE_ID)

        val updated = currentList.filterNot { it.id == workspaceId }
        _workspaces.value = updated

        if (_activeWorkspaceId.value == workspaceId) {
            _activeWorkspaceId.value = DEFAULT_WORKSPACE_ID
        }
        persist(context)
    }

    @Synchronized
    fun reorderWorkspaces(context: Context, orderedIds: List<String>) {
        init(context)
        val currentMap = _workspaces.value.associateBy { it.id }
        val reordered = mutableListOf<PetalWorkspace>()
        orderedIds.forEachIndexed { idx, id ->
            currentMap[id]?.let { reordered.add(it.copy(order = idx)) }
        }
        // Append any not explicitly in list
        _workspaces.value.forEach { ws ->
            if (!orderedIds.contains(ws.id)) {
                reordered.add(ws.copy(order = reordered.size))
            }
        }
        _workspaces.value = reordered.sortedBy { it.order }
        persist(context)
    }
}
