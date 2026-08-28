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
object PhishTankManager {

    private const val TAG = "PhishTankManager";
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    fun interface PhishTankCallback {
        void onCheckCompleted(val isPhishing);
    }

    /**
     * Checks if a target URL exists in PhishTank phishing database.
     * Endpoint: https://checkurl.phishtank.com/checkurl/
     */
    @JvmStatic
    fun checkPhishing(targetUrl: String?, callback: PhishTankCallback?) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            if (callback != null) callback.onCheckCompleted(false);
            return;
        }

        executor.execute {
            val isPhishing = false;
            var connection: HttpURLConnection? = null
            try {
                URL url = new URL("https://checkurl.phishtank.com/checkurl/");
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST");
                connection.connectTimeout = 3500);
                connection.readTimeout = 3500);
                connection.setDoOutput(true);
                connection.setRequestProperty("User-Agent", "petal-browser/1.0");

                val postData = "url=" + URLEncoder.encode(targetUrl.trim(), "UTF-8") + "&format=json";
                OutputStream os = connection.getOutputStream();
                os.write(postData.getBytes("UTF-8"));
                os.flush();
                os.close();

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = BufferedReader(InputStreamReader(connection.inputStream));
                    StringBuilder builder = StringBuilder()
                    val line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                    reader.close();

                    JSONObject json = JSONObject(builder.toString());
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
                if (connection != null) connection?.disconnect()
            }

            val result = isPhishing;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onCheckCompleted(result)
                );
            }
        }
    }
}
