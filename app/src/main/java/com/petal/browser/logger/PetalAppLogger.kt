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

package com.petal.browser.logger

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builtin App Logger & Crash Reporting Engine for Petal Browser.
 *
 * Features:
 * 1. Thread-safe in-memory ring buffer for runtime diagnostic logs.
 * 2. Uncaught Exception interception with persistent disk crash dump (`last_crash.log`).
 * 3. Crash recovery detection on cold launch with Material 3 interactive report prompt.
 * 4. Configurable crash reporting mode ("auto" / "off") inspired by Essentials.
 * 5. Diagnostic bundle exporter (.zip) including logcat, stacktraces, device specs, and sanitized preferences.
 * 6. Direct GitHub Issue reporting pre-populated with crash stack traces and system diagnostics.
 */
object PetalAppLogger {

    private const val TAG = "PetalAppLogger"
    private const val MAX_IN_MEMORY_LOGS = 500
    private const val CRASH_LOG_FILENAME = "last_crash.log"
    const val PREF_CRASH_REPORT_MODE = "sp_crash_report_mode" // "auto" or "off"

    private val logBuffer = ConcurrentLinkedQueue<String>()
    private val crashTraces = ConcurrentLinkedQueue<String>()

    private var defaultUncaughtHandler: Thread.UncaughtExceptionHandler? = null
    private var lastCrashReport: String? = null

    @JvmStatic
    fun init(context: Context) {
        // Read previous crash report from disk if exists
        try {
            val crashFile = File(context.filesDir, CRASH_LOG_FILENAME)
            if (crashFile.exists()) {
                val content = crashFile.readText()
                if (content.isNotBlank()) {
                    lastCrashReport = content
                    crashTraces.add(content)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read crash log from disk", e)
        }

        if (defaultUncaughtHandler == null) {
            defaultUncaughtHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    handleCrash(context, thread, throwable)
                } catch (e: Exception) {
                    Log.e(TAG, "Error recording uncaught crash", e)
                } finally {
                    defaultUncaughtHandler?.uncaughtException(thread, throwable)
                }
            }
            log(TAG, "PetalAppLogger initialized successfully")
        }
    }

    @JvmStatic
    fun log(tag: String, message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val entry = "[$timestamp] [$tag] $message"
        Log.i(tag, message)
        logBuffer.add(entry)
        while (logBuffer.size > MAX_IN_MEMORY_LOGS) {
            logBuffer.poll()
        }
    }

