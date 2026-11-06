package com.petal.browser.browser

import android.content.Context
import android.util.Log
import androidx.annotation.MainThread
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.petal.browser.R
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.database.HistoryBookmarkRepository
import com.petal.browser.engine.gecko.PetalEngineStore
import com.petal.browser.view.PetalGeckoView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.action.RestoreCompleteAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.ContentState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.state.recover.RecoverableTab
import mozilla.components.browser.state.state.recover.TabState
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * PetalSessionRestoreManager
 * ─────────────────────────────────────────────────────────────────────────
 * Official Mozilla Android Components SessionStorage persistence and restore
 * orchestrator for Petal Browser.
 *
 * BrowserStore in [PetalEngineStore] is the single source of truth for all tabs.
 * Persistence to disk is performed atomically by [mozilla.components.browser.session.storage.AutoSave].
 */
object PetalSessionRestoreManager {

    private const val TAG = "PetalSessionRestore"

    const val PREF_RESTORE_TABS = "sp_restoreTabs"
    const val PREF_RELOAD_TABS = "sp_reloadTabs"
    const val PREF_RESTORE_ON_RESTART = "restoreOnRestart"

    // Legacy preference keys for one-time migration
    private const val PREF_SESSION_JSON_LEGACY = "tab_session_state_json"
    private const val PREF_SESSION_JSON_BACKUP_LEGACY = "tab_session_state_json_backup"
    private const val PREF_OPEN_TABS_LEGACY = "openTabs"
    private const val DELIMITER_LEGACY = "‚‗‚"

    private val gson = Gson()

    private data class LegacyTabRecord(
        val persistentTabId: String = "",
        val title: String = "",
        val url: String = "",
        val isIncognito: Boolean = false,
        val isActive: Boolean = false,
        val tabGroupId: String? = null,
        val tabGroupTitle: String? = null,
        val tabGroupColorHex: String? = null
    )

    /**
     * Synchronously flushes all open tabs, including full engineSessionState history stacks
     * and active indices, into BrowserStore and writes the snapshot to disk via SessionStorage.
     */
    @JvmStatic
    @MainThread
    fun saveSession(activity: BrowserActivity) {
        try {
            if (activity.isFinishing) return
            val store = PetalEngineStore.getStore(activity)
            if (!store.state.restoreComplete) return

            // 1. Capture latest engineSessionState, URL, and title for every live tab
            for (i in 0 until BrowserContainer.size()) {
                val controller = BrowserContainer.get(i) ?: continue
                when (controller) {
                    is PetalTabViewController -> {
                        val tabId = controller.getTabId() ?: continue
                        val url = controller.url
                        if (url.isNotBlank() && !isHomeUrl(url) && !url.equals("about:blank", ignoreCase = true)) {
                            store.dispatch(ContentAction.UpdateUrlAction(tabId, url))
                        }
                        val title = controller.title
                        if (title.isNotBlank()) {
                            store.dispatch(ContentAction.UpdateTitleAction(tabId, title))
                        }
                    }
                    is PetalGeckoView -> {
                        val tabId = controller.getTabId()
                        val url = controller.url
                        if (url.isNotBlank() && !isHomeUrl(url) && !url.equals("about:blank", ignoreCase = true)) {
                            store.dispatch(ContentAction.UpdateUrlAction(tabId, url))
                        }
                        val title = controller.title
                        if (title.isNotBlank()) {
                            store.dispatch(ContentAction.UpdateTitleAction(tabId, title))
                        }
                    }
                    is PlaceholderAlbumController -> {
                        // Already suspended with engineSessionState captured
                    }
                }
            }

            // 2. Update selected tab in store to match active album
            val activeController = activity.currentAlbumController
            val activeTabId = when (activeController) {
                is PetalTabViewController -> activeController.getTabId()
                is PetalGeckoView -> activeController.getTabId()
                is PlaceholderAlbumController -> activeController.getTabId()
                else -> null
            }
            if (activeTabId != null) {
                store.dispatch(TabListAction.SelectTabAction(activeTabId))
            }

            // 3. Synchronously flush BrowserStore state to disk via SessionStorage
            val storage = PetalEngineStore.getSessionStorage(activity)
            val saved = storage.save(store.state)
            Log.d(TAG, "saveSession: flushed ${store.state.tabs.size} tabs to SessionStorage (success=$saved)")
        } catch (t: Throwable) {
            Log.w(TAG, "Error in saveSession: ${t.message}", t)
        }
    }

