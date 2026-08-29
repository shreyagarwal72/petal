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
public class UrlUnshortenerManager {

    private static final String TAG = "UrlUnshortenerManager";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface UnshortenCallback {
        void onUrlResolved(String resolvedUrl);
    }

    /**
     * Resolves real target URL for a shortened link using Unshorten.me API.
     * Endpoint: https://unshorten.me/api/v2/unshorten?url=
     */
    public static void unshortenUrl(final String shortUrl, final UnshortenCallback callback) {
        if (shortUrl == null || shortUrl.trim().isEmpty()) {
            if (callback != null) callback.onUrlResolved(shortUrl);
            return;
        }

        executor.execute(() -> {
            String resolved = shortUrl.trim();
            HttpURLConnection connection = null;
            try {
                String apiUrl = "https://unshorten.me/api/v2/unshorten?url=" + URLEncoder.encode(shortUrl.trim(), "UTF-8");
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
                    if (json.optBoolean("success", false)) {
                        resolved = json.optString("unshortened_url", shortUrl);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Unshorten URL resolution failed for: " + shortUrl, e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            final String finalUrl = resolved;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onUrlResolved(finalUrl)
                );
            }
        });
    }
}
