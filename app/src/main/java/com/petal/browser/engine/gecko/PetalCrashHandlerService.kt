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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
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

        // GeckoView crash intent extras (official GeckoView 159 API)
        const val EXTRA_MINIDUMP_PATH = "minidumpPath"
        const val EXTRA_EXTRAS_PATH = "extrasPath"
        const val EXTRA_CRASH_IS_FATAL = "isFatal"
        const val EXTRA_CRASH_PROCESS = "processType"
        const val EXTRA_CRASH_PROCESS_VISIBILITY = "processVisibility"
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
            val notification = Notification.Builder(this, channelId)
                .setContentTitle("Petal Browser")
                .setContentText("Saving diagnostic report...")
                .setSmallIcon(R.drawable.ic_petal_icon)
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

        val isFatal = intent.getBooleanExtra(EXTRA_CRASH_IS_FATAL, false)
        val processType = intent.getStringExtra(EXTRA_CRASH_PROCESS) ?: "unknown"
        val processVisibility = intent.getStringExtra(EXTRA_CRASH_PROCESS_VISIBILITY) ?: "unknown"
        val minidumpPath = intent.getStringExtra(EXTRA_MINIDUMP_PATH)
        val extrasPath = intent.getStringExtra(EXTRA_EXTRAS_PATH)

        val severity = if (isFatal) "FATAL" else "non-fatal"
        Log.e(
            TAG,
            "Gecko $severity crash intercepted: processType=$processType, visibility=$processVisibility"
        )

        val crashDetails = buildString {
            append("processType=$processType, visibility=$processVisibility, fatal=$isFatal")
            if (!minidumpPath.isNullOrBlank()) {
                append(", minidump=$minidumpPath")
                Log.w(TAG, "  minidump: $minidumpPath (local only, no upload)")
            }
            if (!extrasPath.isNullOrBlank()) {
                append(", extras=$extrasPath")
                Log.w(TAG, "  extras: $extrasPath")
            }
        }

        // Feed PetalAppLogger's internal diagnostic crash reporting flow
        try {
            PetalAppLogger.recordProcessCrash(
                this,
                TAG,
                "GeckoRuntime $severity crash in process $processType ($processVisibility)",
                crashDetails
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to feed PetalAppLogger: ${t.message}")
        }

        // Record crash statistics into local SharedPreferences (strictly offline, no network transmission)
        try {
            val prefs = getSharedPreferences("petal_crash_stats", MODE_PRIVATE)
            val count = prefs.getInt("gecko_crash_count", 0) + 1
            prefs.edit()
                .putInt("gecko_crash_count", count)
                .putBoolean("gecko_last_crash_fatal", isFatal)
                .putString("gecko_last_crash_process", processType)
                .putString("gecko_last_crash_visibility", processVisibility)
                .putLong("gecko_last_crash_ts", System.currentTimeMillis())
                .apply()
            Log.i(TAG, "Recorded Gecko crash #$count locally (no network upload)")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to persist local crash stats: ${t.message}")
        }
    }
}
