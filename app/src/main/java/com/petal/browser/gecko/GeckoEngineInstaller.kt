/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT/TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.petal.browser.gecko

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.preference.PreferenceManager
import com.petal.browser.download.PetalDownloadEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

sealed interface GeckoInstallStatus {
    object NotInstalled : GeckoInstallStatus
    data class Downloading(val progress: Int, val bytesDownloaded: Long, val totalBytes: Long) : GeckoInstallStatus
    object Ready : GeckoInstallStatus
    data class Error(val message: String) : GeckoInstallStatus
}

/**
 * Manages external on-demand downloads of the Mozilla Gecko engine module from GitHub Releases.
 * Decreases base APK storage, reduces runtime memory footprint, and gives users direct choice.
 */
object GeckoEngineInstaller {
    private const val TAG = "GeckoEngineInstaller"
    const val PREF_GECKO_DOWNLOADED = "sp_gecko_engine_downloaded"
    const val GECKO_RELEASE_URL = "https://github.com/shreyagarwal72/petal/releases/download/v2.5/gecko-engine-v150.zip"

    private val _status = MutableStateFlow<GeckoInstallStatus>(GeckoInstallStatus.NotInstalled)
    val status: StateFlow<GeckoInstallStatus> = _status.asStateFlow()

    @JvmStatic
    fun isInstalled(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val prefFlag = sp.getBoolean(PREF_GECKO_DOWNLOADED, false)
        val engineDir = File(context.applicationContext.filesDir, "gecko_engine")
        return prefFlag || (engineDir.exists() && engineDir.isDirectory && engineDir.listFiles()?.isNotEmpty() == true)
    }

    @JvmStatic
    fun startDownload(context: Context, onComplete: (() -> Unit)? = null) {
        val appContext = context.applicationContext
        _status.value = GeckoInstallStatus.Downloading(0, 0L, 100L)

        // Enqueue download via Petal's high-speed parallel download engine
        PetalDownloadEngine.getInstance(appContext).enqueueDownload(
            appContext,
            GECKO_RELEASE_URL,
            "gecko-engine-v150.zip",
            null,
            null,
            null
        ) { downloadId, fileName ->
            Log.d(TAG, "Gecko Engine download enqueued: $downloadId, file: $fileName")
            PreferenceManager.getDefaultSharedPreferences(appContext)
                .edit()
                .putBoolean(PREF_GECKO_DOWNLOADED, true)
                .apply()
            _status.value = GeckoInstallStatus.Ready
            onComplete?.invoke()
        }
    }

    @JvmStatic
    fun removeEngine(context: Context) {
        val appContext = context.applicationContext
        PreferenceManager.getDefaultSharedPreferences(appContext)
            .edit()
            .putBoolean(PREF_GECKO_DOWNLOADED, false)
            .putBoolean(PREF_GECKO_ENGINE_ENABLED, false)
            .apply()

        val engineDir = File(appContext.filesDir, "gecko_engine")
        if (engineDir.exists()) {
            engineDir.deleteRecursively()
        }
        GeckoRuntimeHolder.disable(appContext)
        _status.value = GeckoInstallStatus.NotInstalled
    }
}
