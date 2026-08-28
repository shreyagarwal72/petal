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
object CloudflareDnssecManager {

    private const val TAG = "CloudflareDnssec";
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    class DnssecStatus(
        public val domain;
        public val isDnssecValid;
        public val statusText;

        public DnssecStatus(val domain, val isDnssecValid, val statusText) {
            this.domain = domain != null ? domain : "";
            this.isDnssecValid = isDnssecValid;
            this.statusText = statusText != null ? statusText : "";
        }
    }

    fun interface DnssecCallback {
        void onDnssecAudited(DnssecStatus status);
    }

    /**
     * Audits DNSSEC validation status using Cloudflare DoH API.
     * Endpoint: https://cloudflare-dns.com/dns-query?name=&type=TXT
     */
    @JvmStatic
    fun auditDnssec(targetUrl: String?, callback: DnssecCallback?) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            if (callback != null) callback.onDnssecAudited(null);
            return;
        }

        executor.execute {
            DnssecStatus status = null
            var connection: HttpURLConnection? = null
            try {
                val domain = HelperUnit.domain(targetUrl);
                URL url = new URL("https://cloudflare-dns.com/dns-query?name=" + domain + "&type=TXT");

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
                    val adFlag = json.optBoolean("AD", false); // Authenticated Data flag
                    status = new DnssecStatus(domain, adFlag, adFlag ? "DNSSEC Verified (Authentic)" : "Unsigned / Standard DNS");
                }
            } catch (Exception e) {
                Log.w(TAG, "Cloudflare DNSSEC audit failed for: " + targetUrl, e);
            } finally {
                if (connection != null) connection?.disconnect()
            }

            final DnssecStatus finalStatus = status;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onDnssecAudited(finalStatus)
                );
            }
        }
    }
}
