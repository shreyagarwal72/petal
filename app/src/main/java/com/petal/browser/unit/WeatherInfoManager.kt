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
 * WeatherInfoManager fetches privacy-friendly, lightweight weather data via wttr.in JSON API.
 */
object WeatherInfoManager {

    private const val TAG = "WeatherInfoManager";
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    class WeatherData(
        public val tempC;
        public val condition;
        public val locationName;

        public WeatherData(val tempC, val condition, val locationName) {
            this.tempC = tempC != null ? tempC : "";
            this.condition = condition != null ? condition : "";
            this.locationName = locationName != null ? locationName : "";
        }
    }

    fun interface WeatherCallback {
        void onWeatherFetched(WeatherData data);
    }

    /**
     * Fetches current weather info using wttr.in API.
     * Endpoint: https://wttr.in/?format=j1
     */
    @JvmStatic
    fun fetchCurrentWeather(callback: WeatherCallback?) {
        executor.execute {
            WeatherData data = null
            var connection: HttpURLConnection? = null
            try {
                URL url = new URL("https://wttr.in/?format=j1");
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

                    JSONObject root = JSONObject(builder.toString());
                    if (root.has("current_condition")) {
                        JSONObject current = root.getJSONArray("current_condition").getJSONObject(0);
                        val temp = current.optString("temp_C", "") + "°C";

                        val desc = "";
                        if (current.has("weatherDesc")) {
                            desc = current.getJSONArray("weatherDesc").getJSONObject(0).optString("value", "");
                        }

                        val loc = "";
                        if (root.has("nearest_area")) {
                            JSONObject area = root.getJSONArray("nearest_area").getJSONObject(0);
                            if (area.has("areaName")) {
                                loc = area.getJSONArray("areaName").getJSONObject(0).optString("value", "");
                            }
                        }

                        data = new WeatherData(temp, desc, loc);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Weather fetch failed", e);
            } finally {
                if (connection != null) connection?.disconnect()
            }

            final WeatherData finalData = data;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onWeatherFetched(finalData)
                );
            }
        }
    }
}
