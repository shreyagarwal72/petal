/*
    This file is part of the browser WebApp.

    browser WebApp is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    browser WebApp is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with the browser webview app.

    If not, see <http://www.gnu.org/licenses/>.
 */

package com.petal.browser.unit;

import static android.os.Environment.DIRECTORY_DOCUMENTS;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Patterns;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import android.app.DownloadManager;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;

import com.petal.browser.R;
import com.petal.browser.browser.List_standard;
import com.petal.browser.database.Record;
import com.petal.browser.database.RecordAction;
import com.petal.browser.view.NinjaToast;

public class BackupUnit {

    public static final int PERMISSION_REQUEST_CODE = 123;
    private static final String BOOKMARK_TYPE_SIMPLE = "<DT><A HREF=\"{url}\">{title}</A>";
    private static final String BOOKMARK_TITLE = "{title}";
    private static final String BOOKMARK_URL = "{url}";
    // Thread-Pool einmalig global deklarieren statt bei jedem Klick neu zu instanziieren (schont Ressourcen)
    public static boolean checkPermissionStorage(Context context) {
        if (context == null) return false;
        // Ab Android 10 (Q, API 29) wird dank Scoped Storage/MediaStore keine Berechtigung für Documents mehr benötigt
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true;
        }
        // Für Android 9 und älter prüfen wir die klassischen Lese- und Schreibrechte
        int readCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE);
        int writeCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return readCheck == PackageManager.PERMISSION_GRANTED && writeCheck == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestPermission(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        // Ab Android 10 ist dieser Dialog überflüssig, da MediaStore direkt funktioniert
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return;
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        builder.setIcon(R.drawable.icon_alert);
        builder.setTitle(R.string.app_warning);
        builder.setMessage(R.string.app_permission);
        builder.setPositiveButton(R.string.app_ok, (dialog, whichButton) -> {
            // Erst das eigene Fenster sauber schließen, um Klick-Sperren (Overlays) zu vermeiden
            dialog.dismiss();
            // Da wir uns hier sicher unter Android 10 befinden, fordern wir die klassischen Rechte an
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
        });
        builder.setNegativeButton(R.string.app_cancel, (dialog, whichButton) -> dialog.cancel());
        AlertDialog dialog = builder.create();
        dialog.show();
        HelperUnit.setupDialog(activity, dialog);
    }

    public static File getSafeBackupDir(Context context) {
        if (context == null) return null;
        try {
            File publicDocs = Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS);
            File backupDir = new File(publicDocs, "browser_backup");
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }
            if (backupDir.exists()) {
                // Verify real write access by testing a temporary file
                File testFile = new File(backupDir, ".write_test_" + System.currentTimeMillis());
                if (testFile.createNewFile()) {
                    testFile.delete();
                    return backupDir;
                }
            }
        } catch (Throwable ignored) {}
        File internalBackupDir = new File(context.getFilesDir(), "browser_backup");
        if (!internalBackupDir.exists()) {
            try { internalBackupDir.mkdirs(); } catch (Throwable ignored) {}
        }
        return internalBackupDir;
    }

    public static void makeBackupDir(Context context) {
        if (context == null) return;
        getSafeBackupDir(context);
    }

    public static org.json.JSONObject buildBackupJson(Context context, boolean backupBookmarks, boolean backupHistory, boolean backupStartSites, boolean backupTabSessions, boolean backupSavedSites, boolean backupSettings) {
        org.json.JSONObject backupJson = new org.json.JSONObject();
        try {
            backupJson.put("version", 2);
            backupJson.put("timestamp", System.currentTimeMillis());

            if (backupBookmarks) {
                try {
                    RecordAction action = new RecordAction(context);
                    action.open(false);
                    List<Record> bookmarks = action.listBookmark(context, false, 0);
                    action.close();

                    org.json.JSONArray bookmarksArray = new org.json.JSONArray();
                    for (Record r : bookmarks) {
                        org.json.JSONObject obj = new org.json.JSONObject();
                        obj.put("title", r.getTitle() != null ? r.getTitle() : "");
                        obj.put("url", r.getURL() != null ? r.getURL() : "");
                        long bTime = r.getIconColor() > 0 ? r.getIconColor() : (r.getTime() > 0 ? r.getTime() : System.currentTimeMillis());
                        obj.put("time", bTime);
                        obj.put("iconColor", r.getIconColor() > 0 ? r.getIconColor() : bTime);
                        bookmarksArray.put(obj);
                    }
                    backupJson.put("bookmarks", bookmarksArray);
                } catch (Exception e) {
                    Log.e("Petal", "Error extracting bookmarks for JSON backup", e);
                }
            }

            if (backupHistory) {
                try {
                    RecordAction action = new RecordAction(context);
                    action.open(false);
                    List<Record> history = action.listHistory(context);
                    action.close();

                    org.json.JSONArray historyArray = new org.json.JSONArray();
                    for (Record r : history) {
                        org.json.JSONObject obj = new org.json.JSONObject();
                        obj.put("title", r.getTitle() != null ? r.getTitle() : "");
                        obj.put("url", r.getURL() != null ? r.getURL() : "");
                        obj.put("time", r.getTime());
                        historyArray.put(obj);
                    }
                    backupJson.put("history", historyArray);
                } catch (Exception e) {
                    Log.e("Petal", "Error extracting history for JSON backup", e);
                }
            }

            if (backupStartSites) {
                try {
                    RecordAction action = new RecordAction(context);
                    action.open(false);
                    List<Record> startSites = action.listStartSites();
                    action.close();

                    org.json.JSONArray startArray = new org.json.JSONArray();
                    for (Record r : startSites) {
                        org.json.JSONObject obj = new org.json.JSONObject();
                        obj.put("title", r.getTitle() != null ? r.getTitle() : "");
                        obj.put("url", r.getURL() != null ? r.getURL() : "");
                        obj.put("filename", r.getFilename() != null ? r.getFilename() : "");
                        obj.put("ordinal", r.getOrdinal());
                        startArray.put(obj);
                    }
                    backupJson.put("start_sites", startArray);
                } catch (Exception e) {
                    Log.e("Petal", "Error extracting start sites for JSON backup", e);
                }
            }

            if (backupTabSessions) {
                try {
                    RecordAction action = new RecordAction(context);
                    action.open(false);
                    String sessionJson = action.getSessionStateJson();
                    action.close();
                    if (sessionJson == null || sessionJson.trim().isEmpty()) {
                        sessionJson = PreferenceManager.getDefaultSharedPreferences(context)
                                .getString(com.petal.browser.unit.PetalTabSessionManager.PREF_SESSION_JSON, null);
                    }
                    if (sessionJson != null && !sessionJson.trim().isEmpty()) {
                        backupJson.put("tab_sessions", sessionJson);
                    }
                } catch (Exception e) {
                    Log.e("Petal", "Error extracting tab sessions for JSON backup", e);
                }
            }

            if (backupSavedSites) {
                try {
                    RecordAction action = new RecordAction(context);
                    action.open(false);
                    List<String> domains = action.listDomains(RecordUnit.TABLE_STANDARD);
                    List<String> trusted = action.listDomains(RecordUnit.TABLE_TRUSTED);
                    List<String> protect = action.listDomains(RecordUnit.TABLE_PROTECTED);
                    action.close();

                    org.json.JSONArray sitesArray = new org.json.JSONArray();
                    for (String domain : domains) {
                        sitesArray.put(domain);
                    }
                    backupJson.put("saved_sites", sitesArray);

                    org.json.JSONArray trustedArray = new org.json.JSONArray();
                    for (String domain : trusted) {
                        trustedArray.put(domain);
                    }
                    backupJson.put("trusted_sites", trustedArray);

                    org.json.JSONArray protectArray = new org.json.JSONArray();
                    for (String domain : protect) {
                        protectArray.put(domain);
                    }
                    backupJson.put("protected_sites", protectArray);
                } catch (Exception e) {
                    Log.e("Petal", "Error extracting saved sites for JSON backup", e);
                }
            }

            if (backupSettings) {
                try {
                    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
                    org.json.JSONObject settingsObj = new org.json.JSONObject();
                    for (java.util.Map.Entry<String, ?> entry : sp.getAll().entrySet()) {
                        Object val = entry.getValue();
                        if (val instanceof String || val instanceof Integer
                                || val instanceof Boolean || val instanceof Long
                                || val instanceof Float) {
                            settingsObj.put(entry.getKey(), val);
                        } else if (val instanceof java.util.Set) {
                            org.json.JSONArray setArr = new org.json.JSONArray();
                            for (Object item : (java.util.Set<?>) val) {
                                setArr.put(String.valueOf(item));
                            }
                            settingsObj.put(entry.getKey(), setArr);
                        }
                    }
                    backupJson.put("settings", settingsObj);
                } catch (Exception e) {
                    Log.e("Petal", "Error extracting settings for JSON backup", e);
                }
            }
        } catch (Exception e) {
            Log.e("Petal", "buildBackupJson failed", e);
        }
        return backupJson;
    }

    public static void backupToJson(Activity context, boolean backupBookmarks, boolean backupHistory, boolean backupSavedSites, boolean backupSettings) {
        backupToDownloadManager(context, backupBookmarks, backupHistory, true, true, backupSavedSites, backupSettings);
    }

    public static void backupToJson(Activity context, boolean backupBookmarks, boolean backupHistory, boolean backupStartSites, boolean backupTabSessions, boolean backupSavedSites, boolean backupSettings) {
        backupToDownloadManager(context, backupBookmarks, backupHistory, backupStartSites, backupTabSessions, backupSavedSites, backupSettings);
    }

    public static void backupToDownloadManager(Context context, boolean backupBookmarks, boolean backupHistory, boolean backupStartSites, boolean backupTabSessions, boolean backupSavedSites, boolean backupSettings) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            try {
                org.json.JSONObject backupJson = buildBackupJson(context, backupBookmarks, backupHistory, backupStartSites, backupTabSessions, backupSavedSites, backupSettings);
                byte[] dataBytes = backupJson.toString(2).getBytes(StandardCharsets.UTF_8);

                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                String fileName = "petal_backup_" + timeStamp + ".json";

                File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (downloadDir != null && !downloadDir.exists()) {
                    downloadDir.mkdirs();
                }

                File targetFile = null;
                boolean directWriteSuccess = false;

                if (downloadDir != null && downloadDir.exists()) {
                    targetFile = new File(downloadDir, fileName);
                    try (FileOutputStream fos = new FileOutputStream(targetFile, false)) {
                        fos.write(dataBytes);
                        fos.flush();
                        try {
                            fos.getFD().sync();
                        } catch (Exception ignored) {}
                        directWriteSuccess = true;
                    } catch (Throwable writeEx) {
                        Log.w("Petal", "Direct Downloads folder write failed, falling back to MediaStore or app directory: " + writeEx.getMessage());
                        directWriteSuccess = false;
                    }
                }

                // Android 10+ scoped storage MediaStore fallback if direct file access fails
                if (!directWriteSuccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        android.content.ContentValues values = new android.content.ContentValues();
                        values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                        values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/json");
                        values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                        Uri uri = context.getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                        if (uri != null) {
                            try (OutputStream os = context.getContentResolver().openOutputStream(uri, "wt")) {
                                if (os != null) {
                                    os.write(dataBytes);
                                    os.flush();
                                    directWriteSuccess = true;
                                }
                            }
                        }
                    } catch (Throwable msEx) {
                        Log.w("Petal", "MediaStore Downloads write failed: " + msEx.getMessage());
                    }
                }

                // Fallback to internal/safe backup dir if external storage write completely unavailable
                if (!directWriteSuccess) {
                    File safeDir = getSafeBackupDir(context);
                    targetFile = new File(safeDir, fileName);
                    try (FileOutputStream fos = new FileOutputStream(targetFile, false)) {
                        fos.write(dataBytes);
                        fos.flush();
                    }
                }

                // Register with system Downloads and MediaScanner
                if (targetFile != null && targetFile.exists()) {
                    final File finalFile = targetFile;
                    MediaScannerConnection.scanFile(context, new String[]{finalFile.getAbsolutePath()}, new String[]{"application/json"}, null);
                    try {
                        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                        if (dm != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            dm.addCompletedDownload(
                                    finalFile.getName(),
                                    "Petal Browser Backup",
                                    true,
                                    "application/json",
                                    finalFile.getAbsolutePath(),
                                    finalFile.length(),
                                    true
                            );
                        }
                    } catch (Throwable ignored) {}
                }

                final String savedName = fileName;
                handler.post(() -> {
                    NinjaToast.show(context, "Backup downloaded to Downloads: " + savedName);
                });

            } catch (Exception e) {
                Log.e("Petal", "backupToDownloadManager error", e);
                handler.post(() -> {
                    NinjaToast.show(context, "Backup failed: " + e.getMessage());
                });
            }
        });
    }

    public static void backupToUri(Context context, android.net.Uri uri, boolean backupBookmarks, boolean backupHistory, boolean backupSavedSites, boolean backupSettings) {
        backupToUri(context, uri, backupBookmarks, backupHistory, true, true, backupSavedSites, backupSettings);
    }

    public static void backupToUri(Context context, android.net.Uri uri, boolean backupBookmarks, boolean backupHistory, boolean backupStartSites, boolean backupTabSessions, boolean backupSavedSites, boolean backupSettings) {
        // ── Open the OutputStream BEFORE handing off to the background thread ──────────
        // SAF/CreateDocument URIs remain valid for the app process (not just main thread),
        // but opening with "wt" truncates immediately. We open here so the truncation and
        // the write happen atomically in one stream lifecycle.
        final OutputStream[] streamHolder = new OutputStream[1];
        try {
            try {
                streamHolder[0] = context.getContentResolver().openOutputStream(uri, "wt");
            } catch (Throwable t1) {
                try {
                    streamHolder[0] = context.getContentResolver().openOutputStream(uri, "w");
                } catch (Throwable t2) {
                    streamHolder[0] = context.getContentResolver().openOutputStream(uri);
                }
            }
        } catch (Throwable outerEx) {
            // Try ParcelFileDescriptor as last resort before giving up
            try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "wt")) {
                if (pfd != null) streamHolder[0] = new ParcelFileDescriptor.AutoCloseOutputStream(pfd);
            } catch (Throwable ignored) {}
        }

        if (streamHolder[0] == null) {
            new Handler(Looper.getMainLooper()).post(() ->
                    NinjaToast.show(context, "Backup failed: cannot open file for writing"));
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            try (OutputStream destinationStream = streamHolder[0]) {
                org.json.JSONObject backupJson = buildBackupJson(context, backupBookmarks, backupHistory, backupStartSites, backupTabSessions, backupSavedSites, backupSettings);
                byte[] dataBytes = backupJson.toString(2).getBytes(StandardCharsets.UTF_8);
                Log.i("Petal", "Writing backup payload: " + dataBytes.length + " bytes to URI: " + uri);

                try (java.io.BufferedOutputStream bos = new java.io.BufferedOutputStream(destinationStream, 65536)) {
                    bos.write(dataBytes);
                    bos.flush();
                }

                Log.i("Petal", "Backup written successfully (" + dataBytes.length + " bytes)");
                handler.post(() -> NinjaToast.show(context,
                        context.getString(R.string.app_done) + ": Backup saved (" + dataBytes.length + " bytes)"));

            } catch (Exception e) {
                Log.e("Petal", "backupToUri error", e);
                handler.post(() -> NinjaToast.show(context, "Backup failed: " + e.getMessage()));
            }
        });
    }




    public static void restoreFromUri(Context context, android.net.Uri uri, boolean restoreBookmarks, boolean restoreHistory, boolean restoreSavedSites, boolean restoreSettings) {
        restoreFromUri(context, uri, restoreBookmarks, restoreHistory, true, true, restoreSavedSites, restoreSettings);
    }

    public static void restoreFromUri(Context context, android.net.Uri uri, boolean restoreBookmarks, boolean restoreHistory, boolean restoreStartSites, boolean restoreTabSessions, boolean restoreSavedSites, boolean restoreSettings) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            try {
                java.io.InputStream is = context.getContentResolver().openInputStream(uri);
                if (is == null) {
                    handler.post(() -> NinjaToast.show(context, "Failed to open selected file"));
                    return;
                }

                BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                org.json.JSONObject backupJson = new org.json.JSONObject(sb.toString());

                if (restoreBookmarks && backupJson.has("bookmarks")) {
                    try {
                        org.json.JSONArray bookmarksArray = backupJson.getJSONArray("bookmarks");
                        RecordAction action = new RecordAction(context);
                        action.open(true);
                        for (int i = 0; i < bookmarksArray.length(); i++) {
                            org.json.JSONObject obj = bookmarksArray.getJSONObject(i);
                            String title = obj.optString("title", "");
                            if (title.isEmpty()) {
                                title = obj.optString("name", "");
                            }
                            String url = obj.optString("url", "");
                            if (url.isEmpty()) {
                                url = obj.optString("href", "");
                            }
                            if (url.isEmpty()) {
                                url = obj.optString("link", "");
                            }
                            long time = obj.optLong("time", System.currentTimeMillis());
                            long iconColor = obj.optLong("iconColor", time > 0 ? time : 1L);
                            if (title.isEmpty()) {
                                title = url;
                            }
                            if (!url.isEmpty() && !url.equalsIgnoreCase("about:blank") && !action.checkUrl(url, RecordUnit.TABLE_BOOKMARK)) {
                                Record record = new Record();
                                record.setTitle(title);
                                record.setURL(url);
                                record.setTime(time);
                                record.setIconColor(iconColor > 0 ? iconColor : (time > 0 ? time : 1L));
                                action.addBookmark(record);
                            }
                        }
                        action.close();
                    } catch (Exception e) {
                        Log.e("Petal", "Error restoring bookmarks", e);
                    }
                }

                if (restoreHistory && backupJson.has("history")) {
                    try {
                        org.json.JSONArray historyArray = backupJson.getJSONArray("history");
                        RecordAction action = new RecordAction(context);
                        action.open(true);
                        for (int i = 0; i < historyArray.length(); i++) {
                            org.json.JSONObject obj = historyArray.getJSONObject(i);
                            String title = obj.optString("title", "");
                            if (title.isEmpty()) {
                                title = obj.optString("name", "");
                            }
                            String url = obj.optString("url", "");
                            if (url.isEmpty()) {
                                url = obj.optString("href", "");
                            }
                            if (url.isEmpty()) {
                                url = obj.optString("link", "");
                            }
                            long time = obj.optLong("time", System.currentTimeMillis());
                            if (title.isEmpty()) {
                                title = url;
                            }
                            if (!url.isEmpty() && !url.equalsIgnoreCase("about:blank") && !action.checkUrl(url, RecordUnit.TABLE_HISTORY)) {
                                action.addHistory(new Record(title, url, time, 0L));
                            }
                        }
                        action.close();
                    } catch (Exception e) {
                        Log.e("Petal", "Error restoring history", e);
                    }
                }

                if (restoreStartSites && backupJson.has("start_sites")) {
                    try {
                        org.json.JSONArray startArray = backupJson.getJSONArray("start_sites");
                        RecordAction action = new RecordAction(context);
                        action.open(true);
                        for (int i = 0; i < startArray.length(); i++) {
                            org.json.JSONObject obj = startArray.getJSONObject(i);
                            String title = obj.optString("title", "");
                            String url = obj.optString("url", "");
                            String filename = obj.optString("filename", "");
                            int ordinal = obj.optInt("ordinal", i);
                            if (!url.isEmpty() && !action.checkStartSite(url)) {
                                Record record = new Record();
                                record.setTitle(title);
                                record.setURL(url);
                                record.setFilename(filename);
                                record.setOrdinal(ordinal);
                                action.addStartSite(record);
                            }
                        }
                        action.close();
                    } catch (Exception e) {
                        Log.e("Petal", "Error restoring start sites", e);
                    }
                }

                if (restoreTabSessions && backupJson.has("tab_sessions")) {
                    try {
                        String sessionJson = null;
                        Object sessionObj = backupJson.opt("tab_sessions");
                        if (sessionObj instanceof org.json.JSONObject || sessionObj instanceof org.json.JSONArray) {
                            sessionJson = sessionObj.toString();
                        } else if (sessionObj instanceof String) {
                            sessionJson = (String) sessionObj;
                        }
                        if (sessionJson != null && !sessionJson.trim().isEmpty()) {
                            RecordAction action = new RecordAction(context);
                            action.open(true);
                            action.saveSessionStateJson(sessionJson);
                            action.close();

                            PreferenceManager.getDefaultSharedPreferences(context).edit()
                                    .putString(com.petal.browser.unit.PetalTabSessionManager.PREF_SESSION_JSON, sessionJson)
                                    .putBoolean(com.petal.browser.unit.PetalTabSessionManager.PREF_RESTORE_ON_RESTART, true)
                                    .apply();
                        }
                    } catch (Exception e) {
                        Log.e("Petal", "Error restoring tab session", e);
                    }
                }

                if (restoreSavedSites) {
                    if (backupJson.has("saved_sites")) {
                        try {
                            org.json.JSONArray sitesArray = backupJson.getJSONArray("saved_sites");
                            RecordAction action = new RecordAction(context);
                            List_standard listStandard = new List_standard(context);
                            action.open(true);
                            for (int i = 0; i < sitesArray.length(); i++) {
                                String domain = sitesArray.optString(i, "");
                                if (!domain.isEmpty() && !action.checkDomain(domain, RecordUnit.TABLE_STANDARD)) {
                                    listStandard.addDomain(domain);
                                }
                            }
                            action.close();
                        } catch (Exception e) {
                            Log.e("Petal", "Error restoring saved sites", e);
                        }
                    }
                    if (backupJson.has("trusted_sites")) {
                        try {
                            org.json.JSONArray trustedArray = backupJson.getJSONArray("trusted_sites");
                            RecordAction action = new RecordAction(context);
                            action.open(true);
                            for (int i = 0; i < trustedArray.length(); i++) {
                                String domain = trustedArray.optString(i, "");
                                if (!domain.isEmpty() && !action.checkDomain(domain, RecordUnit.TABLE_TRUSTED)) {
                                    action.addDomain(domain, RecordUnit.TABLE_TRUSTED);
                                }
                            }
                            action.close();
                        } catch (Exception e) {
                            Log.e("Petal", "Error restoring trusted sites", e);
                        }
                    }
                    if (backupJson.has("protected_sites")) {
                        try {
                            org.json.JSONArray protectArray = backupJson.getJSONArray("protected_sites");
                            RecordAction action = new RecordAction(context);
                            action.open(true);
                            for (int i = 0; i < protectArray.length(); i++) {
                                String domain = protectArray.optString(i, "");
                                if (!domain.isEmpty() && !action.checkDomain(domain, RecordUnit.TABLE_PROTECTED)) {
                                    action.addDomain(domain, RecordUnit.TABLE_PROTECTED);
                                }
                            }
                            action.close();
                        } catch (Exception e) {
                            Log.e("Petal", "Error restoring protected sites", e);
                        }
                    }
                }

                if (restoreSettings && backupJson.has("settings")) {
                    try {
                        org.json.JSONObject settingsObj = backupJson.getJSONObject("settings");
                        android.content.SharedPreferences sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
                        android.content.SharedPreferences.Editor editor = sp.edit();
                        java.util.Iterator<String> keys = settingsObj.keys();

                        while (keys.hasNext()) {
                            editor.remove(keys.next());
                        }
                        editor.apply();

                        editor = sp.edit();
                        keys = settingsObj.keys();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            Object val = settingsObj.opt(key);
                            if (val == null || val == org.json.JSONObject.NULL) continue;

                            boolean isKnownStringKey = "sp_fontSize".equals(key) || "sp_search_engine".equals(key) ||
                                    "sp_searchEngine".equals(key) || "sp_userAgent".equals(key) ||
                                    "profile".equals(key) || key.startsWith("icon_");

                            if (isKnownStringKey) {
                                editor.putString(key, String.valueOf(val));
                            } else if (val instanceof Boolean) {
                                editor.putBoolean(key, (Boolean) val);
                            } else if (val instanceof Integer) {
                                editor.putInt(key, (Integer) val);
                            } else if (val instanceof Long) {
                                editor.putLong(key, (Long) val);
                            } else if (val instanceof Double) {
                                editor.putFloat(key, ((Double) val).floatValue());
                            } else if (val instanceof Float) {
                                editor.putFloat(key, (Float) val);
                            } else if (val instanceof String) {
                                editor.putString(key, (String) val);
                            } else if (val instanceof org.json.JSONArray) {
                                org.json.JSONArray arr = (org.json.JSONArray) val;
                                java.util.Set<String> set = new java.util.HashSet<>();
                                for (int i = 0; i < arr.length(); i++) {
                                    set.add(arr.optString(i));
                                }
                                editor.putStringSet(key, set);
                            }
                        }
                        editor.apply();
                    } catch (Exception e) {
                        Log.e("Petal", "Error restoring settings", e);
                    }
                }

                handler.post(() -> {
                    NinjaToast.show(context, context.getString(R.string.app_done) + ": " + context.getString(R.string.settings_data_restore));
                });
            } catch (Exception e) {
                Log.e("Petal", "restoreFromUri error", e);
                handler.post(() -> {
                    NinjaToast.show(context, "Restore failed: " + e.getMessage());
                });
            }
        });
    }

    public static void restoreFromJson(Activity context, boolean restoreBookmarks, boolean restoreHistory, boolean restoreSavedSites, boolean restoreSettings) {
        restoreFromJson(context, restoreBookmarks, restoreHistory, true, true, restoreSavedSites, restoreSettings);
    }

    public static void restoreFromJson(Activity context, boolean restoreBookmarks, boolean restoreHistory, boolean restoreStartSites, boolean restoreTabSessions, boolean restoreSavedSites, boolean restoreSettings) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            try {
                File backupDir = getSafeBackupDir(context);
                File jsonFile = backupDir != null ? new File(backupDir, "petal_browser_backup.json") : null;
                if (jsonFile == null || !jsonFile.exists()) {
                    File fallbackFile = new File(new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS), "browser_backup"), "petal_browser_backup.json");
                    if (fallbackFile.exists()) {
                        jsonFile = fallbackFile;
                    }
                }
                if (jsonFile == null || !jsonFile.exists()) {
                    handler.post(() -> NinjaToast.show(context, "No backup file found at Documents/browser_backup/petal_browser_backup.json"));
                    return;
                }

                BufferedReader reader = new BufferedReader(new FileReader(jsonFile));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                org.json.JSONObject backupJson = new org.json.JSONObject(sb.toString());

                if (restoreBookmarks && backupJson.has("bookmarks")) {
                    try {
                        org.json.JSONArray bookmarksArray = backupJson.getJSONArray("bookmarks");
                        RecordAction action = new RecordAction(context);
                        action.open(true);
                        for (int i = 0; i < bookmarksArray.length(); i++) {
                            org.json.JSONObject obj = bookmarksArray.getJSONObject(i);
                            String title = obj.optString("title", "");
                            if (title.isEmpty()) {
                                title = obj.optString("name", "");
                            }
                            String url = obj.optString("url", "");
                            if (url.isEmpty()) {
                                url = obj.optString("href", "");
                            }
                            if (url.isEmpty()) {
                                url = obj.optString("link", "");
                            }
                            long time = obj.optLong("time", System.currentTimeMillis());
                            long iconColor = obj.optLong("iconColor", time > 0 ? time : 1L);
                            if (title.isEmpty()) {
                                title = url;
                            }
                            if (!url.isEmpty() && !url.equalsIgnoreCase("about:blank") && !action.checkUrl(url, RecordUnit.TABLE_BOOKMARK)) {
                                Record record = new Record();
                                record.setTitle(title);
                                record.setURL(url);
                                record.setTime(time);
                                record.setIconColor(iconColor > 0 ? iconColor : (time > 0 ? time : 1L));
                                action.addBookmark(record);
                            }
                        }
                        action.close();
                    } catch (Exception e) {
                        Log.e("Petal", "Error restoring bookmarks", e);
                    }
                }

                if (restoreHistory && backupJson.has("history")) {
                    try {
                        org.json.JSONArray historyArray = backupJson.getJSONArray("history");
                        RecordAction action = new RecordAction(context);
                        action.open(true);
                        for (int i = 0; i < historyArray.length(); i++) {
                            org.json.JSONObject obj = historyArray.getJSONObject(i);
                            String title = obj.optString("title", "");
                            if (title.isEmpty()) {
                                title = obj.optString("name", "");
                            }
                            String url = obj.optString("url", "");
                            if (url.isEmpty()) {
                                url = obj.optString("href", "");
                            }
                            if (url.isEmpty()) {
                                url = obj.optString("link", "");
                            }
                            long time = obj.optLong("time", System.currentTimeMillis());
                            if (title.isEmpty()) {
                                title = url;
                            }
                            if (!url.isEmpty() && !url.equalsIgnoreCase("about:blank") && !action.checkUrl(url, RecordUnit.TABLE_HISTORY)) {
                                action.addHistory(new Record(title, url, time, 0L));
                            }
                        }
                        action.close();
                    } catch (Exception e) {
                        Log.e("Petal", "Error restoring history", e);
                    }
                }

                if (restoreStartSites && backupJson.has("start_sites")) {
                    try {
                        org.json.JSONArray startArray = backupJson.getJSONArray("start_sites");
                        RecordAction action = new RecordAction(context);
                        action.open(true);
                        for (int i = 0; i < startArray.length(); i++) {
                            org.json.JSONObject obj = startArray.getJSONObject(i);
                            String title = obj.optString("title", "");
                            String url = obj.optString("url", "");
                            String filename = obj.optString("filename", "");
                            int ordinal = obj.optInt("ordinal", i);
                            if (!url.isEmpty() && !action.checkStartSite(url)) {
                                Record record = new Record();
                                record.setTitle(title);
                                record.setURL(url);
                                record.setFilename(filename);
                                record.setOrdinal(ordinal);
                                action.addStartSite(record);
                            }
                        }
                        action.close();
                    } catch (Exception e) {
                        Log.e("Petal", "Error restoring start sites", e);
                    }
                }

                if (restoreTabSessions && backupJson.has("tab_sessions")) {
                    try {
                        String sessionJson = null;
                        Object sessionObj = backupJson.opt("tab_sessions");
                        if (sessionObj instanceof org.json.JSONObject || sessionObj instanceof org.json.JSONArray) {
                            sessionJson = sessionObj.toString();
                        } else if (sessionObj instanceof String) {
                            sessionJson = (String) sessionObj;
                        }
                        if (sessionJson != null && !sessionJson.trim().isEmpty()) {
                            RecordAction action = new RecordAction(context);
                            action.open(true);
                            action.saveSessionStateJson(sessionJson);
                            action.close();

                            PreferenceManager.getDefaultSharedPreferences(context).edit()
                                    .putString(com.petal.browser.unit.PetalTabSessionManager.PREF_SESSION_JSON, sessionJson)
                                    .putBoolean(com.petal.browser.unit.PetalTabSessionManager.PREF_RESTORE_ON_RESTART, true)
                                    .apply();
                        }
                    } catch (Exception e) {
                        Log.e("Petal", "Error restoring tab session", e);
                    }
                }

                if (restoreSavedSites) {
                    if (backupJson.has("saved_sites")) {
                        try {
                            org.json.JSONArray sitesArray = backupJson.getJSONArray("saved_sites");
                            RecordAction action = new RecordAction(context);
                            List_standard listStandard = new List_standard(context);
                            action.open(true);
                            for (int i = 0; i < sitesArray.length(); i++) {
                                String domain = sitesArray.optString(i, "");
                                if (!domain.isEmpty() && !action.checkDomain(domain, RecordUnit.TABLE_STANDARD)) {
                                    listStandard.addDomain(domain);
                                }
                            }
                            action.close();
                        } catch (Exception e) {
                            Log.e("Petal", "Error restoring saved sites", e);
                        }
                    }
                    if (backupJson.has("trusted_sites")) {
                        try {
                            org.json.JSONArray trustedArray = backupJson.getJSONArray("trusted_sites");
                            RecordAction action = new RecordAction(context);
                            action.open(true);
                            for (int i = 0; i < trustedArray.length(); i++) {
                                String domain = trustedArray.optString(i, "");
                                if (!domain.isEmpty() && !action.checkDomain(domain, RecordUnit.TABLE_TRUSTED)) {
                                    action.addDomain(domain, RecordUnit.TABLE_TRUSTED);
                                }
                            }
                            action.close();
                        } catch (Exception e) {
                            Log.e("Petal", "Error restoring trusted sites", e);
                        }
                    }
                    if (backupJson.has("protected_sites")) {
                        try {
                            org.json.JSONArray protectArray = backupJson.getJSONArray("protected_sites");
                            RecordAction action = new RecordAction(context);
                            action.open(true);
                            for (int i = 0; i < protectArray.length(); i++) {
                                String domain = protectArray.optString(i, "");
                                if (!domain.isEmpty() && !action.checkDomain(domain, RecordUnit.TABLE_PROTECTED)) {
                                    action.addDomain(domain, RecordUnit.TABLE_PROTECTED);
                                }
                            }
                            action.close();
                        } catch (Exception e) {
                            Log.e("Petal", "Error restoring protected sites", e);
                        }
                    }
                }

                if (restoreSettings && backupJson.has("settings")) {
                    try {
                        org.json.JSONObject settingsObj = backupJson.getJSONObject("settings");
                        android.content.SharedPreferences sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
                        android.content.SharedPreferences.Editor editor = sp.edit();
                        java.util.Iterator<String> keys = settingsObj.keys();

                        while (keys.hasNext()) {
                            editor.remove(keys.next());
                        }
                        editor.apply();

                        editor = sp.edit();
                        keys = settingsObj.keys();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            Object val = settingsObj.opt(key);
                            if (val == null || val == org.json.JSONObject.NULL) continue;

                            boolean isKnownStringKey = "sp_fontSize".equals(key) || "sp_search_engine".equals(key) ||
                                    "sp_searchEngine".equals(key) || "sp_userAgent".equals(key) ||
                                    "profile".equals(key) || key.startsWith("icon_");

                            if (isKnownStringKey) {
                                editor.putString(key, String.valueOf(val));
                            } else if (val instanceof Boolean) {
                                editor.putBoolean(key, (Boolean) val);
                            } else if (val instanceof Integer) {
                                editor.putInt(key, (Integer) val);
                            } else if (val instanceof Long) {
                                editor.putLong(key, (Long) val);
                            } else if (val instanceof Double) {
                                editor.putFloat(key, ((Double) val).floatValue());
                            } else if (val instanceof Float) {
                                editor.putFloat(key, (Float) val);
                            } else if (val instanceof String) {
                                editor.putString(key, (String) val);
                            } else if (val instanceof org.json.JSONArray) {
                                org.json.JSONArray arr = (org.json.JSONArray) val;
                                java.util.Set<String> set = new java.util.HashSet<>();
                                for (int i = 0; i < arr.length(); i++) {
                                    set.add(arr.optString(i));
                                }
                                editor.putStringSet(key, set);
                            }
                        }
                        editor.apply();
                    } catch (Exception e) {
                        Log.e("Petal", "Error restoring settings", e);
                    }
                }

                handler.post(() -> {
                    NinjaToast.show(context, context.getString(R.string.app_done) + ": " + context.getString(R.string.settings_data_restore));
                });
            } catch (Exception e) {
                Log.e("Petal", "restoreFromJson error", e);
                handler.post(() -> {
                    NinjaToast.show(context, "Restore failed: " + e.getMessage());
                });
            }
        });
    }

    public static void backupData(Activity context, int i) {
        backupToJson(context, true, true, true, true);
    }

    public static void performAutoVersionBackup(Context context) {
        if (context == null) return;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                SharedPreferences sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
                int currentVersionCode = 0;
                try {
                    currentVersionCode = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
                } catch (Exception ignored) {}

                int lastVersionCode = sp.getInt("sp_last_app_version_code", 0);
                if (currentVersionCode > 0 && currentVersionCode != lastVersionCode) {
                    sp.edit().putInt("sp_last_app_version_code", currentVersionCode).apply();
                }

                org.json.JSONObject backupJson = new org.json.JSONObject();
                backupJson.put("version", 1);
                backupJson.put("app_version_code", currentVersionCode);
                backupJson.put("timestamp", System.currentTimeMillis());

                RecordAction action = new RecordAction(context);
                action.open(false);
                List<Record> bookmarks = action.listBookmark(context, false, 0);
                List<Record> history = action.listHistory(context);
                List<Record> startSites = action.listStartSites();
                String sessionJson = action.getSessionStateJson();
                List<String> domains = action.listDomains(RecordUnit.TABLE_STANDARD);
                List<String> trusted = action.listDomains(RecordUnit.TABLE_TRUSTED);
                List<String> protect = action.listDomains(RecordUnit.TABLE_PROTECTED);
                action.close();

                org.json.JSONArray bookmarksArray = new org.json.JSONArray();
                for (Record r : bookmarks) {
                    org.json.JSONObject obj = new org.json.JSONObject();
                    obj.put("title", r.getTitle() != null ? r.getTitle() : "");
                    obj.put("url", r.getURL() != null ? r.getURL() : "");
                    long bTime = r.getIconColor() > 0 ? r.getIconColor() : (r.getTime() > 0 ? r.getTime() : System.currentTimeMillis());
                    obj.put("time", bTime);
                    obj.put("iconColor", r.getIconColor() > 0 ? r.getIconColor() : bTime);
                    bookmarksArray.put(obj);
                }
                backupJson.put("bookmarks", bookmarksArray);

                org.json.JSONArray historyArray = new org.json.JSONArray();
                for (Record r : history) {
                    org.json.JSONObject obj = new org.json.JSONObject();
                    obj.put("title", r.getTitle() != null ? r.getTitle() : "");
                    obj.put("url", r.getURL() != null ? r.getURL() : "");
                    obj.put("time", r.getTime());
                    historyArray.put(obj);
                }
                backupJson.put("history", historyArray);

                org.json.JSONArray startArray = new org.json.JSONArray();
                for (Record r : startSites) {
                    org.json.JSONObject obj = new org.json.JSONObject();
                    obj.put("title", r.getTitle() != null ? r.getTitle() : "");
                    obj.put("url", r.getURL() != null ? r.getURL() : "");
                    obj.put("filename", r.getFilename() != null ? r.getFilename() : "");
                    obj.put("ordinal", r.getOrdinal());
                    startArray.put(obj);
                }
                backupJson.put("start_sites", startArray);

                if (sessionJson != null && !sessionJson.trim().isEmpty()) {
                    backupJson.put("tab_sessions", sessionJson);
                }

                org.json.JSONArray sitesArray = new org.json.JSONArray();
                for (String domain : domains) {
                    sitesArray.put(domain);
                }
                backupJson.put("saved_sites", sitesArray);

                org.json.JSONArray trustedArray = new org.json.JSONArray();
                for (String domain : trusted) {
                    trustedArray.put(domain);
                }
                backupJson.put("trusted_sites", trustedArray);

                org.json.JSONArray protectArray = new org.json.JSONArray();
                for (String domain : protect) {
                    protectArray.put(domain);
                }
                backupJson.put("protected_sites", protectArray);

                org.json.JSONObject settingsObj = new org.json.JSONObject();
                for (java.util.Map.Entry<String, ?> entry : sp.getAll().entrySet()) {
                    Object val = entry.getValue();
                    if (val != null) {
                        settingsObj.put(entry.getKey(), val);
                    }
                }
                backupJson.put("settings", settingsObj);

                File backupDir = getSafeBackupDir(context);
                File jsonFile = new File(backupDir, "petal_downgrade_snapshot.json");

                byte[] dataBytes = backupJson.toString(2).getBytes(StandardCharsets.UTF_8);
                try (FileOutputStream fos = new FileOutputStream(jsonFile, false)) {
                    fos.write(dataBytes);
                    fos.flush();
                    try {
                        fos.getFD().sync();
                    } catch (Exception ignored) {}
                }
                Log.i("Petal", "Automatic downgrade protection backup saved: " + jsonFile.getAbsolutePath());
            } catch (Exception e) {
                Log.e("Petal", "Failed to save automatic version snapshot: " + e.getMessage());
            }
        });
    }

    public static void restoreData(Activity context, int i) {
        restoreFromJson(context, true, true, true, true);
    }

    public static void exportList(Context context) {}
    public static void importList(Context context) {}
    public static void exportHistory(Context context) {}
    public static void importHistory(Context context) {}
}