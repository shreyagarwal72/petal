package com.petal.browser.unit;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.petal.browser.view.NinjaToast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateUnit {

    private static final String GITHUB_RELEASES_API = "https://api.github.com/repos/shreyagarwal72/foss_browser/releases/latest";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static void checkForUpdates(final Activity activity, final boolean isLaunchCheck) {
        if (activity == null || activity.isFinishing()) return;

        executor.execute(() -> {
            try {
                URL url = new URL(GITHUB_RELEASES_API);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("User-Agent", "PetalBrowserApp");

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
                    final String latestVersion = json.optString("tag_name", "v1.5.0");
                    final String releaseNotes = json.optString("body", "Bug fixes and performance improvements.");
                    final String downloadUrl = json.optString("html_url", "https://github.com/shreyagarwal72/foss_browser/releases");

                    String ver = "v1.5.0";
                    try {
                        ver = "v" + activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
                    } catch (Exception ignored) {}
                    final String currentVersion = ver;

                    final boolean hasUpdate = !latestVersion.equalsIgnoreCase(currentVersion);

                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (activity.isFinishing()) return;
                        if (hasUpdate) {
                            new MaterialAlertDialogBuilder(activity)
                                    .setTitle("Update Available (" + latestVersion + ")")
                                    .setMessage("A new version of Petal Browser is available!\n\nRelease Notes:\n" + releaseNotes)
                                    .setPositiveButton("Download Update", (dialog, which) -> {
                                        try {
                                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl));
                                            activity.startActivity(intent);
                                        } catch (Exception e) {
                                            NinjaToast.show(activity, "Failed to open update link");
                                        }
                                    })
                                    .setNegativeButton("Later", (dialog, which) -> dialog.dismiss())
                                    .show();
                        } else if (!isLaunchCheck) {
                            NinjaToast.show(activity, "You are using the latest version of Petal Browser (" + currentVersion + ")");
                        }
                    });
                } else if (!isLaunchCheck) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            NinjaToast.show(activity, "You are using the latest version of Petal Browser (v1.5.0)")
                    );
                }
            } catch (Exception e) {
                Log.e("UpdateUnit", "Error checking for updates", e);
                if (!isLaunchCheck) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            NinjaToast.show(activity, "You are using the latest version of Petal Browser (v1.5.0)")
                    );
                }
            }
        });
    }
}
