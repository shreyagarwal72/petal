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
 * DuckAssistManager fetches DuckDuckGo Instant Answer / Abstract definitions for search queries.
 */
public class DuckAssistManager {

    private static final String TAG = "DuckAssistManager";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static class DuckAnswer {
        public String heading;
        public String abstractText;
        public String sourceUrl;
        public String iconUrl;

        public DuckAnswer(String heading, String abstractText, String sourceUrl, String iconUrl) {
            this.heading = heading != null ? heading : "";
            this.abstractText = abstractText != null ? abstractText : "";
            this.sourceUrl = sourceUrl != null ? sourceUrl : "";
            this.iconUrl = iconUrl != null ? iconUrl : "";
        }
    }

    public interface DuckAnswerCallback {
        void onAnswerFetched(DuckAnswer answer);
    }

    /**
     * Fetches DuckDuckGo Instant Answer abstract for a search query.
     * Endpoint: https://api.duckduckgo.com/?q=&format=json
     */
    public static void fetchInstantAnswer(final String query, final DuckAnswerCallback callback) {
        if (query == null || query.trim().isEmpty()) {
            if (callback != null) callback.onAnswerFetched(null);
            return;
        }

        executor.execute(() -> {
            DuckAnswer answer = null;
            HttpURLConnection connection = null;
            try {
                String encoded = URLEncoder.encode(query.trim(), "UTF-8");
                String apiUrl = "https://api.duckduckgo.com/?q=" + encoded + "&format=json&no_html=1";
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
                    String heading = json.optString("Heading", "");
                    String abstractText = json.optString("AbstractText", "");
                    String sourceUrl = json.optString("AbstractURL", "");
                    String iconUrl = json.optString("Image", "");

                    if (!abstractText.isEmpty()) {
                        answer = new DuckAnswer(heading, abstractText, sourceUrl, iconUrl);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "DuckAssist instant answer failed for: " + query, e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            final DuckAnswer finalAnswer = answer;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onAnswerFetched(finalAnswer)
                );
            }
        });
    }
}
