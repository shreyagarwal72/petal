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
 * OpenGraphMetadataManager uses Microlink API to extract OpenGraph titles, descriptions,
 * logos, and cover images for rich site bookmarks and previews.
 */
public class OpenGraphMetadataManager {

    private static final String TAG = "OpenGraphMetadata";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static class OgMetadata {
        public String title;
        public String description;
        public String imageUrl;
        public String logoUrl;
        public String publisher;

        public OgMetadata(String title, String description, String imageUrl, String logoUrl, String publisher) {
            this.title = title != null ? title : "";
            this.description = description != null ? description : "";
            this.imageUrl = imageUrl != null ? imageUrl : "";
            this.logoUrl = logoUrl != null ? logoUrl : "";
            this.publisher = publisher != null ? publisher : "";
        }
    }

    public interface OgMetadataCallback {
        void onMetadataFetched(OgMetadata metadata);
    }

    /**
     * Fetches OpenGraph site metadata using Microlink API.
     * Endpoint: https://api.microlink.io/?url=
     */
    public static void fetchMetadata(final String targetUrl, final OgMetadataCallback callback) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            if (callback != null) callback.onMetadataFetched(null);
            return;
        }

        executor.execute(() -> {
            OgMetadata metadata = null;
            HttpURLConnection connection = null;
            try {
                String apiUrl = "https://api.microlink.io/?url=" + URLEncoder.encode(targetUrl.trim(), "UTF-8");
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

                    JSONObject root = new JSONObject(builder.toString());
                    if (root.has("data")) {
                        JSONObject data = root.getJSONObject("data");
                        String title = data.optString("title", "");
                        String description = data.optString("description", "");
                        String publisher = data.optString("publisher", "");

                        String imageUrl = "";
                        if (data.has("image")) {
                            imageUrl = data.getJSONObject("image").optString("url", "");
                        }

                        String logoUrl = "";
                        if (data.has("logo")) {
                            logoUrl = data.getJSONObject("logo").optString("url", "");
                        }

                        metadata = new OgMetadata(title, description, imageUrl, logoUrl, publisher);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "OpenGraph metadata fetch failed for: " + targetUrl, e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            final OgMetadata finalMeta = metadata;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onMetadataFetched(finalMeta)
                );
            }
        });
    }
}
