package com.petal.browser.unit;

import android.content.Context;
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
 * WikipediaSummaryManager fetches instant article summaries, thumbnails, and descriptions
 * for terms typed into the search bar or long-pressed on web pages.
 */
public class WikipediaSummaryManager {

    private static final String TAG = "WikipediaSummaryManager";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static class WikiSummary {
        public String title;
        public String description;
        public String extract;
        public String thumbnailUrl;
        public String articleUrl;

        public WikiSummary(String title, String description, String extract, String thumbnailUrl, String articleUrl) {
            this.title = title != null ? title : "";
            this.description = description != null ? description : "";
            this.extract = extract != null ? extract : "";
            this.thumbnailUrl = thumbnailUrl != null ? thumbnailUrl : "";
            this.articleUrl = articleUrl != null ? articleUrl : "";
        }
    }

    public interface WikiSummaryCallback {
        void onSummaryFetched(WikiSummary summary);
    }

    /**
     * Fetches Wikipedia summary card for a title term.
     * Endpoint: https://en.wikipedia.org/api/rest_v1/page/summary/{title}
     */
    public static void fetchSummary(final String term, final WikiSummaryCallback callback) {
        if (term == null || term.trim().isEmpty()) {
            if (callback != null) callback.onSummaryFetched(null);
            return;
        }

        executor.execute(() -> {
            WikiSummary summary = null;
            HttpURLConnection connection = null;
            try {
                String encodedTerm = URLEncoder.encode(term.trim().replace(" ", "_"), "UTF-8");
                String apiUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/" + encodedTerm;
                URL url = new URL(apiUrl);

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

                    summary = new WikiSummary(title, description, extract, thumbnailUrl, articleUrl);
                }
            } catch (Exception e) {
                Log.w(TAG, "Wikipedia summary fetch failed for: " + term, e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            final WikiSummary finalSummary = summary;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onSummaryFetched(finalSummary)
                );
            }
        });
    }
}
