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
object PhoneticTransliterationManager {

    private const val TAG = "PhoneticTranslit";
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    fun interface TransliterateCallback {
        void onTransliterated(val transliteratedText);
    }

    /**
     * Transliterates text for input language code (e.g. "hi", "ar", "ja", "ru").
     * Endpoint: https://inputtools.google.com/request?text=&itc=
     */
    @JvmStatic
    fun transliterate(text: String?, langCode: String?, callback: TransliterateCallback?) {
        if (text == null || text.trim().isEmpty()) {
            if (callback != null) callback.onTransliterated("");
            return;
        }

        executor.execute {
            val result = text.trim();
            var connection: HttpURLConnection? = null
            try {
                val itc = (langCode != null && !langCode.isEmpty() ? langCode : "hi") + "-t-i0-und";
                val apiUrl = "https://inputtools.google.com/request?text=" + URLEncoder.encode(text.trim(), "UTF-8") + "&itc=" + itc + "&num=1";
                URL url = new URL(apiUrl);

                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET");
                connection.connectTimeout = 3000);
                connection.readTimeout = 3000);

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = BufferedReader(InputStreamReader(connection.inputStream));
                    StringBuilder builder = StringBuilder()
                    val line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                    reader.close();

                    JSONArray root = JSONArray(builder.toString());
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
                if (connection != null) connection?.disconnect()
            }

            val finalResult = result;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onTransliterated(finalResult)
                );
            }
        }
    }
}
