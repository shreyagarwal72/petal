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
object WordDictionaryManager {

    private const val TAG = "WordDictionaryManager";
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    class WordDefinition(
        public val word;
        public val phonetic;
        public val partOfSpeech;
        public val definition;
        public val example;

        public WordDefinition(val word, val phonetic, val partOfSpeech, val definition, val example) {
            this.word = word != null ? word : "";
            this.phonetic = phonetic != null ? phonetic : "";
            this.partOfSpeech = partOfSpeech != null ? partOfSpeech : "";
            this.definition = definition != null ? definition : "";
            this.example = example != null ? example : "";
        }
    }

    fun interface DictionaryCallback {
        void onDefinitionFetched(WordDefinition def);
    }

    /**
     * Fetches word definition via Free Dictionary API.
     * Endpoint: https://api.dictionaryapi.dev/api/v2/entries/en/
     */
    @JvmStatic
    fun defineWord(word: String?, callback: DictionaryCallback?) {
        if (word == null || word.trim().isEmpty()) {
            if (callback != null) callback.onDefinitionFetched(null);
            return;
        }

        executor.execute {
            WordDefinition def = null
            var connection: HttpURLConnection? = null
            try {
                val encodedWord = URLEncoder.encode(word.trim().toLowerCase(), "UTF-8");
                val apiUrl = "https://api.dictionaryapi.dev/api/v2/entries/en/" + encodedWord;
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
                    if (root.length() > 0) {
                        JSONObject entry = root.getJSONObject(0);
                        val wordStr = entry.optString("word", word);
                        val phonetic = entry.optString("phonetic", "");

                        val partOfSpeech = "";
                        val definition = "";
                        val example = "";

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
                if (connection != null) connection?.disconnect()
            }

            final WordDefinition finalDef = def;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onDefinitionFetched(finalDef)
                );
            }
        }
    }
}
