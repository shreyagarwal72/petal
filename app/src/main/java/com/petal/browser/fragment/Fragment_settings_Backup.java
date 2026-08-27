package com.petal.browser.fragment;

import static android.os.Environment.DIRECTORY_DOCUMENTS;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.preference.PreferenceManager;

import com.google.android.material.snackbar.Snackbar;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Objects;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.petal.browser.R;
import com.petal.browser.preferences.BasePreferenceFragment;
import com.petal.browser.unit.BackupUnit;
import com.petal.browser.unit.HelperUnit;
import com.petal.browser.view.NinjaToast;

public class Fragment_settings_Backup extends BasePreferenceFragment {

    public static File sd;
    public static File data;
    public Context context;
    public Activity activity;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        setPreferencesFromResource(R.xml.preference_backup, rootKey);
        context = getContext();
        activity = getActivity();
        assert context != null;
        assert activity != null;

        sd = Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS);
        data = Environment.getDataDirectory();
        String database_app = "//data//" + requireActivity().getPackageName() + "//databases//Ninja4.db";
        String database_backup = "browser_backup//database.db";
        File sourceDB = new File(data, database_app);
        File backupDB = new File(sd, database_backup);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);

        Button ib_backup = activity.findViewById(R.id.ib_backup);
        ib_backup.setOnClickListener(v -> {
            LinearLayout root = getActivity().findViewById(R.id.root);
            Snackbar snackbarBottom = Snackbar.make(root, R.string.toast_backup, Snackbar.LENGTH_SHORT);
            HelperUnit.makeSnackbarRound(snackbarBottom);
            snackbarBottom.setAction(this.getString(R.string.app_ok), (r -> backup(activity)));
            snackbarBottom.show();
        });

        Button ib_restore = activity.findViewById(R.id.ib_restore);
        ib_restore.setOnClickListener(v -> {
            LinearLayout root = getActivity().findViewById(R.id.root);
            Snackbar snackbarBottom = Snackbar.make(root, R.string.hint_database, Snackbar.LENGTH_SHORT);
            HelperUnit.makeSnackbarRound(snackbarBottom);
            snackbarBottom.setAction(this.getString(R.string.app_ok), (r -> {
                if (!BackupUnit.checkPermissionStorage(context)) {
                    BackupUnit.requestPermission(activity);
                } else {
                    if (sp.getBoolean("database", false)) {
                        copyDirectory(backupDB, sourceDB);
                    }
                    if (sp.getBoolean("settings", false)) {
                        restoreUserPrefs(context);
                        BackupUnit.restoreData(getActivity(), 3);
                    }
                    if (sp.getBoolean("bookmark_simple", false)) {
                        BackupUnit.restoreData(getActivity(), 5);
                    }
                    if (sp.getBoolean("history", true)) {
                        BackupUnit.restoreData(getActivity(), 4);
                    }
                }
            }));
            snackbarBottom.show();
        });
    }

    public static void backup (Activity activity) {
        File sd = Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS);
        File data = Environment.getDataDirectory();
        String database_app = "//data//" + activity.getPackageName() + "//databases//Ninja4.db";
        String database_backup = "browser_backup//database.db";
        File sourceDB = new File(data, database_app);
        File backupDB = new File(sd, database_backup);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
        if (!BackupUnit.checkPermissionStorage(activity)) {
            BackupUnit.requestPermission(activity);
        } else {
            BackupUnit.makeBackupDir(activity);
            if (sp.getBoolean("database", false)) {
                copyDirectory(sourceDB, backupDB);
            }
            if (sp.getBoolean("settings", false)) {
                final File prefsFile = new File(activity.getFilesDir(), "../shared_prefs/" + activity.getPackageName() + "_preferences.xml");
                final File backupFile = new File(sd, "browser_backup/preferenceBackup.xml");
                copyDirectory(prefsFile, backupFile);
                BackupUnit.backupData(activity, 3);
            }
            if (sp.getBoolean("bookmark_simple", false)) {
                BackupUnit.backupData(activity, 5);
            }
            if (sp.getBoolean("history", true)) {
                BackupUnit.backupData(activity, 4);
            }
        }
    }

    public static void copyDirectory(File sourceLocation, File targetLocation) {
        try {
            if (sourceLocation.isDirectory()) {
                if (!targetLocation.exists() && !targetLocation.mkdirs()) {
                    throw new IOException("Cannot create dir " + targetLocation.getAbsolutePath());
                }
                String[] children = sourceLocation.list();
                for (String aChildren : Objects.requireNonNull(children)) {
                    copyDirectory( new File(sourceLocation, aChildren), new File(targetLocation, aChildren));
                }
            } else {
                // make sure the directory we plan to store the recording in exists
                File directory = targetLocation.getParentFile();
                if (directory != null && !directory.exists() && !directory.mkdirs()) {
                    throw new IOException("Cannot create dir " + directory.getAbsolutePath());
                }
                InputStream in = Files.newInputStream(sourceLocation.toPath());
                OutputStream out = Files.newOutputStream(targetLocation.toPath());
                // Copy the bits from InputStream to OutputStream
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                in.close();
                out.close();
            }
        } catch (IOException e) {
            Log.i("Petal", "copyDirectory:" + e);
        }
    }

    private void restoreUserPrefs(Context context) {
        final File backupFile = new File(sd, "browser_backup/preferenceBackup.xml");
        try {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor editor = sharedPreferences.edit();

            InputStream inputStream = Files.newInputStream(backupFile.toPath());
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(inputStream);
            Element root = doc.getDocumentElement();
            Node child = root.getFirstChild();
            while (child != null) {
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) child;
                    String type = element.getNodeName();
                    String name = element.getAttribute("name");
                    editor.remove(name);
                    if (type.equals("string")) {
                        String value = element.getTextContent();
                        editor.putString(name, value);
                    } else if (type.equals("boolean")) {
                        String value = element.getAttribute("value");
                        editor.putBoolean(name, value.equals("true"));
                    } else if (type.equals("int")) {
                        String value = element.getAttribute("value");
                        try { editor.putInt(name, Integer.parseInt(value)); } catch (Exception ignored) {}
                    } else if (type.equals("long")) {
                        String value = element.getAttribute("value");
                        try { editor.putLong(name, Long.parseLong(value)); } catch (Exception ignored) {}
                    } else if (type.equals("float")) {
                        String value = element.getAttribute("value");
                        try { editor.putFloat(name, Float.parseFloat(value)); } catch (Exception ignored) {}
                    }
                }
                child = child.getNextSibling();
            }
            editor.apply();
        } catch (IOException | SAXException | ParserConfigurationException e) {
            Log.i("Petal", "restoreUserPrefs:" + e);
            NinjaToast.show(context, context.getString(R.string.app_error));
        }
    }
}