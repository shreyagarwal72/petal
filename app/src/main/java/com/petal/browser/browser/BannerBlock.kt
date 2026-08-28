package com.petal.browser.browser

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.petal.browser.view.NinjaToast
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL
import java.net.URLConnection
import java.nio.file.Files
import java.nio.file.Paths

object BannerBlock {
    private const val TAG = "BannerBlock"
    private const val FILE = "banners.txt"
    private var configString: String = ""

    private fun loadHosts(context: Context) {
        try {
            val file = File(context.getDir("filesdir", Context.MODE_PRIVATE).toString() + "/" + FILE)
            if (!file.exists()) return
            val jsonDataString = String(Files.readAllBytes(Paths.get(file.path)))
            val jsonData = JSONObject(jsonDataString)
            if (jsonData.has("data")) {
                val data: JSONArray = jsonData.getJSONArray("data")
                configString = data.toString().replace("\\\"", "\\\\\"")
            }
        } catch (e: Exception) {
            Log.i(TAG, "Petal: loadHosts:$e")
        }
    }

    @JvmStatic
    fun downloadBanners(context: Context?) {
        if (context == null) return
        val thread = Thread {
            val hostURL = "https://raw.githubusercontent.com/mozilla/cookie-banner-rules-list/main/cookie-banner-rules-list.json"

            try {
                val url = URL(hostURL)
                Log.d("browser", "Download Mozilla cookie banner rules")
                (context as? Activity)?.runOnUiThread {
                    NinjaToast.show(context, "Downloading cookie-banner-rules.")
                }
                val ucon: URLConnection = url.openConnection().apply {
                    readTimeout = 5000
                    connectTimeout = 10000
                }
                val `is`: InputStream = ucon.getInputStream()
                val bis = BufferedInputStream(`is`)
                val file = File(context.getDir("filesdir", Context.MODE_PRIVATE).toString() + "/" + FILE)

                val fos = FileOutputStream(file)
                val data = ByteArray(1024)
                var current: Int
                while (bis.read(data, 0, 1024).also { current = it } != -1) {
                    fos.write(data, 0, current)
                }
                fos.flush()
                fos.close()
                `is`.close()
                loadHosts(context)
                (context as? Activity)?.runOnUiThread {
                    NinjaToast.show(context, "Cookie banner rules applied.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading banner rules", e)
            }
        }
        thread.start()
    }
}
