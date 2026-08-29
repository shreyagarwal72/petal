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
 * YouTubeOembedManager fetches video title, thumbnail cover, and author details via YouTube oEmbed API.
 */
public class YouTubeOembedManager {

    private static final String TAG = "YouTubeOembedManager";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static class YouTubeMetadata {
        public String title;
        public String authorName;
        public String thumbnailUrl;
        public String videoUrl;

        public YouTubeMetadata(String title, String authorName, String thumbnailUrl, String videoUrl) {
            this.title = title != null ? title : "";
            this.authorName = authorName != null ? authorName : "";
            this.thumbnailUrl = thumbnailUrl != null ? thumbnailUrl : "";
            this.videoUrl = videoUrl != null ? videoUrl : "";
        }
    }

    public interface YouTubeMetadataCallback {
        void onMetadataFetched(YouTubeMetadata metadata);
    }

    /**
     * Fetches YouTube oEmbed metadata for video URL.
     * Endpoint: https://www.youtube.com/oembed?url=&format=json
     */
    public static void fetchMetadata(final String youtubeUrl, final YouTubeMetadataCallback callback) {
        if (youtubeUrl == null || youtubeUrl.trim().isEmpty()) {
            if (callback != null) callback.onMetadataFetched(null);
            return;
        }

        executor.execute(() -> {
            YouTubeMetadata metadata = null;
            HttpURLConnection connection = null;
            try {
                String apiUrl = "https://www.youtube.com/oembed?url=" + URLEncoder.encode(youtubeUrl.trim(), "UTF-8") + "&format=json";
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
                    metadata = new YouTubeMetadata(
                        json.optString("title", ""),
                        json.optString("author_name", ""),
                        json.optString("thumbnail_url", ""),
                        youtubeUrl.trim()
                    );
                }
            } catch (Exception e) {
                Log.w(TAG, "YouTube oEmbed fetch failed for: " + youtubeUrl, e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            final YouTubeMetadata finalMeta = metadata;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onMetadataFetched(finalMeta)
                );
            }
        });
    }
}
