package com.petal.browser.browser;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Message;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.HttpAuthHandler;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebBackForwardList;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.webkit.WebViewFeature;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;

import com.petal.browser.R;
import com.petal.browser.database.Record;
import com.petal.browser.database.RecordAction;
import com.petal.browser.unit.BrowserUnit;
import com.petal.browser.unit.HelperUnit;
import com.petal.browser.unit.RecordUnit;
import com.petal.browser.view.NinjaToast;
import com.petal.browser.view.NinjaWebView;

public class NinjaWebViewClient extends WebViewClient {

    private final NinjaWebView ninjaWebView;
    private final Context context;
    private final SharedPreferences sp;
    private final AdBlock adBlock;
    private volatile String currentUrl = "";

    // Extensions WebView would otherwise render inline as text instead of downloading, since
    // they typically aren't served with a Content-Disposition: attachment header. Kept to
    // source/code/data-file types a user would expect a "Download" prompt for, not markup the
    // browser is meant to display (html, htm, css, and images/media are intentionally excluded).
    private static final Set<String> FORCE_DOWNLOAD_EXTENSIONS = new HashSet<>(java.util.Arrays.asList(
            "kt", "kts", "java", "py", "c", "h", "cpp", "cc", "hpp", "cs", "go", "rb", "php",
            "rs", "swift", "ts", "tsx", "jsx", "sh", "bash", "yml", "yaml", "json", "xml", "csv",
            "md", "gradle", "properties", "toml", "ini", "log", "sql", "txt"
    ));

    public NinjaWebViewClient(NinjaWebView ninjaWebView) {
        super();
        this.ninjaWebView = ninjaWebView;
        this.context = ninjaWebView.getContext();
        this.sp = PreferenceManager.getDefaultSharedPreferences(context);
        this.adBlock = new AdBlock(this.context);
    }

    @Override
    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
        if (view != null) {
            view.post(() -> {
                try {
                    if (view.isAttachedToWindow()) {
                        view.reload();
                    }
                } catch (Exception ignored) {}
            });
        }
        return true;
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        if (url != null) {
            this.currentUrl = url;
        }
        super.onPageFinished(view, url);



        if (ninjaWebView.isForeground()) ninjaWebView.invalidate();
        else ninjaWebView.postInvalidate();

        if (sp.getBoolean("sp_global_google_login", true)) {
            try {
                CookieManager cm = CookieManager.getInstance();
                cm.setAcceptCookie(true);
                cm.setAcceptThirdPartyCookies(view, true);
                cm.flush();
            } catch (Exception ignored) {}
        } else {
            CookieManager.getInstance().flush();
        }

        if (context instanceof com.petal.browser.activity.BrowserActivity) {
            ((com.petal.browser.activity.BrowserActivity) context).resetRefreshState();
        }

        sp.edit().putString("mCurrentUrl", url).apply();

