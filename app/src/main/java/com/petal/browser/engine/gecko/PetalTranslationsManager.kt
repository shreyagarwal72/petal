package com.petal.browser.engine.gecko

import android.util.Log
import com.petal.browser.browser.PetalOwnedDelegate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.TranslationsController
import org.mozilla.geckoview.TranslationsController.SessionTranslation

/**
 * On-device page translation, driven directly by GeckoView's [TranslationsController]
 * (Firefox Translations). Page text and URLs never leave the device: Gecko downloads language
 * packs from Mozilla's remote settings and translates in-process.
 *
 * Holds one [TabState] per tab. The tab controller installs [createDelegate] on its
 * GeckoSession (chaining Mozilla's own delegate) and calls [reset] on navigation.
 */
object PetalTranslationsManager {

    private const val TAG = "PetalTranslations"

    data class TabState(
        /** Detected language tag of the page, e.g. "fr". */
        val pageLanguage: String? = null,
        /** The user's preferred language tag reported by Gecko, e.g. "en". */
        val userLanguage: String? = null,
        val pageLanguageSupported: Boolean = false,
        /** Language pair currently applied to the page, or null when showing the original. */
        val translatedFrom: String? = null,
        val translatedTo: String? = null,
        val processing: Boolean = false,
        val error: String? = null,
        /** Target language picked in the sheet; falls back to [userLanguage] then "en". */
        val targetOverride: String? = null,
    ) {
        val isTranslated: Boolean get() = translatedFrom != null && translatedTo != null && !processing
        val targetLanguage: String get() = targetOverride ?: userLanguage ?: "en"

        /** True when the chip should be shown: the page is translatable, or already translated. */
        val isTranslatable: Boolean
            get() = isTranslated || processing ||
                (pageLanguageSupported && !pageLanguage.isNullOrBlank() &&
                    !sameLanguage(pageLanguage, userLanguage ?: "en"))
    }

    private val _states = MutableStateFlow<Map<String, TabState>>(emptyMap())
    val states: StateFlow<Map<String, TabState>> = _states.asStateFlow()

    private val _supported = MutableStateFlow<TranslationsController.RuntimeTranslation.TranslationSupport?>(null)
    val supported: StateFlow<TranslationsController.RuntimeTranslation.TranslationSupport?> = _supported.asStateFlow()

    private fun sameLanguage(a: String?, b: String?): Boolean =
        a != null && b != null && a.substringBefore('-').equals(b.substringBefore('-'), ignoreCase = true)

    private fun mutate(tabId: String, block: (TabState) -> TabState) {
        _states.update { map ->
            val current = map[tabId] ?: TabState()
            map + (tabId to block(current))
        }
    }

    fun reset(tabId: String) {
        _states.update { map ->
            val prev = map[tabId] ?: return@update map
            map + (tabId to TabState(targetOverride = prev.targetOverride))
        }
    }

    fun remove(tabId: String) {
        _states.update { map ->
            if (map.containsKey(tabId)) map - tabId else map
        }
    }

    fun setTarget(tabId: String, languageTag: String) = mutate(tabId) { it.copy(targetOverride = languageTag) }

    /**
     * Builds the session delegate. [engineDelegate] is Mozilla's own delegate (installed by
     * GeckoEngineSession) and keeps receiving every callback so the BrowserStore stays in sync.
     */
    fun createDelegate(
        tabId: String,
        engineDelegate: SessionTranslation.Delegate?
    ): SessionTranslation.Delegate = object : SessionTranslation.Delegate, PetalOwnedDelegate {
        override fun onOfferTranslate(session: GeckoSession) {
            try { engineDelegate?.onOfferTranslate(session) } catch (_: Throwable) {}
        }

        override fun onExpectedTranslate(session: GeckoSession) {
            try { engineDelegate?.onExpectedTranslate(session) } catch (_: Throwable) {}
        }

        override fun onTranslationStateChange(
            session: GeckoSession,
            translationState: SessionTranslation.TranslationState?
        ) {
            try { engineDelegate?.onTranslationStateChange(session, translationState) } catch (_: Throwable) {}
            val detected = translationState?.detectedLanguages
            val pair = translationState?.requestedTranslationPair
            val error = translationState?.error
            val visible = translationState?.hasVisibleChange == true
            mutate(tabId) { old ->
                old.copy(
                    pageLanguage = detected?.docLangTag ?: old.pageLanguage,
                    userLanguage = detected?.userLangTag ?: old.userLanguage,
                    pageLanguageSupported = detected?.isDocLangTagSupported ?: old.pageLanguageSupported,
                    translatedFrom = if (pair != null && error == null && visible) pair.fromLanguage else if (pair == null) null else old.translatedFrom,
                    translatedTo = if (pair != null && error == null && visible) pair.toLanguage else if (pair == null) null else old.translatedTo,
                    processing = pair != null && error == null && !visible,
                    error = error
                )
            }
        }
    }

