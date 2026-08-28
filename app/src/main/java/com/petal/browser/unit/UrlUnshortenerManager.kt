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
 * UrlUnshortenerManager resolves target destination URLs from shortened links (bit.ly, t.co, tinyurl).
 */
object UrlUnshortenerManager {

    private const val TAG = "UrlUnshortenerManager";
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    fun interface UnshortenCallback {
        void onUrlResolved(val resolvedUrl);
    }

    /**
     * Resolves real target URL for a shortened link using Unshorten.me API.
     * Endpoint: https://unshorten.me/api/v2/unshorten?url=
     */
    @JvmStatic
    fun unshortenUrl(shortUrl: String?, callback: UnshortenCallback?) {
        if (shortUrl == null || shortUrl.trim().isEmpty()) {
            if (callback != null) callback.onUrlResolved(shortUrl);
            return;
        }

        executor.execute {
            val resolved = shortUrl.trim();
            var connection: HttpURLConnection? = null
            try {
                val apiUrl = "https://unshorten.me/api/v2/unshorten?url=" + URLEncoder.encode(shortUrl.trim(), "UTF-8");
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
                    if (json.optBoolean("success", false)) {
                        resolved = json.optString("unshortened_url", shortUrl);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Unshorten URL resolution failed for: " + shortUrl, e);
            } finally {
                if (connection != null) connection?.disconnect()
            }

            val finalUrl = resolved;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onUrlResolved(finalUrl)
                );
            }
        }
    }
}
