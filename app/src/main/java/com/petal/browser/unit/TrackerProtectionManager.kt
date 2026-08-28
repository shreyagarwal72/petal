package com.petal.browser.unit;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TrackerProtectionManager loads and queries Disconnect.me tracking protection database.
 */
object TrackerProtectionManager {

    private const val TAG = "TrackerProtection";
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private static final Set<String> trackerDomains = new HashSet<>();
    private static val isLoaded = false;

    fun interface TrackerCheckCallback {
        void onCheckCompleted(val isTracker);
    }

    /**
     * Downloads Disconnect.me tracker database asynchronously.
     * Endpoint: https://raw.githubusercontent.com/disconnectme/disconnect-tracking-protection/master/services.json
     */
    @JvmStatic
    fun loadTrackerDatabase() {
        if (isLoaded) return;
        executor.execute {
            var connection: HttpURLConnection? = null
            try {
                URL url = new URL("https://raw.githubusercontent.com/disconnectme/disconnect-tracking-protection/master/services.json");
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET");
                connection.connectTimeout = 4000);
                connection.readTimeout = 4000);

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = BufferedReader(InputStreamReader(connection.inputStream));
                    StringBuilder builder = StringBuilder()
                    val line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                    reader.close();

                    JSONObject root = JSONObject(builder.toString());
                    if (root.has("categories")) {
                        JSONObject categories = root.getJSONObject("categories");
                        Iterator<String> catKeys = categories.keys();
                        while (catKeys.hasNext()) {
                            val catName = catKeys.next();
                            JSONArray services = categories.getJSONArray(catName);
                            for (val i = 0; i < services.length(); i++) {
                                JSONObject serviceObj = services.getJSONObject(i);
                                Iterator<String> servKeys = serviceObj.keys();
                                while (servKeys.hasNext()) {
                                    val servName = servKeys.next();
                                    JSONObject servData = serviceObj.getJSONObject(servName);
                                    Iterator<String> domainKeys = servData.keys();
                                    while (domainKeys.hasNext()) {
                                        val domKey = domainKeys.next();
                                        org.json.JSONArray doms = servData.getJSONArray(domKey);
                                        for (val j = 0; j < doms.length(); j++) {
                                            trackerDomains.add(doms.getString(j).toLowerCase());
                                        }
                                    }
                                }
                            }
                        }
                    }
                    isLoaded = true;
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to load Disconnect.me tracker database", e);
            } finally {
                if (connection != null) connection?.disconnect()
            }
        }
    }

    /**
     * Checks if domain is a known tracking domain.
     */
    @JvmStatic
    fun isTrackerDomain(domain: String?): Boolean {
        if (domain == null || domain.isEmpty() || !isLoaded) return false;
        val cleanDomain = domain.toLowerCase().trim();
        for (val tracker : trackerDomains) {
            if (cleanDomain.equals(tracker) || cleanDomain.endsWith("." + tracker)) {
                return true;
            }
        }
        return false;
    }
}