        if (ninjaWebView.getMediaBridge() != null) {
            ninjaWebView.getMediaBridge().injectMediaHooks();
        }

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
                );
            } catch (Exception ignored) {}
        }

        if (ninjaWebView.getPwaManager() != null) {
            ninjaWebView.getPwaManager().detectPwaManifest();
        }

        if (ninjaWebView.isSaveData())
            view.evaluateJavascript("var links=document.getElementsByTagName('video'); for(let i=0;i<links.length;i++){links[i].pause()};", null);

        if (!ninjaWebView.isIncognito() && ninjaWebView.isHistory() && ninjaWebView.getUrl() != null && !ninjaWebView.getUrl().trim().equalsIgnoreCase("about:blank") && !ninjaWebView.getUrl().trim().startsWith("about:")) {
            RecordAction action = new RecordAction(ninjaWebView.getContext());
            action.open(true);
            if (action.checkUrl(ninjaWebView.getUrl(), RecordUnit.TABLE_HISTORY)) action.deleteURL(ninjaWebView.getUrl(), RecordUnit.TABLE_HISTORY);
            action.addHistory(new Record(ninjaWebView.getTitle(), ninjaWebView.getUrl(), System.currentTimeMillis(), 0));
            action.close();
            com.petal.browser.unit.PetalSessionHistoryManager.recordSessionVisit(ninjaWebView.getUrl());
        }

        if (ninjaWebView.isAdBlock()) {
            PetalAdBlockEngine.ensureInitialized(context);
            String currentUrl = ninjaWebView.getUrl();
            view.evaluateJavascript(PetalAdBlockEngine.getuBlockCosmeticAndScriptletPayload(currentUrl), null);
        }

        String profile = NinjaWebView.getProfile();
        if (sp.getBoolean(profile + "_deny_cookie_banners",false)){
            //click opt-out if possible
            String bannerBlockScript = BannerBlock.getBannerBlockScriptPageFinished();
            if (bannerBlockScript != null) view.evaluateJavascript(bannerBlockScript,null);
        }

        // Persist open tabs and WebView state bundle on page finished
        if (!ninjaWebView.isIncognito()) {
            com.petal.browser.unit.TabSessionManager.saveSession(context);
        }

        // Apply Accessibility hooks (per-site zoom, force viewport zoom, reader mode detector, caret browsing)
        com.petal.browser.accessibility.PetalAccessibilityEngine.applyZoomToWebView(view, url);
        com.petal.browser.accessibility.PetalAccessibilityEngine.applyForceZoom(view);
        com.petal.browser.accessibility.PetalAccessibilityEngine.injectReaderModeDetector(view);
        com.petal.browser.accessibility.PetalAccessibilityEngine.applyCaretBrowsing(view, com.petal.browser.accessibility.PetalAccessibilityEngine.isCaretBrowsingEnabled(context));

        // Refresh the tab manager's LRU thumbnail cache so the grid shows an up-to-date
        // preview for this tab the next time it's opened, without the switcher having to
        // capture on the spot.
        ninjaWebView.updatePreviewCache();
        ninjaWebView.resetGestureExclusionRects();
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        if (view == null || ninjaWebView == null) return;
        if (url != null) {
            this.currentUrl = url;
        }
        try {
            ninjaWebView.setStopped(false);
            ninjaWebView.resetFavicon();

            super.onPageStarted(view, url, favicon);

            if (context instanceof com.petal.browser.activity.BrowserActivity) {
                ((com.petal.browser.activity.BrowserActivity) context).onTabUrlStarted(ninjaWebView, url);
            }

        String profile = NinjaWebView.getProfile();
        if (sp.getBoolean("sp_webauthn_enabled", true)) {
            view.evaluateJavascript(com.petal.browser.passkey.PetalWebAuthnBridge.WEBAUTHN_POLYFILL_JS, null);
        }
        if (sp.getBoolean(profile + "_deny_cookie_banners",false)){
            //click opt-out if possible
            String bannerBlockScript = BannerBlock.getBannerBlockScriptPageStarted();
            if (bannerBlockScript != null) view.evaluateJavascript(bannerBlockScript,null);
        }

        boolean webrtcProtection = sp.getBoolean("sp_webrtc_protection", true);
        if (webrtcProtection) {
            // Block WebRTC requests which can reveal local or public IP address
            view.evaluateJavascript("if (window.RTCPeerConnection) { " +
                    "  ['createOffer', 'createAnswer','setLocalDescription', 'setRemoteDescription'].forEach(function(method) {\n" +
                    "    if (window.RTCPeerConnection.prototype[method]) RTCPeerConnection.prototype[method] = function() { return Promise.reject(new Error('WebRTC blocked by Petal Shield')); };\n" +
                    "  });\n" +
                    "} " +
                    "if (window.webkitRTCPeerConnection) {\n" +
                    "  ['createOffer', 'createAnswer','setLocalDescription', 'setRemoteDescription'].forEach(function(method) {\n" +
                    "    if (window.webkitRTCPeerConnection.prototype[method]) webkitRTCPeerConnection.prototype[method] = function() { return null; };\n" +
                    "  });\n" +
                    "}", null);
        }

        if (ninjaWebView.isFingerPrintProtection() || sp.getBoolean("sp_fingerprint_protection", true)) {

            //Prevent canvas fingerprinting by randomizing
            //can be tested e.g. at https://webbrowsertools.com
            //
            //The Javascript part below is taken from "Canvas Fingerprint Defender", Firefox plugin, Version 0.1.9, by ilGur
            //The source code has been published originally under Mozilla Public License V2.0. You can obtain a copy of the license at https://mozilla.org/MPL/2.0/
            //The author has given explicit written permission to use his code under GPL V3 in this project.

            view.evaluateJavascript("try {\n" +
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
                    "  });\n" +
                    "  //\n" +
                    "  Object.defineProperty(HTMLCanvasElement.prototype, \"toDataURL\", {\n" +
                    "    \"value\": function () {\n" +
                    "      noisify(this, this.getContext(\"2d\"));\n" +
                    "      return toDataURL.apply(this, arguments);\n" +
                    "    }\n" +
                    "  });\n" +
                    "  //\n" +
                    "  Object.defineProperty(CanvasRenderingContext2D.prototype, \"getImageData\", {\n" +
                    "    \"value\": function () {\n" +
                    "      noisify(this.canvas, this);\n" +
                    "      return getImageData.apply(this, arguments);\n" +
                    "    }\n" +
                    "  });\n" +
                    "} catch(e) {}", null);

            //Prevent WebGL fingerprinting by randomizing
            //can be tested e.g. at https://webbrowsertools.com
            //
            //The Javascript part below is taken from "WebGL Fingerprint Defender", Firefox plugin, Version 0.1.5, by ilGur
            //The source code has been published originally under Mozilla Public License V2.0. You can obtain a copy of the license at https://mozilla.org/MPL/2.0/
            //The author has given explicit written permission to use his code under GPL V3 in this project.

            view.evaluateJavascript("try {\n" +
                    "  var config = {\n" +
                    "    \"random\": {\n" +
                    "      \"value\": function () {\n" +
                    "        return Math.random();\n" +
                    "      },\n" +
                    "      \"item\": function (e) {\n" +
                    "        var rand = e.length * config.random.value();\n" +
                    "        return e[Math.floor(rand)];\n" +
                    "      },\n" +
                    "      \"number\": function (power) {\n" +
                    "        var tmp = [];\n" +
                    "        for (var i = 0; i < power.length; i++) {\n" +
                    "          tmp.push(Math.pow(2, power[i]));\n" +
                    "        }\n" +
                    "        /*  */\n" +
                    "        return config.random.item(tmp);\n" +
                    "      },\n" +
                    "      \"int\": function (power) {\n" +
                    "        var tmp = [];\n" +
                    "        for (var i = 0; i < power.length; i++) {\n" +
                    "          var n = Math.pow(2, power[i]);\n" +
                    "          tmp.push(new Int32Array([n, n]));\n" +
                    "        }\n" +
                    "        /*  */\n" +
                    "        return config.random.item(tmp);\n" +
                    "      },\n" +
                    "      \"float\": function (power) {\n" +
                    "        var tmp = [];\n" +
                    "        for (var i = 0; i < power.length; i++) {\n" +
                    "          var n = Math.pow(2, power[i]);\n" +
                    "          tmp.push(new Float32Array([1, n]));\n" +
                    "        }\n" +
                    "        /*  */\n" +
                    "        return config.random.item(tmp);\n" +
                    "      }\n" +
                    "    },\n" +
                    "    \"spoof\": {\n" +
                    "      \"webgl\": {\n" +
                    "        \"buffer\": function (target) {\n" +
                    "          var proto = target.prototype ? target.prototype : target.__proto__;\n" +
                    "          const bufferData = proto.bufferData;\n" +
                    "          Object.defineProperty(proto, \"bufferData\", {\n" +
                    "            \"value\": function () {\n" +
                    "              var index = Math.floor(config.random.value() * arguments[1].length);\n" +
                    "              var noise = arguments[1][index] !== undefined ? 0.1 * config.random.value() * arguments[1][index] : 0;\n" +
                    "              //\n" +
                    "              arguments[1][index] = arguments[1][index] + noise;\n" +
                    "              window.top.postMessage(\"webgl-fingerprint-defender-alert\", '*');\n" +
                    "              //\n" +
                    "              return bufferData.apply(this, arguments);\n" +
                    "            }\n" +
                    "          });\n" +
                    "        },\n" +
                    "        \"parameter\": function (target) {\n" +
                    "          var proto = target.prototype ? target.prototype : target.__proto__;\n" +
                    "          const getParameter = proto.getParameter;\n" +
                    "          Object.defineProperty(proto, \"getParameter\", {\n" +
                    "            \"value\": function () {\n" +
                    "              window.top.postMessage(\"webgl-fingerprint-defender-alert\", '*');\n" +
                    "              //\n" +
                    "              if (arguments[0] === 3415) return 0;\n" +
                    "              else if (arguments[0] === 3414) return 24;\n" +
                    "              else if (arguments[0] === 36348) return 30;\n" +
                    "              else if (arguments[0] === 7936) return \"WebKit\";\n" +
                    "              else if (arguments[0] === 37445) return \"Google Inc.\";\n" +
                    "              else if (arguments[0] === 7937) return \"WebKit WebGL\";\n" +
                    "              else if (arguments[0] === 3379) return config.random.number([14, 15]);\n" +
                    "              else if (arguments[0] === 36347) return config.random.number([12, 13]);\n" +
                    "              else if (arguments[0] === 34076) return config.random.number([14, 15]);\n" +
                    "              else if (arguments[0] === 34024) return config.random.number([14, 15]);\n" +
                    "              else if (arguments[0] === 3386) return config.random.int([13, 14, 15]);\n" +
                    "              else if (arguments[0] === 3413) return config.random.number([1, 2, 3, 4]);\n" +
                    "              else if (arguments[0] === 3412) return config.random.number([1, 2, 3, 4]);\n" +
                    "              else if (arguments[0] === 3411) return config.random.number([1, 2, 3, 4]);\n" +
                    "              else if (arguments[0] === 3410) return config.random.number([1, 2, 3, 4]);\n" +
                    "              else if (arguments[0] === 34047) return config.random.number([1, 2, 3, 4]);\n" +
                    "              else if (arguments[0] === 34930) return config.random.number([1, 2, 3, 4]);\n" +
                    "              else if (arguments[0] === 34921) return config.random.number([1, 2, 3, 4]);\n" +
                    "              else if (arguments[0] === 35660) return config.random.number([1, 2, 3, 4]);\n" +
                    "              else if (arguments[0] === 35661) return config.random.number([4, 5, 6, 7, 8]);\n" +
                    "              else if (arguments[0] === 36349) return config.random.number([10, 11, 12, 13]);\n" +
                    "              else if (arguments[0] === 33902) return config.random.float([0, 10, 11, 12, 13]);\n" +
                    "              else if (arguments[0] === 33901) return config.random.float([0, 10, 11, 12, 13]);\n" +
                    "              else if (arguments[0] === 37446) return config.random.item([\"Graphics\", \"HD Graphics\", \"Intel(R) HD Graphics\"]);\n" +
                    "              else if (arguments[0] === 7938) return config.random.item([\"WebGL 1.0\", \"WebGL 1.0 (OpenGL)\", \"WebGL 1.0 (OpenGL Chromium)\"]);\n" +
                    "              else if (arguments[0] === 35724) return config.random.item([\"WebGL\", \"WebGL GLSL\", \"WebGL GLSL ES\", \"WebGL GLSL ES (OpenGL Chromium\"]);\n" +
                    "              //\n" +
                    "              return getParameter.apply(this, arguments);\n" +
                    "            }\n" +
                    "          });\n" +
                    "        }\n" +
                    "      }\n" +
                    "    }\n" +
                    "  };\n" +
                    "  //\n" +
                    "  config.spoof.webgl.buffer(WebGLRenderingContext);\n" +
                    "  config.spoof.webgl.buffer(WebGL2RenderingContext);\n" +
                    "  config.spoof.webgl.parameter(WebGLRenderingContext);\n" +
                    "  config.spoof.webgl.parameter(WebGL2RenderingContext);\n" +
                    "} catch(e) {}", null);

            //Prevent AudioContext fingerprinting by randomizing
            //can be tested e.g. at https://webbrowsertools.com
            //
            //The Javascript part below is taken from "AudioContext Fingerprint Defender", Firefox plugin, Version 0.1.6, by ilGur
            //The source code has been published originally under Mozilla Public License V2.0. You can obtain a copy of the license at https://mozilla.org/MPL/2.0/
            //The author has given explicit written permission to use his code under GPL V3 in this project.

            view.evaluateJavascript("try {\n" +
                    "    const context = {\n" +
                    "    \"BUFFER\": null,\n" +
                    "    \"getChannelData\": function (e) {\n" +
                    "      const getChannelData = e.prototype.getChannelData;\n" +
                    "      Object.defineProperty(e.prototype, \"getChannelData\", {\n" +
                    "        \"value\": function () {\n" +
                    "          const results_1 = getChannelData.apply(this, arguments);\n" +
                    "          if (context.BUFFER !== results_1) {\n" +
                    "            context.BUFFER = results_1;\n" +
                    "            for (var i = 0; i < results_1.length; i += 100) {\n" +
                    "              let index = Math.floor(Math.random() * i);\n" +
                    "              results_1[index] = results_1[index] + Math.random() * 0.0000001;\n" +
                    "            }\n" +
                    "          }\n" +
                    "          //\n" +
                    "          return results_1;\n" +
                    "        }\n" +
                    "      });\n" +
                    "    },\n" +
                    "    \"createAnalyser\": function (e) {\n" +
                    "      const createAnalyser = e.prototype.__proto__.createAnalyser;\n" +
                    "      Object.defineProperty(e.prototype.__proto__, \"createAnalyser\", {\n" +
                    "        \"value\": function () {\n" +
                    "          const results_2 = createAnalyser.apply(this, arguments);\n" +
                    "          const getFloatFrequencyData = results_2.__proto__.getFloatFrequencyData;\n" +
                    "          Object.defineProperty(results_2.__proto__, \"getFloatFrequencyData\", {\n" +
                    "            \"value\": function () {\n" +
                    "              const results_3 = getFloatFrequencyData.apply(this, arguments);\n" +
                    "              for (var i = 0; i < arguments[0].length; i += 100) {\n" +
                    "                let index = Math.floor(Math.random() * i);\n" +
                    "                arguments[0][index] = arguments[0][index] + Math.random() * 0.1;\n" +
                    "              }\n" +
                    "              //\n" +
                    "              return results_3;\n" +
                    "            }\n" +
                    "          });\n" +
                    "          //\n" +
                    "          return results_2;\n" +
                    "        }\n" +
                    "      });\n" +
                    "    }\n" +
                    "  };\n" +
                    "  //\n" +
                    "  context.getChannelData(AudioBuffer);\n" +
                    "  context.createAnalyser(AudioContext);\n" +
                    "  context.getChannelData(OfflineAudioContext);\n" +
                    "  context.createAnalyser(OfflineAudioContext);\n" +
                    "} catch(e) {}", null);

            //Prevent Font fingerprinting by randomizing
            //can be tested e.g. at https://webbrowsertools.com
            //
            //The Javascript part below is taken from "Font Fingerprint Defender", Firefox plugin, Version 0.1.3, by ilGur
            //The source code has been published originally under Mozilla Public License V2.0. You can obtain a copy of the license at https://mozilla.org/MPL/2.0/
            //The author has given explicit written permission to use his code under GPL V3 in this project.

            view.evaluateJavascript("try {\n" +
                    "  var rand = {\n" +
                    "    \"noise\": function () {\n" +
                    "      var SIGN = Math.random() < Math.random() ? -1 : 1;\n" +
                    "      return Math.floor(Math.random() + SIGN * Math.random());\n" +
                    "    },\n" +
                    "    \"sign\": function () {\n" +
                    "      const tmp = [-1, -1, -1, -1, -1, -1, +1, -1, -1, -1];\n" +
                    "      const index = Math.floor(Math.random() * tmp.length);\n" +
                    "      return tmp[index];\n" +
                    "    }\n" +
                    "  };\n" +
                    "  //\n" +
                    "  Object.defineProperty(HTMLElement.prototype, \"offsetHeight\", {\n" +
                    "    get () {\n" +
                    "      const height = Math.floor(this.getBoundingClientRect().height);\n" +
                    "      const valid = height && rand.sign() === 1;\n" +
                    "      const result = valid ? height + rand.noise() : height;\n" +
                    "      //\n" +
                    "      if (valid && result !== height) {\n" +
                    "        window.top.postMessage(\"font-fingerprint-defender-alert\", '*');\n" +
                    "      }\n" +
                    "      //\n" +
                    "      return result;\n" +
                    "    }\n" +
                    "  });\n" +
                    "  //\n" +
                    "  Object.defineProperty(HTMLElement.prototype, \"offsetWidth\", {\n" +
                    "    get () {\n" +
                    "      const width = Math.floor(this.getBoundingClientRect().width);\n" +
                    "      const valid = width && rand.sign() === 1;\n" +
                    "      const result = valid ? width + rand.noise() : width;\n" +
                    "      //\n" +
                    "      if (valid && result !== width) {\n" +
                    "        window.top.postMessage(\"font-fingerprint-defender-alert\", '*');\n" +
                    "      }\n" +
                    "      //\n" +
                    "      return result;\n" +
                    "    }\n" +
                    "  });\n" +
                    "} catch(e) {}", null);

            //Spoof screen resolution, color depth: set values like in Tor browser, random values for device memory, hardwareConcurrency, remove battery, network connection, keyboard, media devices info, prevent sendBeacon

            // Clean JS execution for anti-bot / captcha compliance
            if (!ninjaWebView.isCamera()) {
                // Safe non-destructive media device protection
            }
            if (sp.getBoolean("sp_webauthn_enabled", true)) {
                view.evaluateJavascript(com.petal.browser.passkey.PetalWebAuthnBridge.WEBAUTHN_POLYFILL_JS, null);
            }
            view.evaluateJavascript(com.petal.browser.media.PetalMediaBridge.MEDIA_JS_INJECTION, null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onLoadResource(WebView view, String url) {
        boolean dntGpc = sp.getBoolean("sp_dnt_gpc", true);
        boolean fingerprint = ninjaWebView.isFingerPrintProtection() || sp.getBoolean("sp_fingerprint_protection", true);
        if (fingerprint || dntGpc) {
            view.evaluateJavascript("var test=document.querySelector(\"a[ping]\"); if(test!==null){test.removeAttribute('ping')};", null);
            //  Client-side detection for GlobalPrivacyControl
            view.evaluateJavascript("if (navigator.globalPrivacyControl === undefined) { Object.defineProperty(navigator, 'globalPrivacyControl', { value: true, writable: false,configurable: false});} else {try { navigator.globalPrivacyControl = true;} catch (e) { console.error('globalPrivacyControl is not writable: ', e); }};", null);
            //  Script taken from:
            //
            //  donotsell.js
            //  DuckDuckGo
            //
            //  Copyright © 2020 DuckDuckGo. All rights reserved.
            //
            //  Licensed under the Apache License, Version 2.0 (the "License");
            //  you may not use this file except in compliance with the License.
            //  You may obtain a copy of the License at
            //
            //  http://www.apache.org/licenses/LICENSE-2.0
            //
            //  Unless required by applicable law or agreed to in writing, software
            //  distributed under the License is distributed on an "AS IS" BASIS,
            //  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
            //  See the License for the specific language governing permissions and
            //  limitations under the License.
            //
            view.evaluateJavascript("if (navigator.doNotTrack === null) { Object.defineProperty(navigator, 'doNotTrack', { value: 1, writable: false,configurable: false});} else {try { navigator.doNotTrack = 1;} catch (e) { console.error('doNotTrack is not writable: ', e); }};", null);
            view.evaluateJavascript("if (window.doNotTrack === undefined) { Object.defineProperty(window, 'doNotTrack', { value: 1, writable: false,configurable: false});} else {try { window.doNotTrack = 1;} catch (e) { console.error('doNotTrack is not writable: ', e); }};", null);
            view.evaluateJavascript("if (navigator.msDoNotTrack === undefined) { Object.defineProperty(navigator, 'msDoNotTrack', { value: 1, writable: false,configurable: false});} else {try { navigator.msDoNotTrack = 1;} catch (e) { console.error('msDoNotTrack is not writable: ', e); }};", null);
        }

        boolean trimReferrers = sp.getBoolean("sp_trim_referrers", true);
        if (trimReferrers) {
            view.evaluateJavascript("try { var meta = document.querySelector('meta[name=\"referrer\"]'); if (!meta) { meta = document.createElement('meta'); meta.name = 'referrer'; meta.content = 'strict-origin-when-cross-origin'; document.head.appendChild(meta); } else { meta.content = 'strict-origin-when-cross-origin'; } } catch(e) {}", null);
        }
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        if (request == null || request.getUrl() == null) return false;
        final Uri uri = request.getUrl();
        String url = uri.toString();
        if (com.petal.browser.flags.ChromeFlagsManager.isFlagsUrl(url)) {
            if (context instanceof androidx.activity.ComponentActivity) {
                com.petal.browser.flags.PetalChromeFlagsBridge.showFlags((androidx.activity.ComponentActivity) context, null);
            }
            return true;
        }

        boolean isHttpsOnly = sp.getBoolean("sp_https_only", true);
        if (isHttpsOnly && url.startsWith("http://")) {
            String httpsUrl = "https://" + url.substring(7);
            view.loadUrl(httpsUrl);
            return true;
        }

        // Handle custom non-standard schemes (intent://, market://, tel:, mailto:, whatsapp://, etc.)
        if (handleCustomScheme(view, url)) {
            return true;
        }

        // If Auto Open External Apps is enabled, route external app URLs / App Links directly to native apps
        boolean autoOpenApps = sp.getBoolean("sp_auto_open_apps", false);
        if (autoOpenApps && shouldOpenInExternalApp(context, uri)) {
            if (openInExternalApp(context, uri)) {
                return true;
            }
        }

        // WebView only fires NinjaDownloadListener.onDownloadStart for content types it can't
        // render itself. Plain-text/source responses (.kt, .java, .py, .json, ...) almost never
        // arrive with a Content-Disposition: attachment header, so WebView just navigates and
        // displays them inline - the download confirmation dialog never gets a chance to show at
        // all, regardless of any fix to that listener. The actual force-download decision (with a
        // verified Content-Type, not just a URL-extension guess) happens in shouldInterceptRequest
        // below; shouldForceDownloadForUrl() here is reused only as a cheap pre-filter there, so
        // this method just lets matching URLs fall through to the network layer as normal.

        if (url.startsWith("http:") || url.startsWith("https:") || url.startsWith("file:") || url.startsWith("about:")) {
            return false;
        } else {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(Intent.createChooser(intent, url));
            } catch (Exception ignored) {}
            return true;
        }
    }

    private boolean shouldForceDownloadForUrl(Uri uri) {
        String lastSegment = uri.getLastPathSegment();
        if (lastSegment == null) return false;
        int dot = lastSegment.lastIndexOf('.');
        if (dot < 0 || dot == lastSegment.length() - 1) return false;
        String ext = lastSegment.substring(dot + 1).toLowerCase(Locale.US);
        return FORCE_DOWNLOAD_EXTENSIONS.contains(ext);
    }

    // Content-Types WebView should still be allowed to render inline even when the URL's
    // extension is in FORCE_DOWNLOAD_EXTENSIONS - guards against same-origin SPA/dynamic routes
    // that merely end in e.g. ".json" or ".py" but actually serve an HTML page.
    private static final Set<String> RENDERABLE_CONTENT_TYPES = new HashSet<>(java.util.Arrays.asList(
            "text/html", "application/xhtml+xml", "text/css",
            "application/javascript", "text/javascript", "application/x-javascript"
    ));

    private boolean isRenderableContentType(String contentType) {
        return contentType != null && RENDERABLE_CONTENT_TYPES.contains(contentType);
    }

    /**
     * Issues a lightweight HEAD (falling back to a ranged GET if the server rejects HEAD) to read
     * the server's real Content-Type before deciding to force a download - runs on the background
     * thread WebView already uses for shouldInterceptRequest, so blocking here is safe.
     * Returns the Content-Type (charset stripped, lowercased) or null if the probe fails.
     */
    private String probeContentType(String url, java.util.Map<String, String> requestHeaders) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(4000);
            connection.setRequestMethod("HEAD");
            applyProbeHeaders(connection, url, requestHeaders);

            int code = connection.getResponseCode();
            if (code == HttpURLConnection.HTTP_BAD_METHOD || code == 501) {
                connection.disconnect();
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setInstanceFollowRedirects(true);
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(4000);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Range", "bytes=0-0");
                applyProbeHeaders(connection, url, requestHeaders);
                code = connection.getResponseCode();
            }

            if (code >= 200 && code < 400) {
                String contentType = connection.getContentType();
                if (contentType != null && !contentType.trim().isEmpty()) {
                    return contentType.split(";")[0].trim().toLowerCase(Locale.US);
                }
            }
        } catch (IOException ignored) {
        } finally {
            if (connection != null) connection.disconnect();
        }
        return null;
    }

    private void applyProbeHeaders(HttpURLConnection connection, String url, java.util.Map<String, String> requestHeaders) {
        String cookie = CookieManager.getInstance().getCookie(url);
        if (cookie != null && !cookie.isEmpty()) {
            connection.setRequestProperty("Cookie", cookie);
        }
        if (requestHeaders != null) {
            for (java.util.Map.Entry<String, String> header : requestHeaders.entrySet()) {
                if (!"Range".equalsIgnoreCase(header.getKey())) {
                    connection.setRequestProperty(header.getKey(), header.getValue());
                }
            }
        }
    }

    /**
     * Skips straight to the same AlertDialog-based confirmation used for real downloads
     * (see PetalDownloadDialogBridge.showDownloadConfirmation); mimeType here is the server's
     * verified Content-Type from probeContentType() when available, falling back to an
     * extension guess only if the probe failed. BrowserUnit.download() (Fetch2) resolves the
     * real size itself once the user confirms, so an unknown size here just shows the dialog
     * without a "(X MB)" suffix.
     */
    private void triggerDownloadConfirmationForUrl(String url, String verifiedContentType) {
        String mimeType = verifiedContentType;
        if (mimeType == null || mimeType.isEmpty()) {
            mimeType = android.webkit.MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(android.webkit.MimeTypeMap.getFileExtensionFromUrl(url));
        }
        if (mimeType == null) mimeType = "text/plain";
        final String finalMimeType = mimeType;
        com.petal.browser.ui.components.PetalDownloadDialogBridge.showDownloadConfirmation(
                context,
                url,
                null,
                finalMimeType,
                0L,
                confirmedFileName -> {
                    BrowserUnit.download(context, url, confirmedFileName, finalMimeType);
                    return kotlin.Unit.INSTANCE;
                }
        );
    }

    /**
     * Force-download probe used from shouldInterceptRequest(): for a main-frame GET whose URL
     * extension is in FORCE_DOWNLOAD_EXTENSIONS, verifies the server's real Content-Type before
     * triggering the download confirmation, so an empty 204-style response is returned only when
     * the probe confirms this isn't actually renderable HTML/CSS/JS (e.g. an SPA route that just
     * happens to end in ".json"). Returns null when the request should load normally.
     */
    private WebResourceResponse maybeInterceptForForceDownload(WebView view, WebResourceRequest request) {
        if (request == null || request.getUrl() == null || !request.isForMainFrame()
                || !"GET".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        final Uri uri = request.getUrl();
        final String url = uri.toString();
        if (!(url.startsWith("http:") || url.startsWith("https:")) || !shouldForceDownloadForUrl(uri)) {
            return null;
        }
        String verifiedContentType = probeContentType(url, request.getRequestHeaders());
        if (verifiedContentType != null && !isRenderableContentType(verifiedContentType)) {
            final String finalContentType = verifiedContentType;
            view.post(() -> triggerDownloadConfirmationForUrl(url, finalContentType));
            return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
        }
        // Probe failed (network/timeout) or content is actually renderable (e.g. an SPA
        // route ending in ".json" that really serves HTML) - fall through and let WebView
        // load it normally rather than risk a false-positive download prompt.
        return null;
    }

    private boolean handleCustomScheme(WebView view, String url) {
        if (url == null) return false;
        if (url.startsWith("intent://")) {
            try {
                Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    PackageManager pm = context.getPackageManager();
                    if (pm != null && intent.resolveActivity(pm) != null) {
                        context.startActivity(intent);
                        return true;
                    }
                    String fallbackUrl = intent.getStringExtra("browser_fallback_url");
                    if (fallbackUrl != null && !fallbackUrl.isEmpty()) {
                        view.loadUrl(fallbackUrl);
                        return true;
                    }
                    String pkg = intent.getPackage();
                    if (pkg != null && !pkg.isEmpty()) {
                        try {
                            Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg));
                            marketIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(marketIntent);
                            return true;
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                Log.w("NinjaWebViewClient", "Error handling intent scheme: " + url, e);
            }
            return true;
        }

        if (url.startsWith("market://") || url.startsWith("whatsapp://") || url.startsWith("tg://") ||
            url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("sms:") ||
            url.startsWith("geo:") || url.startsWith("spotify:") || url.startsWith("viber:") ||
            url.startsWith("zoommtg:") || url.startsWith("slack:") || url.startsWith("fb:") ||
            url.startsWith("twitter:") || url.startsWith("instagram:") || url.startsWith("threads:")) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                PackageManager pm = context.getPackageManager();
                if (pm != null && intent.resolveActivity(pm) != null) {
                    context.startActivity(intent);
                    return true;
                }
            } catch (Exception ignored) {}
            return true;
        }

        return false;
    }

    private boolean shouldOpenInExternalApp(Context context, Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase(java.util.Locale.ROOT);
        String path = uri.getPath();
        if (path == null) path = "";

        // YouTube
        if (host.equals("youtube.com") || host.endsWith(".youtube.com") || host.equals("youtu.be")) {
            return true;
        }
        // Google Play Store
        if (host.equals("play.google.com") && (path.startsWith("/store") || path.startsWith("/apps"))) {
            return true;
        }
        // Google Maps
        if (host.equals("maps.google.com") || host.equals("maps.app.goo.gl") || (host.endsWith("google.com") && path.startsWith("/maps"))) {
            return true;
        }
        // Social Media & Media Apps
        if (host.equals("twitter.com") || host.equals("x.com") || host.equals("mobile.twitter.com") ||
            host.equals("instagram.com") || host.endsWith(".instagram.com") ||
            host.equals("reddit.com") || host.equals("www.reddit.com") || host.equals("redd.it") ||
            host.equals("open.spotify.com") || host.equals("spotify.link") ||
            host.equals("t.me") || host.equals("telegram.me") ||
            host.equals("wa.me") || host.equals("api.whatsapp.com") ||
            host.equals("amazon.com") || host.endsWith(".amazon.com")) {
            return true;
        }

        // Generic app link verification
        try {
            PackageManager pm = context.getPackageManager();
            if (pm != null) {
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                List<ResolveInfo> list = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
                if (list != null && !list.isEmpty()) {
                    String myPackage = context.getPackageName();
                    for (ResolveInfo info : list) {
                        if (info.activityInfo != null && info.activityInfo.packageName != null) {
                            String pkg = info.activityInfo.packageName;
                            if (!pkg.equals(myPackage) && !isGenericBrowser(pkg)) {
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return false;
    }

    private boolean openInExternalApp(Context context, Uri uri) {
        if (context == null || uri == null) return false;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PackageManager pm = context.getPackageManager();
            if (pm == null) return false;

            List<ResolveInfo> activities = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (activities == null || activities.isEmpty()) {
                activities = pm.queryIntentActivities(intent, 0);
            }

            String myPackage = context.getPackageName();
            ResolveInfo targetApp = null;

            if (activities != null) {
                for (ResolveInfo info : activities) {
                    if (info.activityInfo != null && info.activityInfo.packageName != null) {
                        String pkg = info.activityInfo.packageName;
                        if (!pkg.equals(myPackage) && !isGenericBrowser(pkg)) {
                            targetApp = info;
                            break;
                        }
                    }
                }
            }

            if (targetApp != null && targetApp.activityInfo != null) {
                intent.setPackage(targetApp.activityInfo.packageName);
                context.startActivity(intent);
                return true;
            }
        } catch (Exception e) {
            Log.w("NinjaWebViewClient", "Failed to launch external app for " + uri, e);
        }
        return false;
    }

    private static boolean isGenericBrowser(String packageName) {
        if (packageName == null) return false;
        return packageName.equals("com.android.browser") ||
               packageName.equals("com.google.android.browser") ||
               packageName.equals("com.chrome.canary") ||
               packageName.equals("com.chrome.beta") ||
               packageName.equals("com.chrome.dev") ||
               packageName.equals("com.android.chrome") ||
               packageName.equals("org.mozilla.firefox") ||
               packageName.equals("org.mozilla.fenix") ||
               packageName.equals("com.opera.browser") ||
               packageName.equals("com.brave.browser") ||
               packageName.equals("com.microsoft.emmx") ||
               packageName.equals("com.sec.android.app.sbrowser") ||
               packageName.equals("com.duckduckgo.mobile.android");
    }

    @Override
    public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
        if (url != null) {
            this.currentUrl = url;
        }
        super.doUpdateVisitedHistory(view, url, isReload);
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        WebResourceResponse forcedDownloadResponse = maybeInterceptForForceDownload(view, request);
        if (forcedDownloadResponse != null) {
            return forcedDownloadResponse;
        }
        if (request != null && !request.isForMainFrame() && ninjaWebView.isAdBlock()) {
            PetalAdBlockEngine.ensureInitialized(context);
            String reqUrl = request.getUrl().toString();
            String pageUrl = currentUrl;
            if (PetalAdBlockEngine.shouldBlockUrl(context, reqUrl, pageUrl) || adBlock.isAd(reqUrl)) {
                return PetalAdBlockEngine.createEmpty204Response();
            }
        }
        return super.shouldInterceptRequest(view, request);
    }
    @Override
    public void onFormResubmission(WebView view, @NonNull final Message doNotResend, final Message resend) {
        HelperUnit.showCustomSnackbarWithTwoActions(
                context, view, null,
                view.getTitle(), context.getString(R.string.dialog_content_resubmission),view.getUrl(),
                R.drawable.icon_check, () -> {
                    resend.sendToTarget();
                    return true;
                },
                R.drawable.icon_close, () -> {
                    doNotResend.sendToTarget();
                    return true;
                }
        );
    }

    @SuppressLint("WebViewClientOnReceivedSslError")
    @Override
    public void onReceivedSslError(WebView view, final SslErrorHandler handler, SslError error) {
        String message;
        switch (error.getPrimaryError()) {
            case SslError.SSL_UNTRUSTED:
                message = "\"Certificate authority is not trusted.\"";
                break;
            case SslError.SSL_EXPIRED:
                message = "\"Certificate has expired.\"";
                break;
            case SslError.SSL_IDMISMATCH:
                message = "\"Certificate Hostname mismatch.\"";
                break;
            case SslError.SSL_NOTYETVALID:
                message = "\"Certificate is not yet valid.\"";
                break;
            case SslError.SSL_DATE_INVALID:
                message = "\"Certificate date is invalid.\"";
                break;
            default:
                message = "\"Certificate is invalid.\"";
                break;
        }
        String text = message + " - " + context.getString(R.string.dialog_content_ssl_error);

        HelperUnit.showCustomSnackbarWithTwoActions(
                context, view, null,
                view.getTitle(), text, ninjaWebView.getUrl(),
                R.drawable.icon_check, () -> {
                    handler.proceed();
                    ninjaWebView.reload();
                    return true;
                },
                R.drawable.icon_close, () -> {
                    handler.cancel();
                    return true;
                }
        );
    }

    @Override
    public void onReceivedHttpAuthRequest(WebView view, @NonNull final HttpAuthHandler handler, String host, String realm) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        View dialogView = View.inflate(context, R.layout.dialog_edit, null);

        TextInputLayout editBottomLayout = dialogView.findViewById(R.id.editBottomLayout);
        TextInputLayout editTopLayout = dialogView.findViewById(R.id.editTopLayout);
        editBottomLayout.setHint(this.context.getString(R.string.dialog_sign_in_password));
        editTopLayout.setHint(this.context.getString(R.string.dialog_sign_in_username));
        EditText editTop = dialogView.findViewById(R.id.editTop);
        EditText editBottom = dialogView.findViewById(R.id.editBottom);

        editTop.setText("");
        editBottom.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        editBottom.setText("");

        builder.setTitle(view.getTitle());
        builder.setIcon(R.drawable.icon_alert);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        dialog.show();
        HelperUnit.setupDialog(context, dialog);
        dialog.setOnCancelListener(dialog1 -> {
            handler.cancel();
            dialog1.cancel();
        });

        Button ib_cancel = dialogView.findViewById(R.id.editCancel);
        ib_cancel.setOnClickListener(v -> dialog.cancel());
        Button ib_ok = dialogView.findViewById(R.id.editOK);
        ib_ok.setOnClickListener(v -> {
            String user = Objects.requireNonNull(editTop.getText()).toString().trim();
            String pass = Objects.requireNonNull(editBottom.getText()).toString().trim();
            handler.proceed(user, pass);
            dialog.cancel();
        });
    }

    @Override
    public void onSafeBrowsingHit(WebView view, WebResourceRequest request, int threatType, final android.webkit.SafeBrowsingResponse response) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_RESPONSE_BACK_TO_SAFETY)) {
            HelperUnit.showCustomSnackbarWithTwoActions(
                    context, view, null,
                    "Security Warning",
                    "Deceptive or malicious website detected! Back to safety recommended.",
                    ninjaWebView.getUrl(),
                    R.drawable.icon_secure, () -> {
                        response.backToSafety(true);
                        return true;
                    },
                    R.drawable.icon_unsecure, () -> {
                        response.proceed(true);
                        return true;
                    }
            );
        } else {
            response.proceed(true);
        }
    }

    @Override
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        super.onReceivedError(view, errorCode, description, failingUrl);
        if (context instanceof com.petal.browser.activity.BrowserActivity) {
            ((com.petal.browser.activity.BrowserActivity) context).resetRefreshState();
        }
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
        super.onReceivedError(view, request, error);
        if (request != null && request.isForMainFrame() && context instanceof com.petal.browser.activity.BrowserActivity) {
            ((com.petal.browser.activity.BrowserActivity) context).resetRefreshState();
        }
    }
}