package com.petal.browser.unit

import android.content.Intent
import android.speech.RecognizerIntent

/**
 * VoiceSearchManager creates Voice Search Intents for address bar hands-free search.
 */
object VoiceSearchManager {

    /**
     * Constructs Voice Search Recognizer Intent.
     */
    @JvmStatic
    fun getVoiceSearchIntent(promptHint: String?): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, promptHint ?: "Search or type URL")
        }
    }
}
