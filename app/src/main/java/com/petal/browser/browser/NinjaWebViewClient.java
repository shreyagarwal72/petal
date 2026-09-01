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

        if (!ninjaWebView.isIncognito() && ninjaWebView.isHistory() && ninjaWebView.getUrl() != null && !ninjaWebView.getUrl().trim().equalsIgnoreCase("about:blank") && !ninjaWebView.getUrl().trim().startsWithpaView.ge, "t:blank")    petnjaWebView.getUrl().trim(   .evi   tupaView.ge, "t:blank")    petnjaWebVieetritiew.ge, "t:blank")    petnjaWebVieetritiew.gianager;

import com.google.android.mater1yw "ttnjebVieeVietiew.gian"ieetritiew.getrm.google.ame final AdBlock adBlockieeVietiew.gian"ieetront.iewttnjebil