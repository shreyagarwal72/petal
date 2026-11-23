package com.petal.browser.compose.ai.ondevice

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import com.petal.browser.compose.ai.PetalAiKeyStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

enum class ModelDownloadStatus {
    IDLE, DOWNLOADING, PAUSED, COMPLETED, FAILED
}

data class ModelDownloadProgress(
    val status: ModelDownloadStatus = ModelDownloadStatus.IDLE,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val progressFraction: Float = 0f,
    val errorMessage: String? = null
)

data class MemoryStats(
    val availMemBytes: Long,
    val totalMemBytes: Long,
    val isLowMemory: Boolean,
    val hasSufficientRam: Boolean
) {
    val isSufficientForModel: Boolean
        get() = hasSufficientRam && !isLowMemory

    constructor(availableRamMb: Long, totalRamMb: Long, isLowMemory: Boolean) : this(
        availMemBytes = availableRamMb * 1024L * 1024L,
        totalMemBytes = totalRamMb * 1024L * 1024L,
        isLowMemory = isLowMemory,
        hasSufficientRam = availableRamMb >= 1500L && !isLowMemory
    )
}

/**
 * Manages on-device Gemma model storage, background downloading with resume,
 * checksum verification, and device RAM diagnostics.
 */
object PetalOnDeviceModelManager {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _downloadProgress = MutableStateFlow(ModelDownloadProgress())
    val downloadProgress: StateFlow<ModelDownloadProgress> = _downloadProgress.asStateFlow()

    private var activeDownloadJob: Job? = null
    @Volatile private var isPaused = false

    fun getModelDirectory(context: Context): File {
        val dir = File(context.applicationContext.filesDir, PetalOnDeviceConfig.MODEL_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getModelFile(context: Context): File? {
        val dir = getModelDirectory(context)
        val files = dir.listFiles { f -> f.isFile && (f.name.endsWith(".bin") || f.name.endsWith(".task")) }
        return files?.firstOrNull()
    }

    fun getMemoryStats(context: Context): MemoryStats {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        if (am != null) {
            am.getMemoryInfo(memInfo)
            val hasEnough = memInfo.availMem >= PetalOnDeviceConfig.MIN_FREE_RAM_BYTES && !memInfo.lowMemory
            return MemoryStats(
                availMemBytes = memInfo.availMem,
                totalMemBytes = memInfo.totalMem,
                isLowMemory = memInfo.lowMemory,
                hasSufficientRam = hasEnough
            )
        }
        return MemoryStats(0L, 0L, isLowMemory = false, hasSufficientRam = true)
    }

    fun setHuggingFaceToken(context: Context, token: String) {
        PetalAiKeyStorage.setKey(context, "huggingface", token)
    }

    fun getHuggingFaceToken(context: Context): String {
        return PetalAiKeyStorage.getKey(context, "huggingface")
    }

    /**
     * Downloads a model file with support for resume and optional Hugging Face authorization.
     */
    suspend fun downloadModel(
        context: Context,
        url: String,
        expectedSha256: String? = null
    ) = withContext(Dispatchers.IO) {
        isPaused = false
        val dir = getModelDirectory(context)
        val targetFile = File(dir, PetalOnDeviceConfig.DEFAULT_MODEL_NAME)
        val partFile = File(dir, "${PetalOnDeviceConfig.DEFAULT_MODEL_NAME}.part")

        val existingBytes = if (partFile.exists()) partFile.length() else 0L

        val reqBuilder = Request.Builder().url(url)
        val hfToken = getHuggingFaceToken(context)
        if (hfToken.isNotBlank()) {
            reqBuilder.addHeader("Authorization", "Bearer $hfToken")
        }
        if (existingBytes > 0) {
            reqBuilder.addHeader("Range", "bytes=$existingBytes-")
        }

        try {
            _downloadProgress.value = ModelDownloadProgress(
                status = ModelDownloadStatus.DOWNLOADING,
                downloadedBytes = existingBytes,
                totalBytes = 0L,
                progressFraction = 0f
            )

            val response = httpClient.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful && response.code != 206) {
                _downloadProgress.value = ModelDownloadProgress(
                    status = ModelDownloadStatus.FAILED,
                    errorMessage = "HTTP ${response.code}: ${response.message}"
                )
                return@withContext
            }

            val body = response.body ?: throw RuntimeException("Empty response body")
            val contentLength = body.contentLength()
            val totalBytes = if (contentLength > 0) existingBytes + contentLength else 0L

            val raf = RandomAccessFile(partFile, "rw")
            raf.seek(existingBytes)
            val stream = body.byteStream()
            val buffer = ByteArray(32 * 1024)
            var currentBytes = existingBytes

            stream.use { input ->
                raf.use { output ->
                    while (true) {
                        if (isPaused) {
                            _downloadProgress.value = _downloadProgress.value.copy(status = ModelDownloadStatus.PAUSED)
                            return@withContext
                        }

                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        currentBytes += read

                        val frac = if (totalBytes > 0) currentBytes.toFloat() / totalBytes else 0f
                        _downloadProgress.value = ModelDownloadProgress(
                            status = ModelDownloadStatus.DOWNLOADING,
                            downloadedBytes = currentBytes,
                            totalBytes = totalBytes,
                            progressFraction = frac
                        )
                    }
                }
            }

            // Verify checksum if provided
            if (!expectedSha256.isNullOrBlank()) {
                val computedSha256 = calculateSha256(partFile)
                if (!computedSha256.equals(expectedSha256.trim(), ignoreCase = true)) {
                    partFile.delete()
                    _downloadProgress.value = ModelDownloadProgress(
                        status = ModelDownloadStatus.FAILED,
                        errorMessage = "Checksum mismatch: expected $expectedSha256, got $computedSha256"
                    )
                    return@withContext
                }
            }

            // Rename .part to final
            if (targetFile.exists()) targetFile.delete()
            partFile.renameTo(targetFile)

            _downloadProgress.value = ModelDownloadProgress(
                status = ModelDownloadStatus.COMPLETED,
                downloadedBytes = currentBytes,
                totalBytes = currentBytes,
                progressFraction = 1f
            )
        } catch (e: Exception) {
            if (e !is CancellationException) {
                _downloadProgress.value = ModelDownloadProgress(
                    status = ModelDownloadStatus.FAILED,
                    errorMessage = e.localizedMessage ?: e.message ?: "Download failed"
                )
            }
        }
    }

    fun pauseDownload() {
        isPaused = true
    }

    fun cancelDownload(context: Context) {
        isPaused = false
        val dir = getModelDirectory(context)
        val partFile = File(dir, "${PetalOnDeviceConfig.DEFAULT_MODEL_NAME}.part")
        if (partFile.exists()) partFile.delete()
        _downloadProgress.value = ModelDownloadProgress(status = ModelDownloadStatus.IDLE)
    }

    fun deleteModel(context: Context): Boolean {
        val dir = getModelDirectory(context)
        var deleted = false
        dir.listFiles()?.forEach { file ->
            if (file.delete()) deleted = true
        }
        _downloadProgress.value = ModelDownloadProgress(status = ModelDownloadStatus.IDLE)
        return deleted
    }

    fun importLocalModelFile(context: Context, uri: Uri): Boolean {
        return try {
            val dir = getModelDirectory(context)
            val destFile = File(dir, PetalOnDeviceConfig.DEFAULT_MODEL_NAME)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(16 * 1024)
            var len: Int
            while (input.read(buf).also { len = it } != -1) {
                digest.update(buf, 0, len)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
