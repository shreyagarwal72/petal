package com.petal.browser.view;

import static android.content.ContentValues.TAG;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebBackForwardList;
import android.webkit.WebHistoryItem;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.petal.browser.unit.TabThumbnailCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.preference.PreferenceManager;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import com.petal.browser.R;
import com.petal.browser.browser.AlbumController;
import com.petal.browser.browser.BrowserController;
import com.petal.browser.browser.List_standard;
import com.petal.browser.browser.NinjaDownloadListener;
import com.petal.browser.browser.NinjaWebChromeClient;
import com.petal.browser.browser.NinjaWebViewClient;
import com.petal.browser.browser.WebAppInterface;
import com.petal.browser.database.FaviconHelper;
import com.petal.browser.database.Record;
import com.petal.browser.database.RecordAction;
import com.petal.browser.unit.BrowserUnit;
import com.petal.browser.unit.HelperUnit;

public class NinjaWebView extends NestedScrollWebView implements AlbumController {

    public boolean fingerPrintProtection;
    public boolean history;
    public boolean adBlock;
    public boolean saveData;
    public boolean camera;
    private boolean isIncognito = false;
    private Context context;
    private boolean stopped;
    private AdapterTabs album;
    private AlbumController predecessor = null;
    private NinjaWebViewClient webViewClient;
    private NinjaWebChromeClient webChromeClient;
    private NinjaDownloadListener downloadListener;
    private static String profile;
    private List_standard listStandard;
    private Bitmap favicon;
    private static SharedPreferences sp;
    private boolean foreground;
    public static BrowserController browserController = null;
    public interface OnScrollChangeListener {
        void onScrollDown();
        void onScrollUp();
    }

    private OnScrollChangeListener onScrollChangeListener;

    public void setOnScrollChangeListener(OnScrollChangeListener listener) {
        this.onScrollChangeListener = listener;
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        if (sp != null && sp.getBoolean("sp_background_play", false)) {
            super.onWindowVisibilityChanged(View.VISIBLE);
        } else {
            super.onWindowVisibilityChanged(visibility);
        }
    }

    private int lastScrollHapticY = 0;

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        // See resetGestureExclusionRects(): Chromium re-widens its own exclusion rects
        // during scroll/fling, so keep reclaiming the edges here too - not just on
        // touch-down - or a fast fling can leave the edges blocked again mid-gesture.
        resetGestureExclusionRects();

        // Tactile Scroll Haptics (Inspired by Ever-Haptics)
        if (Math.abs(t - lastScrollHapticY) > 36) {
            lastScrollHapticY = t;
            com.petal.browser.haptics.PetalHapticEngine.getInstance(getContext())
                    .playIfEnabled(getContext(), com.petal.browser.haptics.PetalHapticEngine.Pattern.TICK, 0.45f, 60L);
        }