    @JvmStatic
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val sw = StringWriter()
        throwable?.printStackTrace(PrintWriter(sw))
        val stack = sw.toString()
        val entry = "[$timestamp] [ERROR] [$tag] $message${if (stack.isNotBlank()) "\n$stack" else ""}"
        Log.e(tag, message, throwable)
        logBuffer.add(entry)
        while (logBuffer.size > MAX_IN_MEMORY_LOGS) {
            logBuffer.poll()
        }
    }

    private fun handleCrash(context: Context, thread: Thread, throwable: Throwable) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stack = sw.toString()

        val report = buildString {
            append("=== PETAL BROWSER UNCAUGHT CRASH ===\n")
            append("Timestamp: $timestamp\n")
            append("Thread: ${thread.name} (id=${thread.id})\n")
            append("Exception: ${throwable.javaClass.name}\n")
            append("Message: ${throwable.message}\n")
            append("App Version: ${try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch (_: Throwable) { "Unknown" }}\n")
            append("Android OS: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
            append("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})\n")
            append("\nStacktrace:\n$stack\n")
            append("\n--- Last In-Memory Logs Before Crash ---\n")
            logBuffer.takeLast(40).forEach { line ->
                append(line)
                append("\n")
            }
        }

        crashTraces.add(report)
        log(TAG, report)

        try {
            val crashFile = File(context.filesDir, CRASH_LOG_FILENAME)
            crashFile.writeText(report)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist crash dump file", e)
        }
    }

    /**
     * Checks if a previous crash log is available for user reporting.
     */
    @JvmStatic
    fun hasPendingCrashReport(): Boolean {
        return !lastCrashReport.isNullOrBlank()
    }

    /**
     * Retrieves the last crash log content.
     */
    @JvmStatic
    fun getLastCrashReport(): String? {
        return lastCrashReport
    }

    /**
     * Clears the pending crash report and deletes the disk log file.
     */
    @JvmStatic
    fun clearPendingCrashReport(context: Context) {
        lastCrashReport = null
        try {
            val crashFile = File(context.filesDir, CRASH_LOG_FILENAME)
            if (crashFile.exists()) {
                crashFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete crash dump file", e)
        }
    }

    /**
     * Simulates a test crash for Developer & Debug validation (like Essentials).
     */
    @JvmStatic
    fun simulateCrash() {
        throw RuntimeException("Simulated test crash from Petal Browser Developer Options")
    }

    /**
     * Generates a GitHub Issue URL pre-filled with the crash trace and device diagnostics.
     */
    @JvmStatic
    fun openGitHubCrashIssue(context: Context, crashText: String?) {
        try {
            val title = "Crash: " + (crashText?.lineSequence()?.firstOrNull { it.startsWith("Exception:") }?.removePrefix("Exception:")?.trim() ?: "Unexpected App Crash")
            val issueBody = buildString {
                append("### Description\nPetal Browser encountered an unexpected crash.\n\n")
                append("### Crash Trace\n```\n")
                append(crashText?.take(3000) ?: "No crash trace available.")
                append("\n```\n\n")
                append("### Device Info\n")
                append("- Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
                append("- Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                append("- App Version: ${try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch (_: Throwable) { "Unknown" }}\n")
            }

            val encodedTitle = Uri.encode(title.take(120))
            val encodedBody = Uri.encode(issueBody)
            val issueUrl = "https://github.com/shreyagarwal72/petal/issues/new?title=$encodedTitle&body=$encodedBody"

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(issueUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to launch GitHub Issue URL", e)
        }
    }

    @JvmStatic
    fun exportLogsZip(context: Context): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val exportDir = File(context.cacheDir, "logs_export").apply { mkdirs() }
        val zipFile = File(exportDir, "petal_diagnostic_logs_$timestamp.zip")

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            // 1. In-memory logs
            val memoryLogsText = logBuffer.joinToString("\n")
            addZipEntry(zos, "petal_memory_logs.txt", memoryLogsText.ifBlank { "No in-memory logs recorded." })

            // 2. Crash logs
            val crashLogsText = crashTraces.joinToString("\n----------------------------------------\n")
            addZipEntry(zos, "petal_crash_logs.txt", crashLogsText.ifBlank { "No uncaught crashes recorded." })

            // 3. System & App Info
            val systemInfo = buildString {
                appendLine("=== PETAL BROWSER SYSTEM INFO ===")
                appendLine("Generated At: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                appendLine("App Version: ${try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch (_: Throwable) { "Unknown" }}")
                appendLine("App Package: ${context.packageName}")
                appendLine("Android OS Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                appendLine("Device Model: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
                appendLine("Board: ${Build.BOARD}")
                appendLine("Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
                val dm = context.resources.displayMetrics
                appendLine("Display: ${dm.widthPixels}x${dm.heightPixels} (densityDpi=${dm.densityDpi}, density=${dm.density})")
            }
            addZipEntry(zos, "device_and_app_info.txt", systemInfo)

            // 4. Sanitized SharedPreferences dump
            val prefsInfo = buildString {
                appendLine("=== SANITIZED PREFERENCES DUMP ===")
                try {
                    val sp = PreferenceManager.getDefaultSharedPreferences(context)
                    val all = sp.all
                    for ((k, v) in all.entries.sortedBy { it.key }) {
                        if (k.contains("password", ignoreCase = true) ||
                            k.contains("token", ignoreCase = true) ||
                            k.contains("secret", ignoreCase = true) ||
                            k.contains("credential", ignoreCase = true)) {
                            appendLine("$k = [REDACTED]")
                        } else {
                            appendLine("$k = $v")
                        }
                    }
                } catch (e: Throwable) {
                    appendLine("Failed to dump preferences: ${e.message}")
                }
            }
            addZipEntry(zos, "shared_preferences.txt", prefsInfo)

            // 5. System Logcat output (process-specific)
            val logcatOutput = try {
                val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time"))
                process.inputStream.bufferedReader().use { it.readText() }
            } catch (e: Throwable) {
                "Failed to capture logcat: ${e.message}"
            }
            addZipEntry(zos, "system_logcat.txt", logcatOutput)
        }

        return zipFile
    }

    private fun addZipEntry(zos: ZipOutputStream, entryName: String, content: String) {
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    @JvmStatic
    fun shareLogsZip(context: Context) {
        try {
            val zipFile = exportLogsZip(context)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                zipFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Petal Browser Diagnostic Logs (${zipFile.name})")
                putExtra(Intent.EXTRA_TEXT, "Attached Petal Browser diagnostic logs ZIP bundle.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Export Diagnostic Logs (.zip)")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to share logs zip", e)
        }
    }
}
