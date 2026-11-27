package com.petal.browser.unit

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.webkit.WebStorage
import androidx.preference.PreferenceManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.petal.browser.activity.MainActivity
import com.petal.browser.activity.SettingsActivity
import com.petal.browser.browser.PetalSessionRestoreManager
import com.petal.browser.browser.StandardDomainWhitelist
import com.petal.browser.compose.settings.SettingsCategory
import com.petal.browser.database.HistoryBookmarkContract
import com.petal.browser.database.HistoryBookmarkRepository
import com.petal.browser.download.PetalDownloadRouter
import com.petal.browser.download.PetalGeckoCookieBridge
import com.petal.browser.engine.gecko.PetalGeckoRuntime
import com.petal.browser.engine.gecko.PetalStorageManager
import com.petal.browser.logger.PetalAppLogger
import com.petal.browser.objects.CustomRedirect
import com.petal.browser.objects.CustomRedirectsHelper
import com.petal.browser.ui.components.PetalAiSearchBridge
import org.json.JSONArray
import org.mozilla.geckoview.StorageController
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

object PetalBrowserUtils {

    const val LOADING_STOPPED: Int = 101 // Must be > PROGRESS_MAX !
    const val MIME_TYPE_TEXT_PLAIN: String = "text/plain"
    const val URL_ENCODING: String = "UTF-8"
    const val PREF_PENDING_EXIT_CLEANUP: String = "sp_pending_exit_cleanup"

    private val isLiveSessionActive = AtomicBoolean(false)
    private val isPerformingClear = AtomicBoolean(false)
    private val hasClearedOnExit = AtomicBoolean(false)

    private val EXIT_EXECUTOR: ExecutorService = Executors.newSingleThreadExecutor { r ->
        val t = Thread(r, "PetalExitWorker")
        t.priority = Thread.MAX_PRIORITY
        t
    }

    @Volatile
    private var exitWorkFuture: Future<*>? = null

    private val DOMAIN_OR_IP_PATTERN = Pattern.compile(
        "^(?:(?:[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})|(?:\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})|(?:localhost))(?::\\d+)?(?:/.*)?$",
        Pattern.CASE_INSENSITIVE
    )

    @JvmStatic
    fun isURL(urlString: String?): Boolean {
        if (urlString.isNullOrBlank()) {
            return false
        }
        val trimmed = urlString.trim()
        if (trimmed.contains(" ")) {
            return false
        }

        return try {
            val uri = URI(trimmed)
            if (uri.scheme != null) {
                val scheme = uri.scheme.lowercase(Locale.ROOT)
                return scheme == "http" || scheme == "https" || scheme == "file" ||
                        scheme == "content" || scheme == "about" || scheme == "chrome" ||
                        scheme == "moz-extension" || scheme == "petal" || scheme == "javascript" ||
                        scheme == "view-source" || scheme == "mailto" || scheme == "data" ||
                        scheme == "blob" || scheme == "ftp" || scheme == "tel" ||
                        scheme == "sms" || scheme == "geo" || scheme == "intent"
            }

            DOMAIN_OR_IP_PATTERN.matcher(trimmed).matches()
        } catch (e: URISyntaxException) {
            false
        }
    }

