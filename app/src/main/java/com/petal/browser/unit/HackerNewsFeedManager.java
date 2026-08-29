package com.petal.browser.unit;

import android.util.Log;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HackerNewsFeedManager fetches top stories via Firebase HackerNews API.
 */
public class HackerNewsFeedManager {

    private static final String TAG = "HackerNewsFeedManager";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface HackerNewsCallback {
        void onTopStoryIdsFetched(List<Long> storyIds);
    }

    /**
     * Fetches top story IDs from HackerNews API.
     * Endpoint: https://hacker-news.firebaseio.com/v0/topstories.json
     */
    public static void fetchTopStoryIds(final HackerNewsCallback callback) {
        executor.execute(() -> {
            List<Long> ids = new ArrayList<>();
            HttpURLConnection connection = null;
            try {
                URL url = new URL("https://hacker-news.firebaseio.com/v0/topstories.json");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder builder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                    reader.close();

                    JSONArray jsonArray = new JSONArray(builder.toString());
                    int limit = Math.min(jsonArray.length(), 10);
                    for (int i = 0; i < limit; i++) {
                        ids.add(jsonArray.getLong(i));
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "HackerNews top stories fetch failed", e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onTopStoryIdsFetched(ids)
                );
            }
        });
    }
}
