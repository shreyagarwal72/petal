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
object OpenGraphMetadataManager {

    private const val TAG = "OpenGraphMetadata";
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    class OgMetadata(
        public val title;
        public val description;
        public val imageUrl;
        public val logoUrl;
        public val publisher;

        public OgMetadata(val title, val description, val imageUrl, val logoUrl, val publisher) {
            this.title = title != null ? title : "";
            this.description = description != null ? description : "";
            this.imageUrl = imageUrl != null ? imageUrl : "";
            this.logoUrl = logoUrl != null ? logoUrl : "";
            this.publisher = publisher != null ? publisher : "";
        }
    }

    fun interface OgMetadataCallback {
        void onMetadataFetched(OgMetadata metadata);
    }

    /**
     * Fetches OpenGraph site metadata using Microlink API.
     * Endpoint: https://api.microlink.io/?url=
     */
    @JvmStatic
    fun fetchMetadata(targetUrl: String?, callback: OgMetadataCallback?) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            if (callback != null) callback.onMetadataFetched(null);
            return;
        }

        executor.execute {
            OgMetadata metadata = null
            var connection: HttpURLConnection? = null
            try {
                val apiUrl = "https://api.microlink.io/?url=" + URLEncoder.encode(targetUrl.trim(), "UTF-8");
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

                    JSONObject root = JSONObject(builder.toString());
                    if (root.has("data")) {
                        JSONObject data = root.getJSONObject("data");
                        val title = data.optString("title", "");
                        val description = data.optString("description", "");
                        val publisher = data.optString("publisher", "");

                        val imageUrl = "";
                        if (data.has("image")) {
                            imageUrl = data.getJSONObject("image").optString("url", "");
                        }

                        val logoUrl = "";
                        if (data.has("logo")) {
                            logoUrl = data.getJSONObject("logo").optString("url", "");
                        }

                        metadata = new OgMetadata(title, description, imageUrl, logoUrl, publisher);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "OpenGraph metadata fetch failed for: " + targetUrl, e);
            } finally {
                if (connection != null) connection?.disconnect()
            }

            final OgMetadata finalMeta = metadata;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onMetadataFetched(finalMeta)
                );
            }
        }
    }
}
