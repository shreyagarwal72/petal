package com.petal.browser.unit;

import android.util.Log;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PhishTankManager verifies target URLs against PhishTank phishing database.
 */
public class PhishTankManager {

    private static final String TAG = "PhishTankManager";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface PhishTankCallback {
        void onCheckCompleted(boolean isPhishing);
    }

    /**
     * Checks if a target URL exists in PhishTank phishing database.
     * Endpoint: https://checkurl.phishtank.com/checkurl/
     */
    public static void checkPhishing(final String targetUrl, final PhishTankCallback callback) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            if (callback != null) callback.onCheckCompleted(false);
            return;
        }

        executor.execute(() -> {
            boolean isPhishing = false;
            HttpURLConnection connection = null;
            try {
                URL url = new URL("https://checkurl.phishtank.com/checkurl/");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(3500);
                connection.setReadTimeout(3500);
                connection.setDoOutput(true);
                connection.setRequestProperty("User-Agent", "petal-browser/1.0");

                String postData = "url=" + URLEncoder.encode(targetUrl.trim(), "UTF-8") + "&format=json";
                OutputStream os = connection.getOutputStream();
                os.write(postData.getBytes("UTF-8"));
                os.flush();
                os.close();

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder builder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(builder.toString());
                    if (json.has("results")) {
                        JSONObject results = json.getJSONObject("results");
                        if (results.has("in_database") && results.getBoolean("in_database")) {
                            isPhishing = results.optBoolean("valid", false);
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "PhishTank check failed for: " + targetUrl, e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            final boolean result = isPhishing;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onCheckCompleted(result)
                );
            }
        });
    }
}
