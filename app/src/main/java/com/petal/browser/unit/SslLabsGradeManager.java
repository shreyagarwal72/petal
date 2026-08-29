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
 * SslLabsGradeManager fetches live SSL/TLS security grades (A+, A, B, C, F) via Qualys SSL Labs API.
 */
public class SslLabsGradeManager {

    private static final String TAG = "SslLabsGradeManager";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static class SslGradeResult {
        public String host;
        public String grade;
        public String status;

        public SslGradeResult(String host, String grade, String status) {
            this.host = host != null ? host : "";
            this.grade = grade != null ? grade : "A";
            this.status = status != null ? status : "";
        }
    }

    public interface SslGradeCallback {
        void onGradeFetched(SslGradeResult result);
    }

    /**
     * Audits SSL grade via Qualys SSL Labs API.
     * Endpoint: https://api.ssllabs.com/api/v3/analyze?host=&publish=off
     */
    public static void fetchSslGrade(final String targetUrl, final SslGradeCallback callback) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            if (callback != null) callback.onGradeFetched(null);
            return;
        }

        executor.execute(() -> {
            SslGradeResult result = null;
            HttpURLConnection connection = null;
            try {
                String host = HelperUnit.domain(targetUrl);
                URL url = new URL("https://api.ssllabs.com/api/v3/analyze?host=" + host + "&publish=off&all=done");

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

                    JSONObject json = new JSONObject(builder.toString());
                    String status = json.optString("status", "READY");

                    String grade = "A";
                    if (json.has("endpoints")) {
                        org.json.JSONArray endpoints = json.getJSONArray("endpoints");
                        if (endpoints.length() > 0) {
                            grade = endpoints.getJSONObject(0).optString("grade", "A");
                        }
                    }

                    result = new SslGradeResult(host, grade, status);
                }
            } catch (Exception e) {
                Log.w(TAG, "SSL Labs grade audit failed for: " + targetUrl, e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            final SslGradeResult finalResult = result;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onGradeFetched(finalResult)
                );
            }
        });
    }
}
