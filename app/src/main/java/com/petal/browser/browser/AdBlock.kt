package com.petal.browser.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.util.Log
import androidx.preference.PreferenceManager
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.net.URLConnection
import java.nio.file.Files
import java.util.HashSet
import java.util.Locale
import java.util.Set

class AdBlock(context: Context) {

    init {
        if (hosts.isEmpty()) {
            loadHosts(context)
        }
    }

    fun isAd(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val cleanUrl = url.lowercase(Locale.getDefault())

        for (pattern in AD_HOST_PATTERNS) {
            if (cleanUrl.contains(pattern)) {
                return true
            }
        }

        for (pathPattern in AD_PATH_PATTERNS) {
            if (cleanUrl.contains(pathPattern)) {
                return true
            }
        }

        try {
            val uri = URI(url)
            val host = uri.host
            if (host != null) {
                if (hosts.contains(host.lowercase(Locale.getDefault()))) {
                    return true
                }
            }
        } catch (ignored: URISyntaxException) {
        }

        return false
    }

    companion object {
        private const val FILE = "hosts.txt"
        private val hosts: MutableSet<String> = HashSet()

        private val AD_HOST_PATTERNS = arrayOf(
            "doubleclick.net", "google-analytics.com", "googlesyndication.com",
            "adservice.google.com", "adnxs.com", "popads.net", "popcash.net",
            "adform.net", "taboola.com", "outbrain.com", "adroll.com", "criteo.com",
            "rubiconproject.com", "pubmatic.com", "smartadserver.com", "zedo.com",
            "amazon-adsystem.com", "adk2.com", "propellerads.com", "exoclick.com",
            "scorecardresearch.com", "quantserve.com", "openx.net", "monetag.com",
            "hilltopads.com", "adcash.com", "adsterra.com", "a-ads.com", "mgid.com",
            "revcontent.com", "juicyads.com", "trafficjunky.com", "coinhive.com",
            "statcounter.com", "hotjar.com", "mixpanel.com", "segment.io", "clarity.ms",
            "pixel.facebook.com", "adservice", "popunder", "popups", "tracking",
            "ublockorigin", "gorhill", "easylist", "easyprivacy", "adguard",
            "uBlock-filters", "uBlock-unbreak", "uBlock-badware", "uBlock-privacy",
            "uBlock-quick-fixes", "analytics", "telemetry", "tracking", "tracker"
        )

        private val AD_PATH_PATTERNS = arrayOf(
            "/pagead/", "/adserv", "/ads/", "/ad_banner", "/popunder", "/popup.js",
            "adsterra", "popcash", "popads", "analytics.js", "gtag/js", "fbevents.js",
            "adsbygoogle.js", "ad_status"
        )

        @JvmStatic
        fun loadHosts(context: Context) {
            Thread {
                try {
                    val file = File(context.getDir("filesdir", Context.MODE_PRIVATE), FILE)
                    if (!file.exists()) {
                        copyHosts(context)
                    }
                    val reader = BufferedReader(FileReader(file))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val host = line?.trim()?.lowercase(Locale.getDefault())
                        if (!host.isNullOrEmpty() && !host.startsWith("#")) {
                            hosts.add(host)
                        }
                    }
                    reader.close()
                } catch (e: Exception) {
                    Log.e("AdBlock", "Error loading hosts file: ${e.message}")
                }
            }.start()
        }

        private fun copyHosts(context: Context) {
            try {
                val assetManager = context.assets
                val `in`: InputStream = assetManager.open(FILE)
                val file = File(context.getDir("filesdir", Context.MODE_PRIVATE), FILE)
                val out: OutputStream = FileOutputStream(file)
                val buffer = ByteArray(1024)
                var read: Int
                while (`in`.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                }
                `in`.close()
                out.flush()
                out.close()
            } catch (ignored: IOException) {
            }
        }

        @JvmStatic
        fun getHostsDate(context: Context?): String {
            if (context == null) return "Updated"
            val file = File(context.getDir("filesdir", Context.MODE_PRIVATE), FILE)
            return if (file.exists()) {
                "Active (" + hosts.size + " rules)"
            } else {
                "Not initialized"
            }
        }
    }
}
