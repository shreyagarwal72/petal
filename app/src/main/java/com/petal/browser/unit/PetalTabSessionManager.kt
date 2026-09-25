package com.petal.browser.unit

import android.content.Context
import android.text.TextUtils
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.petal.browser.R
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.browser.AlbumController
import com.petal.browser.browser.BrowserContainer
import com.petal.browser.browser.PlaceholderAlbumController
import com.petal.browser.controller.BrowserWebViewController
import com.petal.browser.database.RecordAction
import com.petal.browser.view.PetalGeckoView
import java.util.Arrays
import java.util.concurrent.Executors

/**
 * Recreated, resilient Tab Session Restoration and Persistence system for Petal Browser.
 * 
 * Features:
 * - Persistent tab restoration on startup, system kill, and crash recovery.
 * - Non-blocking asynchronous snapshotting to SQLite & SharedPreferences.
 * - Fast lazy rehydration: active tab is instantiated immediately; background tabs
 *   are instantiated as lightweight [PlaceholderAlbumController]s.
 * - Full support for GeckoView standalone engine, Tab Groups, and custom titles.
 * - Full backward compatibility with legacy 'openTabs' delimiter ('‚‗‚') and JSON backups.
 * - Privacy guarantee: Incognito tabs are NEVER persisted to disk.
 */
object PetalTabSessionManager {

    private const val TAG = "PetalTabSession"
    private val executor = Executors.newSingleThreadExecutor()
    private val gson = Gson()

    const val PREF_RESTORE_TABS = "sp_restoreTabs"
    const val PREF_RELOAD_TABS = "sp_reloadTabs"
    const val PREF_RESTORE_ON_RESTART = "restoreOnRestart"
    const val PREF_SESSION_JSON = "tab_session_state_json"
    const val PREF_OPEN_TABS_LEGACY = "openTabs"
    const val DELIMITER_LEGACY = "‚‗‚"

    data class TabSessionRecord(
        val persistentTabId: String = "",
        val title: String = "",
        val url: String = "",
        val isIncognito: Boolean = false,
        val isActive: Boolean = false,
        val tabGroupId: String? = null,
        val tabGroupTitle: String? = null,
        val tabGroupColorHex: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    )

    @JvmStatic
    @JvmOverloads
    fun saveSession(context: Context?, currentController: AlbumController? = null) {
        if (context == null) return

        // Take snapshot of controllers on caller thread to prevent concurrent modification
        val controllers = try {
            BrowserContainer.list().toList()
        } catch (_: Exception) {
            emptyList()
        }

        if (controllers.isEmpty()) {
            clearSession(context)
            return
        }

        val active = currentController ?: controllers.firstOrNull()

        executor.execute {
            try {
                val records = mutableListOf<TabSessionRecord>()
                val legacyUrls = mutableListOf<String>()

                for (controller in controllers) {
                    // Privacy guarantee: NEVER persist incognito tabs
                    val isIncognito = controller.isIncognito

                    if (isIncognito) continue

                    val rawUrl = controller.url ?: ""
                    // Prefer persistentUrl for GeckoView tabs: currentUrl can be reset to
                    // "about:blank" while a page is loading (GeckoView compositor reset).
                    // persistentUrl holds the last real URL that was explicitly navigated to,
                    // so we never save "about:blank" when the tab actually has real content.
                    val effectiveUrl = when {
                        controller is PetalGeckoView &&
                        controller.persistentUrl.isNotBlank() &&
                        !controller.persistentUrl.equals("about:blank", ignoreCase = true) ->
                            controller.persistentUrl
                        rawUrl.isNotBlank() && !rawUrl.equals("about:blank", ignoreCase = true) ->
                            rawUrl
                        else -> rawUrl
                    }

                    val rawTitle = controller.title ?: ""
                    val tabId = when (controller) {
                        is PetalGeckoView -> controller.getTabId()
                        is PlaceholderAlbumController -> controller.getTabId()
                        else -> controller.hashCode().toString()
                    }
                    val groupId = when (controller) {
                        is PetalGeckoView -> controller.getTabGroupId()
                        is PlaceholderAlbumController -> controller.getTabGroupId()
                        else -> null
                    }
                    val groupTitle = when (controller) {
                        is PetalGeckoView -> controller.getTabGroupTitle()
                        is PlaceholderAlbumController -> controller.getTabGroupTitle()
                        else -> null
                    }
                    val isActiveTab = (controller == active)

                    val record = TabSessionRecord(
                        persistentTabId = tabId,
                        title = rawTitle,
                        url = effectiveUrl,   // use effectiveUrl — never saves transient about:blank
                        isIncognito = false,
                        isActive = isActiveTab,
                        tabGroupId = groupId,
                        tabGroupTitle = groupTitle,
                        timestamp = System.currentTimeMillis()
                    )
                    records.add(record)

                    if (effectiveUrl.isNotBlank()) {
                        legacyUrls.add(effectiveUrl)
                    }

                }

                val sp = PreferenceManager.getDefaultSharedPreferences(context)
                if (records.isEmpty()) {
                    sp.edit()
                        .remove(PREF_SESSION_JSON)
                        .remove(PREF_OPEN_TABS_LEGACY)
                        .apply()
                    try {
                        val action = RecordAction(context)
                        action.open(true)
                        action.clearSessionStateJson()
                        action.close()
                    } catch (_: Exception) {}
                    return@execute
                }

                val json = gson.toJson(records)

                // 1. Save JSON to SharedPreferences for fast, fail-safe access
                sp.edit()
                    .putString(PREF_SESSION_JSON, json)
                    .putString(PREF_OPEN_TABS_LEGACY, TextUtils.join(DELIMITER_LEGACY, legacyUrls))
                    .apply()

                // 2. Save JSON to SQLite RecordUnit.TABLE_SESSION for Backup & Restore integration
                try {
                    val action = RecordAction(context)
                    action.open(true)
                    action.saveSessionStateJson(json)
                    action.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to save session JSON to SQLite", e)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error saving tab session", e)
            }
        }
    }

