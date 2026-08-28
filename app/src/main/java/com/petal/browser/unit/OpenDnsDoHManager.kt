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
 * OpenDnsDoHManager resolves domain A-records via OpenDNS encrypted DoH API.
 */
object OpenDnsDoHManager {

    private const val TAG = "OpenDnsDoHManager";
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    class OpenDnsResult(
        public val domain;
        public val resolvedIp;

        public OpenDnsResult(val domain, val resolvedIp) {
            this.domain = domain != null ? domain : "";
            this.resolvedIp = resolvedIp != null ? resolvedIp : "";
        }
    }

    fun interface OpenDnsCallback {
        void onDnsResolved(OpenDnsResult result);
    }

    /**
     * Resolves DoH IP via OpenDNS DNS-over-HTTPS API.
     * Endpoint: https://doh.opendns.com/dns-query?name=&type=A
     */
    @JvmStatic
    fun resolveDoH(targetUrl: String?, callback: OpenDnsCallback?) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            if (callback != null) callback.onDnsResolved(null);
            return;
        }

        executor.execute {
            OpenDnsResult result = null
            var connection: HttpURLConnection? = null
            try {
                val domain = HelperUnit.domain(targetUrl);
                URL url = new URL("https://doh.opendns.com/dns-query?name=" + domain + "&type=A");

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
                    val ip = "Unknown";
                    if (json.has("Answer")) {
                        org.json.JSONArray answer = json.getJSONArray("Answer");
                        if (answer.length() > 0) {
                            ip = answer.getJSONObject(0).optString("data", "Unknown");
                        }
                    }

                    result = new OpenDnsResult(domain, ip);
                }
            } catch (Exception e) {
                Log.w(TAG, "OpenDNS DoH resolution failed for: " + targetUrl, e);
            } finally {
                if (connection != null) connection?.disconnect()
            }

            final OpenDnsResult finalResult = result;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onDnsResolved(finalResult)
                );
            }
        }
    }
}
