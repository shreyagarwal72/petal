package com.petal.browser.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks active hardware camera & microphone streams across web origins.
 */
object PetalActiveMediaSensors {
    private val activeSensors = ConcurrentHashMap<String, MutableSet<PetalPermissionType>>()

    /** Bumped on every change so Compose callers of [hasAnyActive] recompose when sensors start/stop. */
    private var version by mutableIntStateOf(0)

    @JvmStatic
    fun setRecording(origin: String, type: PetalPermissionType, isRecording: Boolean) {
        val set = activeSensors.getOrPut(origin) { Collections.newSetFromMap(ConcurrentHashMap()) }
        if (isRecording) set.add(type) else set.remove(type)
        version++
    }

    @JvmStatic
    fun isRecording(origin: String, type: PetalPermissionType): Boolean {
        return activeSensors[origin]?.contains(type) == true
    }

    @JvmStatic
    fun hasAnyActive(origin: String): Boolean {
        @Suppress("UNUSED_VARIABLE") val observed = version // registers a snapshot read
        return !activeSensors[origin].isNullOrEmpty()
    }

    @JvmStatic
    fun clearOrigin(origin: String) {
        activeSensors.remove(origin)
        version++
    }

    @JvmStatic
    fun clearAll() {
        activeSensors.clear()
        version++
    }
}
