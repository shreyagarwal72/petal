package com.petal.browser.media.ytdlp

/**
 * Media metadata returned by yt-dlp.
 *
 * Format entries are intentionally represented as yt-dlp selectors instead of
 * hard-coded "fake" formats. This keeps the Social Downloader compatible with
 * platforms whose available streams differ from YouTube.
 */
data class YtDlpMediaInfo(
    val url: String,
    val title: String,
    val uploader: String?,
    val thumbnailUrl: String?,
    val durationSeconds: Int?,
    val formats: List<YtDlpFormat>
) {
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
    /** yt-dlp format selector (for example: bestvideo+bestaudio/best). */
    val formatId: String,
    /** User-facing label shown in the format selector. */
    val label: String,
    val isAudioOnly: Boolean = false,
    val fileSizeApprox: Long? = null,
    /** Output extension hint used for the filename/notification. */
    val ext: String = "mp4"
) {
    companion object {
        /**
         * Build selectors from the heights actually advertised by yt-dlp.
         *
         * We deliberately use selectors instead of copying a single format ID:
         * many social platforms expose different format IDs for every request.
         */
        fun buildVideoOptions(heights: Set<Int>, hasAudioVideo: Boolean): List<YtDlpFormat> {
            val options = mutableListOf<YtDlpFormat>()

            options += YtDlpFormat(
                formatId = if (hasAudioVideo) {
                    "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"
                } else {
                    "best"
                },
                label = "Best available",
                ext = "mp4"
            )

            listOf(2160, 1440, 1080, 720, 480, 360)
                .filter { requested -> heights.any { it >= requested } }
                .forEach { height ->
                    options += YtDlpFormat(
                        formatId = "bestvideo[height<=${height}][ext=mp4]+bestaudio[ext=m4a]/best[height<=${height}][ext=mp4]/best[height<=${height}]/best",
                        label = "${height}p",
                        ext = "mp4"
                    )
                }

            return options.distinctBy { it.formatId }
        }

        fun audioOption(): YtDlpFormat =
            YtDlpFormat(
                formatId = "bestaudio[ext=m4a]/bestaudio/best",
                label = "Audio only · M4A",
                isAudioOnly = true,
                ext = "m4a"
            )
    }
}
