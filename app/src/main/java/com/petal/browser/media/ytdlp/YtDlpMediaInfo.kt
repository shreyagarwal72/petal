package com.petal.browser.media.ytdlp

/**
 * Petal Social Downloader — Media metadata returned by a yt-dlp info fetch.
 *
 * Simplified projection of yt-dlp's full VideoInfo JSON blob.
 * Only contains what the UI needs.
 */
data class YtDlpMediaInfo(
    val url: String,
    val title: String,
    val uploader: String?,
    val thumbnailUrl: String?,
    val durationSeconds: Int?,
    val formats: List<YtDlpFormat>
) {
    /** Formatted duration string — e.g. "3:42" or "1:02:15". */
    val durationFormatted: String?
        get() {
            val d = durationSeconds ?: return null
            val h = d / 3600
            val m = (d % 3600) / 60
            val s = d % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s)
            else "%d:%02d".format(m, s)
        }
}

data class YtDlpFormat(
    /** yt-dlp format selector (e.g. "bestvideo+bestaudio/best"). */
    val formatId: String,
    /** User-facing label shown in the dropdown. */
    val label: String,
    val isAudioOnly: Boolean = false,
    val fileSizeApprox: Long? = null,
    /** Output file extension hint. */
    val ext: String = "mp4"
) {
    companion object {
        fun buildOptions(hasVideo: Boolean): List<YtDlpFormat> {
            val opts = mutableListOf<YtDlpFormat>()
            if (hasVideo) {
                opts += YtDlpFormat(
                    formatId = "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best",
                    label = "Best quality · MP4",
                    ext = "mp4"
                )
                opts += YtDlpFormat(
                    formatId = "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/best[height<=1080]",
                    label = "1080p · MP4",
                    ext = "mp4"
                )
                opts += YtDlpFormat(
                    formatId = "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720]",
                    label = "720p · MP4",
                    ext = "mp4"
                )
                opts += YtDlpFormat(
                    formatId = "bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/best[height<=480]",
                    label = "480p · MP4",
                    ext = "mp4"
                )
            }
            opts += YtDlpFormat(
                formatId = "bestaudio[ext=m4a]/bestaudio",
                label = "Audio only · M4A",
                isAudioOnly = true,
                ext = "m4a"
            )
            return opts
        }
    }
}
