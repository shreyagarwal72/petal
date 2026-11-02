/*
 * PetalQueueManager.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Media Queue & Playlist State Manager for Petal Browser.
 * Inspired by PixelPlayer's queue architecture:
 *   - Current playlist queue items with active index
 *   - Dynamic add, remove, reorder, and clear actions
 *   - Seamless traversal for next and previous tracks
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.media.queue

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class PetalQueueItem(
    val id: String,
    val uri: Uri,
    val title: String,
    val artist: String = "Unknown Artist",
    val durationMs: Long = 0L,
    val artwork: Bitmap? = null
)

class PetalQueueManager(initialItem: PetalQueueItem? = null) {

    val queue = mutableStateListOf<PetalQueueItem>()
    var currentIndex by mutableIntStateOf(0)

    init {
        if (initialItem != null) {
            queue.add(initialItem)
        }
    }

    val currentItem: PetalQueueItem?
        get() = if (queue.isNotEmpty() && currentIndex in queue.indices) queue[currentIndex] else null

    fun addItem(item: PetalQueueItem) {
        queue.add(item)
    }

    fun addNext(item: PetalQueueItem) {
        val targetIndex = (currentIndex + 1).coerceAtMost(queue.size)
        queue.add(targetIndex, item)
    }

    fun removeItem(index: Int) {
        if (index in queue.indices) {
            queue.removeAt(index)
            if (currentIndex >= queue.size) {
                currentIndex = (queue.size - 1).coerceAtLeast(0)
            }
        }
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex in queue.indices && toIndex in queue.indices) {
            val item = queue.removeAt(fromIndex)
            queue.add(toIndex, item)
            if (currentIndex == fromIndex) {
                currentIndex = toIndex
            } else if (fromIndex < currentIndex && toIndex >= currentIndex) {
                currentIndex--
            } else if (fromIndex > currentIndex && toIndex <= currentIndex) {
                currentIndex++
            }
        }
    }

    fun playIndex(index: Int): PetalQueueItem? {
        if (index in queue.indices) {
            currentIndex = index
            return queue[index]
        }
        return null
    }

    fun next(): PetalQueueItem? {
        if (queue.isEmpty()) return null
        val nextIdx = (currentIndex + 1) % queue.size
        currentIndex = nextIdx
        return queue[nextIdx]
    }

    fun previous(): PetalQueueItem? {
        if (queue.isEmpty()) return null
        val prevIdx = if (currentIndex - 1 < 0) queue.size - 1 else currentIndex - 1
        currentIndex = prevIdx
        return queue[prevIdx]
    }

    fun clear() {
        val current = currentItem
        queue.clear()
        if (current != null) {
            queue.add(current)
            currentIndex = 0
        }
    }
}
