package com.petal.browser.unit

import java.net.URLEncoder

/**
 * TextToSpeechManager constructs Google TTS Read Aloud audio stream endpoints for web text.
 */
object TextToSpeechManager {

    @JvmStatic
    fun getTtsAudioUrl(text: String?, langCode: String?): String {

        if (text.isNullOrBlank()) return ""
        return try {
            String lang = langCode != null && !langCode.isEmpty() ? langCode : "en";
            "https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=" + lang + "&q=" + URLEncoder.encode(text.trim(), "UTF-8");
        
    }
}
