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
    private static final String GITHUB_RELEASES_API = "https://api.github.com/repos/shreyagarwal72/petal/releases/latest";
    private static final String GITHUB_RELEASES_PAGE = "https://github.com/shreyagarwal72/petal/releases";
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
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("User-Agent", "PetalBrowserApp/" + getAppVersion(activity));

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
                    final String latestTag = json.optString("tag_name", getAppVersion(activity));
                    final String releaseNotes = json.optString("body", "Performance polish, security enhancements, and stability improvements.");

                    // Locate direct APK asset download URL if available, fallback to html_url release page
                    String apkDownloadUrl = json.optString("html_url", GITHUB_RELEASES_PAGE);
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

                    String currentVer = getAppVersion(activity);
                    final String currentVersion = currentVer;

                    // Strictly check if latest release is newer than current version
                    final boolean isNextUpdateAvailable = isNewerVersion(latestTag, currentVersion);

                    // Check if user previously chose to skip this specific version (only applies to launch checks)
                    String skippedVersion = sp.getString(PREF_KEY_SKIP_VERSION, "");
                    boolean isSkipped = isLaunchCheck && latestTag.equalsIgnoreCase(skippedVersion);

                    if (isNextUpdateAvailable && !isSkipped) {
                        // Automatically push system update notification with custom taglines
                        sendUpdateNotification(context, latestTag, finalDownloadUrl);
                    }

                    activity.runOnUiThread(() -> {
                        if (activity.isFinishing()) return;

                        if (isNextUpdateAvailable && !isSkipped) {
                            com.petal.browser.ui.components.PetalUpdateSheetBridge.showUpdateSheet(
                                (androidx.activity.ComponentActivity) activity,
                                new com.petal.browser.ui.components.PetalUpdateInfo(
                                    latestTag,
                                    releaseNotes,
                                    finalDownloadUrl,
                                    GITHUB_RELEASES_PAGE,
                                    true
                                )
                            );
                        } else if (!isLaunchCheck) {
                            com.petal.browser.ui.components.PetalUpdateSheetBridge.showUpdateSheet(
                                (androidx.activity.ComponentActivity) activity,
                                new com.petal.browser.ui.components.PetalUpdateInfo(
                                    currentVersion,
                                    releaseNotes,
                                    finalDownloadUrl,
                                    GITHUB_RELEASES_PAGE,
                                    false
                                )
                            );
                        }
                    });
                } else if (!isLaunchCheck) {
                    activity.runOnUiThread(() ->
                        com.petal.browser.ui.components.PetalUpdateSheetBridge.showUpdateSheet(
                            (androidx.activity.ComponentActivity) activity,
                            new com.petal.browser.ui.components.PetalUpdateInfo(
                                getAppVersion(activity),
                                "You are currently running the latest build of Petal Browser.",
                                "",
                                GITHUB_RELEASES_PAGE,
                                false
                            )
                        )
                    );
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking for updates", e);
                if (!isLaunchCheck) {
                    activity.runOnUiThread(() ->
                        com.petal.browser.ui.components.PetalUpdateSheetBridge.showUpdateSheet(
                            (androidx.activity.ComponentActivity) activity,
                            new com.petal.browser.ui.components.PetalUpdateInfo(
                                getAppVersion(activity),
                                "You are currently running the latest build of Petal Browser.",
                                "",
                                GITHUB_RELEASES_PAGE,
                                false
                            )
                        )
                    );
                }
            }
        });
    }

    /**
     * Automatically pushes a system notification for available app updates with custom user taglines.
     */
    public static void sendUpdateNotification(Context context, String latestTag, String downloadUrl) {
        if (context == null) return;
        try {
            android.app.NotificationManager nm = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            String channelId = "petal_updates_channel";
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                android.app.NotificationChannel channel = new android.app.NotificationChannel(
                        channelId,
                        "App Updates",
                        android.app.NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Notifications for new Petal Browser updates and releases");
                nm.createNotificationChannel(channel);
            }

            String[] customLines = new String[]{
                    "Step into the next era. Update now.",
                    "Something massive just landed. Update to unlock it.",
                    "Warning: Updating may cause extreme satisfaction"
            };
            String body = customLines[new java.util.Random().nextInt(customLines.length)];

            Intent intent = new Intent(context, com.petal.browser.activity.BrowserActivity.class);
            intent.setAction("com.petal.browser.action.SHOW_UPDATE");
            intent.putExtra("update_version", latestTag);
            intent.putExtra("update_url", downloadUrl);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                    context,
                    9901,
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT | (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M ? android.app.PendingIntent.FLAG_IMMUTABLE : 0)
            );

            androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("Petal Browser " + latestTag + " Available!")
                    .setContentText(body)
                    .setStyle(new androidx.core.app.NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent);

            nm.notify(9901, builder.build());
        } catch (Exception e) {
            Log.e(TAG, "Error posting update notification", e);
        }
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
        if (tvSubhead != null) tvSubhead.setText("Squashed bugs, added magic. You know what to do");
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
                downloadAndInstallApk(activity, downloadUrl, latestVersion);
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

    private static void downloadAndInstallApk(final Activity activity, final String apkUrl, final String version) {
        Toast.makeText(activity, "Downloading update " + version + "...", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            try {
                java.io.File apkFile = new java.io.File(activity.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "petal_update_" + version + ".apk");
                if (apkFile.exists()) apkFile.delete();

                String currentUrl = apkUrl;
                HttpURLConnection conn = null;
                int redirects = 0;
                while (redirects < 5) {
                    URL url = new URL(currentUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("User-Agent", "PetalBrowserApp/" + version);
                    conn.setInstanceFollowRedirects(true);
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    int status = conn.getResponseCode();
                    if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                        String redirectUrl = conn.getHeaderField("Location");
                        if (redirectUrl != null && !redirectUrl.isEmpty()) {
                            currentUrl = redirectUrl;
                            redirects++;
                            continue;
                        }
                    }
                    break;
                }

                if (conn == null || conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new Exception("HTTP status " + (conn != null ? conn.getResponseCode() : -1));
                }

                try (java.io.InputStream is = new java.io.BufferedInputStream(conn.getInputStream(), 65536);
                     java.io.OutputStream os = new java.io.BufferedOutputStream(new java.io.FileOutputStream(apkFile), 65536)) {
                    byte[] buffer = new byte[65536];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        os.write(buffer, 0, len);
                    }
                    os.flush();
                }

                activity.runOnUiThread(() -> installApk(activity, apkFile));
            } catch (Exception e) {
                Log.e(TAG, "Error downloading update APK", e);
                activity.runOnUiThread(() -> {
                    Toast.makeText(activity, "Download failed, opening browser...", Toast.LENGTH_SHORT).show();
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        activity.startActivity(intent);
                    } catch (Exception ignored) {}
                });
            }
        });
    }

    private static void installApk(Activity activity, java.io.File apkFile) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (!activity.getPackageManager().canRequestPackageInstalls()) {
                    Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                    intent.setData(Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivity(intent);
                    Toast.makeText(activity, "Please grant permission to install updates", Toast.LENGTH_LONG).show();
                    return;
                }
            }

            Uri apkUri = androidx.core.content.FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + ".fileprovider",
                    apkFile
            );

            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(installIntent);
        } catch (Exception e) {
            Log.e(TAG, "Error launching package installer", e);
            Toast.makeText(activity, "Failed to launch installer: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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
