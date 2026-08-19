package com.petal.browser.unit;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.petal.browser.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Petal Browser Update Engine with Material 3 Expressive UI.
 * Strictly tracks and notifies the user only when a newer update version is available.
 */
public class UpdateUnit {

    private static final String TAG = "UpdateUnit";
    private static final String GITHUB_RELEASES_API = "https://api.github.com/repos/shreyagarwal72/foss_browser/releases/latest";
    private static final String PREF_KEY_LAST_CHECK_TIME = "sp_update_last_check_timestamp";
    private static final String PREF_KEY_SKIP_VERSION = "sp_update_skipped_version";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * Checks for updates from the official GitHub Release channel.
     * Only displays a dialog or prompt if a genuine next version exists.
     *
     * @param activity      The host activity.
     * @param isLaunchCheck True if called automatically on app startup; false if user tapped "Check for Updates".
     */
    public static void checkForUpdates(final Activity activity, final boolean isLaunchCheck) {
        if (activity == null || activity.isFinishing()) return;

        final Context context = activity.getApplicationContext();
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);

        // Track last check timestamp
        sp.edit().putLong(PREF_KEY_LAST_CHECK_TIME, System.currentTimeMillis()).apply();

        executor.execute(() -> {
            try {
                URL url = new URL(GITHUB_RELEASES_API);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("User-Agent", "PetalBrowserApp/1.0.2");

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
                    final String latestTag = json.optString("tag_name", "v1.0.2");
                    final String releaseNotes = json.optString("body", "Performance polish, security enhancements, and stability improvements.");

                    // Locate direct APK asset download URL if available, fallback to html_url release page
                    String apkDownloadUrl = json.optString("html_url", "https://github.com/shreyagarwal72/foss_browser/releases");
                    JSONArray assets = json.optJSONArray("assets");
                    if (assets != null && assets.length() > 0) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.getJSONObject(i);
                            String assetName = asset.optString("name", "");
                            if (assetName.endsWith(".apk")) {
                                apkDownloadUrl = asset.optString("browser_download_url", apkDownloadUrl);
                                break;
                            }
                        }
                    }
                    final String finalDownloadUrl = apkDownloadUrl;

                    String currentVer = "v1.0.2";
                    try {
                        currentVer = "v" + activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
                    } catch (Exception ignored) {}
                    final String currentVersion = currentVer;

                    // Strictly check if latest release is newer than current version
                    final boolean isNextUpdateAvailable = isNewerVersion(latestTag, currentVersion);

                    // Check if user previously chose to skip this specific version (only applies to launch checks)
                    String skippedVersion = sp.getString(PREF_KEY_SKIP_VERSION, "");
                    boolean isSkipped = isLaunchCheck && latestTag.equalsIgnoreCase(skippedVersion);

                    activity.runOnUiThread(() -> {
                        if (activity.isFinishing()) return;

                        if (isNextUpdateAvailable && !isSkipped) {
                            showMaterial3ExpressiveUpdateDialog(activity, currentVersion, latestTag, releaseNotes, finalDownloadUrl, isLaunchCheck);
                        } else if (!isLaunchCheck) {
                            // User manually triggered check and is already on latest version
                            showUpToDateToast(activity, currentVersion);
                        }
                    });
                } else if (!isLaunchCheck) {
                    activity.runOnUiThread(() -> showUpToDateToast(activity, getAppVersion(activity)));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking for updates", e);
                if (!isLaunchCheck) {
                    activity.runOnUiThread(() ->
                        Toast.makeText(activity, "Petal is up to date (" + getAppVersion(activity) + ")", Toast.LENGTH_SHORT).show()
                    );
                }
            }
        });
    }

    /**
     * Renders a Material 3 Expressive Update Dialog with update metrics, release notes, and action buttons.
     */
    private static void showMaterial3ExpressiveUpdateDialog(
            final Activity activity,
            final String currentVersion,
            final String latestVersion,
            final String releaseNotes,
            final String downloadUrl,
            final boolean isLaunchCheck
    ) {
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_petal_update_expressive, null);

        TextView tvTitle = dialogView.findViewById(R.id.update_title);
        TextView tvSubhead = dialogView.findViewById(R.id.update_subhead);
        TextView tvCurrentVersion = dialogView.findViewById(R.id.update_current_version);
        TextView tvLatestVersion = dialogView.findViewById(R.id.update_latest_version);
        TextView tvReleaseNotes = dialogView.findViewById(R.id.update_release_notes);
        MaterialButton btnUpdateNow = dialogView.findViewById(R.id.btn_update_now);
        MaterialButton btnLater = dialogView.findViewById(R.id.btn_update_later);
        MaterialButton btnSkipVersion = dialogView.findViewById(R.id.btn_skip_version);

        if (tvTitle != null) tvTitle.setText("New Update Available");
        if (tvSubhead != null) tvSubhead.setText("A new version of Petal Browser is ready");
        if (tvCurrentVersion != null) tvCurrentVersion.setText(currentVersion);
        if (tvLatestVersion != null) tvLatestVersion.setText(latestVersion);
        if (tvReleaseNotes != null) {
            String formattedNotes = (releaseNotes != null && !releaseNotes.trim().isEmpty())
                    ? releaseNotes.trim()
                    : "• Security & performance improvements\n• Bug fixes & UI polish";
            tvReleaseNotes.setText(formattedNotes);
        }

        final AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setView(dialogView)
                .setCancelable(!isLaunchCheck)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        if (btnUpdateNow != null) {
            btnUpdateNow.setOnClickListener(v -> {
                dialog.dismiss();
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(activity, "Unable to launch download link", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnLater != null) {
            btnLater.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnSkipVersion != null) {
            btnSkipVersion.setOnClickListener(v -> {
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext());
                sp.edit().putString(PREF_KEY_SKIP_VERSION, latestVersion).apply();
                Toast.makeText(activity, "Skipped version " + latestVersion, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        }

        dialog.show();
    }

    private static void showUpToDateToast(Activity activity, String currentVersion) {
        Toast.makeText(activity, "Petal is up to date (" + currentVersion + ")", Toast.LENGTH_SHORT).show();
    }

    private static String getAppVersion(Activity activity) {
        try {
            return "v" + activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "v1.0.2";
        }
    }

    /**
     * SemVer comparator ensuring we ONLY flag an update when latest is strictly greater than current.
     */
    public static boolean isNewerVersion(String latest, String current) {
        if (latest == null || current == null) return false;
        String cleanLatest = latest.trim().replaceAll("^[vV]", "");
        String cleanCurrent = current.trim().replaceAll("^[vV]", "");

        String[] latestParts = cleanLatest.split("\\.");
        String[] currentParts = cleanCurrent.split("\\.");

        int length = Math.max(latestParts.length, currentParts.length);
        for (int i = 0; i < length; i++) {
            int latestNum = 0;
            int currentNum = 0;
            if (i < latestParts.length) {
                try { latestNum = Integer.parseInt(latestParts[i].replaceAll("[^0-9]", "")); } catch (Exception ignored) {}
            }
            if (i < currentParts.length) {
                try { currentNum = Integer.parseInt(currentParts[i].replaceAll("[^0-9]", "")); } catch (Exception ignored) {}
            }
            if (latestNum > currentNum) return true;
            if (latestNum < currentNum) return false;
        }
        return false;
    }
}
