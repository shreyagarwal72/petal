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
 * SslHealthAuditManager fetches DNS-over-HTTPS (DoH) and domain security details
 * via Google DoH API for display in the Site Info bottom sheet.
 */
public class SslHealthAuditManager {

    private static final String TAG = "SslHealthAuditManager";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static class SslDomainHealth {
        public String domain;
        public String status;
        public String ipAddress;
        public boolean isSecure;

        public SslDomainHealth(String domain, String status, String ipAddress, boolean isSecure) {
            this.domain = domain != null ? domain : "";
            this.status = status != null ? status : "";
            this.ipAddress = ipAddress != null ? ipAddress : "";
            this.isSecure = isSecure;
        }
    }

    public interface SslHealthCallback {
        void onHealthAudited(SslDomainHealth health);
    }

    /**
     * Audits domain security and resolves DoH IP using Google DNS API.
     * Endpoint: https://dns.google/resolve?name=
     */
    public static void auditDomain(final String targetUrl, final SslHealthCallback callback) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            if (callback != null) callback.onHealthAudited(null);
            return;
        }

        executor.execute(() -> {
            SslDomainHealth health = null;
            HttpURLConnection connection = null;
            try {
                String domain = HelperUnit.domain(targetUrl);
                String apiUrl = "https://dns.google/resolve?name=" + domain + "&type=A";
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
                    int statusInt = json.optInt("Status", -1);
                    String statusText = statusInt == 0 ? "NOERROR (Valid DNS)" : "REFUSED / FAIL";

                    String ip = "Unknown";
                    if (json.has("Answer")) {
                        org.json.JSONArray answer = json.getJSONArray("Answer");
                        if (answer.length() > 0) {
                            ip = answer.getJSONObject(0).optString("data", "Unknown");
                        }
                    }

                    boolean isHttps = targetUrl.toLowerCase().startsWith("https://");
                    health = new SslDomainHealth(domain, statusText, ip, isHttps);
                }
            } catch (Exception e) {
                Log.w(TAG, "SSL Domain Health audit failed for: " + targetUrl, e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            final SslDomainHealth finalHealth = health;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onHealthAudited(finalHealth)
                );
            }
        });
    }
}
