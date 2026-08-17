package com.petal.browser.unit;

import android.content.Intent;
import android.speech.RecognizerIntent;

/**
 * VoiceSearchManager creates Voice Search Intents for address bar hands-free search.
 */
public class VoiceSearchManager {

    /**
     * Constructs Voice Search Recognizer Intent.
     */
    public static Intent getVoiceSearchIntent(String promptHint) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, promptHint != null ? promptHint : "Search or type URL");
        return intent;
    }
}
