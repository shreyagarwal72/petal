package com.petal.browser.media.ytdlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpOptionBuilderTest {

    private fun video(id: String, h: Int, size: Long?, fps: Int = 30, acodec: String = "none") =
        YtDlpRawFormat(id, "mp4", h, fps, "avc1", acodec, size)

    private fun audio(id: String, size: Long?) =
        YtDlpRawFormat(id, "m4a", 0, 0, "none", "mp4a", size)

    @Test
    fun videoOptionsAreSortedByHeightDescendingAndDeduplicated() {
        val (v, _) = YtDlpOptionBuilder.build(
            listOf(video("a", 360, 10), video("b", 1080, 100), video("c", 1080, 90), video("d", 720, 50), audio("x", 5))
        )
        assertEquals(listOf("1080p", "720p", "360p"), v.map { it.label })
    }

    @Test
    fun videoOnlyFormatsIncludeBestAudioInTheEstimate() {
        val (v, _) = YtDlpOptionBuilder.build(listOf(video("a", 720, 100), audio("x", 20), audio("y", 30)))
        assertEquals(130L, v.single().sizeBytes)
    }

    @Test
    fun muxedFormatsDoNotAddAudioTwice() {
        val (v, _) = YtDlpOptionBuilder.build(listOf(video("a", 720, 100, acodec = "mp4a"), audio("x", 30)))
        assertEquals(100L, v.single().sizeBytes)
    }

    @Test
    fun highFrameRateIsShownInTheLabel() {
        val (v, _) = YtDlpOptionBuilder.build(listOf(video("a", 1080, 100, fps = 60)))
        assertEquals("1080p60fps", v.single().label)
    }

    @Test
    fun audioOptionsAlwaysOfferM4aAndMp3() {
        val (_, a) = YtDlpOptionBuilder.build(emptyList())
        assertEquals(listOf("m4a", "mp3"), a.map { it.extension })
        assertTrue(a.all { it.isAudio })
    }

    @Test
    fun formatsWithoutVideoOrHeightProduceNoVideoOptions() {
        val (v, _) = YtDlpOptionBuilder.build(listOf(audio("x", 5)))
        assertTrue(v.isEmpty())
    }

    @Test
    fun videoSelectorCapsHeightAndFallsBack() {
        val (v, _) = YtDlpOptionBuilder.build(listOf(video("a", 720, 100)))
        assertTrue(v.single().selector.contains("height<=720"))
        assertFalse(v.single().isAudio)
    }

    @Test
    fun parsesProgressSpeed() {
        assertEquals((1.5 * 1024 * 1024).toLong(), YtDlpOptionBuilder.parseSpeed("[download]  45.2% of 10.00MiB at 1.50MiB/s ETA 00:05"))
        assertEquals(512L * 1024, YtDlpOptionBuilder.parseSpeed("at 512.00KiB/s"))
        assertEquals(0L, YtDlpOptionBuilder.parseSpeed("[ffmpeg] Merging formats"))
    }

    @Test
    fun formatsBytes() {
        assertEquals("1.0 MB", YtDlpOptionBuilder.formatBytes(1024L * 1024))
        assertEquals("512 B", YtDlpOptionBuilder.formatBytes(512))
    }
}
