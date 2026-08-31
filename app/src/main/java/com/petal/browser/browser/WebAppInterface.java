package com.petal.browser.browser;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import com.petal.browser.R;
import com.petal.browser.unit.HelperUnit;

public class WebAppInterface {
    private final Context mContext;
    public WebAppInterface(Context context) {
        this.mContext = context;
    }
    @JavascriptInterface
    public void processBlob(String base64Data, String ignoredMimeType, String fileName) {
        try {
            // "data:application/pdf;base64," Prefix herausschneiden, falls vorhanden
            if (base64Data.contains(",")) {
                base64Data = base64Data.split(",")[1];
            }
            // Base64 zu Bytes dekodieren
            byte[] fileBytes = Base64.decode(base64Data, Base64.DEFAULT);
            // Datei im Download-Ordner erstellen
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File file = new File(downloadDir, fileName);

            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(fileBytes);
                fos.flush();
            }
            showSnackbar();
        } catch (IOException | IllegalArgumentException e) {
            Toast.makeText(mContext, mContext.getString(R.string.app_error), Toast.LENGTH_SHORT).show();
        }
    }
    // Hilfsmethode für die Snackbar im UI-Thread
    private void showSnackbar() {
        if (mContext instanceof Activity) {
            Activity activity = (Activity) mContext;
            activity.runOnUiThread(() -> {
                View rootView = activity.findViewById(android.R.id.content);
                if (rootView != null) {
                    String text = mContext.getString(R.string.app_done) + ". " + mContext.getString(R.string.menu_download) +"?";
                    Snackbar snackbar = Snackbar.make(rootView, text, Snackbar.LENGTH_SHORT);
                    snackbar.setAction(mContext.getString(R.string.app_ok), v -> {
                        if (mContext instanceof com.petal.browser.activity.BrowserActivity) {
                            ((com.petal.browser.activity.BrowserActivity) mContext).showDownloads();
                        } else {
                            try {
                                mContext.startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                            } catch (Exception ignored) {}
                        }
                    });
                    snackbar.show();
                }
            });
        }
    }
}

