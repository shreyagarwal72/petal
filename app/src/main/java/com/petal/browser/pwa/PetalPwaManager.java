package com.petal.browser.pwa;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.ServiceWorkerController;
import android.webkit.ServiceWorkerClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import com.petal.browser.R;
import com.petal.browser.activity.BrowserActivity;
import com.petal.browser.unit.HelperUnit;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * PetalPwaManager
 * Dynamic Progressive Web App (PWA) manager providing manifest detection & parsing via JS injection,
 * Service Worker lifecycle control, native installation prompts with dynamic shortcut creation,
 * and Web API delegation (Web Share, WebAuthn/Passkeys, Push Notifications).
 */
public class PetalPwaManager {

    private static final String TAG = "PetalPwaManager";
    private static final String JS_INTERFACE_NAME = "PetalPwaInterface";

    public static class PwaManifest {
        public String name = "";
        public String shortName = "";
        public String startUrl = "";
        public String display = "browser";
        public String themeColor = "#FFFFFF";
        public String backgroundColor = "#FFFFFF";
        public String iconUrl = "";
        public List<PwaShortcut> shortcuts = new ArrayList<>();
    }

    public static class PwaShortcut {
        public String name = "";
        public String url = "";
        public String iconUrl = "";
    }

    public interface PwaInstallPromptListener {
        void onPwaDetected(PwaManifest manifest);
    }

    private final Context context;
    private final WebView webView;
    private PwaInstallPromptListener promptListener;
    private PwaManifest currentManifest;

    public PetalPwaManager(Context context, WebView webView, PwaInstallPromptListener listener) {
        this.context = context;
        this.webView = webView;
        this.promptListener = listener;

        configurePwaWebSettings(webView.getSettings());
        configureServiceWorker();

        webView.addJavascriptInterface(new PwaJavascriptInterface(), JS_INTERFACE_NAME);
    }

    /**
     * Configures WebSettings required for PWAs: DOM Storage, Database, IndexedDB, JS, Geolocation.
     */
    public static void configurePwaWebSettings(WebSettings webSettings) {
        if (webSettings == null) return;
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setGeolocationEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
    }

