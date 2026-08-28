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
object YouTubeOembedManager {

    private const val TAG = "YouTubeOembedManager";
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    class YouTubeMetadata(
        public val title;
        public val authorName;
        public val thumbnailUrl;
        public val videoUrl;

        public YouTubeMetadata(val title, val authorName, val thumbnailUrl, val videoUrl) {
            this.title = title != null ? title : "";
            this.authorName = authorName != null ? authorName : "";
            this.thumbnailUrl = thumbnailUrl != null ? thumbnailUrl : "";
            this.videoUrl = videoUrl != null ? videoUrl : "";
        }
    }

    fun interface YouTubeMetadataCallback {
        void onMetadataFetched(YouTubeMetadata metadata);
    }

    /**
     * Fetches YouTube oEmbed metadata for video URL.
     * Endpoint: https://www.youtube.com/oembed?url=&format=json
     */
    @JvmStatic
    fun fetchMetadata(youtubeUrl: String?, callback: YouTubeMetadataCallback?) {
        if (youtubeUrl == null || youtubeUrl.trim().isEmpty()) {
            if (callback != null) callback.onMetadataFetched(null);
            return;
        }

        executor.execute {
            YouTubeMetadata metadata = null
            var connection: HttpURLConnection? = null
            try {
                val apiUrl = "https://www.youtube.com/oembed?url=" + URLEncoder.encode(youtubeUrl.trim(), "UTF-8") + "&format=json";
                URL url = new URL(apiUrl);

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

                    JSONObject json = JSONObject(builder.toString());
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
                if (connection != null) connection?.disconnect()
            }

            final YouTubeMetadata finalMeta = metadata;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onMetadataFetched(finalMeta)
                );
            }
        }
    }
}
