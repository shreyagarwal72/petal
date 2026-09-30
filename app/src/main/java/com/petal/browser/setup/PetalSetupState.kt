package com.petal.browser.setup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PetalSetupState(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    private val _stage = MutableStateFlow(savedStateHandle[KEY_STAGE] ?: 0)
    val stage: StateFlow<Int> = _stage.asStateFlow()

    private val _language = MutableStateFlow(savedStateHandle[KEY_LANGUAGE] ?: "en")
    val language: StateFlow<String> = _language.asStateFlow()

    private val _skipConfirmation = MutableStateFlow(savedStateHandle[KEY_SKIP_CONFIRMATION] ?: false)
    val skipConfirmation: StateFlow<Boolean> = _skipConfirmation.asStateFlow()

    private val _avatarUri = MutableStateFlow(savedStateHandle[KEY_AVATAR_URI] ?: "")
    val avatarUri: StateFlow<String> = _avatarUri.asStateFlow()

    private val _selectedEngine = MutableStateFlow(savedStateHandle[KEY_ENGINE] ?: 0)
    val selectedEngine: StateFlow<Int> = _selectedEngine.asStateFlow()

    fun goTo(stage: Int) {
        _stage.value = stage.coerceIn(0, 4)
        savedStateHandle[KEY_STAGE] = _stage.value
        _skipConfirmation.value = false
        savedStateHandle[KEY_SKIP_CONFIRMATION] = false
    }

    fun next() = goTo(_stage.value + 1)
    fun previous() {
        if (_stage.value == 0) {
            _skipConfirmation.value = true
            savedStateHandle[KEY_SKIP_CONFIRMATION] = true
        } else goTo(_stage.value - 1)
    }

    fun confirmSkip() {
        _skipConfirmation.value = false
        savedStateHandle[KEY_SKIP_CONFIRMATION] = false
    }

    fun setLanguage(language: String) {
        _language.value = language
        savedStateHandle[KEY_LANGUAGE] = language
    }

    fun setAvatarUri(uri: String) {
        _avatarUri.value = uri
        savedStateHandle[KEY_AVATAR_URI] = uri
    }

    fun setSelectedEngine(index: Int) {
        _selectedEngine.value = index
        savedStateHandle[KEY_ENGINE] = index
    }

    companion object {
        private const val KEY_STAGE = "petal_setup_stage"
        private const val KEY_LANGUAGE = "petal_setup_language"
        private const val KEY_SKIP_CONFIRMATION = "petal_setup_skip_confirmation"
        private const val KEY_AVATAR_URI = "petal_setup_avatar_uri"
        private const val KEY_ENGINE = "petal_setup_engine"
    }
}
