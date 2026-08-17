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
 * IpApiGeoManager fetches IP geolocation, ISP, and AS network provider info via ip-api.com.
 */
public class IpApiGeoManager {

    private static final String TAG = "IpApiGeoManager";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static class IpApiResult {
        public String queryIp;
        public String country;
        public String city;
        public String isp;
        public String as;

        public IpApiResult(String queryIp, String country, String city, String isp, String as) {
            this.queryIp = queryIp != null ? queryIp : "";
            this.country = country != null ? country : "";
            this.city = city != null ? city : "";
            this.isp = isp != null ? isp : "";
            this.as = as != null ? as : "";
        }
    }

    public interface IpApiCallback {
        void onGeoFetched(IpApiResult result);
    }

    /**
     * Queries ip-api.com for IP geolocation & ISP information.
     * Endpoint: http://ip-api.com/json/
     */
    public static void fetchGeo(final String ipOrDomain, final IpApiCallback callback) {
        executor.execute(() -> {
            IpApiResult result = null;
            HttpURLConnection connection = null;
            try {
                String target = (ipOrDomain != null && !ipOrDomain.trim().isEmpty()) ? HelperUnit.domain(ipOrDomain) : "";
                URL url = new URL("http://ip-api.com/json/" + target);

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
                    if ("success".equalsIgnoreCase(json.optString("status", ""))) {
                        result = new IpApiResult(
                            json.optString("query", ""),
                            json.optString("country", ""),
                            json.optString("city", ""),
                            json.optString("isp", ""),
                            json.optString("as", "")
                        );
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "ip-api lookup failed", e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            final IpApiResult finalResult = result;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onGeoFetched(finalResult)
                );
            }
        });
    }
}
