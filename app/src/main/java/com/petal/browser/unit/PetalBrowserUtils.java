package com.petal.browser.unit;

import com.petal.browser.view.PetalToast;
import static android.content.ContentValues.TAG;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.Settings;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import com.petal.browser.logger.PetalAppLogger;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONException;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;

import java.util.ArrayList;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import com.petal.browser.R;
import com.petal.browser.activity.BrowserActivity;
import com.petal.browser.browser.StandardDomainWhitelist;
import com.petal.browser.database.HistoryBookmarkContract;
import com.petal.browser.database.HistoryBookmarkRepository;
import com.petal.browser.objects.CustomRedirect;
import com.petal.browser.objects.CustomRedirectsHelper;

public class PetalBrowserUtils {

    public static final int LOADING_STOPPED = 101;  //Must be > PROGRESS_MAX !
    public static final String MIME_TYPE_TEXT_PLAIN = "text/plain";
    public static final String URL_ENCODING = "UTF-8";

    public static boolean isURL(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) {
            return false;
        }

        urlString = urlString.trim();
        // If string contains unencoded spaces, it's a search query, not a direct URL
        if (urlString.contains(" ")) {
            return false;
        }

        try {
            URI uri = new URI(urlString);

            // Fall 1: Die URL hat bereits ein explizites Schema
            if (uri.getScheme() != null) {
                String scheme = uri.getScheme().toLowerCase();
                // Erlaubt Web-Links sowie lokale Datei- und Inhalts-Pfade von Android
                return "http".equals(scheme) || "https".equals(scheme) || "file".equals(scheme) || "content".equals(scheme) || "about".equals(scheme) || "chrome".equals(scheme) || "moz-extension".equals(scheme) || "petal".equals(scheme);
            }

            // Fall 2: Die Eingabe hat kein Schema (z.B. "google.com")
            Pattern domainPattern = Pattern.compile("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$");
            if (domainPattern.matcher(urlString).matches()) {
                URI fallbackUri = new URI("https://" + urlString);
                return fallbackUri.getHost() != null && fallbackUri.getHost().contains(".");
            }

            return false;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public static String queryWrapper(Context context, String query) {
        if (query == null || query.trim().isEmpty()) {
            return "";
        }
        query = query.trim();

        if (query.contains(";jsessionid=")) {
            String tracking = query.substring(query.lastIndexOf(";"));
            query = query.replace(tracking, "");
        }

        if (isURL(query)) {
            if (query.startsWith("about:") || query.startsWith("mailto:") || query.startsWith("file:") || query.startsWith("content:") || query.startsWith("chrome://") || query.startsWith("moz-extension://") || query.startsWith("petal://")) {
                return query;
            }
            if (!query.contains("://")) {
                query = "https://" + query;
            }
            return query;
        } else {
            String trimmed = query.trim();
            String prefix = "";
            String term = trimmed;
            if (trimmed.startsWith("@") && trimmed.contains(" ")) {
                int firstSpace = trimmed.indexOf(" ");
                prefix = trimmed.substring(0, firstSpace).toLowerCase();
                term = trimmed.substring(firstSpace + 1).trim();
            }

            String encodedQuery;
            try {
                encodedQuery = URLEncoder.encode(term, "UTF-8");
            } catch (Exception e) {
                encodedQuery = term.replace(" ", "+");
            }

            if (!prefix.isEmpty()) {
                switch (prefix) {
                    case "@g":
                    case "@google":
                        return "https://www.google.com/search?client=firefox-b-m&q=" + encodedQuery;
                    case "@yt":
                    case "@youtube":
                        return "https://m.youtube.com/results?search_query=" + encodedQuery;
                    case "@wiki":
                    case "@wikipedia":
                        return "https://en.m.wikipedia.org/w/index.php?search=" + encodedQuery;
                    case "@gh":
                    case "@github":
                        return "https://github.com/search?q=" + encodedQuery;
                    case "@reddit":
                        return "https://www.reddit.com/search/?q=" + encodedQuery;
                    case "@ddg":
                    case "@duckduckgo":
                        return "https://duckduckgo.com/?q=" + encodedQuery;
                    case "@b":
                    case "@bing":
                        return "https://www.bing.com/search?q=" + encodedQuery;
                    case "@brave":
                        return "https://search.brave.com/search?q=" + encodedQuery;
                    case "@ecosia":
                        return "https://www.ecosia.org/search?q=" + encodedQuery;
                    case "@qwant":
                        return "https://www.qwant.com/?q=" + encodedQuery;
                    case "@searx":
                    case "@searxng":
                        return "https://searx.space/search?q=" + encodedQuery;
                    case "@sp":
                    case "@startpage":
                        return "https://www.startpage.com/sp/search?query=" + encodedQuery;
                }
            }

            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            String customSearchEngine = sp.getString("sp_search_engine_custom", "");
            String customSearches = sp.getString("sp_search_customSearches", "");

            if (!customSearches.isEmpty()) {
                return customSearches + encodedQuery;
            } else if (sp.getBoolean("searchEngineSwitch", false) && !customSearchEngine.isEmpty()) {
                return customSearchEngine + encodedQuery;
            } else {
                String selectedEngine = sp.getString("sp_search_engine", "0");
                int i = 0;
                try {
                    i = Integer.parseInt(selectedEngine);
                } catch (Exception e) {
                    PetalAppLogger.w("PetalBrowserUtils", "Failed to parse search engine index: " + selectedEngine, e);
                }

                // Custom engines use ids >= 1000 and are stored as a small JSON array.
                if (i >= 1000) {
                    String customJson = sp.getString("sp_custom_search_engines", "[]");
                    try {
                        org.json.JSONArray engines = new org.json.JSONArray(customJson);
                        for (int n = 0; n < engines.length(); n++) {
                            org.json.JSONObject engine = engines.optJSONObject(n);
                            if (engine != null && engine.optInt("id", -1) == i) {
                                String template = engine.optString("url", "");
                                if (!template.isEmpty()) {
                                    return template.replace("{searchTerms}", encodedQuery).replace("%s", encodedQuery);
                                }
                            }
                        }
                    } catch (Exception e) {
                        PetalAppLogger.w("PetalBrowserUtils", "Failed to parse custom search engines JSON", e);
                    }
                }

                switch (i) {
                    case 0: return "https://www.google.com/search?client=firefox-b-m&q=" + encodedQuery;
                    case 1: return "https://duckduckgo.com/?q=" + encodedQuery;
                    case 2: return "https://www.startpage.com/sp/search?query=" + encodedQuery;
                    case 3: return "https://search.brave.com/search?q=" + encodedQuery;
                    case 4: return "https://www.bing.com/search?q=" + encodedQuery;
                    case 5: return "https://searx.space/search?q=" + encodedQuery;
                    case 6: return "https://www.qwant.com/?q=" + encodedQuery;
                    case 7: return "https://www.ecosia.org/search?q=" + encodedQuery;
                    case 8: return "https://torrays.vercel.app/?q=" + encodedQuery;
                    default: return "https://www.google.com/search?client=firefox-b-m&q=" + encodedQuery;
                }
            }
        }
    }

    public static boolean isGoogleSearchQuery(Context context, String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }
        String trimmed = query.trim();
        if (isURL(trimmed)) {
            String lower = trimmed.toLowerCase(Locale.US);
            return lower.contains("google.com/search") || lower.contains("google.co.") || lower.contains("/search?");
        }
        if (trimmed.startsWith("@")) {
            return false;
        }
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String customSearchEngine = sp.getString("sp_search_engine_custom", "");
        String customSearches = sp.getString("sp_search_customSearches", "");
        if (!customSearches.isEmpty() || (sp.getBoolean("searchEngineSwitch", false) && !customSearchEngine.isEmpty())) {
            return customSearches.contains("google.") || customSearchEngine.contains("google.");
        }
        String selectedEngine = sp.getString("sp_search_engine", "0");
        return "0".equals(selectedEngine);
    }

