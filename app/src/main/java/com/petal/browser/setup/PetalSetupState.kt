package com.petal.browser.setup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Survives rotation / recreation through [SavedStateHandle]. Everything the user picks is written
 * straight to SharedPreferences by the pages, so this only tracks navigation + the two choices that
 * need to outlive a configuration change before they are committed (language, search engine).
 */
class PetalSetupState(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    private val _stage = MutableStateFlow(savedStateHandle[KEY_STAGE] ?: 0)
    val stage: StateFlow<Int> = _stage.asStateFlow()

    /** BCP-47 tag, or "system" to follow the device language. */
    private val _language = MutableStateFlow(savedStateHandle[KEY_LANGUAGE] ?: "system")
    val language: StateFlow<String> = _language.asStateFlow()

    private val _skipConfirmation = MutableStateFlow(savedStateHandle[KEY_SKIP_CONFIRMATION] ?: false)
    val skipConfirmation: StateFlow<Boolean> = _skipConfirmation.asStateFlow()

    private val _selectedEngine = MutableStateFlow(savedStateHandle[KEY_ENGINE] ?: 0)
    val selectedEngine: StateFlow<Int> = _selectedEngine.asStateFlow()

    fun goTo(stage: Int) {
        _stage.value = stage.coerceIn(0, STAGE_COUNT - 1)
        savedStateHandle[KEY_STAGE] = _stage.value
        setSkipConfirmation(false)
    }

    fun next() = goTo(_stage.value + 1)

    /** Back on the first page asks whether to leave setup instead of doing nothing. */
    fun previous() {
        if (_stage.value == 0) setSkipConfirmation(true) else goTo(_stage.value - 1)
    }

    fun requestSkip() = setSkipConfirmation(true)
    fun dismissSkip() = setSkipConfirmation(false)

    fun setLanguage(language: String) {
        _language.value = language
        savedStateHandle[KEY_LANGUAGE] = language
    }

    fun setSelectedEngine(index: Int) {
        _selectedEngine.value = index
        savedStateHandle[KEY_ENGINE] = index
    }

    private fun setSkipConfirmation(value: Boolean) {
        _skipConfirmation.value = value
        savedStateHandle[KEY_SKIP_CONFIRMATION] = value
    }

    companion object {
        /** Hello · Look · Privacy · Access · Search · Ready */
        const val STAGE_COUNT = 6
        const val STAGE_WELCOME = 0
        const val STAGE_LOOK = 1
        const val STAGE_PRIVACY = 2
        const val STAGE_ACCESS = 3
        const val STAGE_SEARCH = 4
        const val STAGE_READY = 5

        private const val KEY_STAGE = "petal_setup_stage"
        private const val KEY_LANGUAGE = "petal_setup_language"
        private const val KEY_SKIP_CONFIRMATION = "petal_setup_skip_confirmation"
        private const val KEY_ENGINE = "petal_setup_engine"
    }
}
