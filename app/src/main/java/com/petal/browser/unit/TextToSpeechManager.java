package com.petal.browser.unit;

import java.net.URLEncoder;

/**
 * TextToSpeechManager constructs Google TTS Read Aloud audio stream endpoints for web text.
 */
public class TextToSpeechManager {

    /**
     * Constructs Google TTS audio stream URL.
     * Endpoint: https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=&q=
     */
    public static String getTtsAudioUrl(String text, String langCode) {
        if (text == null || text.trim().isEmpty()) return "";
        try {
            String lang = langCode != null && !langCode.isEmpty() ? langCode : "en";
            return "https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=" + lang + "&q=" + URLEncoder.encode(text.trim(), "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}