    @JvmStatic
    fun queryWrapper(context: Context, query: String?): String {
        if (query.isNullOrBlank()) {
            return ""
        }
        var processed = query.trim()

        if (processed.contains(";jsessionid=")) {
            val tracking = processed.substring(processed.lastIndexOf(";"))
            processed = processed.replace(tracking, "")
        }

        if (isURL(processed)) {
            if (processed.startsWith("about:", ignoreCase = true) ||
                processed.startsWith("mailto:", ignoreCase = true) ||
                processed.startsWith("file:", ignoreCase = true) ||
                processed.startsWith("content:", ignoreCase = true) ||
                processed.startsWith("chrome://", ignoreCase = true) ||
                processed.startsWith("moz-extension://", ignoreCase = true) ||
                processed.startsWith("petal://", ignoreCase = true) ||
                processed.startsWith("view-source:", ignoreCase = true) ||
                processed.startsWith("data:", ignoreCase = true) ||
                processed.startsWith("blob:", ignoreCase = true) ||
                processed.startsWith("javascript:", ignoreCase = true) ||
                processed.startsWith("tel:", ignoreCase = true) ||
                processed.startsWith("sms:", ignoreCase = true) ||
                processed.startsWith("geo:", ignoreCase = true) ||
                processed.startsWith("intent:", ignoreCase = true) ||
                processed.startsWith("ftp:", ignoreCase = true)
            ) {
                return processed
            }
            if (!processed.contains("://")) {
                processed = if (processed.startsWith("localhost", ignoreCase = true) || processed.startsWith("127.0.0.1")) {
                    "http://$processed"
                } else {
                    "https://$processed"
                }
            }
            return processed
        } else {
            val trimmed = processed.trim()
            var prefix = ""
            var term = trimmed
            if (trimmed.startsWith("@") && trimmed.contains(" ")) {
                val firstSpace = trimmed.indexOf(" ")
                prefix = trimmed.substring(0, firstSpace).lowercase(Locale.ROOT)
                term = trimmed.substring(firstSpace + 1).trim()
            }

            val encodedQuery = try {
                URLEncoder.encode(term, URL_ENCODING).replace("+", "%20")
            } catch (e: Exception) {
                term.replace(" ", "%20")
            }

            if (prefix.isNotEmpty()) {
                when (prefix) {
                    "@g", "@google" -> return "https://www.google.com/search?client=firefox-b-m&q=$encodedQuery"
                    "@yt", "@youtube" -> return "https://m.youtube.com/results?search_query=$encodedQuery"
                    "@wiki", "@wikipedia" -> return "https://en.m.wikipedia.org/w/index.php?search=$encodedQuery"
                    "@gh", "@github" -> return "https://github.com/search?q=$encodedQuery"
                    "@reddit" -> return "https://www.reddit.com/search/?q=$encodedQuery"
                    "@ddg", "@duckduckgo" -> return "https://duckduckgo.com/?q=$encodedQuery"
                    "@b", "@bing" -> return "https://www.bing.com/search?q=$encodedQuery"
                    "@brave" -> return "https://search.brave.com/search?q=$encodedQuery"
                    "@ecosia" -> return "https://www.ecosia.org/search?q=$encodedQuery"
                    "@qwant" -> return "https://www.qwant.com/?q=$encodedQuery"
                    "@searx", "@searxng" -> return "https://searx.space/search?q=$encodedQuery"
                    "@sp", "@startpage" -> return "https://www.startpage.com/sp/search?query=$encodedQuery"
                }
            }

            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            val customSearchEngine = sp.getString("sp_search_engine_custom", "") ?: ""
            val customSearches = sp.getString("sp_search_customSearches", "") ?: ""

            return if (customSearches.isNotEmpty()) {
                customSearches + encodedQuery
            } else if (sp.getBoolean("searchEngineSwitch", false) && customSearchEngine.isNotEmpty()) {
                customSearchEngine + encodedQuery
            } else {
                val selectedEngine = sp.getString("sp_search_engine", "0") ?: "0"
                var i = 0
                try {
                    i = selectedEngine.toInt()
                } catch (e: Exception) {
                    PetalAppLogger.w("PetalBrowserUtils", "Failed to parse search engine index: $selectedEngine", e)
                }

                if (i >= 1000) {
                    val customJson = sp.getString("sp_custom_search_engines", "[]") ?: "[]"
                    try {
                        val engines = JSONArray(customJson)
                        for (n in 0 until engines.length()) {
                            val engine = engines.optJSONObject(n)
                            if (engine != null && engine.optInt("id", -1) == i) {
                                val template = engine.optString("url", "")
                                if (template.isNotEmpty()) {
                                    return template.replace("{searchTerms}", encodedQuery).replace("%s", encodedQuery)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        PetalAppLogger.w("PetalBrowserUtils", "Failed to parse custom search engines JSON", e)
                    }
                }

                when (i) {
                    0 -> "https://www.google.com/search?client=firefox-b-m&q=$encodedQuery"
                    1 -> "https://duckduckgo.com/?q=$encodedQuery"
                    2 -> "https://www.startpage.com/sp/search?query=$encodedQuery"
                    3 -> "https://search.brave.com/search?q=$encodedQuery"
                    4 -> "https://www.bing.com/search?q=$encodedQuery"
                    5 -> "https://searx.space/search?q=$encodedQuery"
                    6 -> "https://www.qwant.com/?q=$encodedQuery"
                    7 -> "https://www.ecosia.org/search?q=$encodedQuery"
                    8 -> "https://torrays.vercel.app/?q=$encodedQuery"
                    else -> "https://www.google.com/search?client=firefox-b-m&q=$encodedQuery"
                }
            }
        }
    }

    @JvmStatic
    fun isGoogleSearchQuery(context: Context, query: String?): Boolean {
        if (query.isNullOrBlank()) {
            return false
        }
        val trimmed = query.trim()
        if (isURL(trimmed)) {
            val lower = trimmed.lowercase(Locale.US)
            return lower.contains("google.com/search") || lower.contains("google.co.") || lower.contains("/search?")
        }
        if (trimmed.startsWith("@")) {
            return false
        }
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val customSearchEngine = sp.getString("sp_search_engine_custom", "") ?: ""
        val customSearches = sp.getString("sp_search_customSearches", "") ?: ""
        if (customSearches.isNotEmpty() || (sp.getBoolean("searchEngineSwitch", false) && customSearchEngine.isNotEmpty())) {
            return customSearches.contains("google.") || customSearchEngine.contains("google.")
        }
        val selectedEngine = sp.getString("sp_search_engine", "0")
        return "0" == selectedEngine
    }

    @JvmStatic
    fun download(context: Context?, url: String?, fileName: String?, mimeType: String?) {
        if (context == null || url.isNullOrBlank()) {
            return
        }
        PetalDownloadRouter.download(
            context, url, fileName, mimeType, null, false, null, null, emptyMap(), null, null
        )
    }

    @JvmStatic
    fun clearBookmark(context: Context) {
        val action = HistoryBookmarkRepository(context)
        action.open(true)
        action.clearTable(HistoryBookmarkContract.TABLE_BOOKMARK)
        action.close()
    }

    @JvmStatic
    fun clearHistory(context: Context) {
        val action = HistoryBookmarkRepository(context)
        action.open(true)
        action.clearTable(HistoryBookmarkContract.TABLE_HISTORY)
        action.close()
        try {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            sp.edit()
                .remove("sp_search_history_list")
                .remove("sp_search_history_queries")
                .remove("sp_removed_suggestions")
                .apply()
        } catch (_: Exception) {}
        // Firefox parity: clearing browsing history also purges recently closed tabs history.
        try {
            PetalRecentlyClosedManager.init(context)
            PetalRecentlyClosedManager.clear()
        } catch (_: Throwable) {}
    }

    @JvmStatic
    fun setLiveSessionActive(active: Boolean) {
        isLiveSessionActive.set(active)
    }

    @JvmStatic
    fun isLiveSessionActive(): Boolean = isLiveSessionActive.get()

    @JvmStatic
    fun resetClearOnExitState() {
        hasClearedOnExit.set(false)
        exitWorkFuture = null
    }

    @JvmStatic
    fun waitForExitCleanupCompletion(timeoutMs: Long) {
        val future = exitWorkFuture
        if (future != null && !future.isDone) {
            try {
                future.get(minOf(timeoutMs, 300L), TimeUnit.MILLISECONDS)
            } catch (e: Exception) {
                PetalAppLogger.w("PetalBrowserUtils", "Wait for exit cleanup interrupted/failed", e)
            }
        }
    }

    @JvmOverloads
    @JvmStatic
    fun clearOnExit(context: Context?, waitForCompletion: Boolean = false) {
        if (context == null) return
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val clearQuit = sp.getBoolean("sp_clear_quit", false) || sp.getBoolean("sp_clear_on_exit", false)
        val backupQuit = sp.getBoolean("sp_backup_quit", false)
        if (!clearQuit && !backupQuit) return

        if (!hasClearedOnExit.compareAndSet(false, true)) {
            if (waitForCompletion && exitWorkFuture != null) {
                try {
                    exitWorkFuture?.get(300, TimeUnit.MILLISECONDS)
                } catch (e: Exception) {
                    PetalAppLogger.w("PetalBrowserUtils", "Wait for exitWorkFuture timed out or failed", e)
                }
            }
            return
        }

        val appContext = context.applicationContext
        sp.edit().putBoolean(PREF_PENDING_EXIT_CLEANUP, true).commit()

        try {
            val workRequest = OneTimeWorkRequest.Builder(PetalExitWorkWorker::class.java).build()
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                PetalExitWorkWorker.WORK_NAME,
                ExistingWorkPolicy.KEEP,
                workRequest
            )
        } catch (t: Throwable) {
            PetalAppLogger.w("PetalBrowserUtils", "Failed to schedule exit work with WorkManager", t)
        }

        val task = Runnable {
            try {
                if (clearQuit) {
                    performClearOnExitInternal(appContext)
                }
                if (backupQuit) {
                    PetalBackupManager.backupData(appContext, 0)
                }
            } catch (t: Throwable) {
                PetalAppLogger.w("PetalBrowserUtils", "Error during clearOnExit background task", t)
            } finally {
                sp.edit().putBoolean(PREF_PENDING_EXIT_CLEANUP, false).commit()
            }
        }

        try {
            exitWorkFuture = EXIT_EXECUTOR.submit(task)
            if (waitForCompletion) {
                exitWorkFuture?.get(300, TimeUnit.MILLISECONDS)
            }
        } catch (e: Exception) {
            PetalAppLogger.w("PetalBrowserUtils", "Exit executor rejected task, running inline", e)
            task.run()
        }
    }

    @JvmStatic
    fun performClearOnExitInternal(context: Context?) {
        if (context == null) return
        if (!isPerformingClear.compareAndSet(false, true)) {
            return
        }
        try {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            val clearQuit = sp.getBoolean("sp_clear_quit", false) || sp.getBoolean("sp_clear_on_exit", false)
            if (!clearQuit) return

            val clearHistory = sp.getBoolean("sp_clear_history", false)
            val clearCache = sp.getBoolean("sp_clear_cache", false)

            if (clearHistory) {
                clearHistory(context)
                PetalSessionHistoryManager.clearSessionHistory(context)
                PetalSessionRestoreManager.clearSession(context)
            } else {
                PetalSessionHistoryManager.resetSession()
            }

            if (clearCache || clearQuit) {
                try {
                    PetalCacheManager.clearAllCache(context, null)
                } catch (exception: Exception) {
                    PetalAppLogger.w("browser", "Error clearing cache on exit", exception)
                }
            }

            try {
                PetalStorageManager.cleanEphemeralStorage(context)
                PetalStorageManager.optimizeDatabases(context)
            } catch (e: Exception) {
                PetalAppLogger.w("PetalBrowserUtils", "Error optimizing databases or cleaning storage", e)
            }
        } finally {
            isPerformingClear.set(false)
        }
    }

    @JvmStatic
    fun clearBrowserData(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val clearCache = sp.getBoolean("sp_clear_cache", false)
        val clearCookie = sp.getBoolean("sp_clear_cookie", false)
        val clearHistory = sp.getBoolean("sp_clear_history", false)
        val clearIndexedDB = sp.getBoolean("sp_clearIndexedDB", false)
        val clearDB = sp.getBoolean("sp_deleteDatabase", false)
        val clearSettings = sp.getBoolean("sp_clear_settings", false)

        if (clearHistory) clearHistory(context)
        if (clearCache) {
            try {
                PetalCacheManager.clearAllCache(context, null)
                PetalGeckoRuntime.clearData(
                    context,
                    StorageController.ClearFlags.ALL_CACHES or StorageController.ClearFlags.IMAGE_CACHE
                )
            } catch (exception: Exception) {
                Log.w("browser", "Error clearing cache", exception)
            }
        }
        if (clearSettings) {
            clearSettingsSafely(sp)
            val listStandard = StandardDomainWhitelist(context)
            listStandard.clearDomains()
        }
        if (clearCookie) {
            try {
                PetalGeckoRuntime.clearData(
                    context,
                    StorageController.ClearFlags.COOKIES or StorageController.ClearFlags.AUTH_SESSIONS
                )
            } catch (e: Exception) {
                PetalAppLogger.w("PetalBrowserUtils", "Error clearing cookies in GeckoRuntime", e)
            }
            PetalGeckoCookieBridge.clearAllCookies()
        }
        if (clearDB) {
            context.deleteDatabase("Ninja4.db")
            context.deleteDatabase("item_icon.db")
            context.deleteDatabase("favicon.db")
            sp.edit().putInt("restart_changed", 1).apply()
        }
        if (clearIndexedDB) {
            try {
                PetalGeckoRuntime.clearData(
                    context,
                    StorageController.ClearFlags.DOM_STORAGES or StorageController.ClearFlags.SITE_SETTINGS or StorageController.ClearFlags.SITE_DATA
                )
            } catch (e: Exception) {
                PetalAppLogger.w("PetalBrowserUtils", "Error clearing DOM storages in GeckoRuntime", e)
            }
            try {
                WebStorage.getInstance().deleteAllData()
            } catch (e: Exception) {
                PetalAppLogger.w("PetalBrowserUtils", "Error deleting WebStorage data", e)
            }
        }
        val clearPermissions = sp.getBoolean("sp_clear_permissions", false)
        if (clearPermissions) {
            try {
                PetalGeckoRuntime.clearData(context, StorageController.ClearFlags.PERMISSIONS)
            } catch (e: Exception) {
                PetalAppLogger.w("PetalBrowserUtils", "Error clearing permissions in GeckoRuntime", e)
            }
        }
        val clearAutofill = sp.getBoolean("sp_clear_autofill", false)
        if (clearAutofill) {
            try {
                PetalGeckoRuntime.clearData(context, StorageController.ClearFlags.AUTH_SESSIONS)
            } catch (e: Exception) {
                PetalAppLogger.w("PetalBrowserUtils", "Error clearing auth sessions in GeckoRuntime", e)
            }
        }
    }

    @JvmStatic
    fun clearSettingsSafely(sp: SharedPreferences?) {
        if (sp == null) return
        val all = sp.all
        val protectedMap = HashMap<String, Any?>()
        for ((key, value) in all) {
            if (key != null && (
                key.contains("api_key") ||
                key.contains("account") ||
                key.contains("auth") ||
                key.contains("token") ||
                key.contains("login") ||
                key.contains("pass") ||
                key.contains("credential") ||
                key.contains("sync") ||
                key.contains("email") ||
                key.contains("profile") ||
                key.startsWith("sp_gemini") ||
                key.startsWith("sp_openrouter") ||
                key.startsWith("sp_openai") ||
                key.startsWith("sp_grok") ||
                key.startsWith("sp_groq") ||
                key.startsWith("sp_google") ||
                key.startsWith("sp_user_") ||
                key.startsWith("sp_custom_font") ||
                key.startsWith("sp_custom_home_shortcuts") ||
                key == "sp_custom_search_engines" ||
                key == "sp_custom_user_scripts_json"
            )) {
                protectedMap[key] = value
            }
        }

        val editor = sp.edit().clear()
        for ((key, value) in protectedMap) {
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
            }
        }
        editor.apply()
        try {
            PetalGeckoRuntime.syncPreferences(sp)
        } catch (_: Throwable) {}
    }

    @JvmStatic
    fun eraseAllAppData(context: Context?) {
        if (context == null) return
        try {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            sp.edit().clear().apply()

            try {
                val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
                if (prefsDir.exists() && prefsDir.isDirectory) {
                    val prefFiles = prefsDir.listFiles()
                    if (prefFiles != null) {
                        for (f in prefFiles) {
                            f.delete()
                        }
                    }
                }
            } catch (e: Exception) {
                PetalAppLogger.w("PetalBrowserUtils", "Error deleting shared_prefs files", e)
            }

            try {
                context.deleteDatabase("Ninja4.db")
                context.deleteDatabase("item_icon.db")
                context.deleteDatabase("passwords.db")
                context.deleteDatabase("autofill.db")
                context.deleteDatabase("downloads.db")
                val dbDir = File(context.applicationInfo.dataDir, "databases")
                if (dbDir.exists() && dbDir.isDirectory) {
                    val dbs = dbDir.listFiles()
                    if (dbs != null) {
                        for (db in dbs) {
                            db.delete()
                        }
                    }
                }
            } catch (e: Exception) {
                PetalAppLogger.w("PetalBrowserUtils", "Error deleting database files", e)
            }

            try {
                PetalGeckoRuntime.clearData(
                    context,
                    StorageController.ClearFlags.COOKIES or
                    StorageController.ClearFlags.AUTH_SESSIONS or
                    StorageController.ClearFlags.DOM_STORAGES
                )
            } catch (e: Exception) {
                PetalAppLogger.w("PetalBrowserUtils", "Error clearing Gecko data", e)
            }
            PetalGeckoCookieBridge.clearAllCookies()
            try {
                WebStorage.getInstance().deleteAllData()
            } catch (e: Exception) {
                PetalAppLogger.w("PetalBrowserUtils", "Error deleting WebStorage data in eraseAppData", e)
            }

            try {
                PetalCacheManager.clearAllCache(context, null)
            } catch (e: Exception) {
                PetalAppLogger.w("PetalBrowserUtils", "Error clearing cache in eraseAppData", e)
            }

            try {
                deleteRecursive(context.cacheDir)
                deleteRecursive(context.codeCacheDir)
            } catch (e: Exception) {
                PetalAppLogger.w("PetalBrowserUtils", "Error clearing app cache/code cache dirs", e)
            }

            try {
                deleteRecursive(context.filesDir)
            } catch (e: Exception) {
                PetalAppLogger.w("PetalBrowserUtils", "Error clearing files dir", e)
            }

            try {
                val webviewDir = File(context.applicationInfo.dataDir, "app_webview")
                deleteRecursive(webviewDir)
            } catch (e: Exception) {
                PetalAppLogger.w("PetalBrowserUtils", "Error clearing app_webview dir", e)
            }
        } catch (e: Exception) {
            Log.e("PetalBrowserUtils", "Error erasing all app data", e)
        }
    }

    private fun deleteRecursive(fileOrDirectory: File?) {
        if (fileOrDirectory != null && fileOrDirectory.exists()) {
            if (fileOrDirectory.isDirectory) {
                val children = fileOrDirectory.listFiles()
                if (children != null) {
                    for (child in children) {
                        deleteRecursive(child)
                    }
                }
            }
            fileOrDirectory.delete()
        }
    }

    @JvmStatic
    fun intentURL(context: Context?, uri: Uri?) {
        if (context == null || uri == null) return
        val uriStr = uri.toString()
        if (uriStr.startsWith("petal://settings") || uriStr.startsWith("petal://credits") || uriStr.startsWith("petal://ai")) {
            if (context is Activity) {
                val activity = context
                if (uriStr.startsWith("petal://ai")) {
                    if (activity is MainActivity) {
                        val query = uri.getQueryParameter("q") ?: uri.getQueryParameter("query") ?: ""
                        PetalAiSearchBridge.showAiSearchResult(activity, query)
                    } else {
                        val intent = Intent(activity, MainActivity::class.java).apply {
                            action = Intent.ACTION_VIEW
                            data = uri
                        }
                        activity.startActivity(intent)
                    }
                    return
                }
                if (uriStr.startsWith("petal://settings")) {
                    if (activity is MainActivity) {
                        if (uriStr.contains("category=api_integrations") || uriStr.contains("category=ai_research")) {
                            activity.openApiIntegrationsHub()
                        } else {
                            activity.openSettingsScreen()
                        }
                    } else {
                        val intent = Intent(activity, SettingsActivity::class.java)
                        if (uriStr.contains("category=api_integrations") || uriStr.contains("category=ai_research")) {
                            intent.putExtra(
                                SettingsActivity.EXTRA_SETTINGS_CATEGORY,
                                SettingsCategory.API_INTEGRATIONS.name
                            )
                        }
                        activity.startActivity(intent)
                    }
                    return
                }
            }
        } else if (uriStr.startsWith("http://") || uriStr.startsWith("https://")) {
            if (context is MainActivity) {
                context.addAlbum(null, uriStr, true)
                return
            } else if (context is Activity) {
                val intent = Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = uri
                }
                context.startActivity(intent)
                return
            }
        }
    }

