package com.petal.browser.compose.downloads

/**
 * Standard download status codes used across Petal Browser's unified download pipeline.
 * Replaces android.app.DownloadManager status constants, allowing completely standalone operation.
 */
object DownloadStatus {
    const val STATUS_PENDING = 1
    const val STATUS_RUNNING = 2
    const val STATUS_PAUSED = 4
    const val STATUS_SUCCESSFUL = 8
    const val STATUS_FAILED = 16
}
