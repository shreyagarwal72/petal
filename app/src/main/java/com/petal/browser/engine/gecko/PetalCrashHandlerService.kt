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

package com.petal.browser.engine.gecko

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.petal.browser.R
import com.petal.browser.logger.PetalAppLogger
import org.mozilla.geckoview.GeckoRuntime

/**
 * PetalCrashHandlerService
 * ─────────────────────────────────────────────────────────────────────────────
 * Out-of-process crash handler service for Mozilla GeckoView.
 * Registered in AndroidManifest.xml (`android:process=":gecko_crash"`, `android:exported="false"`)
 * and configured via GeckoRuntimeSettings.Builder.crashHandler(PetalCrashHandlerService::class.java).
 *
 * When a Gecko child/GPU or main process crashes:
 * 1. Receives the GeckoRuntime.ACTION_CRASHED intent.
 * 2. Extracts crash metadata: process type, fatal flag, process visibility, minidump & extras paths.
 * 3. Feeds PetalAppLogger's internal diagnostic crash report pipeline without uploading anything.
 * 4. Persists lightweight local crash telemetry so UI can gracefully inform the user.
 */
class PetalCrashHandlerService : Service() {

    companion object {
        private const val TAG = "PetalCrashHandler"

        // GeckoView 161 crash intent extras
        const val EXTRA_MINIDUMP_PATH = GeckoRuntime.EXTRA_MINIDUMP_PATH
        const val EXTRA_EXTRAS_PATH = GeckoRuntime.EXTRA_EXTRAS_PATH
        const val EXTRA_CRASH_PROCESS = GeckoRuntime.EXTRA_CRASH_PROCESS_TYPE
        const val EXTRA_CRASH_PROCESS_VISIBILITY = GeckoRuntime.EXTRA_CRASH_PROCESS_VISIBILITY
        const val EXTRA_CRASH_REMOTE_TYPE = GeckoRuntime.EXTRA_CRASH_REMOTE_TYPE
        private const val CRASH_NOTIFICATION_ID = 9021
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            ensureForegroundNotification()
            handleCrashIntent(intent)
        } catch (t: Throwable) {
            Log.e(TAG, "Unexpected error in crash handler service", t)
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Throwable) {}
            }
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun ensureForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "petal_crash_handler_channel"
            val nm = getSystemService(NotificationManager::class.java)
            if (nm != null && nm.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(
                    channelId,
                    "Crash Diagnostics",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("Petal Browser")
                .setContentText("Saving diagnostic report...")
                .setSmallIcon(R.drawable.ic_splash_petal)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    CRASH_NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(CRASH_NOTIFICATION_ID, notification)
            }
        }
    }

    private fun handleCrashIntent(intent: Intent?) {
        if (intent == null) {
            Log.w(TAG, "GeckoCrashHandler received null intent")
            return
        }

        val action = intent.action
        if (action != GeckoRuntime.ACTION_CRASHED) {
            Log.w(TAG, "Unexpected action in crash handler: $action")
            return
        }

        val processType = intent.getStringExtra(EXTRA_CRASH_PROCESS) ?: "unknown"
        val processVisibility = intent.getStringExtra(EXTRA_CRASH_PROCESS_VISIBILITY) ?: "unknown"
        val remoteType = intent.getStringExtra(EXTRA_CRASH_REMOTE_TYPE) ?: ""
        val isFatal = processVisibility == GeckoRuntime.CRASHED_PROCESS_VISIBILITY_MAIN
        val minidumpPath = intent.getStringExtra(EXTRA_MINIDUMP_PATH)
        val extrasPath = intent.getStringExtra(EXTRA_EXTRAS_PATH)

        val severity = if (isFatal) "FATAL" else "non-fatal"
        Log.e(
            TAG,
            "Gecko $severity crash intercepted: processType=$processType, visibility=$processVisibility, remoteType=$remoteType"
        )

        val crashDetails = buildString {
            append("processType=$processType, visibility=$processVisibility, remoteType=$remoteType, fatal=$isFatal")
            if (!minidumpPath.isNullOrBlank()) {
                append(", minidump=$minidumpPath")
                Log.w(TAG, "  minidump: $minidumpPath (local only, no upload)")
            }
            if (!extrasPath.isNullOrBlank()) {
                append(", extras=$extrasPath")
                Log.w(TAG, "  extras: $extrasPath")
            }
        }

        // Write an atomic per-crash report file in filesDir/gecko_crashes instead of
        // using MODE_PRIVATE SharedPreferences or uncoordinated last_crash.log from :gecko_crash.
        try {
            val crashDir = java.io.File(filesDir, "gecko_crashes").apply { mkdirs() }
            val ts = System.currentTimeMillis()
            val outFile = java.io.File(crashDir, "crash_$ts.log")
            val atomic = androidx.core.util.AtomicFile(outFile)
            val stream = atomic.startWrite()
            try {
                stream.write(
                    ("ts=$ts\nseverity=$severity\n$crashDetails\n").toByteArray(Charsets.UTF_8)
                )
                atomic.finishWrite(stream)
            } catch (t: Throwable) {
                atomic.failWrite(stream)
                throw t
            }
            // Retain only the latest 20 crash files
            crashDir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(20)?.forEach {
                try { it.delete() } catch (_: Throwable) {}
            }
            Log.i(TAG, "Recorded Gecko crash locally to ${outFile.name} (no network upload)")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to persist local crash report: ${t.message}")
        }
    }
}
