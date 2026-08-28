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
object WaybackMachineManager {

    private const val TAG = "WaybackMachineManager";
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    fun interface WaybackCallback {
        void onArchiveChecked(val archiveUrl);
    }

    /**
     * Checks Internet Archive for the latest archived snapshot of a URL.
     * Endpoint: http://archive.org/wayback/available?url=
     */
    @JvmStatic
    fun getArchivedSnapshot(pageUrl: String?, callback: WaybackCallback?) {
        if (pageUrl == null || pageUrl.trim().isEmpty()) {
            if (callback != null) callback.onArchiveChecked(null);
            return;
        }

        executor.execute {
            val archiveUrl = null
            var connection: HttpURLConnection? = null
            try {
                val encodedUrl = URLEncoder.encode(pageUrl.trim(), "UTF-8");
                val apiUrl = "https://archive.org/wayback/available?url=" + encodedUrl;
                URL url = new URL(apiUrl);

                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET");
                connection.connectTimeout = 3500);
                connection.readTimeout = 3500);

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = BufferedReader(InputStreamReader(connection.inputStream));
                    StringBuilder builder = StringBuilder()
                    val line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                    reader.close();

                    JSONObject json = JSONObject(builder.toString());
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
                if (connection != null) connection?.disconnect()
            }

            val resultUrl = archiveUrl;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onArchiveChecked(resultUrl)
                );
            }
        }
    }
}
