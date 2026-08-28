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
object Quad9DnsManager {

    private const val TAG = "Quad9DnsManager";
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    class Quad9DnsResult(
        public val domain;
        public val resolvedIp;
        public val isBlocked;

        public Quad9DnsResult(val domain, val resolvedIp, val isBlocked) {
            this.domain = domain != null ? domain : "";
            this.resolvedIp = resolvedIp != null ? resolvedIp : "";
            this.isBlocked = isBlocked;
        }
    }

    fun interface Quad9Callback {
        void onDnsResolved(Quad9DnsResult result);
    }

    /**
     * Resolves DoH IP via Quad9 DNS-over-HTTPS API.
     * Endpoint: https://dns.quad9.net/dns-query?name=&type=A
     */
    @JvmStatic
    fun resolveDoH(targetUrl: String?, callback: Quad9Callback?) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            if (callback != null) callback.onDnsResolved(null);
            return;
        }

        executor.execute {
            Quad9DnsResult result = null
            var connection: HttpURLConnection? = null
            try {
                val domain = HelperUnit.domain(targetUrl);
                URL url = new URL("https://dns.quad9.net/dns-query?name=" + domain + "&type=A");

                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET");
                connection.connectTimeout = 3000);
                connection.readTimeout = 3000);
                connection.setRequestProperty("Accept", "application/dns-json");

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = BufferedReader(InputStreamReader(connection.inputStream));
                    StringBuilder builder = StringBuilder()
                    val line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                    reader.close();

                    JSONObject json = JSONObject(builder.toString());
                    val statusInt = json.optInt("Status", -1);
                    val blocked = (statusInt == 3); // NXDOMAIN / Blocked by Quad9 threat filter

                    val ip = "Unknown";
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
                if (connection != null) connection?.disconnect()
            }

            final Quad9DnsResult finalResult = result;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onDnsResolved(finalResult)
                );
            }
        }
    }
}
