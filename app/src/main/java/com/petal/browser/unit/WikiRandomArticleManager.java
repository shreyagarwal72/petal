package com.petal.browser.unit;

import android.util.Log;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WikiRandomArticleManager fetches a random article summary from Wikipedia for discovery cards.
 */
public class WikiRandomArticleManager {

    private static final String TAG = "WikiRandomArticle";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface WikiRandomCallback {
        void onRandomArticleFetched(WikipediaSummaryManager.WikiSummary summary);
    }

    /**
     * Fetches a random article summary from Wikipedia.
     * Endpoint: https://en.wikipedia.org/api/rest_v1/page/random/summary
     */
    public static void fetchRandomArticle(final WikiRandomCallback callback) {
        executor.execute(() -> {
            WikipediaSummaryManager.WikiSummary summary = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL("https://en.wikipedia.org/api/rest_v1/page/random/summary");
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
                    String title = json.optString("title", "");
                    String description = json.optString("description", "");
                    String extract = json.optString("extract", "");

                    String thumbnailUrl = "";
                    if (json.has("thumbnail")) {
                        thumbnailUrl = json.getJSONObject("thumbnail").optString("source", "");
                    }

                    String articleUrl = "";
                    if (json.has("content_urls") && json.getJSONObject("content_urls").has("desktop")) {
                        articleUrl = json.getJSONObject("content_urls").getJSONObject("desktop").optString("page", "");
                    }

                    summary = new WikipediaSummaryManager.WikiSummary(title, description, extract, thumbnailUrl, articleUrl);
                }
            } catch (Exception e) {
                Log.w(TAG, "Random Wikipedia article fetch failed", e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            final WikipediaSummaryManager.WikiSummary finalSummary = summary;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onRandomArticleFetched(finalSummary)
                );
            }
        });
    }
}
