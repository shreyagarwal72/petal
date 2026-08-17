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
public class TrackerProtectionManager {

    private static final String TAG = "TrackerProtection";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Set<String> trackerDomains = new HashSet<>();
    private static boolean isLoaded = false;

    public interface TrackerCheckCallback {
        void onCheckCompleted(boolean isTracker);
    }

    /**
     * Downloads Disconnect.me tracker database asynchronously.
     * Endpoint: https://raw.githubusercontent.com/disconnectme/disconnect-tracking-protection/master/services.json
     */
    public static void loadTrackerDatabase() {
        if (isLoaded) return;
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL("https://raw.githubusercontent.com/disconnectme/disconnect-tracking-protection/master/services.json");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(4000);

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder builder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                    reader.close();

                    JSONObject root = new JSONObject(builder.toString());
                    if (root.has("categories")) {
                        JSONObject categories = root.getJSONObject("categories");
                        Iterator<String> catKeys = categories.keys();
                        while (catKeys.hasNext()) {
                            String catName = catKeys.next();
                            JSONArray services = categories.getJSONArray(catName);
                            for (int i = 0; i < services.length(); i++) {
                                JSONObject serviceObj = services.getJSONObject(i);
                                Iterator<String> servKeys = serviceObj.keys();
                                while (servKeys.hasNext()) {
                                    String servName = servKeys.next();
                                    JSONObject servData = serviceObj.getJSONObject(servName);
                                    Iterator<String> domainKeys = servData.keys();
                                    while (domainKeys.hasNext()) {
                                        String domKey = domainKeys.next();
                                        org.json.JSONArray doms = servData.getJSONArray(domKey);
                                        for (int j = 0; j < doms.length(); j++) {
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
                if (connection != null) connection.disconnect();
            }
        });
    }

    /**
     * Checks if domain is a known tracking domain.
     */
    public static boolean isTrackerDomain(String domain) {
        if (domain == null || domain.isEmpty() || !isLoaded) return false;
        String cleanDomain = domain.toLowerCase().trim();
        for (String tracker : trackerDomains) {
            if (cleanDomain.equals(tracker) || cleanDomain.endsWith("." + tracker)) {
                return true;
            }
        }
        return false;
    }
}
