package com.petal.browser.download.queue

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row of the download queue. Private downloads use the same class but are kept in memory only
 * (negative ids) and are never written to Room.
 */
@Entity(tableName = "download_queue")
data class QueuedDownload(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val fileName: String,
    val mimeType: String? = null,
    val referrerUrl: String? = null,
    val userAgent: String? = null,
    /** JSON of non-secret request headers (Cookie / Authorization are never stored). */
    val headersJson: String = "{}",
    val priority: Int = PRIORITY_NORMAL,
    val state: Int = STATE_QUEUED,
    /** Do not start before this time (epoch ms); 0 = as soon as a slot is free. */
    val scheduledAt: Long = 0L,
    val wifiOnly: Boolean = false,
    val isPrivate: Boolean = false,
    /** Mozilla BrowserStore download id once started. */
    val storeId: String? = null,
    val attempts: Int = 0,
    val nextAttemptAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val lastError: String? = null
) {
    companion object {
        const val STATE_QUEUED = 0
        const val STATE_STARTING = 1
        const val STATE_RUNNING = 2
        const val STATE_FAILED = 3
        const val STATE_PAUSED = 4 // held by "pause all" / by the user

        const val PRIORITY_LOW = -1
        const val PRIORITY_NORMAL = 0
        const val PRIORITY_HIGH = 1
    }
}
