package com.petal.browser.scanner

/**
 * Immutable model representing a processed barcode / QR code result.
 */
data class PetalScanResult(
    val rawValue: String,
    val formatName: String,
    val payload: ScanPayload,
    val safetyReport: LinkSafetyReport? = null,
    val timestamp: Long = System.currentTimeMillis()
)
