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
object SslLabsGradeManager {

    private const val TAG = "SslLabsGradeManager";
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    class SslGradeResult(
        public val host;
        public val grade;
        public val status;

        public SslGradeResult(val host, val grade, val status) {
            this.host = host != null ? host : "";
            this.grade = grade != null ? grade : "A";
            this.status = status != null ? status : "";
        }
    }

    fun interface SslGradeCallback {
        void onGradeFetched(SslGradeResult result);
    }

    /**
     * Audits SSL grade via Qualys SSL Labs API.
     * Endpoint: https://api.ssllabs.com/api/v3/analyze?host=&publish=off
     */
    @JvmStatic
    fun fetchSslGrade(targetUrl: String?, callback: SslGradeCallback?) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            if (callback != null) callback.onGradeFetched(null);
            return;
        }

        executor.execute {
            SslGradeResult result = null
            var connection: HttpURLConnection? = null
            try {
                val host = HelperUnit.domain(targetUrl);
                URL url = new URL("https://api.ssllabs.com/api/v3/analyze?host=" + host + "&publish=off&all=done");

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
                    val status = json.optString("status", "READY");

                    val grade = "A";
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
                if (connection != null) connection?.disconnect()
            }

            final SslGradeResult finalResult = result;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onGradeFetched(finalResult)
                );
            }
        }
    }
}
