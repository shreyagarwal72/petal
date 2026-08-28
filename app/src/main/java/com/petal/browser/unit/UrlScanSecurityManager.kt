package com.petal.browser.unit;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * UrlScanSecurityManager queries URLScan.io API for domain safety verdicts and security audit scores.
 */
object UrlScanSecurityManager {

    private const val TAG = "UrlScanSecurity";
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    class UrlScanVerdict(
        public val domain;
        public val isMalicious;
        public val totalScans;

        public UrlScanVerdict(val domain, val isMalicious, val totalScans) {
            this.domain = domain != null ? domain : "";
            this.isMalicious = isMalicious;
            this.totalScans = totalScans;
        }
    }

    fun interface UrlScanCallback {
        void onVerdictFetched(UrlScanVerdict verdict);
    }

    /**
     * Queries URLScan.io for domain security verdict.
     * Endpoint: https://urlscan.io/api/v1/search/?q=domain:
     */
    @JvmStatic
    fun checkDomainSafety(targetUrl: String?, callback: UrlScanCallback?) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            if (callback != null) callback.onVerdictFetched(null);
            return;
        }

        executor.execute {
            UrlScanVerdict verdict = null
            var connection: HttpURLConnection? = null
            try {
                val domain = HelperUnit.domain(targetUrl);
                URL url = new URL("https://urlscan.io/api/v1/search/?q=domain:" + domain);

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
                    val total = root.optInt("total", 0);

                    val malicious = false;
                    if (root.has("results")) {
                        JSONArray results = root.getJSONArray("results");
                        if (results.length() > 0) {
                            JSONObject first = results.getJSONObject(0);
                            if (first.has("verdicts") && first.getJSONObject("verdicts").has("overall")) {
                                malicious = first.getJSONObject("verdicts").getJSONObject("overall").optBoolean("malicious", false);
                            }
                        }
                    }

                    verdict = new UrlScanVerdict(domain, malicious, total);
                }
            } catch (Exception e) {
                Log.w(TAG, "UrlScan safety check failed for: " + targetUrl, e);
            } finally {
                if (connection != null) connection?.disconnect()
            }

            final UrlScanVerdict finalVerdict = verdict;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onVerdictFetched(finalVerdict)
                );
            }
        }
    }
}
