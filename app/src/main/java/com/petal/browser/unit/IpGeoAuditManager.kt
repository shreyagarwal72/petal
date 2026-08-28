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
object IpGeoAuditManager {

    private const val TAG = "IpGeoAuditManager";
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    class IpGeoInfo(
        public val ip;
        public val city;
        public val region;
        public val countryName;
        public val countryCode;
        public val org;
        public val asn;

        public IpGeoInfo(val ip, val city, val region, val countryName, val countryCode, val org, val asn) {
            this.ip = ip != null ? ip : "Unknown";
            this.city = city != null ? city : "";
            this.region = region != null ? region : "";
            this.countryName = countryName != null ? countryName : "";
            this.countryCode = countryCode != null ? countryCode : "";
            this.org = org != null ? org : "";
            this.asn = asn != null ? asn : "";
        }
    }

    fun interface IpGeoCallback {
        void onInfoFetched(IpGeoInfo info);
    }

    /**
     * Fetches live IP address & network geolocation info.
     * Endpoint: https://ipapi.co/json/
     */
    @JvmStatic
    fun fetchIpInfo(callback: IpGeoCallback?) {
        executor.execute {
            IpGeoInfo info = null
            var connection: HttpURLConnection? = null
            try {
                URL url = new URL("https://ipapi.co/json/");
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET");
                connection.connectTimeout = 3500);
                connection.readTimeout = 3500);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 PetalBrowser/1.0");

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = BufferedReader(InputStreamReader(connection.inputStream));
                    StringBuilder builder = StringBuilder()
                    val line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                    reader.close();

                    JSONObject json = JSONObject(builder.toString());
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
                if (connection != null) connection?.disconnect()
            }

            final IpGeoInfo finalInfo = info;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onInfoFetched(finalInfo)
                );
            }
        }
    }
}