    @JvmStatic
    fun redirectURL(sp: SharedPreferences?, url: String?): String? {
        if (url == null || sp == null) return url
        try {
            val redirects: List<CustomRedirect> = CustomRedirectsHelper.getRedirects(sp)
            for (redirect in redirects) {
                val source = redirect.source
                val target = redirect.target
                if (source != null && target != null && url.contains(source) && (sp?.getBoolean(source, true) == true)) {
                    return url.replace(source, target)
                }
            }
        } catch (e: Exception) {
            Log.e("Redirect error", e.toString())
        }
        return url
    }

    @JvmStatic
    fun isHomePage(url: String?): Boolean {
        if (url.isNullOrBlank()) return true
        val clean = url.trim().lowercase(Locale.ROOT)
        return clean == "about:blank" || clean == "about:home" ||
                clean == "petal://home" || clean == "petal://start" ||
                clean.contains("petal_home.html") || clean.startsWith("file:///android_asset/")
    }

    @JvmStatic
    fun isCustomHomepageEnabled(context: Context?): Boolean {
        if (context == null) return false
        return try {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            val homeType = try {
                sp.getString("sp_home_type", "0") ?: "0"
            } catch (_: Exception) {
                try { sp.getInt("sp_home_type", 0).toString() } catch (_: Exception) { "0" }
            }
            "1" == homeType
        } catch (_: Exception) {
            false
        }
    }