    fun isHomeUrl(url: String?): Boolean {
        if (url == null || url.trim().isEmpty()) return true
        val clean = url.trim().lowercase(Locale.ROOT)
        return clean == "about:blank" ||
                clean == "about:home" ||
                clean == "petal://home" ||
                clean == "petal://start" ||
                clean.contains("petal_home.html") ||
                clean.startsWith("file:///android_asset/")
    }

    /**
     * Checks if a legacy session store exists and migrates it once into Mozilla SessionStorage,
     * then permanently deletes the old data.
     */
    fun performLegacyMigrationIfNeeded(context: Context) {
        val appContext = context.applicationContext
        val sessionFile = File(appContext.filesDir, "mozilla_components_session_storage_gecko.json")
        if (sessionFile.exists() && sessionFile.length() > 0L) {
            // Already using Mozilla SessionStorage, clean any leftover legacy remnants
            cleanLegacyData(appContext)
            return
        }

        try {
            val sp = PreferenceManager.getDefaultSharedPreferences(appContext)
            var legacyJson = sp.getString(PREF_SESSION_JSON_LEGACY, null)

            if (legacyJson.isNullOrBlank()) {
                try {
                    val repo = HistoryBookmarkRepository(appContext)
                    repo.open(false)
                    legacyJson = repo.sessionStateJson
                    repo.close()
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed reading legacy SQLite session: ${t.message}")
                }
            }

            if (legacyJson.isNullOrBlank()) {
                legacyJson = sp.getString(PREF_SESSION_JSON_BACKUP_LEGACY, null)
            }

            val recordsToMigrate = mutableListOf<LegacyTabRecord>()

            if (!legacyJson.isNullOrBlank()) {
                try {
                    val listType = object : TypeToken<List<LegacyTabRecord>>() {}.type
                    val parsed: List<LegacyTabRecord>? = gson.fromJson(legacyJson, listType)
                    if (!parsed.isNullOrEmpty()) {
                        recordsToMigrate.addAll(parsed.filter { !it.isIncognito })
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed parsing legacy session JSON: ${t.message}")
                }
            }

            // Fallback: check legacy delimited URLs
            if (recordsToMigrate.isEmpty()) {
                val legacyDelimited = sp.getString(PREF_OPEN_TABS_LEGACY, null)
                if (!legacyDelimited.isNullOrBlank()) {
                    val urls = legacyDelimited.split(DELIMITER_LEGACY)
                    for (u in urls) {
                        val trimmed = u.trim()
                        if (trimmed.isNotBlank()) {
                            recordsToMigrate.add(
                                LegacyTabRecord(
                                    persistentTabId = "tab_migrated_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 8)}",
                                    title = "Petal",
                                    url = trimmed
                                )
                            )
                        }
                    }
                }
            }

            if (recordsToMigrate.isNotEmpty()) {
                Log.i(TAG, "Migrating ${recordsToMigrate.size} legacy tabs into Mozilla SessionStorage")
                val tabs = mutableListOf<TabSessionState>()
                var activeId: String? = null

                for (rec in recordsToMigrate) {
                    val id = if (rec.persistentTabId.isNotBlank()) {
                        rec.persistentTabId
                    } else {
                        "tab_migrated_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 8)}"
                    }

                    if (rec.isActive && activeId == null) {
                        activeId = id
                    }

                    if (!rec.tabGroupId.isNullOrBlank()) {
                        PetalTabMetadataStore.setGroup(
                            appContext,
                            id,
                            rec.tabGroupId,
                            rec.tabGroupTitle,
                            rec.tabGroupColorHex
                        )
                    }

                    val effectiveUrl = if (rec.url.isBlank() || isHomeUrl(rec.url)) "about:blank" else rec.url
                    val effectiveTitle = if (rec.title.isBlank() || isHomeUrl(rec.url)) "Petal" else rec.title

                    tabs.add(
                        TabSessionState(
                            id = id,
                            content = ContentState(
                                url = effectiveUrl,
                                title = effectiveTitle,
                                private = false
                            )
                        )
                    )
                }

                val finalActiveId = activeId ?: tabs.firstOrNull()?.id
                val migratedState = BrowserState(
                    tabs = tabs,
                    selectedTabId = finalActiveId
                )

                val storage = PetalEngineStore.getSessionStorage(appContext)
                storage.save(migratedState)
                Log.i(TAG, "Successfully migrated ${tabs.size} tabs into Mozilla SessionStorage")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Error during legacy session migration: ${t.message}", t)
        } finally {
            cleanLegacyData(appContext)
        }
    }

    private fun cleanLegacyData(context: Context) {
        try {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            sp.edit()
                .remove(PREF_SESSION_JSON_LEGACY)
                .remove(PREF_SESSION_JSON_BACKUP_LEGACY)
                .remove(PREF_OPEN_TABS_LEGACY)
                .apply()

            val repo = HistoryBookmarkRepository(context)
            repo.open(true)
            repo.clearSessionStateJson()
            repo.close()

            File(context.noBackupFilesDir, "petal_tab_state").deleteRecursively()
        } catch (t: Throwable) {
            Log.w(TAG, "Error cleaning legacy session data: ${t.message}")
        }
    }

    /**
     * Restores persisted tab session state into [BrowserStore].
     *
     * In a live process where [BrowserStore] already holds tabs, disk restoration is skipped
     * and this returns true immediately.
     */
    @JvmStatic
    fun restoreSession(activity: BrowserActivity): Boolean {
        val store = PetalEngineStore.getStore(activity)

        // Process is alive: reuse existing tabs in BrowserStore
        if (store.state.tabs.isNotEmpty()) {
            Log.i(TAG, "Process alive with ${store.state.tabs.size} tabs in BrowserStore. Reusing live tabs.")
            PetalEngineStore.markRestoreComplete(activity)
            return true
        }

        // Cold start: perform legacy migration if needed
        performLegacyMigrationIfNeeded(activity)

        val sp = PreferenceManager.getDefaultSharedPreferences(activity)
        val restoreTabsPref = sp.getBoolean(PREF_RESTORE_TABS, true)
        val reloadTabsPref = sp.getBoolean(PREF_RELOAD_TABS, false)
        val restoreOnRestart = sp.getBoolean(PREF_RESTORE_ON_RESTART, false)

        if (restoreOnRestart) {
            sp.edit().remove(PREF_RESTORE_ON_RESTART).apply()
        }

        if (!restoreTabsPref && !reloadTabsPref && !restoreOnRestart) {
            Log.i(TAG, "Tab restoration disabled by user preference")
            PetalEngineStore.markRestoreComplete(activity)
            return false
        }

        return try {
            val storage = PetalEngineStore.getSessionStorage(activity)
            val restoredState = storage.restore { !it.state.private }

            if (restoredState != null && restoredState.tabs.isNotEmpty()) {
                Log.i(TAG, "Restoring ${restoredState.tabs.size} tabs into empty BrowserStore")
                store.dispatch(
                    TabListAction.RestoreAction(
                        tabs = restoredState.tabs,
                        selectedTabId = restoredState.selectedTabId,
                        restoreLocation = TabListAction.RestoreAction.RestoreLocation.END
                    )
                )
                PetalEngineStore.markRestoreComplete(activity)
                // Thumbnails live exactly as long as their tab: drop files of tabs that no longer
                // exist (closed while the process was dead, crashes). Safe here: no tab has produced
                // a new thumbnail yet, and the keep-list is the restored tab list.
                try {
                    com.petal.browser.unit.TabThumbnailCache.retainOnly(
                        store.state.tabs.filter { !it.content.private }.map { it.id }
                    )
                } catch (_: Throwable) {}
                true
            } else {
                Log.i(TAG, "No recoverable tabs found in SessionStorage")
                PetalEngineStore.markRestoreComplete(activity)
                false
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to restore session from SessionStorage: ${t.message}", t)
            PetalEngineStore.markRestoreComplete(activity)
            false
        }
    }

    /**
     * Restores a single tab cleanly into the browser container at [targetIndex] without
     * forcibly hiding the tab switcher overlay if [keepOverviewOpen] is true.
     */
    @JvmStatic
    @JvmOverloads
    fun restoreTabSilently(
        activity: BrowserActivity,
        title: String?,
        url: String?,
        targetIndex: Int = -1,
        isIncognito: Boolean = false,
        groupId: String? = null,
        groupTitle: String? = null,
        groupColorHex: String? = null,
        keepOverviewOpen: Boolean = false
    ): AlbumController {
        val safeTitle = if (title.isNullOrBlank() || title == "Petal Home" || title == "about:blank") {
            activity.getString(R.string.app_name)
        } else {
            title
        }
        val safeUrl = if (url.isNullOrBlank() || url == "Petal Home" || url.equals("about:blank", ignoreCase = true)) {
            "about:blank"
        } else {
            url
        }

        val tabId = "tab_silently_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 8)}"

        if (!groupId.isNullOrBlank()) {
            PetalTabMetadataStore.setGroup(activity, tabId, groupId, groupTitle, groupColorHex)
        }

        val sessionPair = PetalEngineStore.createTabSession(
            context = activity,
            tabId = tabId,
            url = safeUrl,
            title = safeTitle,
            isIncognito = isIncognito,
            select = !keepOverviewOpen
        )

        val geckoView = PetalTabViewController(activity)
        geckoView.bindTab(sessionPair.first)
        geckoView.attachLifecycle(activity)
        activity.observePetalTabSurface(geckoView)
        if (!groupId.isNullOrBlank()) {
            geckoView.setTabGroupId(groupId)
            geckoView.setTabGroupTitle(groupTitle)
        }

        if (safeUrl.isNotBlank() && !isHomeUrl(safeUrl)) {
            geckoView.loadUrl(safeUrl)
        }

        if (targetIndex >= 0 && targetIndex <= BrowserContainer.size()) {
            BrowserContainer.add(geckoView, targetIndex)
        } else {
            BrowserContainer.add(geckoView)
        }

        if (!keepOverviewOpen) {
            geckoView.activate()
            activity.showAlbum(geckoView)
        } else {
            geckoView.deactivate()
        }

        return geckoView
    }

    /**
     * Exports session data and sidecar metadata for JSON backups.
     */
    @JvmStatic
    fun exportSessionForBackup(context: Context): String? {
        return try {
            val sessionFile = File(context.filesDir, "mozilla_components_session_storage_gecko.json")
            val acJson = if (sessionFile.exists() && sessionFile.length() > 0L) {
                sessionFile.readText(Charsets.UTF_8)
            } else {
                null
            }
            val metaJson = PetalTabMetadataStore.exportJson(context)
            val root = org.json.JSONObject()
            if (acJson != null) root.put("ac_session", acJson)
            root.put("metadata", metaJson)
            root.toString()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to export session for backup: ${t.message}", t)
            null
        }
    }

    /**
     * Imports session data and sidecar metadata from JSON backups.
     */
    @JvmStatic
    fun importSessionFromBackup(context: Context, payload: String?) {
        if (payload.isNullOrBlank()) return
        try {
            val trimmed = payload.trim()
            if (trimmed.startsWith("{") && trimmed.contains("\"ac_session\"")) {
                val root = org.json.JSONObject(trimmed)
                val acSession = root.optString("ac_session", null)
                val metadata = root.optString("metadata", null)
                if (!acSession.isNullOrBlank()) {
                    File(context.filesDir, "mozilla_components_session_storage_gecko.json")
                        .writeText(acSession, Charsets.UTF_8)
                }
                if (!metadata.isNullOrBlank()) {
                    PetalTabMetadataStore.importJson(context, metadata)
                }
            } else if (trimmed.startsWith("{") && trimmed.contains("\"tabs\"")) {
                File(context.filesDir, "mozilla_components_session_storage_gecko.json")
                    .writeText(trimmed, Charsets.UTF_8)
            } else if (trimmed.startsWith("[")) {
                val listType = object : TypeToken<List<LegacyTabRecord>>() {}.type
                val parsed: List<LegacyTabRecord>? = gson.fromJson(trimmed, listType)
                if (!parsed.isNullOrEmpty()) {
                    val tabs = mutableListOf<TabSessionState>()
                    var activeId: String? = null
                    for (rec in parsed.filter { !it.isIncognito }) {
                        val id = if (rec.persistentTabId.isNotBlank()) {
                            rec.persistentTabId
                        } else {
                            "tab_migrated_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 8)}"
                        }
                        if (rec.isActive && activeId == null) activeId = id
                        if (!rec.tabGroupId.isNullOrBlank()) {
                            PetalTabMetadataStore.setGroup(context, id, rec.tabGroupId, rec.tabGroupTitle, rec.tabGroupColorHex)
                        }
                        tabs.add(
                            TabSessionState(
                                id = id,
                                content = ContentState(
                                    url = if (rec.url.isBlank() || isHomeUrl(rec.url)) "about:blank" else rec.url,
                                    title = if (rec.title.isBlank() || isHomeUrl(rec.url)) "Petal" else rec.title,
                                    private = false
                                )
                            )
                        )
                    }
                    val migratedState = BrowserState(tabs = tabs, selectedTabId = activeId ?: tabs.firstOrNull()?.id)
                    PetalEngineStore.getSessionStorage(context).save(migratedState)
                }
            }

            PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(PREF_RESTORE_ON_RESTART, true)
                .apply()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to import session from backup: ${t.message}", t)
        }
    }

    /**
     * Clears persisted tab session storage and sidecar metadata.
     */
    @JvmStatic
    fun clearSession(context: Context?) {
        if (context == null) return
        try {
            val storage = PetalEngineStore.getSessionStorage(context)
            storage.save(BrowserState())
            PetalTabMetadataStore.clear(context)
            File(context.filesDir, "mozilla_components_session_storage_gecko.json").delete()
        } catch (t: Throwable) {
            Log.w(TAG, "Error clearing session storage", t)
        }
    }
}
