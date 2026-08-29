package com.petal.browser.unit;

import android.util.Log;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PhoneticTransliterationManager converts phonetic search queries into target language scripts using Google Input Tools API.
 */
public class PhoneticTransliterationManager {

    private static final String TAG = "PhoneticTranslit";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface TransliterateCallback {
        void onTransliterated(String transliteratedText);
    }

    /**
     * Transliterates text for input language code (e.g. "hi", "ar", "ja", "ru").
     * Endpoint: https://inputtools.google.com/request?text=&itc=
     */
    public static void transliterate(final String text, final String langCode, final TransliterateCallback callback) {
        if (text == null || text.trim().isEmpty()) {
            if (callback != null) callback.onTransliterated("");
            return;
        }

        executor.execute(() -> {
            String result = text.trim();
            HttpURLConnection connection = null;
            try {
                String itc = (langCode != null && !langCode.isEmpty() ? langCode : "hi") + "-t-i0-und";
                String apiUrl = "https://inputtools.google.com/request?text=" + URLEncoder.encode(text.trim(), "UTF-8") + "&itc=" + itc + "&num=1";
                URL url = new URL(apiUrl);

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder builder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                    reader.close();

                    JSONArray root = new JSONArray(builder.toString());
                    if ("SUCCESS".equalsIgnoreCase(root.optString(0, "")) && root.length() > 1) {
                        JSONArray item = root.getJSONArray(1).getJSONArray(0);
                        if (item.length() > 1) {
                            JSONArray candidates = item.getJSONArray(1);
                            if (candidates.length() > 0) {
                                result = candidates.getString(0);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Phonetic transliteration failed for: " + text, e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            final String finalResult = result;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onTransliterated(finalResult)
                );
            }
        });
    }
}
