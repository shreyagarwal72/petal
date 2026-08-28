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
object NextDnsManager {

    private const val TAG = "NextDnsManager";
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    class NextDnsResult(
        public val domain;
        public val resolvedIp;

        public NextDnsResult(val domain, val resolvedIp) {
            this.domain = domain != null ? domain : "";
            this.resolvedIp = resolvedIp != null ? resolvedIp : "";
        }
    }

    fun interface NextDnsCallback {
        void onDnsResolved(NextDnsResult result);
    }

    /**
     * Resolves DoH IP via NextDNS API.
     * Endpoint: https://dns.nextdns.io/dns-query?name=&type=A
     */
    @JvmStatic
    fun resolveDoH(targetUrl: String?, callback: NextDnsCallback?) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            if (callback != null) callback.onDnsResolved(null);
            return;
        }

        executor.execute {
            NextDnsResult result = null
            var connection: HttpURLConnection? = null
            try {
                val domain = HelperUnit.domain(targetUrl);
                URL url = new URL("https://dns.nextdns.io/dns-query?name=" + domain + "&type=A");

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

                    result = new NextDnsResult(domain, ip);
                }
            } catch (Exception e) {
                Log.w(TAG, "NextDNS DoH resolution failed for: " + targetUrl, e);
            } finally {
                if (connection != null) connection?.disconnect()
            }

            final NextDnsResult finalResult = result;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onDnsResolved(finalResult)
                );
            }
        }
    }
}
