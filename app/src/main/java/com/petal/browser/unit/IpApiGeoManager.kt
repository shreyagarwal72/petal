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
object IpApiGeoManager {

    private const val TAG = "IpApiGeoManager";
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    class IpApiResult(
        public val queryIp;
        public val country;
        public val city;
        public val isp;
        public val as;

        public IpApiResult(val queryIp, val country, val city, val isp, val as) {
            this.queryIp = queryIp != null ? queryIp : "";
            this.country = country != null ? country : "";
            this.city = city != null ? city : "";
            this.isp = isp != null ? isp : "";
            this.as = as != null ? as : "";
        }
    }

    fun interface IpApiCallback {
        void onGeoFetched(IpApiResult result);
    }

    /**
     * Queries ip-api.com for IP geolocation & ISP information.
     * Endpoint: http://ip-api.com/json/
     */
    @JvmStatic
    fun fetchGeo(ipOrDomain: String?, callback: IpApiCallback?) {
        executor.execute {
            IpApiResult result = null
            var connection: HttpURLConnection? = null
            try {
                val target = (ipOrDomain != null && !ipOrDomain.trim().isEmpty()) ? HelperUnit.domain(ipOrDomain) : "";
                URL url = new URL("http://ip-api.com/json/" + target);

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
                if (connection != null) connection?.disconnect()
            }

            final IpApiResult finalResult = result;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onGeoFetched(finalResult)
                );
            }
        }
    }
}
