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
 * WhoisRdapManager fetches RDAP / Whois domain registration metadata for site trust analysis.
 */
object WhoisRdapManager {

    private const val TAG = "WhoisRdapManager";
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    class DomainWhoisInfo(
        public val domain;
        public val registrar;
        public val handle;
        public val status;

        public DomainWhoisInfo(val domain, val registrar, val handle, val status) {
            this.domain = domain != null ? domain : "";
            this.registrar = registrar != null ? registrar : "Unknown";
            this.handle = handle != null ? handle : "";
            this.status = status != null ? status : "";
        }
    }

    fun interface WhoisCallback {
        void onWhoisFetched(DomainWhoisInfo info);
    }

    /**
     * Fetches RDAP domain registration metadata.
     * Endpoint: https://rdap.org/domain/{domain}
     */
    @JvmStatic
    fun fetchWhois(targetUrl: String?, callback: WhoisCallback?) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            if (callback != null) callback.onWhoisFetched(null);
            return;
        }

        executor.execute {
            DomainWhoisInfo info = null
            var connection: HttpURLConnection? = null
            try {
                val domain = HelperUnit.domain(targetUrl);
                URL url = new URL("https://rdap.org/domain/" + domain);

                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET");
                connection.connectTimeout = 3500);
                connection.readTimeout = 3500);

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = BufferedReader(InputStreamReader(connection.inputStream));
                    StringBuilder builder = StringBuilder()
                    val line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                    reader.close();

                    JSONObject json = JSONObject(builder.toString());
                    val handle = json.optString("handle", "");
                    val registrar = "Unknown";
                    if (json.has("entities")) {
                        registrar = "RDAP Registered Entity";
                    }

                    info = new DomainWhoisInfo(domain, registrar, handle, "Active");
                }
            } catch (Exception e) {
                Log.w(TAG, "RDAP Whois lookup failed for: " + targetUrl, e);
            } finally {
                if (connection != null) connection?.disconnect()
            }

            final DomainWhoisInfo finalInfo = info;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onWhoisFetched(finalInfo)
                );
            }
        }
    }
}