        if (onScrollChangeListener != null) {
            int dy = t - oldt;
            if (dy > 12) {
                onScrollChangeListener.onScrollDown();
            } else if (dy < -12) {
                onScrollChangeListener.onScrollUp();
            }
        }
    }

    @Override
    protected void onGestureExclusionRefreshNeeded() {
        resetGestureExclusionRects();
    }

    public NinjaWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NinjaWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private String tabId = null;

    public String getTabId() {
        if (tabId == null || tabId.isEmpty()) {
            tabId = "tab_" + System.currentTimeMillis() + "_" + Math.abs(hashCode());
        }
        return tabId;
    }

    public void setTabId(String tabId) {
        if (tabId != null && !tabId.isEmpty()) {
            this.tabId = tabId;
        }
    }

    public NinjaWebView(Context context) {
        super(context);
        this.tabId = "tab_" + System.currentTimeMillis() + "_" + Math.abs(hashCode());
        sp = PreferenceManager.getDefaultSharedPreferences(context);
        String profile = sp.getString("profile", "standard");
        this.context = context;
        this.foreground = false;
        this.fingerPrintProtection = sp.getBoolean("sp_fingerprint_protection", sp.getBoolean(profile + "_fingerPrintProtection", true));
        this.history = sp.getBoolean("sp_history", sp.getBoolean(profile + "_history", true));
        this.adBlock = sp.getBoolean("sp_ad_block", sp.getBoolean(profile + "_adBlock", true));
        this.saveData = sp.getBoolean(profile + "_saveData", false);
        this.camera = sp.getBoolean(profile + "_camera", false);

        this.stopped = false;
        this.listStandard = new List_standard(this.context);
        this.album = new AdapterTabs(this.context, this, browserController);
        this.webViewClient = new NinjaWebViewClient(this);
        this.webChromeClient = new NinjaWebChromeClient(this);
        this.downloadListener = new NinjaDownloadListener(this.context, this);

        initWebView();
        initAlbum();
    }

    private com.petal.browser.media.PetalMediaBridge mediaBridge;

    public com.petal.browser.media.PetalMediaBridge getMediaBridge() {
        return mediaBridge;
    }

    public void setMediaBridge(com.petal.browser.media.PetalMediaBridge mediaBridge) {
        this.mediaBridge = mediaBridge;
    }

    private com.petal.browser.pwa.PetalPwaManager pwaManager;

    public com.petal.browser.pwa.PetalPwaManager getPwaManager() {
        return pwaManager;
    }

    public void setPwaManager(com.petal.browser.pwa.PetalPwaManager pwaManager) {
        this.pwaManager = pwaManager;
    }

    public boolean isForeground() {
        return foreground;
    }

    public static BrowserController getBrowserController() {
        return browserController;
    }

    public void setBrowserController(BrowserController browserController) {
        NinjaWebView.browserController = browserController;
        this.album.setBrowserController(browserController);
    }

    private synchronized void initWebView() {
        try {
            com.petal.browser.engine.ChromiumNativeEngineCore.initialize(context);
        } catch (Exception e) {
            Log.w("ChromiumNativeEngine", "Native engine core initialization bypassed: " + e.getMessage());
        }

        setOverScrollMode(View.OVER_SCROLL_NEVER);
        setWebViewClient(webViewClient);
        setWebChromeClient(webChromeClient);
        setDownloadListener(downloadListener);
        initPreferences(null);

        // Track last touch coordinates in page coordinate space for precise DOM element lookups
        setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                float density = getResources().getDisplayMetrics().density;
                float cssX = event.getX() / density;
                float cssY = event.getY() / density;
                evaluateJavascript("(function(){ window.lastTouchX = " + cssX + "; window.lastTouchY = " + cssY + "; })();", null);
            }
            return false;
        });

        // Wire robust default context menu long-click listener
        setOnLongClickListener(v -> {
            HitTestResult result = getHitTestResult();
            if (result == null) return false;
            int type = result.getType();
            Activity activity = (context instanceof Activity) ? (Activity) context : null;
            if (activity == null && getHostWindow() != null && getHostWindow().getContext() instanceof Activity) {
                activity = (Activity) getHostWindow().getContext();
            }
            if (activity instanceof com.petal.browser.activity.BrowserActivity) {
                com.petal.browser.activity.BrowserActivity browserActivity = (com.petal.browser.activity.BrowserActivity) activity;
                if (type == HitTestResult.IMAGE_TYPE || type == HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                    final String imageURL = result.getExtra();
                    if (imageURL != null && !imageURL.isEmpty()) {
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                        com.petal.browser.compose.menu.BrowserContextMenuManager.showImageContextMenu(browserActivity, imageURL);
                        return true;
                    }
                }
                if (type == HitTestResult.SRC_ANCHOR_TYPE) {
                    final String urlResult = result.getExtra();
                    if (urlResult != null && !urlResult.isEmpty()) {
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                        com.petal.browser.compose.menu.BrowserContextMenuManager.showLinkContextMenu(browserActivity, urlResult);
                        return true;
                    }
                }

                // If HitTestResult is UNKNOWN_TYPE or another element, check if long-press landed on an anchor link / image / video in DOM
                evaluateJavascript(
                    "(function() {" +
                    "   var el = document.elementFromPoint(window.lastTouchX || 0, window.lastTouchY || 0);" +
                    "   if (!el) el = document.activeElement;" +
                    "   if (!el) return '';" +
                    "   var a = el.closest('a');" +
                    "   if (a && a.href) return 'LINK:' + a.href;" +
                    "   var img = (el.tagName === 'IMG') ? el : el.querySelector('img');" +
                    "   if (img && img.src) return 'IMG:' + img.src;" +
                    "   var v = (el.tagName === 'VIDEO') ? el : (el.querySelector('video') || el.closest('video'));" +
                    "   if (v && (v.currentSrc || v.src)) return 'VIDEO:' + (v.currentSrc || v.src);" +
                    "   return '';" +
                    "})();",
                    evalResult -> {
                        if (evalResult != null && !evalResult.equals("null") && !evalResult.isEmpty()) {
                            String clean = evalResult.replace("\"", "").trim();
                            if (clean.startsWith("LINK:")) {
                                String linkUrl = clean.substring(5).trim();
                                if (!linkUrl.isEmpty()) {
                                    v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                                    com.petal.browser.compose.menu.BrowserContextMenuManager.showLinkContextMenu(browserActivity, linkUrl);
                                }
                            } else if (clean.startsWith("IMG:")) {
                                String imgUrl = clean.substring(4).trim();
                                if (!imgUrl.isEmpty()) {
                                    v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                                    com.petal.browser.compose.menu.BrowserContextMenuManager.showImageContextMenu(browserActivity, imgUrl);
                                }
                            } else if (clean.startsWith("VIDEO:")) {
                                String videoUrl = clean.substring(6).trim();
                                if (!videoUrl.isEmpty()) {
                                    v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                                    com.petal.browser.compose.menu.BrowserContextMenuManager.showVideoContextMenu(browserActivity, videoUrl);
                                }
                            }
                        }
                    }
                );
            }
            return false;
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> resetGestureExclusionRects());
            post(this::resetGestureExclusionRects);
        }
    }

    private boolean isExclusionResetPending = false;
    private final Runnable exclusionResetRunnable = () -> {
        isExclusionResetPending = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                java.util.List<android.graphics.Rect> currentRects = getSystemGestureExclusionRects();
                if (currentRects != null && !currentRects.isEmpty()) {
                    setSystemGestureExclusionRects(java.util.Collections.emptyList());
                }
            } catch (Exception ignored) {}
        }
    };

    /**
     * Resets system gesture exclusion rects on API 29+ (Android 10+) so Android's system
     * predictive back edge swipes (left/right display edges) reach OnBackPressedCallback
     * and trigger back navigation instead of being swallowed by WebView's default auto-exclusion.
     */
    public void resetGestureExclusionRects() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!isExclusionResetPending) {
                isExclusionResetPending = true;
                post(exclusionResetRunnable);
            }
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    public synchronized void initPreferences(String url) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                this.setRendererPriorityPolicy(RENDERER_PRIORITY_IMPORTANT, false);
            } catch (Exception ignored) {}
        }
        // Let Chromium handle the window/canvas background naturally without forced opaque paint

        WebSettings webSettings = getSettings();
        com.petal.browser.flags.ChromeFlagsManager.applyFlagsToWebSettings(context, webSettings);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            webSettings.setOffscreenPreRaster(true);
        }
        webSettings.setDefaultTextEncodingName("utf-8");
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setLoadsImagesAutomatically(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);

        // Async Safe Browsing initialization to eliminate main thread blocking latency
        if (!isIncognito && WebViewFeature.isFeatureSupported(WebViewFeature.START_SAFE_BROWSING)) {
            new Thread(() -> {
                try {
                    androidx.webkit.WebViewCompat.startSafeBrowsing(context.getApplicationContext(), value -> {});
                } catch (Exception ignored) {}
            }).start();
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            try {
                WebSettingsCompat.setSafeBrowsingEnabled(webSettings, true);
            } catch (Exception ignored) {}
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webSettings.setSafeBrowsingEnabled(true);
        }

        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);

        setVerticalScrollBarEnabled(true);
        setHorizontalScrollBarEnabled(true);
        setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        setScrollbarFadingEnabled(false);

        webSettings.setSupportMultipleWindows(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        String fontSizeStr = HelperUnit.getSafeString(sp, "sp_fontSize", "100");
        try {
            webSettings.setTextZoom(Integer.parseInt(fontSizeStr));
        } catch (Exception e) {
            webSettings.setTextZoom(100);
        }

        profile = HelperUnit.getSafeString(sp, "profile", "profileStandard");
        String profileOriginal = profile;

        if (listStandard.isWhite(url)) {
            profile = HelperUnit.domain(url);
        }

        // Ensure algorithmic darkening is explicitly turned off for web content across all SDK levels
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webSettings, false);
        }

        String desktopUserAgent = getDerivedDesktopUserAgent(context);
        String rawDefaultUa = WebSettings.getDefaultUserAgent(context);
        // Replace custom webview tokens (e.g. wv or custom app signatures) with standard Mobile Chrome user agent for Google login compatibility
        String googleLoginMobileUa = rawDefaultUa != null ? rawDefaultUa.replaceAll("; wv\\)", ")").replaceAll(" Version/\\d+\\.\\d+", "") : "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36";
        String mobileUserAgent = googleLoginMobileUa;

        //Override UserAgent if own UserAgent is defined
        if (!sp.contains("userAgentSwitch")) {
            //if new switch_text_preference has never been used initialize the switch
            if (HelperUnit.getSafeString(sp, "sp_userAgent", "").isEmpty()) {
                sp.edit().putBoolean("userAgentSwitch", false).apply();
            } else sp.edit().putBoolean("userAgentSwitch", true).apply();
        }

        String ownUserAgent = HelperUnit.getSafeString(sp, "sp_userAgent", "");
        if (!ownUserAgent.isEmpty() && (HelperUnit.getSafeBoolean(sp, "userAgentSwitch", false))) mobileUserAgent = ownUserAgent;

        if (sp.getBoolean(profile + "_desktop", false) || sp.getBoolean("sp_desktop_site", false)) {
            webSettings.setUserAgentString(desktopUserAgent);
            webSettings.setUseWideViewPort(true);
            webSettings.setLoadWithOverviewMode(true);
            this.setInitialScale(0);
        } else {
            webSettings.setUserAgentString(mobileUserAgent);
            webSettings.setUseWideViewPort(true);
            webSettings.setLoadWithOverviewMode(true);
            this.setInitialScale(0);
        }

        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);

        com.petal.browser.unit.BrowsingDataManager.configureWebSettings(this, isIncognito);

        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        boolean backgroundPlay = sp.getBoolean("sp_background_play", false);
        webSettings.setMediaPlaybackRequiresUserGesture(backgroundPlay ? false : sp.getBoolean(profile + "_saveData", true));
        webSettings.setBlockNetworkImage(!sp.getBoolean(profile + "_images", true));
        webSettings.setGeolocationEnabled(sp.getBoolean(profile + "_location", false));

        boolean isForceDark = sp.getBoolean("sp_force_dark_mode", false) || sp.getBoolean("sp_dark_mode", false);
        if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.ALGORITHMIC_DARKENING)) {
            androidx.webkit.WebSettingsCompat.setAlgorithmicDarkeningAllowed(webSettings, isForceDark);
        }
        if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.FORCE_DARK)) {
            @SuppressWarnings("deprecation")
            int forceDarkState = isForceDark ? androidx.webkit.WebSettingsCompat.FORCE_DARK_ON : androidx.webkit.WebSettingsCompat.FORCE_DARK_OFF;
            androidx.webkit.WebSettingsCompat.setForceDark(webSettings, forceDarkState);
        }
        if (isForceDark && androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.FORCE_DARK_STRATEGY)) {
            androidx.webkit.WebSettingsCompat.setForceDarkStrategy(webSettings, androidx.webkit.WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING);
        }

        boolean enableJs = sp.getBoolean("sp_javascript", sp.getBoolean(profile + "_javascript", true));
        webSettings.setJavaScriptEnabled(enableJs);

        boolean blockPopups = sp.getBoolean("sp_block_popups", true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(!blockPopups);

        float fontScale = sp.getFloat("sp_font_size_scale", 1.0f);
        float zoomScale = sp.getFloat("sp_zoom_level_scale", 1.0f);
        com.petal.browser.accessibility.PetalAccessibilityEngine.applyZoomToWebView(this, url);

        fingerPrintProtection = sp.getBoolean("sp_fingerprint_protection", sp.getBoolean(profile + "_fingerPrintProtection", true));
        history = sp.getBoolean("sp_history", sp.getBoolean(profile + "_saveHistory", true));
        adBlock = sp.getBoolean("sp_ad_block", sp.getBoolean(profile + "_adBlock", true));
        saveData = sp.getBoolean(profile + "_saveData", true);
        camera = sp.getBoolean(profile + "_camera", true);

        try {
            CookieManager manager = CookieManager.getInstance();
            boolean blockThirdParty = sp.getBoolean("sp_block_third_party_cookies", false);
            boolean globalSso = sp.getBoolean("sp_global_google_login", true);
            boolean acceptCookies = globalSso || sp.getBoolean(profile + "_cookies", true);
            boolean acceptThirdParty = !blockThirdParty && (globalSso || sp.getBoolean(profile + "_cookiesThirdParty", true));

            manager.setAcceptCookie(acceptCookies);
            manager.setAcceptThirdPartyCookies(this, acceptThirdParty);
            if (acceptCookies) {
                manager.getCookie(url);
            }
        } catch (Exception e) {
            Log.i(TAG, "Error loading cookies:" + e);
        }
        this.addJavascriptInterface(new WebAppInterface(context), "AndroidInterface");
        this.addJavascriptInterface(
            new com.petal.browser.passkey.PetalWebAuthnBridge.WebAuthnJavascriptInterface(this),
            "PetalWebAuthn"
        );
        this.addJavascriptInterface(
            new com.petal.browser.accessibility.PetalAccessibilityEngine.AccessibilityJavascriptInterface(title -> {
                if (context instanceof com.petal.browser.activity.BrowserActivity) {
                    ((com.petal.browser.activity.BrowserActivity) context).runOnUiThread(() -> {
                        // Reader mode available
                    });
                }
                return kotlin.Unit.INSTANCE;
            }),
            com.petal.browser.accessibility.PetalAccessibilityEngine.JS_BRIDGE_NAME
        );

        try {
            if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)) {
                androidx.webkit.WebViewCompat.addDocumentStartJavaScript(
                    this,
                    com.petal.browser.passkey.PetalWebAuthnBridge.WEBAUTHN_POLYFILL_JS,
                    java.util.Collections.singleton("*")
                );
            }
        } catch (Throwable t) {
            Log.w(TAG, "Document start script not supported: " + t.getMessage());
        }

        profile = profileOriginal;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (com.petal.browser.accessibility.PetalAccessibilityEngine.handleGenericMotion(this, event)) {
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    public static String getDerivedDesktopUserAgent(Context context) {
        String rawDefaultUa = WebSettings.getDefaultUserAgent(context);
        if (rawDefaultUa == null) {
            return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
        }
        String cleaned = rawDefaultUa.replaceAll("; wv\\)", ")").replaceAll(" Version/\\d+\\.\\d+", "");
        return cleaned.replaceAll("\\(Linux; U; Android[^;)]+;[^)]+\\)|\\(Linux; Android[^)]+\\)", "(X11; Linux x86_64)").replace(" Mobile ", " ");
    }

    public void setDesktopMode(boolean enabled) {
        String desktopUserAgent = getDerivedDesktopUserAgent(context);
        String rawDefaultUa = WebSettings.getDefaultUserAgent(context);
        String googleLoginMobileUa = rawDefaultUa != null ? rawDefaultUa.replaceAll("; wv\\)", ")").replaceAll(" Version/\\d+\\.\\d+", "") : "";
        String mobileUserAgent = sp.getString("sp_userAgent", "");
        if (mobileUserAgent.isEmpty() || !sp.getBoolean("userAgentSwitch", false)) {
            mobileUserAgent = googleLoginMobileUa;
        }

        if (enabled) {
            getSettings().setUserAgentString(desktopUserAgent);
            getSettings().setUseWideViewPort(true);
            getSettings().setLoadWithOverviewMode(true);
            setInitialScale(0);
        } else {
            getSettings().setUserAgentString(mobileUserAgent);
            getSettings().setUseWideViewPort(true);
            getSettings().setLoadWithOverviewMode(true);
            setInitialScale(0);
        }
        reload();
    }

    public void setProfileDefaultValues() {

        RecordAction action = new RecordAction(context);
        action.open(true);
        action.addBookmark(new Record("Petal Start", "about:blank", 0, 0));
        action.addBookmark(new Record("DuckDuckGo", "https://duckduckgo.com", 0, 0));
        action.addBookmark(new Record("Google", "https://www.google.com", 0, 0));
        action.addBookmark(new Record("Wikipedia", "https://www.wikipedia.org", 0, 0));
        action.addBookmark(new Record("YouTube", "https://www.youtube.com", 0, 0));
        action.addBookmark(new Record("GitHub", "https://github.com", 0, 0));
        action.close();

        sp.edit()
                .putBoolean("profileStandard_saveData", true)
                .putBoolean("profileStandard_images", true)
                .putBoolean("profileStandard_adBlock", true)
                .putBoolean("profileStandard_trackingULS", false)
                .putBoolean("profileStandard_location", false)
                .putBoolean("profileStandard_fingerPrintProtection", false)
                .putBoolean("profileStandard_cookies", true)
                .putBoolean("profileStandard_cookiesThirdParty", false)
                .putBoolean("profileStandard_deny_cookie_banners", true)
                .putBoolean("profileStandard_javascript", true)
                .putBoolean("profileStandard_javascriptPopUp", false)
                .putBoolean("profileStandard_saveHistory", true)
                .putBoolean("profileStandard_camera", false)
                .putBoolean("profileStandard_microphone", false)
                .putBoolean("profileStandard_dom", true)
                .putBoolean("profileStandard_night", true)
                .putBoolean("profileStandard_desktop", false).apply();
    }

    public void setProfileChanged () {
        sp.edit()
                .putString("profile", "profileChanged")
                .putBoolean("profileChanged_saveData", sp.getBoolean( "profileStandard_saveData", true))
                .putBoolean("profileChanged_images", sp.getBoolean( "profileStandard_images", true))
                .putBoolean("profileChanged_adBlock", sp.getBoolean( "profileStandard_adBlock", true))
                .putBoolean("profileChanged_trackingULS", sp.getBoolean( "profileStandard_trackingULS", true))
                .putBoolean("profileChanged_location", sp.getBoolean( "profileStandard_location", false))
                .putBoolean("profileChanged_fingerPrintProtection", sp.getBoolean( "profileStandard_fingerPrintProtection", true))
                .putBoolean("profileChanged_cookies", sp.getBoolean( "_cookies", false))
                .putBoolean("profileChanged_cookiesThirdParty", sp.getBoolean( "profileStandard_cookiesThirdParty", false))
                .putBoolean("profileChanged_deny_cookie_banners", sp.getBoolean( "profileStandard_deny_cookie_banners", false))
                .putBoolean("profileChanged_javascript", sp.getBoolean( "profileStandard_javascript", true))
                .putBoolean("profileChanged_javascriptPopUp", sp.getBoolean( "profileStandard_javascriptPopUp", false))
                .putBoolean("profileChanged_saveHistory", sp.getBoolean( "profileStandard_saveHistory", true))
                .putBoolean("profileChanged_camera", sp.getBoolean( "profileStandard_camera", false))
                .putBoolean("profileChanged_microphone", sp.getBoolean( "profileStandard_microphone", false))
                .putBoolean("profileChanged_dom", sp.getBoolean( "profileStandard_dom", true))
                .putBoolean("profileChanged_night", sp.getBoolean( "profileStandard_night", true))
                .putBoolean("profileChanged_desktop", sp.getBoolean( "profileStandard_desktop", false)).apply();
    }

    private synchronized void initAlbum() {
        album.setBrowserController(browserController);
    }

    public synchronized HashMap<String, String> getRequestHeaders() {
        HashMap<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put("DNT", "1");
        requestHeaders.put("Sec-GPC", "1");
        if (getUrl() != null && !getUrl().isEmpty() && !getUrl().equals("about:blank")) {
            requestHeaders.put("Referer", getUrl());
        }
        profile = sp.getString("profile", "profileStandard");
        if (sp.getBoolean(profile + "_saveData", true)) requestHeaders.put("Save-Data", "on");
        return requestHeaders;
    }

    @Override
    public synchronized void stopLoading() {
        stopped = true;
        super.stopLoading();
    }

    public synchronized void reloadWithoutInit() {  //needed for camera usage without deactivating "save_data"
        stopped = false;
        super.reload();
    }

    public synchronized void goBack() {
        try {
            WebBackForwardList mWebBackForwardList = this.copyBackForwardList();
            if (mWebBackForwardList != null && mWebBackForwardList.getCurrentIndex() > 0) {
                stopLoading();
                int prevIndex = mWebBackForwardList.getCurrentIndex() - 1;
                if (prevIndex >= 0 && prevIndex < mWebBackForwardList.getSize()) {
                    WebHistoryItem item = mWebBackForwardList.getItemAtIndex(prevIndex);
                    if (item != null && item.getUrl() != null) {
                        String historyUrl = item.getUrl();
                        initPreferences(historyUrl);
                        if (!Objects.equals(HelperUnit.domain(this.getUrl()), HelperUnit.domain(historyUrl)) && sp.getBoolean("sp_standard_always", true)) {
                            sp.edit().putString("profile", "profileStandard").apply();
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        try {
            super.goBack();
        } catch (Exception ignored) {}
        resetGestureExclusionRects();
    }

    @Override
    public synchronized void goBackOrForward(int steps) {
        try {
            WebBackForwardList mWebBackForwardList = this.copyBackForwardList();
            if (mWebBackForwardList != null) {
                int targetIndex = mWebBackForwardList.getCurrentIndex() + steps;
                if (targetIndex >= 0 && targetIndex < mWebBackForwardList.getSize()) {
                    stopLoading();
                    WebHistoryItem item = mWebBackForwardList.getItemAtIndex(targetIndex);
                    if (item != null && item.getUrl() != null) {
                        String historyUrl = item.getUrl();
                        initPreferences(historyUrl);
                        if (!Objects.equals(HelperUnit.domain(this.getUrl()), HelperUnit.domain(historyUrl)) && sp.getBoolean("sp_standard_always", true)) {
                            sp.edit().putString("profile", "profileStandard").apply();
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        try {
            super.goBackOrForward(steps);
        } catch (Exception ignored) {}
        resetGestureExclusionRects();
    }

    public synchronized void initWebSettings() {
        this.initPreferences(this.getUrl());
    }

    @Override
    public synchronized void reload() {
        stopped = false;
        this.initPreferences(this.getUrl());
        super.reload();
    }

    @Override
    public synchronized void loadUrl(@NonNull String url) {

        InputMethodManager imm = (InputMethodManager) this.context.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(this.getWindowToken(), 0);

        if (url.contains(";jsessionid=")) {
            String tracking = url.substring(url.lastIndexOf(";"));
            url = url.replace(tracking, "");
        }

        String urlToLoad = BrowserUnit.redirectURL( this, sp, url);

        if (!Objects.equals(HelperUnit.domain(this.getUrl()), HelperUnit.domain(urlToLoad)) && sp.getBoolean("sp_standard_always", true)) {
            sp.edit().putString("profile", "profileStandard").apply();
        }

        favicon = null;
        stopped = false;

        listStandard = new List_standard(context);
        profile = sp.getString("profile", "profileStandard");
        if (listStandard.isWhite(url)) profile = HelperUnit.domain(urlToLoad);

        String targetUrl = BrowserUnit.queryWrapper(context, urlToLoad);

        if (com.petal.browser.flags.ChromeFlagsManager.isFlagsUrl(targetUrl) || com.petal.browser.flags.ChromeFlagsManager.isFlagsUrl(url)) {
            if (context instanceof androidx.activity.ComponentActivity) {
                com.petal.browser.flags.PetalChromeFlagsBridge.showFlags((androidx.activity.ComponentActivity) context, null);
            }
            return;
        }

        if (com.petal.browser.extensions.PetalExtensionManager.isExtensionsUrl(targetUrl) || com.petal.browser.extensions.PetalExtensionManager.isExtensionsUrl(url)) {
            if (context instanceof androidx.activity.ComponentActivity) {
                com.petal.browser.extensions.PetalExtensionsBridge.showExtensions((androidx.activity.ComponentActivity) context);
            }
            return;
        }

        initPreferences(targetUrl);
        resetGestureExclusionRects();
        super.loadUrl(targetUrl);
    }

    @Override
    public View getAlbumView() {
        return album.getAlbumView();
    }

    public void setAlbumTitle(String title, String url) {
        album.setAlbumTitle(title, url);
    }

    /** Returns the URL currently displayed for this tab's row in the tab switcher/list. */
    public String getAlbumUrl() {
        Object url = album.getUrl();
        return url != null ? url.toString() : null;
    }

    @Override
    public synchronized void activate() {
        requestFocus();
        foreground = true;
        album.activate();
        try {
            onResume();
            resumeTimers();
        } catch (Exception ignored) {}
    }

    @Override
    public synchronized void deactivate() {
        clearFocus();
        foreground = false;
        album.deactivate();
        updatePreviewCache();
        try {
            onPause();
        } catch (Exception ignored) {}
    }

    public synchronized void updateTitle(int progress) {
        if (browserController == null) return;
        if (foreground && !stopped) browserController.updateProgress(progress);
        else if (foreground) browserController.updateProgress(BrowserUnit.LOADING_STOPPED);
    }

    public synchronized void updateTitle(String title, String url) {
        album.setAlbumTitle(title, url);
    }

    public synchronized void updateFavicon(String url) {
        FaviconHelper.setFavicon(context, album.getAlbumView(), url, R.id.item_icon, R.drawable.icon_image_broken);
    }

    @Override
    public synchronized void destroy() {
        stopLoading();
        onPause();
        clearHistory();
        setVisibility(GONE);
        removeAllViews();
        if (isIncognito) {
            try {
                clearCache(true);
                clearFormData();
                clearSslPreferences();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        super.destroy();
    }

    public boolean isFingerPrintProtection() {
        return fingerPrintProtection;
    }

    public boolean isHistory() {
        return history;
    }

    public boolean isAdBlock() {
        return adBlock;
    }

    public boolean isSaveData() {
        return saveData;
    }

    public boolean isCamera() {
        return camera;
    }

    public void resetFavicon() {
        this.favicon = null;
    }

    @Nullable
    @Override
    public Bitmap getFavicon() {
        return favicon;
    }

    public void setFavicon(Bitmap favicon) {
        // Always keep the in-memory favicon so it still renders in the tab UI during an
        // incognito session - only the disk write below is a privacy concern.
        this.favicon = favicon;
        if (isIncognito) {
            // Zero disk logging for incognito: persisting favicons here would leave a
            // trace of every site visited in a private tab, the same class of leak the
            // history-write path already guards against in NinjaWebViewClient.
            return;
        }
        FaviconHelper faviconHelper = new FaviconHelper(context);
        RecordAction action = new RecordAction(context);
        action.open(false);
        action.close();
        faviconHelper.addFavicon(this.context, getUrl(), getFavicon());
    }

    @Nullable
    public Bitmap capturePreviewBitmap() {
        try {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                w = getMeasuredWidth();
                h = getMeasuredHeight();
            }
            if (w <= 0 || h <= 0) return null;
            // Downscale capture for memory-efficient tab grid thumbnail previewing
            int targetWidth = Math.min(w, 480);
            int targetHeight = Math.max(1, (int) ((float) h * targetWidth / w));
            Bitmap bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
            float scaleX = (float) targetWidth / w;
            float scaleY = (float) targetHeight / h;
            canvas.scale(scaleX, scaleY);
            draw(canvas);
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Unique key for tab thumbnail caching, strictly scoped to this tab instance.
     */
    public String getThumbnailKey() {
        return getTabId();
    }

    public void capturePreviewBitmapAsync(@NonNull java.util.function.Consumer<Bitmap> callback) {
        final String thumbnailKey = getThumbnailKey();
        final String currentUrl = getUrl();
        java.util.function.Consumer<Bitmap> cachingCallback = bitmap -> {
            if (bitmap != null) {
                TabThumbnailCache.put(thumbnailKey, bitmap);
                if (currentUrl != null && !currentUrl.isEmpty() && !"about:blank".equalsIgnoreCase(currentUrl)) {
                    TabThumbnailCache.put(currentUrl, bitmap);
                }
            }
            callback.accept(bitmap);
        };

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            w = getMeasuredWidth();
            h = getMeasuredHeight();
        }
        if (w <= 0 || h <= 0) {
            cachingCallback.accept(null);
            return;
        }

        Window window = getHostWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && window != null && isAttachedToWindow()
                && getVisibility() == View.VISIBLE) {
            try {
                int[] location = new int[2];
                getLocationInWindow(location);
                Rect srcRect = new Rect(location[0], location[1], location[0] + w, location[1] + h);

                int targetWidth = Math.min(w, 480);
                int targetHeight = Math.max(1, (int) ((float) h * targetWidth / w));
                final Bitmap bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
                PixelCopy.request(
                        window,
                        srcRect,
                        bitmap,
                        copyResult -> {
                            if (copyResult == PixelCopy.SUCCESS) {
                                cachingCallback.accept(bitmap);
                            } else {
                                cachingCallback.accept(capturePreviewBitmap());
                            }
                        },
                        new Handler(Looper.getMainLooper())
                );
            } catch (Exception e) {
                cachingCallback.accept(capturePreviewBitmap());
            }
        } else {
            cachingCallback.accept(capturePreviewBitmap());
        }
    }

    /**
     * Fire-and-forget capture used purely to keep {@link TabThumbnailCache} warm - called
     * from page-load-finished and tab-switch hook points so the tab manager's grid always
     * has a recent thumbnail ready without the switcher itself needing to trigger a capture
     * the moment it opens.
     */
    public void updatePreviewCache() {
        capturePreviewBitmapAsync(bitmap -> { /* cache write already happened above */ });
    }

    /** Returns the most recently cached preview thumbnail for this tab, if any. */
    @Nullable
    public Bitmap getCachedPreviewBitmap() {
        Bitmap bitmap = TabThumbnailCache.get(getThumbnailKey());
        if (bitmap != null && !bitmap.isRecycled()) {
            return bitmap;
        }
        String url = getUrl();
        if (url != null && !url.isEmpty() && !"about:blank".equalsIgnoreCase(url)) {
            bitmap = TabThumbnailCache.get(url);
            if (bitmap != null && !bitmap.isRecycled()) {
                return bitmap;
            }
        }
        return null;
    }

    /**
     * URL of the history entry that {@code goBack()} would land on right now, or {@code null}
     * if there isn't one.
     */
    @Nullable
    public String getBackHistoryUrl() {
        try {
            WebBackForwardList list = copyBackForwardList();
            int targetIndex = list.getCurrentIndex() - 1;
            if (targetIndex < 0) return null;
            WebHistoryItem item = list.getItemAtIndex(targetIndex);
            return item != null ? item.getUrl() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Real, live screenshot of the page a back-navigation would reveal, sourced from
     * {@link TabThumbnailCache} - the same URL-keyed cache {@link #updatePreviewCache()} already
     * fills in on every page-finished/tab-switch. Returns {@code null} (never a placeholder)
     * when that page hasn't been visited/cached yet, so callers can fall back honestly instead
     * of showing a fake preview.
     */
    @Nullable
    public Bitmap getBackPreviewBitmap() {
        String url = getBackHistoryUrl();
        if (url == null || url.isEmpty()) return null;
        Bitmap bitmap = TabThumbnailCache.get(url);
        if (bitmap != null && !bitmap.isRecycled()) {
            return bitmap;
        }
        return null;
    }

    /**
     * Walks up the Context chain to find the {@link Window} of the hosting {@link Activity},
     * unwrapping any {@code ContextWrapper} layers (e.g. from theming libraries). Returns
     * {@code null} if this view isn't hosted in an Activity (e.g. a detached or preview context).
     */
    @Nullable
    private Window getHostWindow() {
        Context context = getContext();
        while (context instanceof android.content.ContextWrapper) {
            if (context instanceof Activity) {
                return ((Activity) context).getWindow();
            }
            context = ((android.content.ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    public void setStopped(boolean stopped) {
        this.stopped = stopped;
    }

    public static String getProfile() {
        return sp.getString("profile", "profileStandard");
    }

    public AlbumController getPredecessor() {
        return predecessor;
    }

    public void setPredecessor(AlbumController predecessor) {
        this.predecessor = predecessor;
    }

    public boolean isIncognito() {
        return isIncognito;
    }

    private String tabGroupId = null;
    private String tabGroupTitle = null;

    public String getTabGroupId() {
        return tabGroupId;
    }

    public void setTabGroupId(String tabGroupId) {
        this.tabGroupId = tabGroupId;
    }

    public String getTabGroupTitle() {
        return tabGroupTitle;
    }

    public void setTabGroupTitle(String tabGroupTitle) {
        this.tabGroupTitle = tabGroupTitle;
    }

    public void setIncognito(boolean incognito) {
        this.isIncognito = incognito;
        if (incognito) {
            this.history = false;
            try {
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false);
            } catch (Exception ignored) {}
        }
        com.petal.browser.unit.BrowsingDataManager.configureWebSettings(this, incognito);
    }
}