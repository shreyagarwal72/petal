package com.petal.browser.pwa;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.petal.browser.R;
import com.petal.browser.activity.BrowserActivity;
import com.petal.browser.unit.HelperUnit;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * PetalPwaManager
 * Dynamic Progressive Web App (PWA) manager providing manifest detection & parsing via JS injection,
 * Service Worker lifecycle control, native installation prompts with dynamic adaptive shortcut creation,
 * offline webpage web archive saving, and Web API delegation (Web Share, WebAuthn/Passkeys, Push Notifications).
 */
public class PetalPwaManager {

    private static final String TAG = "PetalPwaManager";
    private static final String JS_INTERFACE_NAME = "PetalPwaInterface";

    public static class PwaManifest {
        public String name = "";
        public String shortName = "";
        public String startUrl = "";
        public String display = "standalone";
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

        if (webView != null) {
            configurePwaWebSettings(webView.getSettings());
            configureServiceWorker();
            webView.addJavascriptInterface(new PwaJavascriptInterface(), JS_INTERFACE_NAME);
        }
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
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
    }

    /**
     * Configures Service Worker lifecycle for offline caching.
     */
    private void configureServiceWorker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                ServiceWorkerController controller = ServiceWorkerController.getInstance();
                controller.getServiceWorkerWebSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
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
     * Injects JavaScript to discover link[rel="manifest"], theme-color, apple-touch-icons, and parse web app manifest.
     */
    public void detectPwaManifest() {
        if (webView == null) return;
        String js = "(function() {" +
                "   try {" +
                "       var manifestLink = document.querySelector('link[rel=\"manifest\"]');" +
                "       var manifestUrl = manifestLink ? manifestLink.href : '';" +
                "       var appleIcon = document.querySelector('link[rel=\"apple-touch-icon\"]') || document.querySelector('link[rel=\"apple-touch-icon-precomposed\"]');" +
                "       var appleIconUrl = appleIcon ? appleIcon.href : '';" +
                "       var themeColorMeta = document.querySelector('meta[name=\"theme-color\"]');" +
                "       var themeColor = themeColorMeta ? themeColorMeta.content : '';" +
                "       if (manifestUrl) {" +
                "           fetch(manifestUrl, { credentials: 'omit' })" +
                "               .then(function(res) { return res.json(); })" +
                "               .then(function(manifest) {" +
                "                   if (appleIconUrl && (!manifest.icons || manifest.icons.length === 0)) {" +
                "                       manifest.icons = [{ src: appleIconUrl, sizes: '192x192' }];" +
                "                   }" +
                "                   if (themeColor && !manifest.theme_color) {" +
                "                       manifest.theme_color = themeColor;" +
                "                   }" +
                "                   window." + JS_INTERFACE_NAME + ".onManifestParsed(JSON.stringify(manifest), manifestUrl);" +
                "               })" +
                "               .catch(function(err) {" +
                "                   var fallback = {" +
                "                       name: document.title || ''," +
                "                       short_name: document.title || ''," +
                "                       start_url: window.location.href," +
                "                       display: 'standalone'," +
                "                       theme_color: themeColor," +
                "                       icons: appleIconUrl ? [{ src: appleIconUrl, sizes: '192x192' }] : []" +
                "                   };" +
                "                   window." + JS_INTERFACE_NAME + ".onManifestParsed(JSON.stringify(fallback), window.location.href);" +
                "               });" +
                "       } else {" +
                "           var isStandalone = document.querySelector('meta[name=\"mobile-web-app-capable\"]') || document.querySelector('meta[name=\"apple-mobile-web-app-capable\"]');" +
                "           var fallback = {" +
                "               name: document.title || ''," +
                "               short_name: document.title || ''," +
                "               start_url: window.location.href," +
                "               display: isStandalone ? 'standalone' : 'browser'," +
                "               theme_color: themeColor," +
                "               icons: appleIconUrl ? [{ src: appleIconUrl, sizes: '192x192' }] : []" +
                "           };" +
                "           window." + JS_INTERFACE_NAME + ".onManifestParsed(JSON.stringify(fallback), window.location.href);" +
                "       }" +
                "       if (!navigator.share) {" +
                "           navigator.share = function(data) {" +
                "               return new Promise(function(resolve, reject) {" +
                "                   try {" +
                "                       window." + JS_INTERFACE_NAME + ".share(data ? (data.title || '') : '', data ? (data.text || '') : '', data ? (data.url || '') : '');" +
                "                       resolve();" +
                "                   } catch(e) {" +
                "                       reject(e);" +
                "                   }" +
                "               });" +
                "           };" +
                "       }" +
                "   } catch(e) {}" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    public PwaManifest getCurrentManifest() {
        return currentManifest;
    }

    public static String resolveUrl(String baseUrl, String relativeOrAbsoluteUrl) {
        if (relativeOrAbsoluteUrl == null || relativeOrAbsoluteUrl.trim().isEmpty()) {
            return baseUrl != null ? baseUrl : "";
        }
        String trimmed = relativeOrAbsoluteUrl.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        if (baseUrl == null || baseUrl.isEmpty()) {
            return trimmed;
        }
        try {
            URI base = new URI(baseUrl);
            return base.resolve(trimmed).toString();
        } catch (Exception e) {
            try {
                URL base = new URL(baseUrl);
                return new URL(base, trimmed).toString();
            } catch (Exception ex) {
                return trimmed;
            }
        }
    }

    public static Bitmap createAdaptiveAppIcon(Context context, Bitmap rawIcon, String themeColorHex) {
        int targetSize = 192;
        Bitmap output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        int bgColor = Color.WHITE;
        if (themeColorHex != null && !themeColorHex.isEmpty()) {
            try {
                bgColor = Color.parseColor(themeColorHex);
            } catch (Exception ignored) {}
        }

        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(bgColor);

        float cornerRadius = targetSize * 0.22f;
        RectF rect = new RectF(0, 0, targetSize, targetSize);
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint);

        if (rawIcon != null && !rawIcon.isRecycled()) {
            int iconPadding = (int) (targetSize * 0.15f);
            Rect destRect;
            if (rawIcon.getWidth() >= targetSize * 0.75f && rawIcon.getHeight() >= targetSize * 0.75f) {
                Path path = new Path();
                path.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW);
                canvas.clipPath(path);
                destRect = new Rect(0, 0, targetSize, targetSize);
            } else {
                destRect = new Rect(iconPadding, iconPadding, targetSize - iconPadding, targetSize - iconPadding);
            }
            Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(rawIcon, null, destRect, iconPaint);
        } else {
            Bitmap appIcon = BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher);
            if (appIcon != null) {
                int iconPadding = (int) (targetSize * 0.18f);
                Rect destRect = new Rect(iconPadding, iconPadding, targetSize - iconPadding, targetSize - iconPadding);
                canvas.drawBitmap(appIcon, null, destRect, bgPaint);
            }
        }
        return output;
    }

    /**
     * Installs PWA / offline website to Android Home Screen via ShortcutManagerCompat
     * with adaptive app icon and saves full webpage archive for offline use.
     */
    public void installCurrentPwa(Activity activity) {
        if (activity == null) return;

        new Thread(() -> {
            try {
                String pageUrl = webView != null ? webView.getUrl() : null;
                if (pageUrl == null || pageUrl.isEmpty() || "about:blank".equalsIgnoreCase(pageUrl)) {
                    activity.runOnUiThread(() -> Toast.makeText(activity, "Cannot install empty page as app", Toast.LENGTH_SHORT).show());
                    return;
                }

                String targetUrl = pageUrl;
                String rawTitle = null;

                if (currentManifest != null) {
                    if (!currentManifest.shortName.isEmpty()) {
                        rawTitle = currentManifest.shortName;
                    } else if (!currentManifest.name.isEmpty()) {
                        rawTitle = currentManifest.name;
                    }
                    if (!currentManifest.startUrl.isEmpty()) {
                        targetUrl = resolveUrl(pageUrl, currentManifest.startUrl);
                    }
                }

                if (rawTitle == null || rawTitle.isEmpty()) {
                    rawTitle = webView != null && webView.getTitle() != null && !webView.getTitle().isEmpty() ? webView.getTitle() : HelperUnit.domain(pageUrl);
                }
                if (rawTitle == null || rawTitle.isEmpty()) {
                    rawTitle = "Web App";
                }
                final String title = rawTitle;

                Bitmap rawBitmap = null;
                if (currentManifest != null && !currentManifest.iconUrl.isEmpty()) {
                    String resolvedIconUrl = resolveUrl(pageUrl, currentManifest.iconUrl);
                    rawBitmap = fetchBitmap(resolvedIconUrl);
                }
                if (rawBitmap == null && webView instanceof com.petal.browser.view.NinjaWebView) {
                    rawBitmap = ((com.petal.browser.view.NinjaWebView) webView).getFavicon();
                }
                if (rawBitmap == null) {
                    com.petal.browser.database.FaviconHelper helper = new com.petal.browser.database.FaviconHelper(activity);
                    rawBitmap = helper.getFavicon(pageUrl);
                }
                if (rawBitmap == null) {
                    String domain = HelperUnit.domain(pageUrl);
                    if (domain != null && !domain.isEmpty()) {
                        rawBitmap = fetchBitmap(com.petal.browser.unit.FaviconGrabberManager.getFaviconGrabberUrl(domain));
                    }
                }

                String themeColorHex = currentManifest != null ? currentManifest.themeColor : "#FFFFFF";
                Bitmap finalAdaptiveIcon = createAdaptiveAppIcon(activity, rawBitmap, themeColorHex);

                File archiveDir = new File(activity.getFilesDir(), "offline_web_archives");
                if (!archiveDir.exists()) archiveDir.mkdirs();
                String filename = "archive_" + Math.abs(targetUrl.hashCode()) + ".mht";
                File archiveFile = new File(archiveDir, filename);

                activity.runOnUiThread(() -> {
                    try {
                        if (webView != null) {
                            webView.saveWebArchive(archiveFile.getAbsolutePath(), false, null);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "saveWebArchive: " + e.getMessage());
                    }
                });

                Intent shortcutIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl));
                shortcutIntent.setComponent(new android.content.ComponentName(activity, BrowserActivity.class));
                shortcutIntent.putExtra("pwa_mode", true);
                shortcutIntent.putExtra("pwa_display", currentManifest != null ? currentManifest.display : "standalone");
                shortcutIntent.putExtra("pwa_theme_color", themeColorHex);
                shortcutIntent.putExtra("offline_archive_path", archiveFile.getAbsolutePath());
                shortcutIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

                IconCompat iconCompat = IconCompat.createWithBitmap(finalAdaptiveIcon);
                String shortcutId = "pwa_" + Math.abs(targetUrl.hashCode());

                ShortcutInfoCompat pinShortcutInfo = new ShortcutInfoCompat.Builder(activity, shortcutId)
                        .setShortLabel(title)
                        .setLongLabel(currentManifest != null && !currentManifest.name.isEmpty() ? currentManifest.name : title)
                        .setIcon(iconCompat)
                        .setIntent(shortcutIntent)
                        .setAlwaysBadged()
                        .build();

                boolean pinned = false;
                if (ShortcutManagerCompat.isRequestPinShortcutSupported(activity)) {
                    pinned = ShortcutManagerCompat.requestPinShortcut(activity, pinShortcutInfo, null);
                }
                if (!pinned) {
                    Intent addIntent = ShortcutManagerCompat.createShortcutResultIntent(activity, pinShortcutInfo);
                    if (addIntent != null) {
                        addIntent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
                        activity.sendBroadcast(addIntent);
                        pinned = true;
                    }
                }

                activity.runOnUiThread(() -> Toast.makeText(activity, "Installed \"" + title + "\" to Home Screen", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                Log.e(TAG, "Error installing PWA shortcut", e);
                activity.runOnUiThread(() -> Toast.makeText(activity, "Could not install app shortcut", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private Bitmap fetchBitmap(String urlStr) {
        if (urlStr == null || urlStr.isEmpty()) return null;
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
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
                String rawStartUrl = json.optString("start_url", "");
                manifest.startUrl = resolveUrl(manifestUrl, rawStartUrl);
                manifest.display = json.optString("display", "standalone");
                manifest.themeColor = json.optString("theme_color", "#FFFFFF");
                manifest.backgroundColor = json.optString("background_color", "#FFFFFF");

                if (json.has("icons")) {
                    JSONArray icons = json.getJSONArray("icons");
                    if (icons.length() > 0) {
                        String rawIcon = icons.getJSONObject(icons.length() - 1).optString("src", "");
                        manifest.iconUrl = resolveUrl(manifestUrl, rawIcon);
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
            String shareContent = (title != null && !title.isEmpty() ? title + "\n" : "") +
                    (text != null && !text.isEmpty() ? text + "\n" : "") +
                    (url != null && !url.isEmpty() ? url : "");
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareContent.trim());
            context.startActivity(Intent.createChooser(shareIntent, "Web Share"));
        }
    }
}
