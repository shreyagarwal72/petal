package com.petal.browser.unit;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WordDictionaryManager fetches definitions, phonetic pronunciations, and examples for English terms.
 */
public class WordDictionaryManager {

    private static final String TAG = "WordDictionaryManager";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static class WordDefinition {
        public String word;
        public String phonetic;
        public String partOfSpeech;
        public String definition;
        public String example;

        public WordDefinition(String word, String phonetic, String partOfSpeech, String definition, String example) {
            this.word = word != null ? word : "";
            this.phonetic = phonetic != null ? phonetic : "";
            this.partOfSpeech = partOfSpeech != null ? partOfSpeech : "";
            this.definition = definition != null ? definition : "";
            this.example = example != null ? example : "";
        }
    }

    public interface DictionaryCallback {
        void onDefinitionFetched(WordDefinition def);
    }

    /**
     * Fetches word definition via Free Dictionary API.
     * Endpoint: https://api.dictionaryapi.dev/api/v2/entries/en/
     */
    public static void defineWord(final String word, final DictionaryCallback callback) {
        if (word == null || word.trim().isEmpty()) {
            if (callback != null) callback.onDefinitionFetched(null);
            return;
        }

        executor.execute(() -> {
            WordDefinition def = null;
            HttpURLConnection connection = null;
            try {
                String encodedWord = URLEncoder.encode(word.trim().toLowerCase(), "UTF-8");
                String apiUrl = "https://api.dictionaryapi.dev/api/v2/entries/en/" + encodedWord;
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
                    if (root.length() > 0) {
                        JSONObject entry = root.getJSONObject(0);
                        String wordStr = entry.optString("word", word);
                        String phonetic = entry.optString("phonetic", "");

                        String partOfSpeech = "";
                        String definition = "";
                        String example = "";

                        if (entry.has("meanings")) {
                            JSONArray meanings = entry.getJSONArray("meanings");
                            if (meanings.length() > 0) {
                                JSONObject meaning = meanings.getJSONObject(0);
                                partOfSpeech = meaning.optString("partOfSpeech", "");
                                if (meaning.has("definitions")) {
                                    JSONObject defObj = meaning.getJSONArray("definitions").getJSONObject(0);
                                    definition = defObj.optString("definition", "");
                                    example = defObj.optString("example", "");
                                }
                            }
                        }

                        def = new WordDefinition(wordStr, phonetic, partOfSpeech, definition, example);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Dictionary lookup failed for word: " + word, e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            final WordDefinition finalDef = def;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onDefinitionFetched(finalDef)
                );
            }
        });
    }
}
