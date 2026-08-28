package com.petal.browser.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Message
import android.text.InputType
import android.view.View
import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import androidx.webkit.WebViewFeature
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.petal.browser.R
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.database.Record
import com.petal.browser.database.RecordAction
import com.petal.browser.flags.ChromeFlagsManager
import com.petal.browser.flags.PetalChromeFlagsBridge
import com.petal.browser.media.PetalMediaBridge
import com.petal.browser.unit.BrowserUnit
import com.petal.browser.unit.HelperUnit
import com.petal.browser.unit.RecordUnit
import com.petal.browser.unit.TabSessionManager
import com.petal.browser.view.NinjaToast
import com.petal.browser.view.NinjaWebView

class NinjaWebViewClient(private val ninjaWebView: NinjaWebView) : WebViewClient() {

    private val context: Context = ninjaWebView.context
    private val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val adBlock: AdBlock = AdBlock(context)
    @Volatile
    private var currentUrl: String = ""

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        if (detail != null && detail.didCrash()) {
            NinjaToast.show(context, context.getString(R.string.app_error))
            if (view != null && view.isAttachedToWindow) {
                view.post { view.reload() }
            }
        }
        return true
    }

    override fun onPageFinished(view: WebView, url: String?) {
        if (url != null) {
            this.currentUrl = url
        }
        super.onPageFinished(view, url)

        if (ninjaWebView.isForeground) ninjaWebView.invalidate()
        else ninjaWebView.postInvalidate()

        if (sp.getBoolean("sp_global_google_login", true)) {
            try {
                val cm = CookieManager.getInstance()
                cm.setAcceptCookie(true)
                cm.setAcceptThirdPartyCookies(view, true)
                cm.flush()
            } catch (ignored: Exception) {
            }
        } else {
            CookieManager.getInstance().flush()
        }

        if (context is BrowserActivity) {
            context.resetRefreshState()
        }

        sp.edit().putString("mCurrentUrl", url).apply()

        ninjaWebView.mediaBridge?.injectMediaScript()

        if (sp.getBoolean("sp_auto_pip", true)) {
            try {
                view.evaluateJavascript(
                    "if (!document.pictureInPictureEnabled) { " +
                    "  document.pictureInPictureEnabled = true; " +
                    "  HTMLVideoElement.prototype.requestPictureInPicture = function() { " +
                    "    var self = this; " +
                    "    return new Promise(function(resolve, reject) { " +
                    "      if (window.PetalMediaBridge) { window.PetalMediaBridge.triggerPip(); resolve(self); } " +
                    "      else { resolve(self); } " +
                    "    }); " +
                    "  }; " +
                    "}", null
                )
            } catch (ignored: Exception) {
            }
        }

        ninjaWebView.pwaManager?.injectPwaDetection()

        if (ninjaWebView.isSaveData) {
            view.evaluateJavascript("var links=document.getElementsByTagName('video'); for(let i=0;i<links.length;i++){links[i].pause()};", null)
        }

        val webUrl = ninjaWebView.url
        if (!ninjaWebView.isIncognito && ninjaWebView.isHistory && !webUrl.isNullOrBlank() && !webUrl.trim().equals("about:blank", ignoreCase = true) && !webUrl.trim().startsWith("about:")) {
            val action = RecordAction(ninjaWebView.context)
            action.open(true)
            if (action.checkUrl(webUrl, RecordUnit.TABLE_HISTORY)) action.deleteURL(webUrl, RecordUnit.TABLE_HISTORY)
            action.addHistory(Record(ninjaWebView.title, webUrl, System.currentTimeMillis(), 0))
            action.close()
        }

        if (ninjaWebView.isAdBlock) {
            PetalAdBlockEngine.ensureInitialized(context)
            val currentWebUrl = ninjaWebView.url ?: ""
            view.evaluateJavascript(PetalAdBlockEngine.getuBlockCosmeticAndScriptletPayload(currentWebUrl), null)
        }

        // Persist open tabs and WebView state bundle on page finished
        if (!ninjaWebView.isIncognito) {
            TabSessionManager.saveSession(context)
        }

        ninjaWebView.updatePreviewCache()
        ninjaWebView.resetGestureExclusionRects()
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        if (view == null) return
        if (url != null) {
            this.currentUrl = url
        }
        try {
            ninjaWebView.isStopped = false
            ninjaWebView.resetFavicon()

            super.onPageStarted(view, url, favicon)

            if (context is BrowserActivity) {
                context.onTabUrlStarted(ninjaWebView, url)
            }

            if (ninjaWebView.isFingerPrintProtection) {
                // Block WebRTC requests which can reveal local IP address
                view.evaluateJavascript(
                    "['createOffer', 'createAnswer','setLocalDescription', 'setRemoteDescription'].forEach(function(method) {\n" +
                    "    webkitRTCPeerConnection.prototype[method] = function() {\n" +
                    "      console.log('webRTC snoop');\n" +
                    "      return null;\n" +
                    "    };\n" +
                    "  });", null
                )

                // Canvas fingerprint protection
                view.evaluateJavascript(
                    "\n" +
                    "  const toBlob = HTMLCanvasElement.prototype.toBlob;\n" +
                    "  const toDataURL = HTMLCanvasElement.prototype.toDataURL;\n" +
                    "  const getImageData = CanvasRenderingContext2D.prototype.getImageData;\n" +
                    "  //\n" +
                    "  var noisify = function (canvas, context) {\n" +
                    "    if (context) {\n" +
                    "      const shift = {\n" +
                    "        'r': Math.floor(Math.random() * 10) - 5,\n" +
                    "        'g': Math.floor(Math.random() * 10) - 5,\n" +
                    "        'b': Math.floor(Math.random() * 10) - 5,\n" +
                    "        'a': Math.floor(Math.random() * 10) - 5\n" +
                    "      };\n" +
                    "      //\n" +
                    "      const width = canvas.width;\n" +
                    "      const height = canvas.height;\n" +
                    "      if (width && height) {\n" +
                    "        const imageData = getImageData.apply(context, [0, 0, width, height]);\n" +
                    "        for (let i = 0; i < height; i++) {\n" +
                    "          for (let j = 0; j < width; j++) {\n" +
                    "            const n = ((i * (width * 4)) + (j * 4));\n" +
                    "            imageData.data[n + 0] = imageData.data[n + 0] + shift.r;\n" +
                    "            imageData.data[n + 1] = imageData.data[n + 1] + shift.g;\n" +
                    "            imageData.data[n + 2] = imageData.data[n + 2] + shift.b;\n" +
                    "            imageData.data[n + 3] = imageData.data[n + 3] + shift.a;\n" +
                    "          }\n" +
                    "        }\n" +
                    "        //\n" +
                    "        window.top.postMessage(\"canvas-fingerprint-defender-alert\", '*');\n" +
                    "        context.putImageData(imageData, 0, 0); \n" +
                    "      }\n" +
                    "    }\n" +
                    "  };\n" +
                    "  //\n" +
                    "  Object.defineProperty(HTMLCanvasElement.prototype, \"toBlob\", {\n" +
                    "    \"value\": function () {\n" +
                    "      noisify(this, this.getContext(\"2d\"));\n" +
                    "      return toBlob.apply(this, arguments);\n" +
                    "    }\n" +
                    "  });", null
                )

                view.evaluateJavascript(PetalMediaBridge.MEDIA_JS_INJECTION, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onLoadResource(view: WebView, url: String?) {
        if (ninjaWebView.isFingerPrintProtection) {
            view.evaluateJavascript("var test=document.querySelector(\"a[ping]\"); if(test!==null){test.removeAttribute('ping')};", null)
            if (view.settings.useWideViewPort && (view.width < 1300)) {
                view.evaluateJavascript("document.querySelector('meta[name=\"viewport\"]').setAttribute('content', 'width=1200px');", null)
            }
            view.evaluateJavascript("if (navigator.globalPrivacyControl === undefined) { Object.defineProperty(navigator, 'globalPrivacyControl', { value: true, writable: false,configurable: false});} else {try { navigator.globalPrivacyControl = true;} catch (e) { console.error('globalPrivacyControl is not writable: ', e); }};", null)
            view.evaluateJavascript("if (navigator.doNotTrack === null) { Object.defineProperty(navigator, 'doNotTrack', { value: 1, writable: false,configurable: false});} else {try { navigator.doNotTrack = 1;} catch (e) { console.error('doNotTrack is not writable: ', e); }};", null)
            view.evaluateJavascript("if (window.doNotTrack === undefined) { Object.defineProperty(window, 'doNotTrack', { value: 1, writable: false,configurable: false});} else {try { window.doNotTrack = 1;} catch (e) { console.error('doNotTrack is not writable: ', e); }};", null)
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest?): Boolean {
        if (request?.url == null) return false
        val uri: Uri = request.url
        val url = uri.toString()
        if (ChromeFlagsManager.isFlagsUrl(url)) {
            if (context is androidx.activity.ComponentActivity) {
                PetalChromeFlagsBridge.showFlags(context, null)
            }
            return true
        }

        val isHttpsOnly = sp.getBoolean("sp_https_only", true)
        if (isHttpsOnly && url.startsWith("http://")) {
            val httpsUrl = "https://" + url.substring(7)
            view.loadUrl(httpsUrl)
            return true
        }

        val autoOpenApps = sp.getBoolean("sp_auto_open_apps", false)
        if (autoOpenApps) {
            if (url.startsWith("intent://") || url.startsWith("market://") || url.startsWith("whatsapp://") ||
                url.startsWith("tg://") || url.startsWith("tel:") || url.startsWith("mailto:")) {
                try {
                    val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        return true
                    }
                } catch (e: Exception) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        return true
                    } catch (ignored: Exception) {
                    }
                }
            }
        }

        return if (url.startsWith("http:") || url.startsWith("https:") || url.startsWith("file:") || url.startsWith("about:")) {
            false
        } else {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(Intent.createChooser(intent, url))
            } catch (ignored: Exception) {
            }
            true
        }
    }

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        if (url != null) {
            this.currentUrl = url
        }
        super.doUpdateVisitedHistory(view, url, isReload)
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        if (request != null && !request.isForMainFrame && ninjaWebView.isAdBlock) {
            PetalAdBlockEngine.ensureInitialized(context)
            val reqUrl = request.url.toString()
            val pageUrl = currentUrl
            if (PetalAdBlockEngine.shouldBlockUrl(context, reqUrl, pageUrl) || adBlock.isAd(reqUrl)) {
                return PetalAdBlockEngine.createEmpty204Response()
            }
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun onFormResubmission(view: WebView, doNotResend: Message, resend: Message) {
        HelperUnit.showCustomSnackbarWithTwoActions(
            context, view, null,
            view.title, context.getString(R.string.dialog_content_resubmission), view.url,
            R.drawable.icon_check, {
                resend.sendToTarget()
                true
            },
            R.drawable.icon_close, {
                doNotResend.sendToTarget()
                true
            }
        )
    }

    @SuppressLint("WebViewClientOnReceivedSslError")
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        val message = when (error.primaryError) {
            SslError.SSL_UNTRUSTED -> "\"Certificate authority is not trusted.\""
            SslError.SSL_EXPIRED -> "\"Certificate has expired.\""
            SslError.SSL_IDMISMATCH -> "\"Certificate Hostname mismatch.\""
            SslError.SSL_NOTYETVALID -> "\"Certificate is not yet valid.\""
            SslError.SSL_DATE_INVALID -> "\"Certificate date is invalid.\""
            else -> "\"Certificate is invalid.\""
        }
        val text = message + " - " + context.getString(R.string.dialog_content_ssl_error)

        HelperUnit.showCustomSnackbarWithTwoActions(
            context, view, null,
            view.title, text, ninjaWebView.url,
            R.drawable.icon_check, {
                handler.proceed()
                ninjaWebView.reload()
                true
            },
            R.drawable.icon_close, {
                handler.cancel()
                true
            }
        )
    }

    override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String?, realm: String?) {
        val builder = MaterialAlertDialogBuilder(context)
        val dialogView = View.inflate(context, R.layout.dialog_edit, null)

        val editBottomLayout = dialogView.findViewById<TextInputLayout>(R.id.editBottomLayout)
        val editTopLayout = dialogView.findViewById<TextInputLayout>(R.id.editTopLayout)
        editBottomLayout.hint = context.getString(R.string.dialog_sign_in_password)
        editTopLayout.hint = context.getString(R.string.dialog_sign_in_username)
        val editTop = dialogView.findViewById<EditText>(R.id.editTop)
        val editBottom = dialogView.findViewById<EditText>(R.id.editBottom)

        editTop.setText("")
        editBottom.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        editBottom.setText("")

        builder.setTitle(view.title)
        builder.setIcon(R.drawable.icon_alert)
        builder.setView(dialogView)

        val dialog = builder.create()
        dialog.show()
        HelperUnit.setupDialog(context, dialog)
        dialog.setOnCancelListener {
            handler.cancel()
            it.cancel()
        }

        val ibCancel = dialogView.findViewById<Button>(R.id.editCancel)
        ibCancel.setOnClickListener { dialog.cancel() }
        val ibOk = dialogView.findViewById<Button>(R.id.editOK)
        ibOk.setOnClickListener {
            val user = editTop.text.toString().trim()
            val pass = editBottom.text.toString().trim()
            handler.proceed(user, pass)
            dialog.cancel()
        }
    }

    override fun onSafeBrowsingHit(view: WebView?, request: WebResourceRequest?, threatType: Int, response: SafeBrowsingResponse?) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_RESPONSE_BACK_TO_SAFETY)) {
            HelperUnit.showCustomSnackbarWithTwoActions(
                context, view, null,
                "Security Warning",
                "Deceptive or malicious website detected! Back to safety recommended.",
                ninjaWebView.url,
                R.drawable.icon_secure, {
                    response?.backToSafety(true)
                    true
                },
                R.drawable.icon_unsecure, {
                    response?.proceed(true)
                    true
                }
            )
        } else {
            response?.proceed(true)
        }
    }

    override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
        super.onReceivedError(view, errorCode, description, failingUrl)
        if (context is BrowserActivity) {
            context.resetRefreshState()
        }
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        super.onReceivedError(view, request, error)
        if (request != null && request.isForMainFrame && context is BrowserActivity) {
            context.resetRefreshState()
        }
    }
}
