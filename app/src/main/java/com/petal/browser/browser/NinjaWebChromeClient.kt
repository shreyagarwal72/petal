package com.petal.browser.browser

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.petal.browser.R
import com.petal.browser.ui.components.PetalPermissionDialogBridge
import com.petal.browser.ui.components.PetalPermissionType
import com.petal.browser.unit.BrowserUnit
import com.petal.browser.unit.HelperUnit
import com.petal.browser.view.NinjaToast
import com.petal.browser.view.NinjaWebView

class NinjaWebChromeClient(private val ninjaWebView: NinjaWebView) : WebChromeClient() {

    override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
        val dialog = MaterialAlertDialogBuilder(ninjaWebView.context)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
            .create()
        dialog.show()
        HelperUnit.setupDialog(ninjaWebView.context, dialog)
        return true
    }

    override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
        val dialog = MaterialAlertDialogBuilder(ninjaWebView.context)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
            .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
            .create()
        dialog.show()
        HelperUnit.setupDialog(ninjaWebView.context, dialog)
        return true
    }

    override fun onJsPrompt(
        view: WebView,
        url: String,
        message: String,
        defaultValue: String,
        result: JsPromptResult
    ): Boolean {
        val editText = EditText(ninjaWebView.context).apply {
            setText(defaultValue)
        }
        val dialog = MaterialAlertDialogBuilder(ninjaWebView.context)
            .setView(editText)
            .setTitle(message)
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm(editText.text.toString()) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
            .create()
        dialog.show()
        HelperUnit.setupDialog(ninjaWebView.context, dialog)
        return true
    }

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        ninjaWebView.updateProgress(newProgress)
        val title = ninjaWebView.title
        val url = ninjaWebView.url
        if (title.isNullOrEmpty()) {
            ninjaWebView.updateTitle(url, url)
        } else {
            ninjaWebView.updateTitle(title, url)
        }
    }

    override fun onCreateWindow(
        view: WebView,
        dialog: Boolean,
        userGesture: Boolean,
        resultMsg: android.os.Message
    ): Boolean {
        if (!userGesture) {
            return false
        }
        val context = view.context
        val newWebView = NinjaWebView(context)
        view.addView(newWebView)
        val transport = resultMsg.obj as WebView.WebViewTransport
        transport.webView = newWebView
        resultMsg.sendToTarget()
        newWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                try {
                    BrowserUnit.intentURL(context, request.url)
                } catch (e: Exception) {
                    Log.i("NinjaWebChromeClient", "shouldOverrideUrlLoading Exception:$e")
                }
                return true
            }
        }
        return true
    }

    override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
        NinjaWebView.getBrowserController()?.onShowCustomView(view, callback)
        super.onShowCustomView(view, callback)
    }

    override fun onHideCustomView() {
        NinjaWebView.getBrowserController()?.onHideCustomView()
        super.onHideCustomView()
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: WebChromeClient.FileChooserParams?
    ): Boolean {
        if (NinjaWebView.getBrowserController() != null) {
            NinjaWebView.getBrowserController().showFileChooser(filePathCallback, fileChooserParams)
            return true
        }
        return false
    }

    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
        val activity = ninjaWebView.context as Activity
        val sp = PreferenceManager.getDefaultSharedPreferences(activity)
        PetalPermissionDialogBridge.showPermissionPrompt(
            activity,
            PetalPermissionType.LOCATION,
            origin,
            {
                sp.edit().putBoolean(NinjaWebView.getProfile() + "_location", true).apply()
                HelperUnit.grantPermissionsLoc(activity)
                callback.invoke(origin, true, true)
            },
            {
                sp.edit().putBoolean(NinjaWebView.getProfile() + "_location", false).apply()
                callback.invoke(origin, false, false)
            }
        )
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        val sp = PreferenceManager.getDefaultSharedPreferences(ninjaWebView.context)
        val activity = ninjaWebView.context as Activity
        val resources = request.resources
        val originStr = request.origin?.toString() ?: "Webpage"

        for (resource in resources) {
            when (resource) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                    PetalPermissionDialogBridge.showPermissionPrompt(
                        activity,
                        PetalPermissionType.CAMERA,
                        originStr,
                        {
                            sp.edit().putBoolean(NinjaWebView.getProfile() + "_camera", true).apply()
                            HelperUnit.grantPermissionsCamera(activity)
                            if (ninjaWebView.settings.mediaPlaybackRequiresUserGesture) {
                                ninjaWebView.settings.mediaPlaybackRequiresUserGesture = false
                            }
                            request.grant(arrayOf(resource))
                        },
                        {
                            sp.edit().putBoolean(NinjaWebView.getProfile() + "_camera", false).apply()
                            request.deny()
                        }
                    )
                }
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                    PetalPermissionDialogBridge.showPermissionPrompt(
                        activity,
                        PetalPermissionType.MICROPHONE,
                        originStr,
                        {
                            sp.edit().putBoolean(NinjaWebView.getProfile() + "_microphone", true).apply()
                            HelperUnit.grantPermissionsMic(activity)
                            request.grant(arrayOf(resource))
                        },
                        {
                            sp.edit().putBoolean(NinjaWebView.getProfile() + "_microphone", false).apply()
                            request.deny()
                        }
                    )
                }
                PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> {
                    PetalPermissionDialogBridge.showPermissionPrompt(
                        activity,
                        PetalPermissionType.DRM,
                        originStr,
                        {
                            sp.edit().putBoolean(NinjaWebView.getProfile() + "_drm", true).apply()
                            request.grant(arrayOf(resource))
                        },
                        {
                            sp.edit().putBoolean(NinjaWebView.getProfile() + "_drm", false).apply()
                            request.deny()
                        }
                    )
                }
            }
        }
    }

    override fun onReceivedIcon(view: WebView, icon: Bitmap) {
        val url = ninjaWebView.url
        val iv = ninjaWebView.albumView?.findViewById<ImageView>(R.id.item_icon)
        if (url == null || url == "about:blank") {
            iv?.setImageResource(R.drawable.icon_image_broken)
        } else if (BrowserUnit.isURL(url)) {
            ninjaWebView.favicon = icon
            ninjaWebView.updateFavicon(ninjaWebView.url)
        } else {
            iv?.setImageResource(R.drawable.icon_image_broken)
        }
        super.onReceivedIcon(view, icon)
    }

    override fun onReceivedTitle(view: WebView, sTitle: String) {
        super.onReceivedTitle(view, sTitle)
        val url = ninjaWebView.url
        val iv = ninjaWebView.albumView?.findViewById<ImageView>(R.id.item_icon)
        if (url == null || url == "about:blank") {
            iv?.setImageResource(R.drawable.icon_image_broken)
        } else if (BrowserUnit.isURL(url)) {
            ninjaWebView.updateFavicon(ninjaWebView.url)
        } else {
            iv?.setImageResource(R.drawable.icon_image_broken)
        }
    }
}
