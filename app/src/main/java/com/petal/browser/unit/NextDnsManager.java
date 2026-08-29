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
 * NextDnsManager resolves domain A-records via NextDNS DoH (DNS-over-HTTPS) encrypted resolver.
 */
public class NextDnsManager {

    private static final String TAG = "NextDnsManager";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static class NextDnsResult {
        public String domain;
        public String resolvedIp;

        public NextDnsResult(String domain, String resolvedIp) {
            this.domain = domain != null ? domain : "";
            this.resolvedIp = resolvedIp != null ? resolvedIp : "";
        }
    }

    public interface NextDnsCallback {
        void onDnsResolved(NextDnsResult result);
    }

    /**
     * Resolves DoH IP via NextDNS API.
     * Endpoint: https://dns.nextdns.io/dns-query?name=&type=A
     */
    public static void resolveDoH(final String targetUrl, final NextDnsCallback callback) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            if (callback != null) callback.onDnsResolved(null);
            return;
        }

        executor.execute(() -> {
            NextDnsResult result = null;
            HttpURLConnection connection = null;
            try {
                String domain = HelperUnit.domain(targetUrl);
                URL url = new URL("https://dns.nextdns.io/dns-query?name=" + domain + "&type=A");

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
                    String ip = "Unknown";
                    if (json.has("Answer")) {
                        org.json.JSONArray answer = json.getJSONArray("Answer");
                        if (answer.length() > 0) {
                            ip = answer.getJSONObject(0).optString("data", "Unknown");
                        }
                    }

                    result = new NextDnsResult(domain, ip);
                }
            } catch (Exception e) {
                Log.w(TAG, "NextDNS DoH resolution failed for: " + targetUrl, e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            final NextDnsResult finalResult = result;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onDnsResolved(finalResult)
                );
            }
        });
    }
}
