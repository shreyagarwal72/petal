package com.petal.browser.media.ytdlp

import android.net.Uri

/**
 * Petal Social Downloader — Supported Platform Registry
 *
 * Fast local domain-pattern check used to decide whether to show the
 * "Social Download" section in the media sheet. Checked without ever
 * invoking yt-dlp (which has a cold-start cost).
 *
 * yt-dlp itself supports 1800+ sites — this list is just the "show the
 * download button" hint. If a URL isn't listed but yt-dlp can handle it,
 * the user still benefits because we fall through to yt-dlp anyway.
 */
object SupportedPlatforms {

    data class PlatformInfo(
        val displayName: String,
        val emoji: String
    )

    private val platformMap: Map<String, PlatformInfo> = mapOf(
        "youtube.com"       to PlatformInfo("YouTube", "▶️"),
        "youtu.be"          to PlatformInfo("YouTube", "▶️"),
        "music.youtube.com" to PlatformInfo("YouTube Music", "🎵"),
        "instagram.com"     to PlatformInfo("Instagram", "📸"),
        "twitter.com"       to PlatformInfo("Twitter / X", "🐦"),
        "x.com"             to PlatformInfo("Twitter / X", "🐦"),
        "tiktok.com"        to PlatformInfo("TikTok", "🎵"),
        "vm.tiktok.com"     to PlatformInfo("TikTok", "🎵"),
        "facebook.com"      to PlatformInfo("Facebook", "👥"),
        "fb.watch"          to PlatformInfo("Facebook", "👥"),
        "m.facebook.com"    to PlatformInfo("Facebook", "👥"),
        "reddit.com"        to PlatformInfo("Reddit", "🔴"),
        "v.redd.it"         to PlatformInfo("Reddit", "🔴"),
        "vimeo.com"         to PlatformInfo("Vimeo", "🎬"),
        "twitch.tv"         to PlatformInfo("Twitch", "💜"),
        "clips.twitch.tv"   to PlatformInfo("Twitch", "💜"),
        "dailymotion.com"   to PlatformInfo("Dailymotion", "🎞️"),
        "bilibili.com"      to PlatformInfo("Bilibili", "📺"),
        "soundcloud.com"    to PlatformInfo("SoundCloud", "🎧"),
        "pinterest.com"     to PlatformInfo("Pinterest", "📌"),
        "streamable.com"    to PlatformInfo("Streamable", "🎥"),
        "medal.tv"          to PlatformInfo("Medal", "🏅"),
        "rumble.com"        to PlatformInfo("Rumble", "🎙️"),
        "odysee.com"        to PlatformInfo("Odysee", "🎯"),
        "bandcamp.com"      to PlatformInfo("Bandcamp", "🎶"),
        "kick.com"          to PlatformInfo("Kick", "🟢")
    )

    /**
     * Returns [PlatformInfo] if the URL is from a known supported platform,
     * or null if the host isn't recognised.
     */
    fun getPlatform(url: String): PlatformInfo? {
        val host = try {
            Uri.parse(url).host?.lowercase()?.removePrefix("www.") ?: return null
        } catch (_: Exception) { return null }
        return platformMap[host]
            ?: platformMap.entries.firstOrNull { (domain, _) ->
                host.endsWith(".$domain")
            }?.value
    }

    fun isSupported(url: String): Boolean = getPlatform(url) != null
}
