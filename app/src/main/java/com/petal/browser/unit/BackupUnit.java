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
            // Unter Scoped Storage übernimmt der MediaStore das automatische Erstellen von Unterordnern beim ersten Schreiben.
            // Ein manuelles Vorerstellen über File-Objekte im öffentlichen Speicher ist ab Android 10 blockiert.
            Log.d("Petal", "Verzeichnis-Erstellung wird automatisch vom MediaStore verwaltet.");
        } else {
            // Klassischer Weg für ältere Geräte (Fehlerhaften Doppel-Slash '//' entfernt)
            File backupDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "browser_backup");
            if (!backupDir.exists() && !backupDir.mkdirs()) {
                Log.e("Petal", "Ordner konnte auf altem Gerät nicht erstellt werden.");
            }
        }
    }

    public static void backupData(Activity context, int i) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            if (i == 5) {
                exportBookmarksSimple(context);
            } else if (i == 4) {
                exportHistory(context);
            } else {
                exportList(context);
            }
            handler.post(() -> {
                String text = context.getString(R.string.app_done) + ": " + context.getString(R.string.setting_title_data);
                NinjaToast.show(context, text);
            });
        });
    }

    public static void restoreData(Activity context, int i) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            if (i == 5) {
                importBookmarksSimple(context);
            } else if (i == 4) {
                importHistory(context);
            } else {
                importList(context);
            }
            handler.post(() -> {
                String text = context.getString(R.string.app_done) + ": " + context.getString(R.string.settings_data_restore);
                NinjaToast.show(context, text);
            });
        });
    }

    public static void exportList(Context context) {
        RecordAction action = new RecordAction(context);
        List<String> list;
        String filename;
        action.open(false);
        list = action.listDomains(RecordUnit.TABLE_STANDARD);
        filename = "list_savedWebSites.txt";
        action.close();
        File file = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS), "browser_backup//" + filename);
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(file, false));
            for (String domain : list) {
                writer.write(domain);
                writer.newLine();
            }
            writer.close();
            String wasSuccessful = file.getAbsolutePath();
            if (wasSuccessful.isEmpty()) System.out.println("was not successful."); }
        catch (Exception ignored) { }
    }

    public static void importList(Context context) {
        try {
            String filename;
            List_standard listStandard;
            listStandard = new List_standard(context);
            filename = "list_savedWebSites.txt";
            File file = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS), "browser_backup//" + filename);
            RecordAction action = new RecordAction(context);
            action.open(true);
            listStandard.clearDomains();

            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!action.checkDomain(line, RecordUnit.TABLE_STANDARD)) listStandard.addDomain(line);
            }
            reader.close();
            action.close(); }
        catch (Exception e) {
            Log.w("browser", "Error reading file", e);
        }
    }

    public static void exportBookmarksSimple(Context context) {
        RecordAction action = new RecordAction(context);
        action.open(false);
        List<Record> list = action.listBookmark(context, false, 0);
        action.close();
        File fileTxt = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS), "browser_backup//list_bookmarks_simple.html");

        try {
            BufferedWriter writerTxt = new BufferedWriter(new FileWriter(fileTxt, false));
            for (Record record : list) {
                String type = BOOKMARK_TYPE_SIMPLE;
                type = type.replace(BOOKMARK_TITLE, record.getTitle());
                type = type.replace(BOOKMARK_URL, record.getURL());
                writerTxt.write(type);
                writerTxt.newLine();
            }
            writerTxt.close();
            String wasSuccessfulTxt = fileTxt.getAbsolutePath();
            if (wasSuccessfulTxt.isEmpty()) {System.out.println("was not successful."); }
        } catch (Exception ignored) { }
    }

    public static void importBookmarksSimple(Context context) {
        File file = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS), "browser_backup//list_bookmarks_simple.html");
        List<Record> list = new ArrayList<>();
        try {
            RecordAction action = new RecordAction(context);
            action.open(true);
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!((line.startsWith("<dt><a ") && line.endsWith("</a>")) || (line.startsWith("<DT><A ") && line.endsWith("</A>")))) {
                    continue; }
                String title = getBookmarkTitle(line);
                String url = extractLinks(line);
                //if no color defined yet set it red (123 is max: 11 for color + 16 for desktop mode + 32 for List_trusted + 64 for List_standard Content
                if (title.trim().isEmpty() || url.trim().isEmpty()) {
                    continue; }
                Record record = new Record();
                record.setTitle(title);
                record.setURL(url);
                record.setIconColor(1);

                if (!action.checkUrl(url, RecordUnit.TABLE_BOOKMARK)) list.add(record);}
            reader.close();
            list.sort(Comparator.comparing(Record::getTitle));
            for (Record record : list) {action.addBookmark(record);}
            action.close();
        } catch (Exception ignored) { }
        list.size();
    }

    private static String getBookmarkTitle(String line) {
        // Remove last </a>
        line = line.substring(0, line.length() - 4);
        int index = line.lastIndexOf(">");
        return line.substring(index + 1);
    }

    public static String extractLinks(String text) {
        List<String> links = new ArrayList<>();
        String link = null;
        Matcher m = Patterns.WEB_URL.matcher(text);
        while (m.find()) {
            String url = m.group();
            Log.d("Petal", "URL extracted: " + url);
            if (links.isEmpty()) {
                links.add(url);
                link = url;
            }
        }
        return link;
    }

    public static void exportHistory(Context context) {
        RecordAction action = new RecordAction(context);
        action.open(false);
        List<Record> list = action.listHistory(context);
        action.close();
        File fileTxt = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS), "browser_backup/history_backup.txt");
        try {
            File dir = fileTxt.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            BufferedWriter writer = new BufferedWriter(new FileWriter(fileTxt, false));
            for (Record record : list) {
                String title = record.getTitle() != null ? record.getTitle().replace("|", " ") : "";
                String url = record.getURL() != null ? record.getURL() : "";
                long time = record.getTime();
                if (!url.isEmpty()) {
                    writer.write(url + "|" + title + "|" + time);
                    writer.newLine();
                }
            }
            writer.close();
        } catch (Exception e) {
            Log.e("Petal", "exportHistory error", e);
        }
    }

    public static void importHistory(Context context) {
        File fileTxt = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS), "browser_backup/history_backup.txt");
        if (!fileTxt.exists()) return;
        try {
            RecordAction action = new RecordAction(context);
            action.open(true);
            BufferedReader reader = new BufferedReader(new FileReader(fileTxt));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\|", 3);
                if (parts.length >= 2) {
                    String url = parts[0];
                    String title = parts[1];
                    long time = System.currentTimeMillis();
                    if (parts.length >= 3) {
                        try { time = Long.parseLong(parts[2]); } catch (Exception ignored) {}
                    }
                    if (!action.checkUrl(url, RecordUnit.TABLE_HISTORY)) {
                        action.addHistory(new Record(title, url, time, 0L));
                    }
                }
            }
            reader.close();
            action.close();
        } catch (Exception e) {
            Log.e("Petal", "importHistory error", e);
        }
    }
}