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
 * WebTranslatorManager uses Google Translate endpoint for instant web text/page translation.
 */
public class WebTranslatorManager {

    private static final String TAG = "WebTranslatorManager";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface TranslateCallback {
        void onTranslationResult(String translatedText);
    }

    /**
     * Translates input text into target language code (e.g. "en", "es", "fr", "de", "hi", "zh").
     */
    public static void translateText(final String text, final String targetLangCode, final TranslateCallback callback) {
        if (text == null || text.trim().isEmpty()) {
            if (callback != null) callback.onTranslationResult("");
            return;
        }

        executor.execute(() -> {
            String result = "";
            HttpURLConnection connection = null;
            try {
                String encodedText = URLEncoder.encode(text.trim(), "UTF-8");
                String lang = targetLangCode != null && !targetLangCode.isEmpty() ? targetLangCode : "en";
                String apiUrl = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=" + lang + "&dt=t&q=" + encodedText;
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

                    JSONArray jsonArray = new JSONArray(builder.toString());
                    if (jsonArray.length() > 0) {
                        JSONArray sentences = jsonArray.getJSONArray(0);
                        StringBuilder translated = new StringBuilder();
                        for (int i = 0; i < sentences.length(); i++) {
                            JSONArray sentence = sentences.getJSONArray(i);
                            translated.append(sentence.getString(0));
                        }
                        result = translated.toString();
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Translation failed for text", e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            final String finalResult = result;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onTranslationResult(finalResult)
                );
            }
        });
    }
}
