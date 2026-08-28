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
object HackerNewsFeedManager {

    private const val TAG = "HackerNewsFeedManager";
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    fun interface HackerNewsCallback {
        void onTopStoryIdsFetched(List<Long> storyIds);
    }

    /**
     * Fetches top story IDs from HackerNews API.
     * Endpoint: https://hacker-news.firebaseio.com/v0/topstories.json
     */
    @JvmStatic
    fun fetchTopStoryIds(callback: HackerNewsCallback?) {
        executor.execute {
            List<Long> ids = ArrayList();
            var connection: HttpURLConnection? = null
            try {
                URL url = new URL("https://hacker-news.firebaseio.com/v0/topstories.json");
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET");
                connection.connectTimeout = 3000);
                connection.readTimeout = 3000);

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = BufferedReader(InputStreamReader(connection.inputStream));
                    StringBuilder builder = StringBuilder()
                    val line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                    reader.close();

                    JSONArray jsonArray = JSONArray(builder.toString());
                    val limit = Math.min(jsonArray.length(), 10);
                    for (val i = 0; i < limit; i++) {
                        ids.add(jsonArray.getLong(i));
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "HackerNews top stories fetch failed", e);
            } finally {
                if (connection != null) connection?.disconnect()
            }

            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onTopStoryIdsFetched(ids)
                );
            }
        }
    }
}
