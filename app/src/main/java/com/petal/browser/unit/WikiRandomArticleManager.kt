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
object WikiRandomArticleManager {

    private const val TAG = "WikiRandomArticle";
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    fun interface WikiRandomCallback {
        void onRandomArticleFetched(WikipediaSummaryManager.WikiSummary summary);
    }

    /**
     * Fetches a random article summary from Wikipedia.
     * Endpoint: https://en.wikipedia.org/api/rest_v1/page/random/summary
     */
    @JvmStatic
    fun fetchRandomArticle(callback: WikiRandomCallback?) {
        executor.execute {
            WikipediaSummaryManager.WikiSummary summary = null
            var connection: HttpURLConnection? = null
            try {
                URL url = new URL("https://en.wikipedia.org/api/rest_v1/page/random/summary");
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

                    JSONObject json = JSONObject(builder.toString());
                    val title = json.optString("title", "");
                    val description = json.optString("description", "");
                    val extract = json.optString("extract", "");

                    val thumbnailUrl = "";
                    if (json.has("thumbnail")) {
                        thumbnailUrl = json.getJSONObject("thumbnail").optString("source", "");
                    }

                    val articleUrl = "";
                    if (json.has("content_urls") && json.getJSONObject("content_urls").has("desktop")) {
                        articleUrl = json.getJSONObject("content_urls").getJSONObject("desktop").optString("page", "");
                    }

                    summary = new WikipediaSummaryManager.WikiSummary(title, description, extract, thumbnailUrl, articleUrl);
                }
            } catch (Exception e) {
                Log.w(TAG, "Random Wikipedia article fetch failed", e);
            } finally {
                if (connection != null) connection?.disconnect()
            }

            final WikipediaSummaryManager.WikiSummary finalSummary = summary;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onRandomArticleFetched(finalSummary)
                );
            }
        }
    }
}
