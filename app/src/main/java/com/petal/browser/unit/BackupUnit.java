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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

    public static void makeBackupDir(Context context) {
        if (context == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.d("Petal", "Verzeichnis-Erstellung wird automatisch vom MediaStore verwaltet.");
        } else {
            File backupDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "browser_backup");
            if (!backupDir.exists() && !backupDir.mkdirs()) {
                Log.e("Petal", "Ordner konnte auf altem Gerät nicht erstellt werden.");
            }
        }
    }

    public static void backupToJson(Activity context, boolean backupBookmarks, boolean backupHistory, boolean backupSavedSites, boolean backupSettings) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            try {
                org.json.JSONObject backupJson = new org.json.JSONObject();
                backupJson.put("version", 1);
                backupJson.put("timestamp", System.currentTimeMillis());

                RecordAction action = new RecordAction(context);

                if (backupBookmarks) {
                    action.open(false);
                    List<Record> bookmarks = action.listBookmark(context, false, 0);
                    action.close();

                    org.json.JSONArray bookmarksArray = new org.json.JSONArray();
                    for (Record r : bookmarks) {
                        org.json.JSONObject obj = new org.json.JSONObject();
                        obj.put("title", r.getTitle() != null ? r.getTitle() : "");
                        obj.put("url", r.getURL() != null ? r.getURL() : "");
                        obj.put("time", r.getTime());
                        bookmarksArray.put(obj);
                    }
                    backupJson.put("bookmarks", bookmarksArray);
                }

                if (backupHistory) {
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
                }

                if (backupSavedSites) {
                    action.open(false);
                    List<String> domains = action.listDomains(RecordUnit.TABLE_STANDARD);
                    action.close();

                    org.json.JSONArray sitesArray = new org.json.JSONArray();
                    for (String domain : domains) {
                        sitesArray.put(domain);
                    }
                    backupJson.put("saved_sites", sitesArray);
                }

                if (backupSettings) {
                    android.content.SharedPreferences sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
                    org.json.JSONObject settingsObj = new org.json.JSONObject();
                    for (java.util.Map.Entry<String, ?> entry : sp.getAll().entrySet()) {
                        Object val = entry.getValue();
                        if (val != null) {
                            settingsObj.put(entry.getKey(), val);
                        }
                    }
                    backupJson.put("settings", settingsObj);
                }

                File backupDir = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS), "browser_backup");
                if (!backupDir.exists()) backupDir.mkdirs();
                File jsonFile = new File(backupDir, "petal_browser_backup.json");

                BufferedWriter writer = new BufferedWriter(new FileWriter(jsonFile, false));
                writer.write(backupJson.toString(2));
                writer.close();

                handler.post(() -> {
                    NinjaToast.show(context, context.getString(R.string.app_done) + ": Backup saved to " + jsonFile.getName());
                });
            } catch (Exception e) {
                Log.e("Petal", "backupToJson error", e);
                handler.post(() -> {
                    NinjaToast.show(context, "Backup failed: " + e.getMessage());
                });
            }
        });
    }

    public static void backupToUri(Context context, android.net.Uri uri, boolean backupBookmarks, boolean backupHistory, boolean backupSavedSites, boolean backupSettings) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            try {
                org.json.JSONObject backupJson = new org.json.JSONObject();
                backupJson.put("version", 1);
                backupJson.put("timestamp", System.currentTimeMillis());

                RecordAction action = new RecordAction(context);

                if (backupBookmarks) {
                    action.open(false);
                    List<Record> bookmarks = action.listBookmark(context, false, 0);
                    action.close();

                    org.json.JSONArray bookmarksArray = new org.json.JSONArray();
                    for (Record r : bookmarks) {
                        org.json.JSONObject obj = new org.json.JSONObject();
                        obj.put("title", r.getTitle() != null ? r.getTitle() : "");
                        obj.put("url", r.getURL() != null ? r.getURL() : "");
                        obj.put("time", r.getTime());
                        bookmarksArray.put(obj);
                    }
                    backupJson.put("bookmarks", bookmarksArray);
                }

                if (backupHistory) {
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
                }

                if (backupSavedSites) {
                    action.open(false);
                    List<String> domains = action.listDomains(RecordUnit.TABLE_STANDARD);
                    action.close();

                    org.json.JSONArray sitesArray = new org.json.JSONArray();
                    for (String domain : domains) {
                        sitesArray.put(domain);
                    }
                    backupJson.put("saved_sites", sitesArray);
                }

                if (backupSettings) {
                    android.content.SharedPreferences sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
                    org.json.JSONObject settingsObj = new org.json.JSONObject();
                    for (java.util.Map.Entry<String, ?> entry : sp.getAll().entrySet()) {
                        Object val = entry.getValue();
                        if (val != null) {
                            settingsObj.put(entry.getKey(), val);
                        }
                    }
                    backupJson.put("settings", settingsObj);
                }

                java.io.OutputStream os = context.getContentResolver().openOutputStream(uri);
                if (os != null) {
                    BufferedWriter writer = new BufferedWriter(new java.io.OutputStreamWriter(os));
                    writer.write(backupJson.toString(2));
                    writer.close();
                }

                handler.post(() -> {
                    NinjaToast.show(context, context.getString(R.string.app_done) + ": Backup saved successfully");
                });
            } catch (Exception e) {
                Log.e("Petal", "backupToUri error", e);
                handler.post(() -> {
                    NinjaToast.show(context, "Backup failed: " + e.getMessage());
                });
            }
        });
    }

    public static void restoreFromUri(Context context, android.net.Uri uri, boolean restoreBookmarks, boolean restoreHistory, boolean restoreSavedSites, boolean restoreSettings) {
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
                            String url = obj.optString("url", "");
                            long time = obj.optLong("time", System.currentTimeMillis());
                            if (!url.isEmpty() && !action.checkUrl(url, RecordUnit.TABLE_BOOKMARK)) {
                                Record record = new Record();
                                record.setTitle(title);
                                record.setURL(url);
                                record.setTime(time);
                                record.setIconColor(1);
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
                            String url = obj.optString("url", "");
                            long time = obj.optLong("time", System.currentTimeMillis());
                            if (!url.isEmpty() && !action.checkUrl(url, RecordUnit.TABLE_HISTORY)) {
                                action.addHistory(new Record(title, url, time, 0L));
                            }
                        }
                        action.close();
                    } catch (Exception e) {
                        Log.e("Petal", "Error restoring history", e);
                    }
                }

                if (restoreSavedSites && backupJson.has("saved_sites")) {
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
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            try {
                File backupDir = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS), "browser_backup");
                File jsonFile = new File(backupDir, "petal_browser_backup.json");
                if (!jsonFile.exists()) {
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
                            String url = obj.optString("url", "");
                            long time = obj.optLong("time", System.currentTimeMillis());
                            if (!url.isEmpty() && !action.checkUrl(url, RecordUnit.TABLE_BOOKMARK)) {
                                Record record = new Record();
                                record.setTitle(title);
                                record.setURL(url);
                                record.setTime(time);
                                record.setIconColor(1);
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
                            String url = obj.optString("url", "");
                            long time = obj.optLong("time", System.currentTimeMillis());
                            if (!url.isEmpty() && !action.checkUrl(url, RecordUnit.TABLE_HISTORY)) {
                                action.addHistory(new Record(title, url, time, 0L));
                            }
                        }
                        action.close();
                    } catch (Exception e) {
                        Log.e("Petal", "Error restoring history", e);
                    }
                }

                if (restoreSavedSites && backupJson.has("saved_sites")) {
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
                List<String> domains = action.listDomains(RecordUnit.TABLE_STANDARD);
                action.close();

                org.json.JSONArray bookmarksArray = new org.json.JSONArray();
                for (Record r : bookmarks) {
                    org.json.JSONObject obj = new org.json.JSONObject();
                    obj.put("title", r.getTitle() != null ? r.getTitle() : "");
                    obj.put("url", r.getURL() != null ? r.getURL() : "");
                    obj.put("time", r.getTime());
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

                org.json.JSONArray sitesArray = new org.json.JSONArray();
                for (String domain : domains) {
                    sitesArray.put(domain);
                }
                backupJson.put("saved_sites", sitesArray);

                org.json.JSONObject settingsObj = new org.json.JSONObject();
                for (java.util.Map.Entry<String, ?> entry : sp.getAll().entrySet()) {
                    Object val = entry.getValue();
                    if (val != null) {
                        settingsObj.put(entry.getKey(), val);
                    }
                }
                backupJson.put("settings", settingsObj);

                File backupDir = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS), "browser_backup");
                if (!backupDir.exists()) backupDir.mkdirs();
                File jsonFile = new File(backupDir, "petal_downgrade_snapshot.json");

                BufferedWriter writer = new BufferedWriter(new FileWriter(jsonFile, false));
                writer.write(backupJson.toString(2));
                writer.close();
                Log.i("Petal", "Automatic downgrade protection backup saved: " + jsonFile.getAbsolutePath());
            } catch (Exception e) {
                Log.e("Petal", "Failed to save automatic version snapshot", e);
            }
        });
    }

    public static void restoreData(Activity context, int i) {
        restoreFromJson(context, true, true, true, true);
    }

    public static void exportList(Context context) {}
    public static void importList(Context context) {}
    public static void exportBookmarksSimple(Context context) {}
    public static void importBookmarksSimple(Context context) {}
    public static void exportHistory(Context context) {}
    public static void importHistory(Context context) {}
}