package com.petal.browser.island

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * True while any Petal activity is on screen. The in-app island is used then; the
 * over-other-apps overlay only appears when this is false, so you never see two islands.
 */
object PetalForegroundTracker {
    private val _inForeground = MutableStateFlow(false)
    val inForeground: StateFlow<Boolean> = _inForeground.asStateFlow()

    fun onStartedCountChanged(count: Int) {
        _inForeground.value = count > 0
    }
}
