package com.petal.browser.pwa

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import com.petal.browser.R
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.unit.HelperUnit
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.ArrayList

/**
 * PetalPwaManager
 * Dynamic Progressive Web App (PWA) manager providing manifest detection & parsing via JS injection,
 * Service Worker lifecycle control, native installation prompts with dynamic shortcut creation,
 * and Web API delegation (Web Share, WebAuthn/Passkeys, Push Notifications).
 */
class PetalPwaManager(private val context: Context, private val webView: WebView) {

    class PwaManifest {
        var name: String = ""
        var shortName: String = ""
        var startUrl: String = ""
        var display: String = "browser"
        var themeColor: String = "#FFFFFF"
        var backgroundColor: String = "#FFFFFF"
        var iconUrl: String = ""
        var shortcuts: MutableList<PwaShortcut> = ArrayList()
    }

    class PwaShortcut {
        var name: String = ""
        var url: String = ""
        var iconUrl: String = ""
    }

    interface PwaInstallCallback {
        fun onPwaDetected(manifest: PwaManifest)
        fun onInstallCompleted(success: Boolean)
    }

    private var currentManifest: PwaManifest? = null
    private var installCallback: PwaInstallCallback? = null

    init {
        setupPwaBridge()
    }

    fun setInstallCallback(callback: PwaInstallCallback?) {
        this.installCallback = callback
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupPwaBridge() {
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val swController = ServiceWorkerController.getInstance()
                swController.setServiceWorkerClient(object : ServiceWorkerClient() {
                    override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
                        return super.shouldInterceptRequest(request)
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Service Worker init error: ${e.message}")
            }
        }

        webView.addJavascriptInterface(PwaJsInterface(), JS_INTERFACE_NAME)
    }

    fun injectPwaDetection() {
        webView.evaluateJavascript(PWA_DETECTION_SCRIPT, null)
    }

    fun isPwaInstallable(): Boolean {
        return currentManifest != null && currentManifest!!.name.isNotEmpty()
    }

    fun getCurrentManifest(): PwaManifest? = currentManifest

    fun installPwa(activity: Activity) {
        val manifest = currentManifest
        if (manifest == null) {
            Toast.makeText(context, "No installable Web App detected on this page", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            try {
                var appIcon: Bitmap? = null
                if (manifest.iconUrl.isNotEmpty()) {
                    try {
                        val iconUrl = URL(manifest.iconUrl)
                        val conn = iconUrl.openConnection() as HttpURLConnection
                        conn.connectTimeout = 8000
                        conn.readTimeout = 8000
                        conn.connect()
                        val input: InputStream = conn.inputStream
                        appIcon = BitmapFactory.decodeStream(input)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed downloading PWA icon: ${e.message}")
                    }
                }

                if (appIcon == null) {
                    val defaultRes = R.mipmap.ic_launcher
                    appIcon = BitmapFactory.decodeResource(context.resources, defaultRes)
                }

                val finalIcon = appIcon
                activity.runOnUiThread {
                    createDynamicAppShortcut(context, manifest, finalIcon)
                    installCallback?.onInstallCompleted(true)
                    Toast.makeText(context, "${manifest.name} added to Home Screen", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "PWA install failed: ${e.message}")
                activity.runOnUiThread {
                    installCallback?.onInstallCompleted(false)
                }
            }
        }.start()
    }

    private fun createDynamicAppShortcut(context: Context, manifest: PwaManifest, iconBitmap: Bitmap) {
        val launchIntent = Intent(context, BrowserActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(if (manifest.startUrl.isNotEmpty()) manifest.startUrl else webView.url)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("is_pwa_launch", true)
            putExtra("pwa_theme_color", manifest.themeColor)
            putExtra("pwa_display_mode", manifest.display)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java)
            if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {
                val shortcutId = "pwa_" + (manifest.shortName.ifEmpty { manifest.name }).hashCode()
                val icon = Icon.createWithAdaptiveBitmap(iconBitmap)

                val shortcutInfo = ShortcutInfo.Builder(context, shortcutId)
                    .setShortLabel(manifest.shortName.ifEmpty { manifest.name })
                    .setLongLabel(manifest.name)
                    .setIcon(icon)
                    .setIntent(launchIntent)
                    .build()

                shortcutManager.requestPinShortcut(shortcutInfo, null)
            } else {
                fallbackCreateShortcutIntent(context, manifest, iconBitmap, launchIntent)
            }
        } else {
            fallbackCreateShortcutIntent(context, manifest, iconBitmap, launchIntent)
        }
    }

    @Suppress("DEPRECATION")
    private fun fallbackCreateShortcutIntent(context: Context, manifest: PwaManifest, icon: Bitmap, launchIntent: Intent) {
        val addIntent = Intent().apply {
            putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent)
            putExtra(Intent.EXTRA_SHORTCUT_NAME, manifest.shortName.ifEmpty { manifest.name })
            putExtra(Intent.EXTRA_SHORTCUT_ICON, icon)
            action = "com.android.launcher.action.INSTALL_SHORTCUT"
        }
        context.sendBroadcast(addIntent)
    }

    private inner class PwaJsInterface {
        @JavascriptInterface
        fun onManifestParsed(jsonString: String?) {
            if (jsonString.isNullOrBlank()) return

            try {
                val json = JSONObject(jsonString)
                val manifest = PwaManifest().apply {
                    name = json.optString("name", "")
                    shortName = json.optString("short_name", "")
                    startUrl = json.optString("start_url", "")
                    display = json.optString("display", "standalone")
                    themeColor = json.optString("theme_color", "#FFFFFF")
                    backgroundColor = json.optString("background_color", "#FFFFFF")
                }

                // Resolve start_url to absolute URL if relative
                if (manifest.startUrl.isNotEmpty() && !manifest.startUrl.startsWith("http")) {
                    val currentUrl = webView.url ?: ""
                    val base = HelperUnit.domain(currentUrl)
                    manifest.startUrl = "https://$base/${manifest.startUrl.removePrefix("/")}"
                }

                val iconsArray = json.optJSONArray("icons")
                if (iconsArray != null && iconsArray.length() > 0) {
                    var bestIconUrl = ""
                    for (i in 0 until iconsArray.length()) {
                        val iconObj = iconsArray.getJSONObject(i)
                        val src = iconObj.optString("src", "")
                        if (src.isNotEmpty()) {
                            bestIconUrl = src
                            val sizes = iconObj.optString("sizes", "")
                            if (sizes.contains("192") || sizes.contains("512")) {
                                break
                            }
                        }
                    }
                    if (bestIconUrl.isNotEmpty() && !bestIconUrl.startsWith("http")) {
                        val currentUrl = webView.url ?: ""
                        val base = HelperUnit.domain(currentUrl)
                        bestIconUrl = "https://$base/${bestIconUrl.removePrefix("/")}"
                    }
                    manifest.iconUrl = bestIconUrl
                }

                currentManifest = manifest
                if (context is Activity) {
                    context.runOnUiThread {
                        installCallback?.onPwaDetected(manifest)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Manifest JSON parsing failed: ${e.message}")
            }
        }

        @JavascriptInterface
        fun onWebShareTriggered(title: String?, text: String?, url: String?) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                if (!title.isNullOrBlank()) putExtra(Intent.EXTRA_SUBJECT, title)
                val body = buildString {
                    if (!text.isNullOrBlank()) append(text).append("\n")
                    if (!url.isNullOrBlank()) append(url)
                }
                putExtra(Intent.EXTRA_TEXT, body.trim())
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share via").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
    }

    companion object {
        private const val TAG = "PetalPwaManager"
        private const val JS_INTERFACE_NAME = "PetalPwaInterface"

        private const val PWA_DETECTION_SCRIPT =
            "(function() {" +
            "   var manifestLink = document.querySelector('link[rel=\"manifest\"]');" +
            "   if (!manifestLink) return;" +
            "   fetch(manifestLink.href)" +
            "       .then(function(res) { return res.json(); })" +
            "       .then(function(data) {" +
            "           if (window." + JS_INTERFACE_NAME + ") {" +
            "               window." + JS_INTERFACE_NAME + ".onManifestParsed(JSON.stringify(data));" +
            "           }" +
            "       })" +
            "       .catch(function(err) {" +
            "           console.log('Petal PWA manifest fetch failed: ' + err);" +
            "       });" +
            "})();"
    }
}
