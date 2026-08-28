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
object SslHealthAuditManager {

    private const val TAG = "SslHealthAuditManager";
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    class SslDomainHealth(
        public val domain;
        public val status;
        public val ipAddress;
        public val isSecure;

        public SslDomainHealth(val domain, val status, val ipAddress, val isSecure) {
            this.domain = domain != null ? domain : "";
            this.status = status != null ? status : "";
            this.ipAddress = ipAddress != null ? ipAddress : "";
            this.isSecure = isSecure;
        }
    }

    fun interface SslHealthCallback {
        void onHealthAudited(SslDomainHealth health);
    }

    /**
     * Audits domain security and resolves DoH IP using Google DNS API.
     * Endpoint: https://dns.google/resolve?name=
     */
    @JvmStatic
    fun auditDomain(targetUrl: String?, callback: SslHealthCallback?) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            if (callback != null) callback.onHealthAudited(null);
            return;
        }

        executor.execute {
            SslDomainHealth health = null
            var connection: HttpURLConnection? = null
            try {
                val domain = HelperUnit.domain(targetUrl);
                val apiUrl = "https://dns.google/resolve?name=" + domain + "&type=A";
                URL url = new URL(apiUrl);

                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET");
                connection.connectTimeout = 3000);
                connection.readTimeout = 3000);

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
                    val statusText = statusInt == 0 ? "NOERROR (Valid DNS)" : "REFUSED / FAIL";

                    val ip = "Unknown";
                    if (json.has("Answer")) {
                        org.json.JSONArray answer = json.getJSONArray("Answer");
                        if (answer.length() > 0) {
                            ip = answer.getJSONObject(0).optString("data", "Unknown");
                        }
                    }

                    val isHttps = targetUrl.toLowerCase().startsWith("https://");
                    health = new SslDomainHealth(domain, statusText, ip, isHttps);
                }
            } catch (Exception e) {
                Log.w(TAG, "SSL Domain Health audit failed for: " + targetUrl, e);
            } finally {
                if (connection != null) connection?.disconnect()
            }

            final SslDomainHealth finalHealth = health;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onHealthAudited(finalHealth)
                );
            }
        }
    }
}