    @JvmStatic
    @JvmOverloads
    fun getEffectiveHomepageUrl(context: Context?, sp: SharedPreferences? = null): String {
        try {
            val prefs = sp ?: (if (context != null) PreferenceManager.getDefaultSharedPreferences(context) else null)
            if (prefs != null) {
                val homeType = try {
                    prefs.getString("sp_home_type", "0") ?: "0"
                } catch (_: Exception) {
                    try { prefs.getInt("sp_home_type", 0).toString() } catch (_: Exception) { "0" }
                }
                if ("1" == homeType) {
                    val customUrl = prefs.getString("sp_custom_homepage_url", "https://google.com")
                    if (!customUrl.isNullOrBlank()) {
                        val trimmed = customUrl.trim()
                        val formatted = if (context != null) queryWrapper(context, trimmed) else (if (trimmed.contains("://") || trimmed.startsWith("about:")) trimmed else "https://$trimmed")
                        if (formatted.isNotBlank()) {
                            return formatted
                        }
                    }
                }
            }
        } catch (e: Exception) {
            PetalAppLogger.w("PetalBrowserUtils", "Error resolving effective homepage URL", e)
        }
        return "about:home"
    }

    private val TRACKING_PARAM_PREFIXES = listOf("utm_", "pk_", "piwik_")
    private val TRACKING_PARAM_NAMES = setOf(
        "fbclid", "gclid", "dclid", "gclsrc", "wbraid", "gbraid", "yclid",
        "msclkid", "igshid", "mc_cid", "mc_eid", "mkt_tok", "_hsenc", "_hsmi",
        "ref_src", "ref_url"
    )

