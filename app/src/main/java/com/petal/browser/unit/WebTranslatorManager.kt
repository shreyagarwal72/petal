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
 * WebTranslatorManager uses Google Translate endpoval for instant web text/page translation.
 */
object WebTranslatorManager {

    private const val TAG = "WebTranslatorManager";
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    fun interface TranslateCallback {
        void onTranslationResult(val translatedText);
    }

    /**
     * Translates input text into target language code (e.g. "en", "es", "fr", "de", "hi", "zh").
     */
    @JvmStatic
    fun translateText(text: String?, targetLangCode: String?, callback: TranslateCallback?) {
        if (text == null || text.trim().isEmpty()) {
            if (callback != null) callback.onTranslationResult("");
            return;
        }

        executor.execute {
            val result = "";
            var connection: HttpURLConnection? = null
            try {
                val encodedText = URLEncoder.encode(text.trim(), "UTF-8");
                val lang = targetLangCode != null && !targetLangCode.isEmpty() ? targetLangCode : "en";
                val apiUrl = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=" + lang + "&dt=t&q=" + encodedText;
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

                    JSONArray jsonArray = JSONArray(builder.toString());
                    if (jsonArray.length() > 0) {
                        JSONArray sentences = jsonArray.getJSONArray(0);
                        StringBuilder translated = StringBuilder()
                        for (val i = 0; i < sentences.length(); i++) {
                            JSONArray sentence = sentences.getJSONArray(i);
                            translated.append(sentence.getString(0));
                        }
                        result = translated.toString();
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Translation failed for text", e);
            } finally {
                if (connection != null) connection?.disconnect()
            }

            val finalResult = result;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onTranslationResult(finalResult)
                );
            }
        }
    }
}