    public static void download(final Context context, final String url, final String fileName, final String mimeType) {
        if (context == null || url == null || url.trim().isEmpty()) {
            return;
        }
        com.petal.browser.download.PetalDownloadRouter.download(
                context, url, fileName, mimeType, null, false, null, null, java.util.Collections.emptyMap(), null, null
        );
    }

    public static void clearBookmark(Context context) {
        HistoryBookmarkRepository action = new HistoryBookmarkRepository(context);
        action.open(true);
        action.clearTable(HistoryBookmarkContract.TABLE_BOOKMARK);
        action.close();
    }

    public static void clearHistory(Context context) {
        HistoryBookmarkRepository action = new HistoryBookmarkRepository(context);
        action.open(true);
        action.clearTable(HistoryBookmarkContract.TABLE_HISTORY);
        action.close();
    }

    public static final String PREF_PENDING_EXIT_CLEANUP = "sp_pending_exit_cleanup";
    private static final java.util.concurrent.atomic.AtomicBoolean isLiveSessionActive = new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final java.util.concurrent.atomic.AtomicBoolean isPerformingClear = new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final java.util.concurrent.atomic.AtomicBoolean hasClearedOnExit = new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final java.util.concurrent.ExecutorService EXIT_EXECUTOR = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "PetalExitWorker");
        t.setPriority(Thread.MAX_PRIORITY);
        return t;
    });
    private static volatile java.util.concurrent.Future<?> exitWorkFuture = null;

    public static void setLiveSessionActive(boolean active) {
        isLiveSessionActive.set(active);
    }

    public static boolean isLiveSessionActive() {
        return isLiveSessionActive.get();
    }

    public static void resetClearOnExitState() {
        hasClearedOnExit.set(false);
        exitWorkFuture = null;
    }

    public static void waitForExitCleanupCompletion(long timeoutMs) {
        java.util.concurrent.Future<?> future = exitWorkFuture;
        if (future != null && !future.isDone()) {
            try {
                future.get(Math.min(timeoutMs, 300L), java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                PetalAppLogger.w("PetalBrowserUtils", "Wait for exit cleanup interrupted/failed", e);
            }
        }
    }

    public static void clearOnExit(Context context) {
        clearOnExit(context, false);
    }

    public static void clearOnExit(Context context, boolean waitForCompletion) {
        if (context == null) return;
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        boolean clearQuit = sp.getBoolean("sp_clear_quit", false) || sp.getBoolean("sp_clear_on_exit", false);
        boolean backupQuit = sp.getBoolean("sp_backup_quit", false);
        if (!clearQuit && !backupQuit) return;

        if (!hasClearedOnExit.compareAndSet(false, true)) {
            // Already scheduled or completed
            if (waitForCompletion && exitWorkFuture != null) {
                try {
                    exitWorkFuture.get(300, java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    PetalAppLogger.w("PetalBrowserUtils", "Wait for exitWorkFuture timed out or failed", e);
                }
            }
            return;
        }

        final Context appContext = context.getApplicationContext();

        // Persist marker before starting executor path
        sp.edit().putBoolean(PREF_PENDING_EXIT_CLEANUP, true).commit();

        // Enqueue WorkManager job as insurance in case the process is killed before the background thread finishes
        try {
            androidx.work.OneTimeWorkRequest workRequest = new androidx.work.OneTimeWorkRequest.Builder(PetalExitWorkWorker.class).build();
            androidx.work.WorkManager.getInstance(appContext).enqueueUniqueWork(
                PetalExitWorkWorker.WORK_NAME,
                androidx.work.ExistingWorkPolicy.KEEP,
                workRequest
            );
        } catch (Throwable t) {
            com.petal.browser.logger.PetalAppLogger.w("PetalBrowserUtils", "Failed to schedule exit work with WorkManager", t);
        }

        Runnable task = () -> {
            try {
                if (clearQuit) {
                    performClearOnExitInternal(appContext);
                }
                if (backupQuit) {
                    com.petal.browser.unit.PetalBackupManager.backupData(appContext, 0);
                }
            } catch (Throwable t) {
                com.petal.browser.logger.PetalAppLogger.w("PetalBrowserUtils", "Error during clearOnExit background task", t);
            } finally {
                sp.edit().putBoolean(PREF_PENDING_EXIT_CLEANUP, false).commit();
            }
        };

        try {
            exitWorkFuture = EXIT_EXECUTOR.submit(task);
            if (waitForCompletion) {
                exitWorkFuture.get(300, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            com.petal.browser.logger.PetalAppLogger.w("PetalBrowserUtils", "Exit executor rejected task, running inline", e);
            task.run();
        }
    }

    public static void performClearOnExitInternal(Context context) {
        if (context == null) return;
        if (!isPerformingClear.compareAndSet(false, true)) {
            return;
        }
        try {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            boolean clearQuit = sp.getBoolean("sp_clear_quit", false) || sp.getBoolean("sp_clear_on_exit", false);
            if (!clearQuit) return;

            boolean clearHistory = sp.getBoolean("sp_clear_history", false);
            boolean clearCache = sp.getBoolean("sp_clear_cache", false);

            if (clearHistory) {
                clearHistory(context);
                PetalSessionHistoryManager.clearSessionHistory(context);
                com.petal.browser.browser.PetalSessionRestoreManager.clearSession(context);
            } else {
                PetalSessionHistoryManager.resetSession();
            }

            if (clearCache || clearQuit) {
                try {
                    PetalCacheManager.clearAllCache(context, null);
                } catch (Exception exception) {
                    com.petal.browser.logger.PetalAppLogger.w("browser", "Error clearing cache on exit", exception);
                }
            }

            try {
                com.petal.browser.engine.gecko.PetalStorageManager.cleanEphemeralStorage(context);
                com.petal.browser.engine.gecko.PetalStorageManager.optimizeDatabases(context);
            } catch (Exception e) {
                PetalAppLogger.w("PetalBrowserUtils", "Error optimizing databases or cleaning storage", e);
            }
        } finally {
            isPerformingClear.set(false);
        }
    }

    public static void clearBrowserData(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        boolean clearCache = sp.getBoolean("sp_clear_cache", false);
        boolean clearCookie = sp.getBoolean("sp_clear_cookie", false);
        boolean clearHistory = sp.getBoolean("sp_clear_history", false);
        boolean clearIndexedDB = sp.getBoolean("sp_clearIndexedDB", false);
        boolean clearDB = sp.getBoolean("sp_deleteDatabase", false);
        boolean clearSettings = sp.getBoolean("sp_clear_settings", false);

        if (clearHistory) clearHistory(context);
        if (clearCache)  {
            try {
                PetalCacheManager.clearAllCache(context, null);
            } catch (Exception exception) {
                Log.w("browser", "Error clearing cache", exception);
            }
        }
        if (clearSettings) {
            clearSettingsSafely(sp);
            StandardDomainWhitelist listStandard = new StandardDomainWhitelist(context);
            listStandard.clearDomains();
        }
        // ONLY clear cookies and logins if the user explicitly commanded manual cookie deletion
        if (clearCookie) {
            try {
                com.petal.browser.engine.gecko.PetalGeckoRuntime.clearData(
                    context,
                    org.mozilla.geckoview.StorageController.ClearFlags.COOKIES |
                    org.mozilla.geckoview.StorageController.ClearFlags.AUTH_SESSIONS
                );
            } catch (Exception e) {
                PetalAppLogger.w("PetalBrowserUtils", "Error clearing cookies in GeckoRuntime", e);
            }
            com.petal.browser.download.PetalGeckoCookieBridge.clearAllCookies();
        }
        if (clearDB) {
            context.deleteDatabase("Ninja4.db");
            context.deleteDatabase("item_icon.db");
            sp.edit().putInt("restart_changed", 1).apply();
        }
        if (clearIndexedDB) {
            try {
                com.petal.browser.engine.gecko.PetalGeckoRuntime.clearData(
                    context,
                    org.mozilla.geckoview.StorageController.ClearFlags.DOM_STORAGES
                );
            } catch (Exception e) {
                PetalAppLogger.w("PetalBrowserUtils", "Error clearing DOM storages in GeckoRuntime", e);
            }
            try {
                WebStorage.getInstance().deleteAllData();
            } catch (Exception e) {
                PetalAppLogger.w("PetalBrowserUtils", "Error deleting WebStorage data", e);
            }
        }
    }

    public static void clearSettingsSafely(SharedPreferences sp) {
        if (sp == null) return;
        java.util.Map<String, ?> all = sp.getAll();
        java.util.Map<String, Object> protectedMap = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, ?> entry : all.entrySet()) {
            String key = entry.getKey();
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
                key.startsWith("sp_custom_")
            )) {
                protectedMap.put(key, entry.getValue());
            }
        }

        SharedPreferences.Editor editor = sp.edit().clear();
        for (java.util.Map.Entry<String, Object> entry : protectedMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof String) {
                editor.putString(key, (String) value);
            } else if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
            } else if (value instanceof Integer) {
                editor.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                editor.putLong(key, (Long) value);
            } else if (value instanceof Float) {
                editor.putFloat(key, (Float) value);
            }
        }
        editor.apply();
    }

    /**
     * Completely erases all app data (shared preferences, databases, cache, web storage, cookies, files).
     * Used for total factory reset when recovering from forgotten app lock password.
     */
    public static void eraseAllAppData(Context context) {
        if (context == null) return;
        try {
            // 1. Clear SharedPreferences completely
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            sp.edit().clear().apply();

            // Clear any other shared preference files
            try {
                File prefsDir = new File(context.getApplicationInfo().dataDir, "shared_prefs");
                if (prefsDir.exists() && prefsDir.isDirectory()) {
                    File[] prefFiles = prefsDir.listFiles();
                    if (prefFiles != null) {
                        for (File f : prefFiles) {
                            f.delete();
                        }
                    }
                }
            } catch (Exception e) {
                PetalAppLogger.w("PetalBrowserUtils", "Error deleting shared_prefs files", e);
            }

            // 2. Clear Databases (Bookmarks, History, Webview DBs)
            try {
                context.deleteDatabase("Ninja4.db");
                context.deleteDatabase("item_icon.db");
                context.deleteDatabase("passwords.db");
                context.deleteDatabase("autofill.db");
                context.deleteDatabase("downloads.db");
                File dbDir = new File(context.getApplicationInfo().dataDir, "databases");
                if (dbDir.exists() && dbDir.isDirectory()) {
                    File[] dbs = dbDir.listFiles();
                    if (dbs != null) {
                        for (File db : dbs) {
                            db.delete();
                        }
                    }
                }
            } catch (Exception e) {
                PetalAppLogger.w("PetalBrowserUtils", "Error deleting database files", e);
            }

            // 3. Clear Cookies and WebStorage
            try {
                com.petal.browser.engine.gecko.PetalGeckoRuntime.clearData(
                    context,
                    org.mozilla.geckoview.StorageController.ClearFlags.COOKIES |
                    org.mozilla.geckoview.StorageController.ClearFlags.AUTH_SESSIONS |
                    org.mozilla.geckoview.StorageController.ClearFlags.DOM_STORAGES
                );
            } catch (Exception e) {
                PetalAppLogger.w("PetalBrowserUtils", "Error clearing Gecko data", e);
            }
            com.petal.browser.download.PetalGeckoCookieBridge.clearAllCookies();
            try {
                WebStorage.getInstance().deleteAllData();
            } catch (Exception e) {
                PetalAppLogger.w("PetalBrowserUtils", "Error deleting WebStorage data in eraseAppData", e);
            }

            // 4. Clear Caches
            try {
                PetalCacheManager.clearAllCache(context, null);
            } catch (Exception e) {
                PetalAppLogger.w("PetalBrowserUtils", "Error clearing cache in eraseAppData", e);
            }

            // 5. Clear App Cache and Code Cache directories
            try {
                File cacheDir = context.getCacheDir();
                deleteRecursive(cacheDir);
                File codeCacheDir = context.getCodeCacheDir();
                deleteRecursive(codeCacheDir);
            } catch (Exception e) {
                PetalAppLogger.w("PetalBrowserUtils", "Error clearing app cache/code cache dirs", e);
            }

            // 6. Clear App Internal Files directory (excluding lib)
            try {
                File filesDir = context.getFilesDir();
                deleteRecursive(filesDir);
            } catch (Exception e) {
                PetalAppLogger.w("PetalBrowserUtils", "Error clearing files dir", e);
            }

            // 7. Clear WebView internal app_webview data
            try {
                File webviewDir = new File(context.getApplicationInfo().dataDir, "app_webview");
                deleteRecursive(webviewDir);
            } catch (Exception e) {
                PetalAppLogger.w("PetalBrowserUtils", "Error clearing app_webview dir", e);
            }
        } catch (Exception e) {
            Log.e("PetalBrowserUtils", "Error erasing all app data", e);
        }
    }

    private static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory != null && fileOrDirectory.exists()) {
            if (fileOrDirectory.isDirectory()) {
                File[] children = fileOrDirectory.listFiles();
                if (children != null) {
                    for (File child : children) {
                        deleteRecursive(child);
                    }
                }
            }
            fileOrDirectory.delete();
        }
    }

    public static void intentURL(Context context, Uri uri) {
        if (context == null || uri == null) return;
        String uriStr = uri.toString();
        if (uriStr.startsWith("petal://flags") || uriStr.startsWith("about:flags") || uriStr.startsWith("about:config") || uriStr.startsWith("chrome://flags")) {
            if (context instanceof com.petal.browser.activity.BrowserActivity) {
                ((com.petal.browser.activity.BrowserActivity) context).openFlagsHubScreen();
                return;
            }
        }
        if (uriStr.startsWith("petal://settings") || uriStr.startsWith("petal://credits") || uriStr.startsWith("petal://ai")) {
            if (context instanceof Activity) {
                Activity activity = (Activity) context;
                if (uriStr.startsWith("petal://ai")) {
                    if (activity instanceof com.petal.browser.activity.BrowserActivity) {
                        // AI deep links are handled directly by the existing AI search
                        // bridge. Do not depend on a BrowserActivity helper that may not
                        // exist in older/newer activity revisions.
                        String query = uri.getQueryParameter("q");
                        if (query == null) query = uri.getQueryParameter("query");
                        if (query == null) query = "";
                        com.petal.browser.ui.components.PetalAiSearchBridge.showAiSearchResult(
                                (com.petal.browser.activity.BrowserActivity) activity,
                                query
                        );
                    } else {
                        Intent intent = new Intent(activity, com.petal.browser.activity.BrowserActivity.class);
                        intent.setAction(Intent.ACTION_VIEW);
                        intent.setData(uri);
                        activity.startActivity(intent);
                    }
                    return;
                }
                if (uriStr.startsWith("petal://settings")) {
                    if (activity instanceof com.petal.browser.activity.BrowserActivity) {
                        if (uriStr.contains("category=api_integrations") || uriStr.contains("category=ai_research")) {
                            ((com.petal.browser.activity.BrowserActivity) activity).openApiIntegrationsHub();
                        } else {
                            ((com.petal.browser.activity.BrowserActivity) activity).openSettingsScreen();
                        }
                    } else {
                        Intent intent = new Intent(activity, com.petal.browser.activity.SettingsActivity.class);
                        if (uriStr.contains("category=api_integrations") || uriStr.contains("category=ai_research")) {
                            intent.putExtra(
                                com.petal.browser.activity.SettingsActivity.EXTRA_SETTINGS_CATEGORY,
                                com.petal.browser.compose.settings.SettingsCategory.API_INTEGRATIONS.name()
                            );
                        }
                        activity.startActivity(intent);
                    }
                    return;
                }
            }
        } else if (uriStr.startsWith("http://") || uriStr.startsWith("https://")) {
            if (context instanceof com.petal.browser.activity.BrowserActivity) {
                ((com.petal.browser.activity.BrowserActivity) context).addAlbum(null, uriStr, true);
                return;
            } else if (context instanceof Activity) {
                Intent intent = new Intent(context, com.petal.browser.activity.BrowserActivity.class);
                intent.setAction(Intent.ACTION_VIEW);
                intent.setData(uri);
                context.startActivity(intent);
                return;
            }
        }
    }

    public static String redirectURL(SharedPreferences sp, String url) {
        if (url == null) return null;
        try {
            List<CustomRedirect> redirects = CustomRedirectsHelper.getRedirects(sp);
            for (CustomRedirect redirect : redirects) {
                if (redirect != null && redirect.getSource() != null && redirect.getTarget() != null &&
                        url.contains(redirect.getSource()) && sp != null && sp.getBoolean(redirect.getSource(), true)) {
                    return url.replace(redirect.getSource(), redirect.getTarget());
                }
            }
        } catch (Exception e) { Log.e("Redirect error", e.toString()); }
        return url;
    }

    public static boolean isHomePage(String url) {
        if (url == null || url.trim().isEmpty()) return true;
        String clean = url.trim().toLowerCase(Locale.ROOT);
        return clean.equals("about:blank") || clean.equals("about:home") ||
                clean.equals("petal://home") || clean.equals("petal://start") ||
                clean.contains("petal_home.html") || clean.startsWith("file:///android_asset/");
    }

    /**
     * Resolves the effective homepage URL based on user settings (Petal Start Page vs Custom URL).
     */
    public static String getEffectiveHomepageUrl(Context context) {
        if (context == null) return "about:blank";
        try {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            String homeType = sp.getString("sp_home_type", "0");
            if ("1".equals(homeType)) {
                String customUrl = sp.getString("sp_custom_homepage_url", "https://google.com");
                if (customUrl != null && !customUrl.trim().isEmpty()) {
                    return customUrl.trim();
                }
            }
        } catch (Exception e) {
            PetalAppLogger.w("PetalBrowserUtils", "Error resolving effective homepage URL", e);
        }
        return "about:blank";
    }

    /**
     * Cleans tracking, referral, analytics, and telemetry parameters from a web URL
     * (e.g., utm_*, fbclid, gclid, yclid, igshid, msclkid, ref, etc.).
     */
    public static String cleanTrackingParams(String url) {
        if (url == null || url.trim().isEmpty()) return url;
        try {
            Uri uri = Uri.parse(url);
            if (uri == null || uri.getQuery() == null || uri.getQuery().isEmpty()) {
                return url;
            }
            Uri.Builder builder = uri.buildUpon().clearQuery();
            for (String paramName : uri.getQueryParameterNames()) {
                if (paramName == null) continue;
                String lower = paramName.toLowerCase(Locale.ROOT);
                if (lower.startsWith("utm_") ||
                    lower.equals("fbclid") ||
                    lower.equals("gclid") ||
                    lower.equals("dclid") ||
                    lower.equals("gclsrc") ||
                    lower.equals("wbraid") ||
                    lower.equals("gbraid") ||
                    lower.equals("yclid") ||
                    lower.equals("msclkid") ||
                    lower.equals("igshid") ||
                    lower.equals("mc_cid") ||
                    lower.equals("mc_eid") ||
                    lower.equals("mkt_tok") ||
                    lower.startsWith("pk_") ||
                    lower.startsWith("piwik_") ||
                    lower.equals("_hsenc") ||
                    lower.equals("_hsmi") ||
                    lower.equals("ref_src") ||
                    lower.equals("ref_url")) {
                    continue; // Strip tracking query parameter
                }
                for (String value : uri.getQueryParameters(paramName)) {
                    builder.appendQueryParameter(paramName, value);
                }
            }
            return builder.build().toString();
        } catch (Exception e) {
            return url;
        }
    }

}
