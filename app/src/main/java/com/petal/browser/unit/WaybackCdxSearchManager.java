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
public class WaybackCdxSearchManager {

    private static final String TAG = "WaybackCdxSearch";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface CdxCallback {
        void onSnapshotsFetched(List<String> timestampUrls);
    }

    /**
     * Queries CDX API for historical timestamps of a web page URL.
     * Endpoint: http://web.archive.org/cdx/search/cdx?url=&output=json&limit=10
     */
    public static void fetchHistoricalSnapshots(final String pageUrl, final CdxCallback callback) {
        if (pageUrl == null || pageUrl.trim().isEmpty()) {
            if (callback != null) callback.onSnapshotsFetched(new ArrayList<>());
            return;
        }

        executor.execute(() -> {
            List<String> results = new ArrayList<>();
            HttpURLConnection connection = null;
            try {
                String encodedUrl = URLEncoder.encode(pageUrl.trim(), "UTF-8");
                String apiUrl = "https://web.archive.org/cdx/search/cdx?url=" + encodedUrl + "&output=json&limit=10";
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

                    JSONArray jsonArray = new JSONArray(builder.toString());
                    for (int i = 1; i < jsonArray.length(); i++) {
                        JSONArray row = jsonArray.getJSONArray(i);
                        if (row.length() >= 3) {
                            String timestamp = row.getString(1);
                            String origUrl = row.getString(2);
                            results.add("https://web.archive.org/web/" + timestamp + "/" + origUrl);
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Wayback CDX search failed for: " + pageUrl, e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onSnapshotsFetched(results)
                );
            }
        });
    }
}
