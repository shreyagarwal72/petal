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
public class UrlScanSecurityManager {

    private static final String TAG = "UrlScanSecurity";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static class UrlScanVerdict {
        public String domain;
        public boolean isMalicious;
        public int totalScans;

        public UrlScanVerdict(String domain, boolean isMalicious, int totalScans) {
            this.domain = domain != null ? domain : "";
            this.isMalicious = isMalicious;
            this.totalScans = totalScans;
        }
    }

    public interface UrlScanCallback {
        void onVerdictFetched(UrlScanVerdict verdict);
    }

    /**
     * Queries URLScan.io for domain security verdict.
     * Endpoint: https://urlscan.io/api/v1/search/?q=domain:
     */
    public static void checkDomainSafety(final String targetUrl, final UrlScanCallback callback) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            if (callback != null) callback.onVerdictFetched(null);
            return;
        }

        executor.execute(() -> {
            UrlScanVerdict verdict = null;
            HttpURLConnection connection = null;
            try {
                String domain = HelperUnit.domain(targetUrl);
                URL url = new URL("https://urlscan.io/api/v1/search/?q=domain:" + domain);

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

                    JSONObject root = new JSONObject(builder.toString());
                    int total = root.optInt("total", 0);

                    boolean malicious = false;
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
                if (connection != null) connection.disconnect();
            }

            final UrlScanVerdict finalVerdict = verdict;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onVerdictFetched(finalVerdict)
                );
            }
        });
    }
}