    /** Loads the list of languages Gecko can translate from/to (for the language pickers). */
    fun loadSupportedLanguages() {
        if (_supported.value != null) return
        try {
            TranslationsController.RuntimeTranslation.listSupportedLanguages().accept(
                { support -> _supported.value = support },
                { t -> Log.w(TAG, "listSupportedLanguages failed: ${t?.message}") }
            )
        } catch (t: Throwable) {
            Log.w(TAG, "listSupportedLanguages unavailable", t)
        }
    }

    /** Download size in bytes of the language packs [from]->[to] still needs (0 when cached). */
    fun checkDownloadSize(from: String, to: String, onResult: (Long) -> Unit) {
        try {
            TranslationsController.RuntimeTranslation.checkPairDownloadSize(from, to).accept(
                { size -> onResult(size ?: 0L) },
                { onResult(0L) }
            )
        } catch (_: Throwable) {
            onResult(0L)
        }
    }

    fun translate(tabId: String, session: GeckoSession?, from: String, to: String) {
        val st = session?.sessionTranslation ?: return
        mutate(tabId) { it.copy(processing = true, error = null, targetOverride = to) }
        try {
            // downloadModel(true): fetch missing language packs on demand (on-device, from Mozilla).
            val options = SessionTranslation.TranslationOptions.Builder().downloadModel(true).build()
            st.translate(from, to, options).accept(
                { /* success is reported through onTranslationStateChange */ },
                { t ->
                    Log.w(TAG, "translate failed: ${t?.message}")
                    mutate(tabId) { it.copy(processing = false, error = t?.message ?: "error") }
                }
            )
        } catch (t: Throwable) {
            mutate(tabId) { it.copy(processing = false, error = t.message ?: "error") }
        }
    }

    fun restoreOriginal(tabId: String, session: GeckoSession?) {
        val st = session?.sessionTranslation ?: return
        try {
            st.restoreOriginalPage().accept(
                { mutate(tabId) { it.copy(translatedFrom = null, translatedTo = null, processing = false, error = null) } },
                { t ->
                    Log.w(TAG, "restoreOriginalPage failed: ${t?.message}")
                    mutate(tabId) { it.copy(processing = false, error = t?.message ?: "error") }
                }
            )
        } catch (t: Throwable) {
            mutate(tabId) { it.copy(processing = false, error = t.message ?: "error") }
        }
    }

    /** Always translate (or stop translating) [languageTag] automatically on page load. */
    fun setAlwaysTranslate(languageTag: String, always: Boolean) {
        try {
            val setting = if (always) TranslationsController.RuntimeTranslation.ALWAYS
            else TranslationsController.RuntimeTranslation.OFFER
            TranslationsController.RuntimeTranslation.setLanguageSettings(languageTag, setting).accept(
                { },
                { t -> Log.w(TAG, "setLanguageSettings failed: ${t?.message}") }
            )
        } catch (_: Throwable) {}
    }

    fun getAlwaysTranslate(languageTag: String, onResult: (Boolean) -> Unit) {
        try {
            TranslationsController.RuntimeTranslation.getLanguageSetting(languageTag).accept(
                { value -> onResult(value == TranslationsController.RuntimeTranslation.ALWAYS) },
                { onResult(false) }
            )
        } catch (_: Throwable) {
            onResult(false)
        }
    }
}
