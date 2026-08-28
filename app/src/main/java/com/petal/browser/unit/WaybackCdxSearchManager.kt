package com.petal.browser.unit;

import android.util.Log;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WaybackCdxSearchManager searches Wayback Machine CDX API for historical web page snapshot revisions.
 */
object WaybackCdxSearchManager {

    private const val TAG = "WaybackCdxSearch";
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    fun interface CdxCallback {
        void onSnapshotsFetched(List<String> timestampUrls);
    }

    /**
     * Queries CDX API for historical timestamps of a web page URL.
     * Endpoint: http://web.archive.org/cdx/search/cdx?url=&output=json&limit=10
     */
    @JvmStatic
    fun fetchHistoricalSnapshots(pageUrl: String?, callback: CdxCallback?) {
        if (pageUrl == null || pageUrl.trim().isEmpty()) {
            if (callback != null) callback.onSnapshotsFetched(ArrayList());
            return;
        }

        executor.execute {
            List<String> results = ArrayList();
            var connection: HttpURLConnection? = null
            try {
                val encodedUrl = URLEncoder.encode(pageUrl.trim(), "UTF-8");
                val apiUrl = "https://web.archive.org/cdx/search/cdx?url=" + encodedUrl + "&output=json&limit=10";
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

                    JSONArray jsonArray = JSONArray(builder.toString());
                    for (val i = 1; i < jsonArray.length(); i++) {
                        JSONArray row = jsonArray.getJSONArray(i);
                        if (row.length() >= 3) {
                            val timestamp = row.getString(1);
                            val origUrl = row.getString(2);
                            results.add("https://web.archive.org/web/" + timestamp + "/" + origUrl);
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Wayback CDX search failed for: " + pageUrl, e);
            } finally {
                if (connection != null) connection?.disconnect()
            }

            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onSnapshotsFetched(results)
                );
            }
        }
    }
}
