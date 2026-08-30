package com.petal.browser.logger

import android.content.Context
import android.content.Intent
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
 * Builtin App Logger for Petal Browser.
 * Records runtime events, catches uncaught exceptions, and exports
 * a comprehensive diagnostic ZIP bundle containing logcat, crash traces,
 * device/OS info, and sanitized preferences.
 */
object PetalAppLogger {

    private const val TAG = "PetalAppLogger"
    private const val MAX_IN_MEMORY_LOGS = 500

    private val logBuffer = ConcurrentLinkedQueue<String>()
    private val crashTraces = ConcurrentLinkedQueue<String>()

    private var defaultUncaughtHandler: Thread.UncaughtExceptionHandler? = null

    @JvmStatic
    fun init(context: Context) {
        if (defaultUncaughtHandler == null) {
            defaultUncaughtHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                recordCrash(thread, throwable)
                defaultUncaughtHandler?.uncaughtException(thread, throwable)
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

    private fun recordCrash(thread: Thread, throwable: Throwable) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stack = sw.toString()
        val crashReport = "=== UNCAUGHT CRASH ===\nTimestamp: $timestamp\nThread: ${thread.name} (id=${thread.id})\nException: ${throwable.javaClass.name}: ${throwable.message}\nStacktrace:\n$stack\n"
        crashTraces.add(crashReport)
        log(TAG, crashReport)
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
                        // Exclude any credential/sensitive keys
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
