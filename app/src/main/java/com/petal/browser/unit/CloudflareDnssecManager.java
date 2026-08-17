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
 * CloudflareDnssecManager audits DNSSEC security and TXT records via Cloudflare DoH API.
 */
public class CloudflareDnssecManager {

    private static final String TAG = "CloudflareDnssec";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static class DnssecStatus {
        public String domain;
        public boolean isDnssecValid;
        public String statusText;

        public DnssecStatus(String domain, boolean isDnssecValid, String statusText) {
            this.domain = domain != null ? domain : "";
            this.isDnssecValid = isDnssecValid;
            this.statusText = statusText != null ? statusText : "";
        }
    }

    public interface DnssecCallback {
        void onDnssecAudited(DnssecStatus status);
    }

    /**
     * Audits DNSSEC validation status using Cloudflare DoH API.
     * Endpoint: https://cloudflare-dns.com/dns-query?name=&type=TXT
     */
    public static void auditDnssec(final String targetUrl, final DnssecCallback callback) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            if (callback != null) callback.onDnssecAudited(null);
            return;
        }

        executor.execute(() -> {
            DnssecStatus status = null;
            HttpURLConnection connection = null;
            try {
                String domain = HelperUnit.domain(targetUrl);
                URL url = new URL("https://cloudflare-dns.com/dns-query?name=" + domain + "&type=TXT");

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);
                connection.setRequestProperty("Accept", "application/dns-json");

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder builder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(builder.toString());
                    boolean adFlag = json.optBoolean("AD", false); // Authenticated Data flag
                    status = new DnssecStatus(domain, adFlag, adFlag ? "DNSSEC Verified (Authentic)" : "Unsigned / Standard DNS");
                }
            } catch (Exception e) {
                Log.w(TAG, "Cloudflare DNSSEC audit failed for: " + targetUrl, e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            final DnssecStatus finalStatus = status;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onDnssecAudited(finalStatus)
                );
            }
        });
    }
}