    private fun isTrackingParam(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return TRACKING_PARAM_PREFIXES.any { lower.startsWith(it) } || lower in TRACKING_PARAM_NAMES
    }

    @JvmStatic
    fun cleanTrackingParams(url: String?): String? {
        if (url.isNullOrBlank()) return url
        return try {
            val queryStart = url.indexOf('?')
            if (queryStart < 0) return url
            var rawQuery = url.substring(queryStart + 1)
            val fragStart = rawQuery.indexOf('#')
            val fragment = if (fragStart >= 0) {
                val f = rawQuery.substring(fragStart)
                rawQuery = rawQuery.substring(0, fragStart)
                f
            } else ""
            if (rawQuery.isBlank()) return url

            val pairs = rawQuery.split('&')
            var hasTracking = false
            for (pair in pairs) {
                val key = pair.substringBefore('=')
                if (isTrackingParam(key)) {
                    hasTracking = true
                    break
                }
            }
            if (!hasTracking) return url

            val cleanPairs = pairs.filterNot { pair ->
                val key = pair.substringBefore('=')
                isTrackingParam(key)
            }
            val base = url.substring(0, queryStart)
            if (cleanPairs.isEmpty()) {
                base + fragment
            } else {
                base + "?" + cleanPairs.joinToString("&") + fragment
            }
        } catch (_: Exception) {
            url
        }
    }
}