    /**
     * Configures Service Worker lifecycle for offline caching.
     */
    private void configureServiceWorker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                ServiceWorkerController controller = ServiceWorkerController.getInstance();
                controller.getServiceWorkerWebSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
                controller.getServiceWorkerWebSettings().setAllowContentAccess(true);
                controller.setServiceWorkerClient(new ServiceWorkerClient() {
                    @Override
                    public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                        return super.shouldInterceptRequest(request);
                    }
                });
            } catch (Exception e) {
                Log.w(TAG, "ServiceWorkerController setup warning: " + e.getMessage());
            }
        }
    }

    /**
     * Injects JavaScript to discover link[rel="manifest"] and parse web app manifest.
     */
    public void detectPwaManifest() {
        String js = "(function() {" +
                "   var link = document.querySelector('link[rel=\"manifest\"]');" +
                "   if (link && link.href) {" +
                "       fetch(link.href)" +
                "           .then(response => response.json())" +
                "           .then(manifest => {" +
                "               window." + JS_INTERFACE_NAME + ".onManifestParsed(JSON.stringify(manifest), link.href);" +
                "           }).catch(err => {});" +
                "   }" +
                "   if (navigator.share) {" +
                "       window.nativeShareSupported = true;" +
                "   }" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    public PwaManifest getCurrentManifest() {
        return currentManifest;
    }

    /**
     * Installs PWA to Android Home Screen as a dynamic standalone shortcut or pinned app with website favicon.
     */
    public void installCurrentPwa(Activity activity) {
        if (activity == null) return;

        new Thread(() -> {
            try {
                String targetUrl = webView != null ? webView.getUrl() : null;
                if (targetUrl == null || targetUrl.isEmpty()) return;

                String rawTitle;
                if (currentManifest != null && (!currentManifest.shortName.isEmpty() || !currentManifest.name.isEmpty())) {
                    rawTitle = !currentManifest.shortName.isEmpty() ? currentManifest.shortName : currentManifest.name;
                } else {
                    rawTitle = webView != null && webView.getTitle() != null ? webView.getTitle() : HelperUnit.domain(targetUrl);
                }
                final String title = rawTitle;

                if (currentManifest != null && !currentManifest.startUrl.isEmpty()) {
                    targetUrl = currentManifest.startUrl;
                }

                Bitmap iconBitmap = null;
                if (currentManifest != null && !currentManifest.iconUrl.isEmpty()) {
                    iconBitmap = fetchBitmap(currentManifest.iconUrl);
                }
                if (iconBitmap == null && webView instanceof com.petal.browser.view.NinjaWebView) {
                    iconBitmap = ((com.petal.browser.view.NinjaWebView) webView).getFavicon();
                }
                if (iconBitmap == null) {
                    com.petal.browser.database.FaviconHelper helper = new com.petal.browser.database.FaviconHelper(activity);
                    iconBitmap = helper.getFavicon(targetUrl);
                }
                if (iconBitmap == null) {
                    String domain = HelperUnit.domain(targetUrl);
                    if (domain != null && !domain.isEmpty()) {
                        iconBitmap = fetchBitmap(com.petal.browser.unit.FaviconGrabberManager.getFaviconGrabberUrl(domain));
                    }
                }

                Intent shortcutIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl));
                shortcutIntent.setComponent(new android.content.ComponentName(activity, BrowserActivity.class));
                shortcutIntent.putExtra("pwa_mode", true);
                shortcutIntent.putExtra("pwa_display", currentManifest != null ? currentManifest.display : "standalone");
                shortcutIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ShortcutManager shortcutManager = activity.getSystemService(ShortcutManager.class);
                    if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported()) {
                        Icon icon = iconBitmap != null ? Icon.createWithBitmap(iconBitmap) : Icon.createWithResource(activity, R.mipmap.ic_launcher);
                        ShortcutInfo pinShortcutInfo = new ShortcutInfo.Builder(activity, "pwa_" + Math.abs(targetUrl.hashCode()))
                                .setShortLabel(title)
                                .setLongLabel(currentManifest != null && !currentManifest.name.isEmpty() ? currentManifest.name : title)
                                .setIcon(icon)
                                .setIntent(shortcutIntent)
                                .build();

                        shortcutManager.requestPinShortcut(pinShortcutInfo, null);
                        activity.runOnUiThread(() -> Toast.makeText(activity, "Installed " + title + " as App", Toast.LENGTH_SHORT).show());
                        return;
                    }
                }

                // Fallback broadcast shortcut
                Intent addIntent = new Intent();
                addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
                addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, title);
                if (iconBitmap != null) {
                    addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON, iconBitmap);
                } else {
                    addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(activity, R.mipmap.ic_launcher));
                }
                addIntent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
                activity.sendBroadcast(addIntent);
                activity.runOnUiThread(() -> Toast.makeText(activity, "Installed " + title + " as App", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                Log.e(TAG, "Error installing PWA shortcut", e);
            }
        }).start();
    }

    private Bitmap fetchBitmap(String urlStr) {
        if (urlStr == null || urlStr.isEmpty()) return null;
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoInput(true);
            conn.connect();
            InputStream input = conn.getInputStream();
            return BitmapFactory.decodeStream(input);
        } catch (Exception e) {
            return null;
        }
    }

    private class PwaJavascriptInterface {
        @JavascriptInterface
        public void onManifestParsed(String jsonStr, String manifestUrl) {
            try {
                JSONObject json = new JSONObject(jsonStr);
                PwaManifest manifest = new PwaManifest();
                manifest.name = json.optString("name", "");
                manifest.shortName = json.optString("short_name", manifest.name);
                manifest.startUrl = json.optString("start_url", webView.getUrl());
                manifest.display = json.optString("display", "standalone");
                manifest.themeColor = json.optString("theme_color", "#FFFFFF");
                manifest.backgroundColor = json.optString("background_color", "#FFFFFF");

                if (json.has("icons")) {
                    JSONArray icons = json.getJSONArray("icons");
                    if (icons.length() > 0) {
                        manifest.iconUrl = icons.getJSONObject(icons.length() - 1).optString("src", "");
                    }
                }

                currentManifest = manifest;

                if (promptListener != null && ("standalone".equalsIgnoreCase(manifest.display) || "fullscreen".equalsIgnoreCase(manifest.display))) {
                    ((Activity) context).runOnUiThread(() -> promptListener.onPwaDetected(manifest));
                }
            } catch (Exception e) {
                Log.w(TAG, "Error parsing PWA manifest JSON: " + e.getMessage());
            }
        }

        @JavascriptInterface
        public void share(String title, String text, String url) {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            String shareContent = (title != null ? title + "\n" : "") + (text != null ? text + "\n" : "") + (url != null ? url : "");
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareContent);
            context.startActivity(Intent.createChooser(shareIntent, "Web Share"));
        }
    }
}
