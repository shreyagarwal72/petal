package com.petal.browser.unit;

import android.content.Context;
import android.util.Log;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * IpGeoAuditManager fetches live IP address, country, ISP, and network privacy info
 * via ipapi.co JSON API for display in Settings & Privacy Audit.
 */
public class IpGeoAuditManager {

    private static final String TAG = "IpGeoAuditManager";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static class IpGeoInfo {
        public String ip;
        public String city;
        public String region;
        public String countryName;
        public String countryCode;
        public String org;
        public String asn;

        public IpGeoInfo(String ip, String city, String region, String countryName, String countryCode, String org, String asn) {
            this.ip = ip != null ? ip : "Unknown";
            this.city = city != null ? city : "";
            this.region = region != null ? region : "";
            this.countryName = countryName != null ? countryName : "";
            this.countryCode = countryCode != null ? countryCode : "";
            this.org = org != null ? org : "";
            this.asn = asn != null ? asn : "";
        }
    }

    public interface IpGeoCallback {
        void onInfoFetched(IpGeoInfo info);
    }

    /**
     * Fetches live IP address & network geolocation info.
     * Endpoint: https://ipapi.co/json/
     */
    public static void fetchIpInfo(final IpGeoCallback callback) {
        executor.execute(() -> {
            IpGeoInfo info = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL("https://ipapi.co/json/");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(3500);
                connection.setReadTimeout(3500);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 PetalBrowser/1.0");

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder builder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(builder.toString());
                    info = new IpGeoInfo(
                        json.optString("ip", ""),
                        json.optString("city", ""),
                        json.optString("region", ""),
                        json.optString("country_name", ""),
                        json.optString("country_code", ""),
                        json.optString("org", ""),
                        json.optString("asn", "")
                    );
                }
            } catch (Exception e) {
                Log.w(TAG, "IP Geolocation audit failed", e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            final IpGeoInfo finalInfo = info;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onInfoFetched(finalInfo)
                );
            }
        });
    }
}
