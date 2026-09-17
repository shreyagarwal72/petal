/*
 * MediaHandoff.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Seamless Media Handoff model for Petal Browser.
 * Captures the live playback state of a webpage's HTML5 <video> element
 * so playback transitions into the native player without losing position.
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.media.handoff

data class MediaHandoff(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val isPaused: Boolean = false,
    val volume: Float = 1.0f,
    val sourceUri: String = ""
) {
    companion object {
        const val EXTRA_HANDOFF_POSITION_MS = "extra_handoff_position_ms"
        const val EXTRA_HANDOFF_SPEED = "extra_handoff_speed"
        const val EXTRA_HANDOFF_IS_PAUSED = "extra_handoff_is_paused"
    }
}
