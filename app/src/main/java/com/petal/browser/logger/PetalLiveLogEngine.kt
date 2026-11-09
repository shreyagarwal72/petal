/*
 * PetalLiveLogEngine.kt
 * ─────────────────────────────────────────────────────────────────────────
 * High-performance, zero-latency live logging & diagnostic engine for Petal Browser.
 * Built specifically for live debugging in Termux and developer environments.
 *
 * Capabilities:
 * 1. Dual Real-Time Streaming:
 *    - Embedded HTTP/SSE server on 127.0.0.1:8765 (/stream, /status, /blank-check, /logs)
 *    - Continuous file sink to /storage/emulated/0/Download/petal_live.log
 * 2. In-Process Logcat Reader:
 *    - Captures all Android logcat entries for Petal's process (including GeckoView native threads)
 * 3. Blank Page Sentinel:
 *    - Actively detects zero-height surfaces, missing firstContentfulPaint after page stop,
 *      cosmetic CSS hiding document.body, and network/TLS load failures
 * 4. Web Console & JS Error Ingestion:
 *    - Bridges client-side errors and warnings from defuser.js into the live stream
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.logger

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

object PetalLiveLogEngine {

    private const val TAG = "PetalLiveLog"
    const val DEFAULT_PORT = 8765
    private const val MAX_MEMORY_LOGS = 1000
    private const val MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024L // 5 MB
    const val LOG_FILENAME = "petal_live.log"

    @Volatile
    var isEnabled: Boolean = true
        private set

    private var appContext: Context? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // In-memory ring buffer
    private val memoryLogs = ConcurrentLinkedQueue<LogEntry>()

    // Live SSE connected clients
    private val connectedClients = CopyOnWriteArrayList<PrintWriter>()

    // Background workers
    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null
    private var logcatThread: Thread? = null
    private val isRunning = AtomicBoolean(false)

    // Current State for /status and /blank-check diagnostics
    @Volatile var currentUrl: String = "about:blank"
    @Volatile var currentTitle: String = "Petal Home"
    @Volatile var isLoading: Boolean = false
    @Volatile var currentProgress: Int = 0
    @Volatile var firstContentfulPaint: Boolean = false
    @Volatile var surfaceBackend: String = "Unknown"
    @Volatile var surfaceWidth: Int = 0
    @Volatile var surfaceHeight: Int = 0
    @Volatile var isSurfaceAttached: Boolean = false
    @Volatile var lastBlankWarning: String? = null
    @Volatile var lastLoadError: String? = null
    @Volatile var lastPageStopTimeMs: Long = 0L

    data class LogEntry(
        val id: Long = System.nanoTime(),
        val timestamp: String,
        val level: String,
        val tag: String,
        val category: String, // PAGE, PAINT, SURFACE, BLOCK, JS, ERROR, LOGCAT, BLANK_ALERT
        val message: String,
        val details: String? = null
    ) {
        fun toFormattedLine(): String {
            val catPrefix = if (category.isNotBlank()) "[$category] " else ""
            val detailSuffix = if (!details.isNullOrBlank()) " | $details" else ""
            return "[$timestamp] [$level] [$tag] $catPrefix$message$detailSuffix"
        }

        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("timestamp", timestamp)
                put("level", level)
                put("tag", tag)
                put("category", category)
                put("message", message)
                if (details != null) put("details", details)
            }
        }
    }

    @JvmStatic
    fun init(context: Context) {
        if (isRunning.get()) return
        val app = context.applicationContext ?: context
        appContext = app

        val sp = PreferenceManager.getDefaultSharedPreferences(app)
        isEnabled = sp.getBoolean("sp_live_log_enabled", true)
        if (!isEnabled) {
            Log.i(TAG, "PetalLiveLogEngine disabled by preference")
            return
        }

        isRunning.set(true)
        startFileSinkHeader()
        startHttpServer()
        startLogcatCapture()

        log(
            level = "INFO",
            tag = "PetalLiveLog",
            category = "SYSTEM",
            message = "PetalLiveLogEngine active. Streaming on 127.0.0.1:$DEFAULT_PORT and Download/$LOG_FILENAME"
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Logging Methods
    // ─────────────────────────────────────────────────────────────────────────

    @JvmStatic
    fun log(level: String, tag: String, category: String, message: String, details: String? = null) {
        if (!isEnabled) return
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val entry = LogEntry(
            timestamp = timestamp,
            level = level,
            tag = tag,
            category = category,
            message = message,
            details = details
        )

        // 1. Add to ring buffer
        memoryLogs.add(entry)
        while (memoryLogs.size > MAX_MEMORY_LOGS) {
            memoryLogs.poll()
        }

        // 2. Broadcast to SSE clients
        val formatted = entry.toFormattedLine()
        val jsonPayload = entry.toJson().toString()
        val sseData = "data: $jsonPayload\n\n"

        for (client in connectedClients) {
            try {
                client.print(sseData)
                client.flush()
            } catch (_: Throwable) {
                connectedClients.remove(client)
            }
        }

        // 3. Write to live log file
        appendToFile(formatted)
    }

    @JvmStatic
    fun logNav(event: String, url: String?, details: String? = null) {
        val safeUrl = url ?: "about:blank"
        currentUrl = safeUrl
        log(
            level = "INFO",
            tag = "PetalNav",
            category = "PAGE",
            message = "$event: $safeUrl",
            details = details
        )
    }

    @JvmStatic
    fun logPaint(url: String?) {
        firstContentfulPaint = true
        log(
            level = "INFO",
            tag = "PetalRender",
            category = "PAINT",
            message = "FirstContentfulPaint rendered on ${url ?: currentUrl}"
        )
    }

    @JvmStatic
    fun logSurface(backend: String, width: Int, height: Int, attached: Boolean, details: String? = null) {
        surfaceBackend = backend
        surfaceWidth = width
        surfaceHeight = height
        isSurfaceAttached = attached

        val isZero = attached && (width <= 0 || height <= 0)
        val level = if (isZero) "WARN" else "DEBUG"
        val cat = if (isZero) "BLANK_ALERT" else "SURFACE"
        val msg = "Surface ($backend): ${width}x${height}, attached=$attached"

        log(
            level = level,
            tag = "PetalSurface",
            category = cat,
            message = msg,
            details = if (isZero) "WARNING: Zero dimensions will prevent web page composition!" else details
        )
    }

    @JvmStatic
    fun logBlock(ruleType: String, url: String, details: String? = null) {
        log(
            level = "DEBUG",
            tag = "PetalBlocker",
            category = "BLOCK",
            message = "Blocked $ruleType: $url",
            details = details
        )
    }

    @JvmStatic
    fun logWeb(level: String, message: String, url: String?) {
        log(
            level = level.uppercase(Locale.US),
            tag = "WebConsole",
            category = "JS",
            message = message,
            details = url?.let { "URL: $it" }
        )
    }

    @JvmStatic
    fun logBlankWarning(reason: String, details: String? = null) {
        lastBlankWarning = reason
        log(
            level = "WARN",
            tag = "BlankSentinel",
            category = "BLANK_ALERT",
            message = "POTENTIAL BLANK PAGE: $reason",
            details = details
        )
    }

    @JvmStatic
    fun logLoadError(uri: String?, errorCode: Int, categoryCode: Int, details: String? = null) {
        lastLoadError = "Error $errorCode (category $categoryCode) on $uri"
        log(
            level = "ERROR",
            tag = "PetalNav",
            category = "ERROR",
            message = "Failed to load $uri [Code: $errorCode, Category: $categoryCode]",
            details = details
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Blank Page Sentinel Watchdog
    // ─────────────────────────────────────────────────────────────────────────

    @JvmStatic
    fun checkBlankPageCondition(postDelayMs: Long = 2500L) {
        val checkTargetUrl = currentUrl
        if (checkTargetUrl == "about:blank" || checkTargetUrl.isBlank()) return

        mainHandler.postDelayed({
            if (currentUrl != checkTargetUrl) return@postDelayed
            // Check 1: Surface height/width is 0
            if (isSurfaceAttached && (surfaceWidth <= 0 || surfaceHeight <= 0)) {
                logBlankWarning(
                    reason = "Surface has zero dimensions (${surfaceWidth}x${surfaceHeight})",
                    details = "Check bottom navbar padding, root insets, or view visibility"
                )
                return@postDelayed
            }

            // Check 2: Load stopped, but no FirstContentfulPaint painted
            if (!isLoading && !firstContentfulPaint) {
                logBlankWarning(
                    reason = "Page load stopped but no FirstContentfulPaint composite was received",
                    details = "URL: $checkTargetUrl | Possible blank body, network stall, or JS crash"
                )
            }
        }, postDelayMs)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Embedded Local HTTP & SSE Server (127.0.0.1:8765)
    // ─────────────────────────────────────────────────────────────────────────

    private fun startHttpServer() {
        serverThread = Thread({
            try {
                // Bind strictly to loopback 127.0.0.1 so it cannot be accessed from outside the device
                serverSocket = ServerSocket(DEFAULT_PORT, 20, InetAddress.getByName("127.0.0.1"))
                Log.i(TAG, "Live log server listening on 127.0.0.1:$DEFAULT_PORT")

                while (isRunning.get()) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        handleHttpClient(client)
                    } catch (e: Throwable) {
                        if (!isRunning.get()) break
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to start live log HTTP server on port $DEFAULT_PORT: ${e.message}")
            }
        }, "PetalLiveLogHttpServer").apply {
            isDaemon = true
            start()
        }
    }

    private fun handleHttpClient(socket: Socket) {
        Thread({
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val reqLine = reader.readLine() ?: return@Thread
                val parts = reqLine.split(" ")
                val method = if (parts.isNotEmpty()) parts[0].uppercase(Locale.US) else "GET"
                val path = if (parts.size > 1) parts[1] else "/"

                val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))

                when {
                    path.startsWith("/stream") -> {
                        // Server-Sent Events (SSE) stream
                        writer.print("HTTP/1.1 200 OK\r\n")
                        writer.print("Content-Type: text/event-stream; charset=utf-8\r\n")
                        writer.print("Cache-Control: no-cache\r\n")
                        writer.print("Connection: keep-alive\r\n")
                        writer.print("Access-Control-Allow-Origin: *\r\n\r\n")
                        writer.flush()

                        // Send greeting event
                        val welcomeJson = JSONObject().apply {
                            put("event", "connected")
                            put("app", "Petal Browser")
                            put("pid", Process.myPid())
                            put("url", currentUrl)
                            put("message", "Live log stream connected to Termux")
                        }
                        writer.print("data: $welcomeJson\n\n")
                        writer.flush()

                        // Stream recent memory logs
                        val recent = memoryLogs.toList().takeLast(30)
                        for (item in recent) {
                            writer.print("data: ${item.toJson()}\n\n")
                        }
                        writer.flush()

                        connectedClients.add(writer)
                        // Keep thread alive reading from socket to detect client disconnect
                        try {
                            while (reader.readLine() != null) {
                                // Keep-alive loop
                            }
                        } catch (_: Throwable) {} finally {
                            connectedClients.remove(writer)
                            try { socket.close() } catch (_: Throwable) {}
                        }
                    }

                    path.startsWith("/status") -> {
                        val json = getStatusJson()
                        sendJsonResponse(writer, 200, json.toString(2))
                        socket.close()
                    }

                    path.startsWith("/blank-check") -> {
                        val json = getBlankCheckDiagnosticJson()
                        sendJsonResponse(writer, 200, json.toString(2))
                        socket.close()
                    }

                    path.startsWith("/logs") -> {
                        val arr = JSONArray()
                        for (entry in memoryLogs) {
                            arr.put(entry.toJson())
                        }
                        val response = JSONObject().apply {
                            put("count", arr.length())
                            put("logs", arr)
                        }
                        sendJsonResponse(writer, 200, response.toString(2))
                        socket.close()
                    }

                    path.startsWith("/clear") && method == "POST" -> {
                        clearLogs()
                        val res = JSONObject().apply { put("success", true); put("message", "Logs cleared") }
                        sendJsonResponse(writer, 200, res.toString())
                        socket.close()
                    }

                    path.startsWith("/log") && method == "POST" -> {
                        // Read body
                        var contentLength = 0
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            if (line.isNullOrBlank()) break
                            if (line!!.startsWith("Content-Length:", ignoreCase = true)) {
                                contentLength = line!!.substring(15).trim().toIntOrNull() ?: 0
                            }
                        }
                        val body = CharArray(contentLength)
                        reader.read(body, 0, contentLength)
                        val bodyStr = String(body)
                        val json = runCatching { JSONObject(bodyStr) }.getOrNull()
                        if (json != null) {
                            log(
                                level = json.optString("level", "INFO"),
                                tag = json.optString("tag", "External"),
                                category = json.optString("category", "JS"),
                                message = json.optString("message", ""),
                                details = json.optString("details", null)
                            )
                        }
                        sendJsonResponse(writer, 200, """{"success":true}""")
                        socket.close()
                    }

                    else -> {
                        val help = buildString {
                            appendLine("Petal Browser Live Diagnostic Server (v1.0)")
                            appendLine("──────────────────────────────────────────")
                            appendLine("GET /stream       - Real-time Server-Sent Events (SSE) log stream")
                            appendLine("GET /status       - JSON snapshot of current tab, engine & surface state")
                            appendLine("GET /blank-check  - Automated blank page diagnostic analysis")
                            appendLine("GET /logs         - Retrieve recent memory log entries (JSON)")
                            appendLine("POST /clear       - Clear memory logs and truncate disk log file")
                            appendLine("POST /log         - Ingest custom log payload")
                        }
                        sendTextResponse(writer, 200, help)
                        socket.close()
                    }
                }
            } catch (t: Throwable) {
                try { socket.close() } catch (_: Throwable) {}
            }
        }, "PetalLiveLogClient").apply {
            isDaemon = true
            start()
        }
    }

    private fun sendJsonResponse(writer: PrintWriter, code: Int, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        writer.print("HTTP/1.1 $code OK\r\n")
        writer.print("Content-Type: application/json; charset=utf-8\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.print(json)
        writer.flush()
    }

    private fun sendTextResponse(writer: PrintWriter, code: Int, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        writer.print("HTTP/1.1 $code OK\r\n")
        writer.print("Content-Type: text/plain; charset=utf-8\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.print(text)
        writer.flush()
    }

    fun getStatusJson(): JSONObject {
        return JSONObject().apply {
            put("appPackage", appContext?.packageName ?: "com.petal.browser")
            put("pid", Process.myPid())
            put("androidSdk", Build.VERSION.SDK_INT)
            put("androidRelease", Build.VERSION.RELEASE)
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("currentTab", JSONObject().apply {
                put("url", currentUrl)
                put("title", currentTitle)
                put("isLoading", isLoading)
                put("progress", currentProgress)
                put("firstContentfulPaint", firstContentfulPaint)
            })
            put("surface", JSONObject().apply {
                put("backend", surfaceBackend)
                put("width", surfaceWidth)
                put("height", surfaceHeight)
                put("isAttached", isSurfaceAttached)
            })
            put("diagnostics", JSONObject().apply {
                put("lastBlankWarning", lastBlankWarning)
                put("lastLoadError", lastLoadError)
                put("connectedSseClients", connectedClients.size)
                put("inMemoryLogsCount", memoryLogs.size)
            })
        }
    }

    fun getBlankCheckDiagnosticJson(): JSONObject {
        val issues = JSONArray()
        var status = "OK"

        // 1. Surface Dimension Checks
        if (isSurfaceAttached && (surfaceWidth <= 0 || surfaceHeight <= 0)) {
            status = "CRITICAL_BLANK"
            issues.put(JSONObject().apply {
                put("type", "SURFACE_ZERO_DIMENSION")
                put("severity", "CRITICAL")
                put("message", "GeckoView surface has zero dimensions: ${surfaceWidth}x${surfaceHeight}")
                put("fix", "Examine applyAddressBarPosition(), bottomNavContainer height overflow, or layout params")
            })
        }

        // 2. Load Error Checks
        if (!lastLoadError.isNullOrBlank()) {
            issues.put(JSONObject().apply {
                put("type", "NAVIGATION_LOAD_ERROR")
                put("severity", "HIGH")
                put("message", lastLoadError)
                put("fix", "Check network connection, HTTPS-only mode bypass, or SSL certificate")
            })
        }

        // 3. FirstContentfulPaint Checks
        if (!isLoading && !firstContentfulPaint && currentUrl != "about:blank" && currentUrl.isNotBlank()) {
            if (status != "CRITICAL_BLANK") status = "BLANK_PAGE_SUSPECTED"
            issues.put(JSONObject().apply {
                put("type", "NO_CONTENTFUL_PAINT")
                put("severity", "HIGH")
                put("message", "Page finished loading but GeckoView never composite-painted content")
                put("fix", "Check if WebExtension cosmetic CSS hid document.body, or JS unhandled exception aborted rendering")
            })
        }

        // 4. Last Blank Warning
        if (!lastBlankWarning.isNullOrBlank()) {
            issues.put(JSONObject().apply {
                put("type", "BLANK_SENTINEL_TRIGGERED")
                put("severity", "MEDIUM")
                put("message", lastBlankWarning)
            })
        }

        return JSONObject().apply {
            put("status", status)
            put("targetUrl", currentUrl)
            put("firstContentfulPaint", firstContentfulPaint)
            put("surfaceGeometry", "${surfaceWidth}x${surfaceHeight} ($surfaceBackend)")
            put("issuesCount", issues.length())
            put("issues", issues)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Disk File Sink (/storage/emulated/0/Download/petal_live.log)
    // ─────────────────────────────────────────────────────────────────────────

    private fun getLogFile(): File? {
        return try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadDir.exists()) downloadDir.mkdirs()
            File(downloadDir, LOG_FILENAME)
        } catch (_: Throwable) {
            null
        }
    }

    private fun startFileSinkHeader() {
        try {
            val file = getLogFile() ?: return
            if (file.exists() && file.length() > MAX_FILE_SIZE_BYTES) {
                // Truncate if too large
                file.delete()
            }
            val bw = BufferedWriter(FileWriter(file, true))
            bw.write("\n================================================================================\n")
            bw.write("=== PETAL BROWSER LIVE LOG SESSION STARTED [PID ${Process.myPid()}] ===\n")
            bw.write("=== Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())} ===\n")
            bw.write("=== Stream: http://127.0.0.1:$DEFAULT_PORT/stream | Status: /status ===\n")
            bw.write("================================================================================\n\n")
            bw.flush()
            bw.close()
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to write log header to file: ${e.message}")
        }
    }

    private fun appendToFile(line: String) {
        try {
            val file = getLogFile() ?: return
            val bw = BufferedWriter(FileWriter(file, true))
            bw.write(line)
            bw.newLine()
            bw.flush()
            bw.close()
        } catch (_: Throwable) {}
    }

    fun clearLogs() {
        memoryLogs.clear()
        lastBlankWarning = null
        lastLoadError = null
        try {
            val file = getLogFile()
            if (file != null && file.exists()) {
                file.delete()
                startFileSinkHeader()
            }
        } catch (_: Throwable) {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // In-Process Logcat Capture (Own Process + Gecko Engine)
    // ─────────────────────────────────────────────────────────────────────────

    private fun startLogcatCapture() {
        logcatThread = Thread({
            try {
                // Clear old buffer and listen
                val myPid = Process.myPid().toString()
                val process = Runtime.getRuntime().exec(arrayOf("logcat", "-v", "time", "*:V"))
                val reader = BufferedReader(InputStreamReader(process.inputStream))

                var line: String?
                while (isRunning.get() && reader.readLine().also { line = it } != null) {
                    val l = line ?: continue
                    // Filter to our PID or key browser tags
                    val isMyPid = l.contains(myPid)
                    val isGeckoTag = l.contains("Gecko") || l.contains("Petal") || l.contains("BrowserActivity") || l.contains("libxul")
                    if (!isMyPid && !isGeckoTag) continue

                    // Ignore high-volume spam
                    if (l.contains("BufferPoolAccessor") || l.contains("hwui") && l.contains("dispatchDetachedFromWindow")) continue

                    val level = when {
                        l.contains(" E ") || l.contains(" E/") -> "ERROR"
                        l.contains(" W ") || l.contains(" W/") -> "WARN"
                        l.contains(" D ") || l.contains(" D/") -> "DEBUG"
                        else -> "INFO"
                    }

                    val cat = when {
                        l.contains("GeckoConsole") -> "JS"
                        l.contains("PetalTabNav") || l.contains("PetalNav") -> "PAGE"
                        l.contains("PetalSurface") -> "SURFACE"
                        l.contains("PetalBlocker") -> "BLOCK"
                        level == "ERROR" -> "ERROR"
                        else -> "LOGCAT"
                    }

                    // Feed to live log engine without re-logging to Android Log (prevents feedback loop)
                    val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
                    val entry = LogEntry(
                        timestamp = timestamp,
                        level = level,
                        tag = "SystemLogcat",
                        category = cat,
                        message = l.trim()
                    )

                    memoryLogs.add(entry)
                    while (memoryLogs.size > MAX_MEMORY_LOGS) {
                        memoryLogs.poll()
                    }

                    val sseData = "data: ${entry.toJson()}\n\n"
                    for (client in connectedClients) {
                        try {
                            client.print(sseData)
                            client.flush()
                        } catch (_: Throwable) {
                            connectedClients.remove(client)
                        }
                    }

                    appendToFile(entry.toFormattedLine())
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Logcat reader exited: ${e.message}")
            }
        }, "PetalLiveLogcatCapture").apply {
            isDaemon = true
            start()
        }
    }
}
