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
 * Quad9DnsManager audits domain A-records via Quad9 DoH (DNS-over-HTTPS) encrypted resolver.
 */
public class Quad9DnsManager {

    private static final String TAG = "Quad9DnsManager";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static class Quad9DnsResult {
        public String domain;
        public String resolvedIp;
        public boolean isBlocked;

        public Quad9DnsResult(String domain, String resolvedIp, boolean isBlocked) {
            this.domain = domain != null ? domain : "";
            this.resolvedIp = resolvedIp != null ? resolvedIp : "";
            this.isBlocked = isBlocked;
        }
    }

    public interface Quad9Callback {
        void onDnsResolved(Quad9DnsResult result);
    }

    /**
     * Resolves DoH IP via Quad9 DNS-over-HTTPS API.
     * Endpoint: https://dns.quad9.net/dns-query?name=&type=A
     */
    public static void resolveDoH(final String targetUrl, final Quad9Callback callback) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            if (callback != null) callback.onDnsResolved(null);
            return;
        }

        executor.execute(() -> {
            Quad9DnsResult result = null;
            HttpURLConnection connection = null;
            try {
                String domain = HelperUnit.domain(targetUrl);
                URL url = new URL("https://dns.quad9.net/dns-query?name=" + domain + "&type=A");

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
                    int statusInt = json.optInt("Status", -1);
                    boolean blocked = (statusInt == 3); // NXDOMAIN / Blocked by Quad9 threat filter

                    String ip = "Unknown";
                    if (json.has("Answer")) {
                        org.json.JSONArray answer = json.getJSONArray("Answer");
                        if (answer.length() > 0) {
                            ip = answer.getJSONObject(0).optString("data", "Unknown");
                        }
                    }

                    result = new Quad9DnsResult(domain, ip, blocked);
                }
            } catch (Exception e) {
                Log.w(TAG, "Quad9 DoH resolution failed for: " + targetUrl, e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            final Quad9DnsResult finalResult = result;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onDnsResolved(finalResult)
                );
            }
        });
    }
}
