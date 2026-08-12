package com.petal.browser.view;

import static android.content.ContentValues.TAG;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebBackForwardList;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;

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

public class NinjaWebView extends WebView implements AlbumController {

    public boolean fingerPrintProtection;
    public boolean history;
    public boolean adBlock;
    public boolean saveData;
    public boolean camera;
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
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (onScrollChangeListener != null) {
            int dy = t - oldt;
            if (dy > 12) {
                onScrollChangeListener.onScrollDown();
            } else if (dy < -12) {
                onScrollChangeListener.onScrollUp();
            }
        }
    }

    public NinjaWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NinjaWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public NinjaWebView(Context context) {
        super(context);
        sp = PreferenceManager.getDefaultSharedPreferences(context);
        String profile = sp.getString("profile", "standard");
        this.context = context;
        this.foreground = false;
        this.fingerPrintProtection = sp.getBoolean(profile + "_fingerPrintProtection", true);
        this.history = sp.getBoolean(profile + "_history", true);
        this.adBlock = sp.getBoolean(profile + "_adBlock", false);
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
        setWebViewClient(webViewClient);
        setWebChromeClient(webChromeClient);
        setDownloadListener(downloadListener);
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    public synchronized void initPreferences(String url) {

        this.setRendererPriorityPolicy(RENDERER_PRIORITY_IMPORTANT, true);
        WebSettings webSettings = getSettings();
        webSettings.setDefaultTextEncodingName("utf-8");
        webSettings.setSafeBrowsingEnabled(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);

        setVerticalScrollBarEnabled(true);
        setHorizontalScrollBarEnabled(true);
        setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        setScrollbarFadingEnabled(false);

        webSettings.setSupportMultipleWindows(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setTextZoom(Integer.parseInt(Objects.requireNonNull(sp.getString("sp_fontSize", "100"))));

        profile = sp.getString("profile", "profileStandard");
        String profileOriginal = profile;

        if (listStandard.isWhite(url)) {
            profile = HelperUnit.domain(url);
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webSettings, sp.getBoolean(profile + "_night", true));
        }

        String desktopUserAgent = "Mozilla/5.0 (X11; Linux " + System.getProperty("os.arch") + ")";
        String mobileUserAgent = WebSettings.getDefaultUserAgent(context);

        //Override UserAgent if own UserAgent is defined
        if (!sp.contains("userAgentSwitch")) {
            //if new switch_text_preference has never been used initialize the switch
            if (Objects.requireNonNull(sp.getString("sp_userAgent", "")).isEmpty()) {
                sp.edit().putBoolean("userAgentSwitch", false).apply();
            } else sp.edit().putBoolean("userAgentSwitch", true).apply();
        }

        String ownUserAgent = sp.getString("sp_userAgent", "");
        if (!ownUserAgent.isEmpty() && (sp.getBoolean("userAgentSwitch", false))) mobileUserAgent = ownUserAgent;

        if (sp.getBoolean(profile + "_desktop", false)) {
            webSettings.setUserAgentString(desktopUserAgent);
            getSettings().setUseWideViewPort(true);
            getSettings().setLoadWithOverviewMode(true);
            this.setInitialScale(100);
        } else {
            webSettings.setUserAgentString(mobileUserAgent);
            getSettings().setUseWideViewPort(false);
            getSettings().setLoadWithOverviewMode(false);
            this.setInitialScale(0);
        }

        webSettings.setDomStorageEnabled(sp.getBoolean(profile + "_dom", false));

        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        webSettings.setMediaPlaybackRequiresUserGesture(sp.getBoolean(profile + "_saveData", true));
        webSettings.setBlockNetworkImage(!sp.getBoolean(profile + "_images", true));
        webSettings.setGeolocationEnabled(sp.getBoolean(profile + "_location", false));
        webSettings.setJavaScriptEnabled(sp.getBoolean(profile + "_javascript", true));
        webSettings.setJavaScriptCanOpenWindowsAutomatically(sp.getBoolean(profile + "_javascriptPopUp", false));

        fingerPrintProtection = sp.getBoolean(profile + "_fingerPrintProtection", false);
        history = sp.getBoolean(profile + "_saveHistory", true);
        adBlock = sp.getBoolean(profile + "_adBlock", true);
        saveData = sp.getBoolean(profile + "_saveData", true);
        camera = sp.getBoolean(profile + "_camera", true);

        try {
            CookieManager manager = CookieManager.getInstance();
            if (sp.getBoolean(profile + "_cookies", true)) {
                manager.setAcceptCookie(true);
                manager.getCookie(url);
            } else manager.setAcceptCookie(false);
            if (sp.getBoolean(profile + "_cookiesThirdParty", false)) {
                manager.setAcceptThirdPartyCookies(this, true);
                manager.getCookie(url);
            } else manager.setAcceptThirdPartyCookies(this, false);
        } catch (Exception e) {
            Log.i(TAG, "Error loading cookies:" + e);
        }
        this.addJavascriptInterface(new WebAppInterface(context), "AndroidInterface");

        profile = profileOriginal;
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
        //  Server-side detection for GlobalPrivacyControl
        requestHeaders.put("Sec-GPC", "1");
        requestHeaders.put("X-Requested-With", "com.duckduckgo.mobile.android");
        requestHeaders.put("Referer", this.getUrl());
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
        WebBackForwardList mWebBackForwardList = this.copyBackForwardList();
        if (mWebBackForwardList.getCurrentIndex() > 0) {
            stopLoading();
            String historyUrl = mWebBackForwardList.getItemAtIndex(mWebBackForwardList.getCurrentIndex()-1).getUrl();
            initPreferences(historyUrl);
            if (!Objects.equals(HelperUnit.domain(this.getUrl()), HelperUnit.domain(historyUrl)) && sp.getBoolean("sp_standard_always", true)) {
                sp.edit().putString("profile", "profileStandard").apply();
            }
        }
        super.goBack();
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
        initPreferences(targetUrl);
        super.loadUrl(targetUrl, getRequestHeaders());
    }

    @Override
    public View getAlbumView() {
        return album.getAlbumView();
    }

    public void setAlbumTitle(String title, String url) {
        album.setAlbumTitle(title, url);
    }

    @Override
    public synchronized void activate() {
        requestFocus();
        foreground = true;
        album.activate();
    }

    @Override
    public synchronized void deactivate() {
        clearFocus();
        foreground = false;
        album.deactivate();
    }

    public synchronized void updateTitle(int progress) {
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
        this.favicon = favicon;
        FaviconHelper faviconHelper = new FaviconHelper(context);
        RecordAction action = new RecordAction(context);
        action.open(false);
        action.close();
        faviconHelper.addFavicon(this.context, getUrl(), getFavicon());
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
}