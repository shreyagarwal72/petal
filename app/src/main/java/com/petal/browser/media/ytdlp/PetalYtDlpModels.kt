package com.petal.browser.media.ytdlp

/** One downloadable choice shown in the grabber sheet. */
data class YtDlpOption(
    val id: String,
    val label: String,
    val detail: String,
    val isAudio: Boolean,
    /** yt-dlp `-f` selector. */
    val selector: String,
    val sizeBytes: Long?,
    /** Container yt-dlp is asked to produce. */
    val extension: String
)

data class YtDlpInfo(
    val pageUrl: String,
    val title: String,
    val thumbnail: String?,
    val uploader: String?,
    val durationSeconds: Long?,
    val videoOptions: List<YtDlpOption>,
    val audioOptions: List<YtDlpOption>
)

sealed class YtDlpEngineState {
    object Idle : YtDlpEngineState()
    object Initializing : YtDlpEngineState()
    object Ready : YtDlpEngineState()
    data class Failed(val message: String) : YtDlpEngineState()
}

/** Plain format description decoupled from the library's VideoFormat so option building is unit-testable. */
data class YtDlpRawFormat(
    val formatId: String,
    val ext: String?,
    val height: Int,
    val fps: Int,
    val vcodec: String?,
    val acodec: String?,
    val sizeBytes: Long?
) {
    val hasVideo: Boolean get() = !vcodec.isNullOrBlank() && vcodec != "none"
    val hasAudio: Boolean get() = !acodec.isNullOrBlank() && acodec != "none"
}

object YtDlpOptionBuilder {

    fun build(formats: List<YtDlpRawFormat>): Pair<List<YtDlpOption>, List<YtDlpOption>> {
        val bestAudioSize = formats.filter { it.hasAudio && !it.hasVideo }
            .mapNotNull { it.sizeBytes }.maxOrNull()

        val video = formats.filter { it.hasVideo && it.height > 0 }
            .groupBy { it.height }
            .toSortedMap(compareByDescending { it })
            .map { (height, group) ->
                val best = group.maxWithOrNull(
                    compareBy<YtDlpRawFormat> { it.fps }.thenBy { it.sizeBytes ?: 0L }
                )!!
                val videoSize = group.mapNotNull { it.sizeBytes }.maxOrNull()
                val muxed = best.hasAudio
                val total = when {
                    videoSize == null -> null
                    muxed -> videoSize
                    else -> videoSize + (bestAudioSize ?: 0L)
                }
                val fpsLabel = if (best.fps > 30) "${best.fps}fps" else ""
                YtDlpOption(
                    id = "v$height",
                    label = "${height}p$fpsLabel",
                    detail = listOfNotNull(best.ext?.uppercase(), total?.let(::formatBytes)).joinToString(" • "),
                    isAudio = false,
                    selector = "bv*[height<=$height][ext=mp4]+ba[ext=m4a]/bv*[height<=$height]+ba/b[height<=$height]/b",
                    sizeBytes = total,
                    extension = "mp4"
                )
            }

        val audio = buildList {
            add(
                YtDlpOption(
                    id = "a-m4a",
                    label = "Audio M4A",
                    detail = listOfNotNull("Best quality", bestAudioSize?.let(::formatBytes)).joinToString(" • "),
                    isAudio = true,
                    selector = "ba[ext=m4a]/ba/b",
                    sizeBytes = bestAudioSize,
                    extension = "m4a"
                )
            )
            add(
                YtDlpOption(
                    id = "a-mp3",
                    label = "Audio MP3",
                    detail = "Converted",
                    isAudio = true,
                    selector = "ba/b",
                    sizeBytes = null,
                    extension = "mp3"
                )
            )
        }
        return video to audio
    }

    fun formatBytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(java.util.Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(java.util.Locale.US, "%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    private val PROGRESS_SPEED = Regex("""at\s+([0-9.]+)\s*([KMG]i?B)/s""", RegexOption.IGNORE_CASE)

    /** Parses the speed out of a yt-dlp progress line, in bytes per second (0 when absent). */
    fun parseSpeed(line: String): Long {
        val m = PROGRESS_SPEED.find(line) ?: return 0L
        val value = m.groupValues[1].toDoubleOrNull() ?: return 0L
        val mult = when (m.groupValues[2].uppercase().first()) {
            'K' -> 1024.0
            'M' -> 1024.0 * 1024.0
            'G' -> 1024.0 * 1024.0 * 1024.0
            else -> 1.0
        }
        return (value * mult).toLong()
    }
}
