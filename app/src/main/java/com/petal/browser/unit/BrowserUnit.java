package com.petal.browser.unit;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
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
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

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
import com.petal.browser.browser.List_standard;
import com.petal.browser.database.RecordAction;
import com.petal.browser.objects.CustomRedirect;
import com.petal.browser.objects.CustomRedirectsHelper;

public class BrowserUnit {

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
                return "http".equals(scheme) || "https".equals(scheme) || "file".equals(scheme) || "content".equals(scheme) || "about".equals(scheme) || "chrome".equals(scheme) || "petal".equals(scheme);
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
            if (query.startsWith("about:blank") || query.startsWith("mailto:") || query.startsWith("file:") || query.startsWith("content:") || query.startsWith("chrome://") || query.startsWith("about:flags") || query.startsWith("petal://")) {
                return query;
            }
            if (!query.contains("://")) {
                query = "https://" + query;
            }
            return query;
        } else {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            String customSearchEngine = sp.getString("sp_search_engine_custom", "");
            String customSearches = sp.getString("sp_search_customSearches", "");
            
            String encodedQuery;
            try {
                encodedQuery = URLEncoder.encode(query, "UTF-8");
            } catch (Exception e) {
                encodedQuery = query.replace(" ", "+");
            }

            if (!customSearches.isEmpty()) {
                return customSearches + encodedQuery;
            } else if (sp.getBoolean("searchEngineSwitch", false) && !customSearchEngine.isEmpty()) {
                return customSearchEngine + encodedQuery;
            } else {
                int i = 0;
                try {
                    i = Integer.parseInt(Objects.requireNonNull(sp.getString("sp_search_engine", "0")));
                } catch (Exception ignored) {}

                switch (i) {
                    case 0:
                        return "https://www.google.com/search?q=" + encodedQuery;
                    case 1:
                        return "https://duckduckgo.com/?q=" + encodedQuery;
                    case 2:
                        return "https://search.brave.com/search?q=" + encodedQuery;
                    case 3:
                        return "https://www.bing.com/search?q=" + encodedQuery;
                    case 4:
                        return "https://www.ecosia.org/search?q=" + encodedQuery;
                    default:
                        return "https://www.google.com/search?q=" + encodedQuery;
                }
            }
        }
    }

    public static void download(final Context context, final String url, final String fileName, final String mimeType) {
        if (context == null || url == null || url.trim().isEmpty()) {
            return;
        }
        // Sicherstellen, dass das Protokoll für den Android-Uri-Parser passt
        String verifiedUrl = url;
        if (!url.toLowerCase(Locale.US).startsWith("http://") && !url.toLowerCase(Locale.US).startsWith("https://")) {
            verifiedUrl = "https://" + url;
        }
        // Berechtigungsprüfung (Ab Android 10/Q wird WRITE_EXTERNAL_STORAGE für Downloads nicht mehr benötigt)
        boolean hasPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || BackupUnit.checkPermissionStorage(context);
        if (hasPermission) {
            try {
                String userAgent = WebSettings.getDefaultUserAgent(context);
                CookieManager cookieManager = CookieManager.getInstance();
                String cookie = cookieManager.getCookie(verifiedUrl);

                java.util.Map<String, String> extraHeaders = new java.util.HashMap<>();
                extraHeaders.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
                extraHeaders.put("Accept-Language", Locale.getDefault().toLanguageTag());
                extraHeaders.put("Accept-Encoding", "gzip, deflate, br, identity");
                extraHeaders.put("Connection", "keep-alive");
                extraHeaders.put("Cache-Control", "no-transform");
                extraHeaders.put("Referer", verifiedUrl);

                // If 1DM / 1DM+ / 1DM Lite is installed, hand off the download directly to 1DM
                if (context instanceof Activity && Util1DM.is1DMInstalled(context)) {
                    try {
                        Util1DM.downloadFile((Activity) context, verifiedUrl, verifiedUrl, fileName, userAgent, cookie, extraHeaders, false, false);
                        return;
                    } catch (Exception e) {
                        Log.w(TAG, "Util1DM download handoff failed, falling back to PetalDownloadEngine", e);
                    }
                }

                // Fetch2 is now the single download engine (it's the only one that actually
                // supports pause/resume) - the system DownloadManager used to also enqueue the
                // same file in parallel here, which both wasted bandwidth and left the Downloads
                // screen's pause/resume calling into an engine (system DownloadManager) that
                // never applied them. The finished file is still registered with the system
                // DownloadManager/MediaStore afterwards so it shows up in the Files app etc.
                com.petal.browser.download.PetalDownloadEngine.getInstance(context).enqueueDownload(
                        context, verifiedUrl, fileName, userAgent, cookie, extraHeaders,
                        (fetchId, resolvedName) -> com.petal.browser.compose.downloads.PetalLiveAlertManager.trackDownload(
                                context, fetchId.longValue(), resolvedName)
                );
            } catch (Exception e) {
                // Sicherer Umgang mit Fehlermeldungen ohne StringIndexOutOfBoundsException
                String errorMessage = e.getMessage() != null ? e.getMessage() : e.toString();
                Toast.makeText(context, context.getString(R.string.app_error) + ": " + errorMessage, Toast.LENGTH_LONG).show();
                Log.e(TAG, "Petal: Error Downloading File", e);
            }
        } else {
            // Sicherer Cast zu Activity nur, wenn der Context tatsächlich eine ist
            if (context instanceof Activity) {
                BackupUnit.requestPermission((Activity) context);
            } else {
                Log.e(TAG, "Cannot request permission: Context is not an Activity");
            }
        }
    }

    public static void clearBookmark(Context context) {
        RecordAction action = new RecordAction(context);
        action.open(true);
        action.clearTable(RecordUnit.TABLE_BOOKMARK);
        action.close();
    }

    public static void clearHistory(Context context) {
        RecordAction action = new RecordAction(context);
        action.open(true);
        action.clearTable(RecordUnit.TABLE_HISTORY);
        action.close();
    }

    public static void clearOnExit(Context context) {
        if (context == null) return;
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        boolean clearQuit = sp.getBoolean("sp_clear_quit", false) || sp.getBoolean("sp_clear_on_exit", false);
        if (!clearQuit) return;

        // 1. Session-scoped history cleanup: ONLY delete history URLs/records from THIS session
        PetalSessionHistoryManager.clearSessionHistory(context);

        // 2. Clear render cache if explicitly enabled (never touching cookies, logins, or auth tokens)
        boolean clearCache = sp.getBoolean("sp_clear_cache", false);
        if (clearCache) {
            try {
                CacheManager.clearAllCache(context, null);
            } catch (Exception exception) {
                Log.w("browser", "Error clearing cache on exit", exception);
            }
        }

        // 3. Clear session tab thumbnails
        try {
            TabThumbnailCache.evictAll();
        } catch (Exception ignored) {}
    }

    public static void clearBrowserData(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        boolean clearCache = sp.getBoolean("sp_clear_cache", false);
        boolean clearCookie = sp.getBoolean("sp_clear_cookie", false);
        boolean clearHistory = sp.getBoolean("sp_clear_history", false);
        boolean clearIndexedDB = sp.getBoolean("sp_clearIndexedDB", false);
        boolean clearDB = sp.getBoolean("sp_deleteDatabase", false);
        boolean clearSettings = sp.getBoolean("sp_clear_settings", false);

        if (clearHistory) BrowserUnit.clearHistory(context);
        if (clearCache)  {
            try {
                CacheManager.clearAllCache(context, null);
            } catch (Exception exception) {
                Log.w("browser", "Error clearing cache", exception);
            }
        }
        if (clearSettings) {
            clearSettingsSafely(sp);
            List_standard listStandard = new List_standard(context);
            listStandard.clearDomains();
        }
        // ONLY clear cookies and logins if the user explicitly commanded manual cookie deletion
        if (clearCookie) {
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.flush();
            cookieManager.removeAllCookies(value -> {
            });
        }
        if (clearDB) {
            context.deleteDatabase("Ninja4.db");
            context.deleteDatabase("item_icon.db");
            sp.edit().putInt("restart_changed", 1).apply();
        }
        if (clearIndexedDB) {
            // Use WebStorage instead of raw app_webview file deletion while tabs are open
            try {
                WebStorage.getInstance().deleteAllData();
            } catch (Exception ignored) {}
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
                key.startsWith("sp_gemini") ||
                key.startsWith("sp_openrouter") ||
                key.startsWith("sp_openai") ||
                key.startsWith("sp_grok") ||
                key.startsWith("sp_groq") ||
                key.startsWith("sp_google") ||
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

    public static void intentURL(Context context, Uri uri) {
        if (context == null || uri == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            PackageManager pm = context.getPackageManager();
            List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            List<Intent> targetIntents = new ArrayList<>();

            for (ResolveInfo info : resolveInfos) {
                String packageName = info.activityInfo.packageName;
                if (!packageName.equals(context.getPackageName())) {
                    Intent targetIntent = new Intent(Intent.ACTION_VIEW, uri);
                    targetIntent.setPackage(packageName);
                    if (!(context instanceof Activity)) {
                        targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    }
                    targetIntents.add(targetIntent);
                }
            }

            if (!targetIntents.isEmpty()) {
                Intent chooserIntent = Intent.createChooser(targetIntents.remove(targetIntents.size() - 1), null);
                if (!targetIntents.isEmpty()) {
                    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, targetIntents.toArray(new Parcelable[0]));
                }
                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(chooserIntent);
            } else {
                Intent chooserIntent = Intent.createChooser(intent, null);
                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(chooserIntent);
            }
        } catch (Exception e) {
            Log.e("BrowserUnit", "Failed to launch ACTION_VIEW for " + uri, e);
        }
    }

    public static String redirectURL (WebView ninjaWebView, SharedPreferences sp, String url) {
        try {
            List<CustomRedirect> redirects = CustomRedirectsHelper.getRedirects(sp);
            for (int i = 0; i < redirects.size(); i++) {
                CustomRedirect customRedirect = redirects.get(i);
                if (url.contains(customRedirect.getSource()) && sp.getBoolean(customRedirect.getSource(), true)) {
                    ninjaWebView.stopLoading();
                    url = url.replace(customRedirect.getSource(), customRedirect.getTarget());
                    return url;
                }
            }
        } catch (JSONException e) {
            Log.e("Redirect error", e.toString());
        }
        return url;
    }

    public static void openInBackground(Activity activity, WebView webView) {
        if (activity == null || webView == null) return;
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
        if (!sp.getBoolean("sp_tabBackground", false)) return;
        String dialogSetting = sp.getString("openBackground_dialog", "show");
        if ("never".equals(dialogSetting)) return;
        // Notification-Inhalt vorbereiten
        String url = webView.getUrl();
        String text = activity.getString(R.string.dialog_backGround);
        NotificationManager mNotifyMgr = (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
        // PendingIntent für Klick auf die Benachrichtigung
        Intent intentP = new Intent(activity, BrowserActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(activity, 0, intentP, PendingIntent.FLAG_IMMUTABLE);
        // Notification Channel erstellen (nur ab Android 8/Orest)
        if (mNotifyMgr != null) {
            NotificationChannel channel = new NotificationChannel("1", "Links background", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Open links in background -> click to open");
            channel.setShowBadge(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            mNotifyMgr.createNotificationChannel(channel);
        }
        Notification buildNotification = new NotificationCompat.Builder(activity, "1")
                .setSmallIcon(R.drawable.icon_web)
                .setAutoCancel(true)
                .setContentTitle(HelperUnit.domain(url))
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .build();
        // Verzweigung für Snackbar oder direkten Aufruf
        if ("show".equals(dialogSetting)) {
            HelperUnit.showCustomSnackbarWithTwoActions(
                    activity, webView, null, text, activity.getString(R.string.app_session), url,
                    R.drawable.icon_check, () -> {
                        sp.edit().putString("openBackground_dialog", "always").apply();
                        displayNotification(activity, mNotifyMgr, buildNotification);
                        return true;
                    },
                    R.drawable.icon_close, () -> {
                        sp.edit().putString("openBackground_dialog", "never").apply();
                        return true;
                    }
            );
        } else {
            displayNotification(activity, mNotifyMgr, buildNotification);
        }
    }

    private static void displayNotification(Activity activity, NotificationManager mNotifyMgr, Notification buildNotification) {

        if (activity == null || mNotifyMgr == null || buildNotification == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            new MaterialAlertDialogBuilder(activity)
                    .setIcon(R.drawable.icon_alert)
                    .setTitle(R.string.app_permission_notification)
                    .setMessage(R.string.app_permission)
                    .setPositiveButton(R.string.app_ok, (dialog, whichButton) -> {
                        dialog.dismiss();
                        try {
                            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
                            activity.startActivity(intent);
                        } catch (Exception e) {
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(Uri.fromParts("package", activity.getPackageName(), null));
                            activity.startActivity(intent);
                        }
                    })
                    .setNegativeButton(R.string.app_cancel, (dialog, whichButton) -> dialog.cancel())
                    .show(); // Direkt anzeigen über Fluent-API
            return;
        }
        // Berechtigung vorhanden oder älteres Android -> Benachrichtigung senden
        mNotifyMgr.notify(4, buildNotification);
        activity.moveTaskToBack(true);
    }
    public static boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    boolean success = deleteDir(child);
                    if (!success) {
                        return false;
                    }
                }
            }
        }
        return dir != null && dir.delete();
    }
}