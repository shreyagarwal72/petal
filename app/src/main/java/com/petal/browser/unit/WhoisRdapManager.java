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
public class WhoisRdapManager {

    private static final String TAG = "WhoisRdapManager";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static class DomainWhoisInfo {
        public String domain;
        public String registrar;
        public String handle;
        public String status;

        public DomainWhoisInfo(String domain, String registrar, String handle, String status) {
            this.domain = domain != null ? domain : "";
            this.registrar = registrar != null ? registrar : "Unknown";
            this.handle = handle != null ? handle : "";
            this.status = status != null ? status : "";
        }
    }

    public interface WhoisCallback {
        void onWhoisFetched(DomainWhoisInfo info);
    }

    /**
     * Fetches RDAP domain registration metadata.
     * Endpoint: https://rdap.org/domain/{domain}
     */
    public static void fetchWhois(final String targetUrl, final WhoisCallback callback) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            if (callback != null) callback.onWhoisFetched(null);
            return;
        }

        executor.execute(() -> {
            DomainWhoisInfo info = null;
            HttpURLConnection connection = null;
            try {
                String domain = HelperUnit.domain(targetUrl);
                URL url = new URL("https://rdap.org/domain/" + domain);

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(3500);
                connection.setReadTimeout(3500);

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder builder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(builder.toString());
                    String handle = json.optString("handle", "");
                    String registrar = "Unknown";
                    if (json.has("entities")) {
                        registrar = "RDAP Registered Entity";
                    }

                    info = new DomainWhoisInfo(domain, registrar, handle, "Active");
                }
            } catch (Exception e) {
                Log.w(TAG, "RDAP Whois lookup failed for: " + targetUrl, e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            final DomainWhoisInfo finalInfo = info;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onWhoisFetched(finalInfo)
                );
            }
        });
    }
}