    @JvmStatic
    fun loadSession(context: Context): List<TabSessionRecord> {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)

        // 1. Try SQLite Database first
        try {
            val action = RecordAction(context)
            action.open(false)
            val dbJson = action.getSessionStateJson()
            action.close()

            if (!dbJson.isNullOrBlank()) {
                val listType = object : TypeToken<List<TabSessionRecord>>() {}.type
                val loaded: List<TabSessionRecord>? = gson.fromJson(dbJson, listType)
                if (!loaded.isNullOrEmpty()) {
                    return loaded
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not load session from SQLite, falling back to SharedPreferences", e)
        }

        // 2. Fallback to SharedPreferences JSON
        val prefJson = sp.getString(PREF_SESSION_JSON, null)
        if (!prefJson.isNullOrBlank()) {
            try {
                val listType = object : TypeToken<List<TabSessionRecord>>() {}.type
                val loaded: List<TabSessionRecord>? = gson.fromJson(prefJson, listType)
                if (!loaded.isNullOrEmpty()) {
                    return loaded
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not parse session JSON from SharedPreferences", e)
            }
        }

        // 3. Fallback to legacy delimited openTabs string
        val legacyTabs = sp.getString(PREF_OPEN_TABS_LEGACY, null)
        if (!legacyTabs.isNullOrBlank()) {
            val urls = Arrays.asList(*TextUtils.split(legacyTabs, DELIMITER_LEGACY))
            if (urls.isNotEmpty()) {
                return urls.filter { !it.isNullOrBlank() }.mapIndexed { index, url ->
                    TabSessionRecord(
                        persistentTabId = "tab_legacy_$index",
                        title = "",
                        url = url,
                        isIncognito = false,
                        isActive = (index == 0)
                    )
                }
            }
        }

        return emptyList()
    }

    @JvmStatic
    fun restoreSession(activity: BrowserActivity): Boolean {
        try {
            val sp = PreferenceManager.getDefaultSharedPreferences(activity)

            val restoreTabsPref = sp.getBoolean(PREF_RESTORE_TABS, true)
            val reloadTabsPref = sp.getBoolean(PREF_RELOAD_TABS, false)
            val restoreOnRestart = sp.getBoolean(PREF_RESTORE_ON_RESTART, false)

            if (!restoreTabsPref && !reloadTabsPref && !restoreOnRestart) {
                return false
            }

            val savedRecords = loadSession(activity)
            if (savedRecords.isEmpty()) {
                return false
            }

            var activeIndex = savedRecords.indexOfFirst { it.isActive }
            if (activeIndex < 0) activeIndex = 0

            var activeGeckoView: PetalGeckoView? = null

            for (i in savedRecords.indices) {
                val record = savedRecords[i]
                val isForeground = (i == activeIndex)

                if (isForeground) {
                    val tabId = record.persistentTabId.ifBlank { "tab_restored_${System.currentTimeMillis()}_$i" }
                    val sessionPair = com.petal.browser.engine.gecko.PetalEngineStore.createTabSession(
                        context = activity,
                        tabId = tabId,
                        url = record.url.ifBlank { "about:blank" },
                        title = record.title.ifBlank { activity.getString(R.string.app_name) },
                        isIncognito = false,
                        select = true
                    )

                    val geckoView = BrowserWebViewController.createAndConfigureGeckoView(
                        activity = activity,
                        title = record.title.ifBlank { activity.getString(R.string.app_name) },
                        url = record.url,
                        foreground = true,
                        isIncognito = false,
                        adoptedSession = null,
                        engineSession = sessionPair.second
                    )

                    geckoView.setTabId(tabId)
                    geckoView.setBrowserController(activity)

                    if (record.title.isNotBlank()) {
                        geckoView.setAlbumTitle(record.title, record.url)
                    }
                    if (!record.tabGroupId.isNullOrBlank()) {
                        geckoView.setTabGroupId(record.tabGroupId)
                        geckoView.setTabGroupTitle(record.tabGroupTitle)
                    }

                    // Fix (Bug 5): for home-URL tabs, do NOT call geckoView.loadUrl("about:blank").
                    // That redundant load triggers GeckoView's page lifecycle callbacks which race
                    // with showAlbum() — causing the Compose home surface to be torn down and rebuilt
                    // mid-render, resulting in a blank screen. showAlbum("about:blank") will display
                    // the native Compose home without any web engine load required.
                    if (record.url.isNotBlank() && !isHomeUrl(record.url)) {
                        // Check if Mozilla EngineSession has an underlying restored SessionState
                        val restoredEngineSession = sessionPair.second
                        val engineHasState = try {
                            val stateField = restoredEngineSession.javaClass.methods.firstOrNull {
                                it.parameterCount == 0 && (it.name == "hasSessionState" || it.name == "isRestored")
                            }
                            stateField?.invoke(restoredEngineSession) as? Boolean ?: false
                        } catch (_: Throwable) {
                            false
                        }

                        if (!engineHasState) {
                            geckoView.loadUrl(record.url)
                        }
                    }
                    // else: leave GeckoView unloaded; showAlbum() will display the Compose home

                    BrowserContainer.add(geckoView)
                    activeGeckoView = geckoView
                } else {
                    val placeholder = PlaceholderAlbumController(
                        activity,
                        record.title.ifBlank { activity.getString(R.string.app_name) },
                        record.url.ifBlank { "about:blank" },
                        null,
                        record.persistentTabId.ifBlank { "tab_restored_${System.currentTimeMillis()}_$i" },
                        record.tabGroupId,
                        record.tabGroupTitle,
                        false
                    )
                    BrowserContainer.add(placeholder)
                }
            }

            if (activeGeckoView != null) {
                activity.showAlbum(activeGeckoView)
            } else if (BrowserContainer.size() > 0) {
                activity.showAlbum(BrowserContainer.get(0))
            }

            // Consume one-shot restart restoration flag
            if (restoreOnRestart) {
                sp.edit().putBoolean(PREF_RESTORE_ON_RESTART, false).apply()
            }

            return BrowserContainer.size() > 0

        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore tab session", e)
            return false
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

        val tabId = "tab_silently_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().substring(0, 8)}"
        val sessionPair = com.petal.browser.engine.gecko.PetalEngineStore.createTabSession(
            context = activity,
            tabId = tabId,
            url = safeUrl,
            title = safeTitle,
            isIncognito = isIncognito,
            select = !keepOverviewOpen
        )

        val geckoView = BrowserWebViewController.createAndConfigureGeckoView(
            activity = activity,
            title = safeTitle,
            url = safeUrl,
            foreground = !keepOverviewOpen,
            isIncognito = isIncognito,
            adoptedSession = null,
            engineSession = sessionPair.second
        )
        geckoView.setTabId(tabId)
        geckoView.setBrowserController(activity)
        geckoView.setAlbumTitle(safeTitle, safeUrl)
        if (!groupId.isNullOrBlank()) {
            geckoView.setTabGroupId(groupId)
            geckoView.setTabGroupTitle(groupTitle)
        }

        // Fix (Bug 5): same as restoreSession — don't load about:blank for home tabs.
        if (safeUrl.isNotBlank() && !isHomeUrl(safeUrl)) {
            val restoredEngineSession = sessionPair.second
            val engineHasState = try {
                val stateField = restoredEngineSession.javaClass.methods.firstOrNull {
                    it.parameterCount == 0 && (it.name == "hasSessionState" || it.name == "isRestored")
                }
                stateField?.invoke(restoredEngineSession) as? Boolean ?: false
            } catch (_: Throwable) {
                false
            }

            if (!engineHasState) {
                geckoView.loadUrl(safeUrl)
            }
        }
        // else: leave GeckoView unloaded; showAlbum() will display the Compose home

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

        saveSession(activity, if (keepOverviewOpen) null else geckoView)
        return geckoView
    }

    @JvmStatic
    fun clearSession(context: Context?) {
        if (context == null) return
        executor.execute {
            try {
                val sp = PreferenceManager.getDefaultSharedPreferences(context)
                sp.edit()
                    .remove(PREF_SESSION_JSON)
                    .remove(PREF_OPEN_TABS_LEGACY)
                    .apply()

                val action = RecordAction(context)
                action.open(true)
                action.clearSessionStateJson()
                action.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error clearing session storage", e)
            }
        }
    }

    private fun isHomeUrl(url: String?): Boolean {
        if (url == null || url.trim().isEmpty()) return true
        val clean = url.trim().lowercase(java.util.Locale.ROOT)
        return clean == "about:blank" ||
                clean == "about:home" ||
                clean == "petal://home" ||
                clean == "petal://start" ||
                clean.contains("petal_home.html") ||
                clean.startsWith("file:///android_asset/")
    }
}
