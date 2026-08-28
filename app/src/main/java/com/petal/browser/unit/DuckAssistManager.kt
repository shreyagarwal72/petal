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
object DuckAssistManager {

    private const val TAG = "DuckAssistManager";
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    class DuckAnswer(
        public val heading;
        public val abstractText;
        public val sourceUrl;
        public val iconUrl;

        public DuckAnswer(val heading, val abstractText, val sourceUrl, val iconUrl) {
            this.heading = heading != null ? heading : "";
            this.abstractText = abstractText != null ? abstractText : "";
            this.sourceUrl = sourceUrl != null ? sourceUrl : "";
            this.iconUrl = iconUrl != null ? iconUrl : "";
        }
    }

    fun interface DuckAnswerCallback {
        void onAnswerFetched(DuckAnswer answer);
    }

    /**
     * Fetches DuckDuckGo Instant Answer abstract for a search query.
     * Endpoint: https://api.duckduckgo.com/?q=&format=json
     */
    @JvmStatic
    fun fetchInstantAnswer(query: String?, callback: DuckAnswerCallback?) {
        if (query == null || query.trim().isEmpty()) {
            if (callback != null) callback.onAnswerFetched(null);
            return;
        }

        executor.execute {
            DuckAnswer answer = null
            var connection: HttpURLConnection? = null
            try {
                val encoded = URLEncoder.encode(query.trim(), "UTF-8");
                val apiUrl = "https://api.duckduckgo.com/?q=" + encoded + "&format=json&no_html=1";
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
                    val heading = json.optString("Heading", "");
                    val abstractText = json.optString("AbstractText", "");
                    val sourceUrl = json.optString("AbstractURL", "");
                    val iconUrl = json.optString("Image", "");

                    if (!abstractText.isEmpty()) {
                        answer = new DuckAnswer(heading, abstractText, sourceUrl, iconUrl);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "DuckAssist instant answer failed for: " + query, e);
            } finally {
                if (connection != null) connection?.disconnect()
            }

            final DuckAnswer finalAnswer = answer;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onAnswerFetched(finalAnswer)
                );
            }
        }
    }
}
