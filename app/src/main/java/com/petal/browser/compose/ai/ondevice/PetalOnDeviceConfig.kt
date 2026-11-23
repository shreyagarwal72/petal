package com.petal.browser.compose.ai.ondevice

import android.content.Context
import com.petal.browser.BuildConfig

/**
 * Configuration and distribution flag gates for on-device Gemma LLM inference.
 *
 * Rules:
 * - Only enabled for the `github` flavor (`DISTRIBUTION_CHANNEL == "github"`).
 * - Excluded from `fdroid` due to binary blob / non-FOSS native runtime constraints.
 * - Excluded from `play` to satisfy Play Store compliance and strict package size limits.
 * - Never bundles model weights in the APK.
 */
object PetalOnDeviceConfig {

    const val MODEL_DIR = "petal_ai_models"
    const val DEFAULT_MODEL_NAME = "gemma-2b-it-cpu.bin"

    /** Minimum recommended free RAM in bytes to safely load and run a 2B parameter model (~2.5 GB) */
    const val MIN_FREE_RAM_BYTES = 2_500_000_000L

    /**
     * Returns true if on-device Gemma inference is allowed on this build flavor.
     * Excluded on F-Droid (pure FOSS, no binary blobs) and Google Play (store policy & package size).
     */
    fun isSupported(): Boolean {
        return try {
            !BuildConfig.IS_FDROID_BUILD && !BuildConfig.IS_PLAY_STORE_BUILD
        } catch (_: Throwable) {
            true
        }
    }
}
