package com.petal.browser.unit;

import android.util.Log;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WaybackMachineManager queries Internet Archive API to check and load archived snapshots
 * for broken links or 404 pages.
 */
public class WaybackMachineManager {

    private static final String TAG = "WaybackMachineManager";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface WaybackCallback {
        void onArchiveChecked(String archiveUrl);
    }

    /**
     * Checks Internet Archive for the latest archived snapshot of a URL.
     * Endpoint: http://archive.org/wayback/available?url=
     */
    public static void getArchivedSnapshot(final String pageUrl, final WaybackCallback callback) {
        if (pageUrl == null || pageUrl.trim().isEmpty()) {
            if (callback != null) callback.onArchiveChecked(null);
            return;
        }

        executor.execute(() -> {
            String archiveUrl = null;
            HttpURLConnection connection = null;
            try {
                String encodedUrl = URLEncoder.encode(pageUrl.trim(), "UTF-8");
                String apiUrl = "https://archive.org/wayback/available?url=" + encodedUrl;
                URL url = new URL(apiUrl);

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(3500);
                connection.setReadTimeout(3500);

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder builder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(builder.toString());
                    if (json.has("archived_snapshots")) {
                        JSONObject snapshots = json.getJSONObject("archived_snapshots");
                        if (snapshots.has("closest")) {
                            JSONObject closest = snapshots.getJSONObject("closest");
                            if (closest.optBoolean("available", false)) {
                                archiveUrl = closest.optString("url", "");
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Wayback Machine check failed for: " + pageUrl, e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            final String resultUrl = archiveUrl;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onArchiveChecked(resultUrl)
                );
            }
        });
    }
}
