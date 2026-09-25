package com.petal.browser.pwa;

import com.petal.browser.view.PetalToast;
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
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.petal.browser.R;
import com.petal.browser.activity.BrowserActivity;
import com.petal.browser.unit.HelperUnit;
import com.petal.browser.view.PetalGeckoView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PetalPwaManager
 * Dynamic Progressive Web App (PWA) manager supporting PetalGeckoView (GeckoView engine),
 * providing manifest detection & parsing, high-fidelity app icon extraction matching
 * official Firefox for Android, offline webpage web archive saving, and standalone Web App launching.
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
    private final com.petal.browser.browser.AlbumController albumController;
    private final WebView webView;
    private final PetalGeckoView geckoView;
    private PwaInstallPromptListener promptListener;
    private PwaManifest currentManifest;

    public PetalPwaManager(Context context, com.petal.browser.browser.AlbumController albumController, PwaInstallPromptListener listener) {
        this.context = context;
        this.albumController = albumController;
        this.webView = (albumController instanceof WebView) ? (WebView) albumController : null;
        this.geckoView = (albumController instanceof PetalGeckoView) ? (PetalGeckoView) albumController : null;
        this.promptListener = listener;

        if (this.webView != null) {
            configurePwaWebSettings(this.webView.getSettings());
            configureServiceWorker();
            this.webView.addJavascriptInterface(new PwaJavascriptInterface(), JS_INTERFACE_NAME);
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
        String js = "(function() {" +
                "   try {" +
                "       var manifestLink = document.querySelector('link[rel=\"manifest\"]');" +
                "       var manifestUrl = manifestLink ? manifestLink.href : '';" +
                "       var appleIcon = document.querySelector('link[rel=\"apple-touch-icon\"]') || document.querySelector('link[rel=\"apple-touch-icon-precomposed\"]') || document.querySelector('link[rel=\"icon\"][sizes=\"192x192\"]') || document.querySelector('link[rel=\"icon\"][sizes=\"512x512\"]') || document.querySelector('link[rel=\"icon\"]');" +
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
                "                   if (window." + JS_INTERFACE_NAME + ") {" +
                "                       window." + JS_INTERFACE_NAME + ".onManifestParsed(JSON.stringify(manifest), manifestUrl);" +
                "                   }" +
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
                "                   if (window." + JS_INTERFACE_NAME + ") {" +
                "                       window." + JS_INTERFACE_NAME + ".onManifestParsed(JSON.stringify(fallback), window.location.href);" +
                "                   }" +
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
                "           if (window." + JS_INTERFACE_NAME + ") {" +
                "               window." + JS_INTERFACE_NAME + ".onManifestParsed(JSON.stringify(fallback), window.location.href);" +
                "           }" +
                "       }" +
                "       if (!navigator.share && window." + JS_INTERFACE_NAME + ") {" +
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

        if (webView != null) {
            webView.evaluateJavascript(js, null);
        } else if (geckoView != null) {
            geckoView.evaluateJavascript(js, null);
        }
    }

    public PwaManifest getCurrentManifest() {
        return currentManifest;
    }

    public void setCurrentManifest(PwaManifest manifest) {
        this.currentManifest = manifest;
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

    /**
     * Resolves app metadata and manifest from HTML head if not yet cached or if running in GeckoView.
     */
    private static void discoverManifestAndIcons(String pageUrl, PwaManifest manifest, List<String> iconCandidates) {
        if (pageUrl == null || !pageUrl.startsWith("http")) return;
        try {
            URL url = new URL(pageUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setInstanceFollowRedirects(true);
            conn.connect();

            int code = conn.getResponseCode();
            if (code >= 200 && code < 400) {
                String actualUrl = conn.getURL().toString();
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder headHtml = new StringBuilder();
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null && lineCount++ < 150) {
                    headHtml.append(line).append("\n");
                    if (line.toLowerCase().contains("</head>")) break;
                }
                reader.close();

                String html = headHtml.toString();

                // 1. Check title
                Pattern titlePattern = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
                Matcher titleMatcher = titlePattern.matcher(html);
                if (titleMatcher.find()) {
                    String extractedTitle = titleMatcher.group(1).trim();
                    if (!extractedTitle.isEmpty() && (manifest.name == null || manifest.name.isEmpty())) {
                        manifest.name = extractedTitle;
                        manifest.shortName = extractedTitle;
                    }
                }

                // 2. Check manifest link
                Pattern manifestPattern = Pattern.compile("<link[^>]+rel=[\"']manifest[\"'][^>]*href=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
                Matcher manifestMatcher = manifestPattern.matcher(html);
                if (!manifestMatcher.find()) {
                    manifestPattern = Pattern.compile("<link[^>]+href=[\"']([^\"']+)[\"'][^>]*rel=[\"']manifest[\"']", Pattern.CASE_INSENSITIVE);
                    manifestMatcher = manifestPattern.matcher(html);
                }
                if (manifestMatcher.find()) {
                    String manifestHref = manifestMatcher.group(1);
                    String manifestResolvedUrl = resolveUrl(actualUrl, manifestHref);
                    parseManifestFromNetwork(manifestResolvedUrl, manifest, iconCandidates);
                }

                // 3. Apple Touch Icons
                Pattern applePattern = Pattern.compile("<link[^>]+rel=[\"']apple-touch-icon(?:-precomposed)?[\"'][^>]*href=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
                Matcher appleMatcher = applePattern.matcher(html);
                while (appleMatcher.find()) {
                    String resolved = resolveUrl(actualUrl, appleMatcher.group(1));
                    if (!iconCandidates.contains(resolved)) {
                        iconCandidates.add(resolved);
                    }
                }
                applePattern = Pattern.compile("<link[^>]+href=[\"']([^\"']+)[\"'][^>]*rel=[\"']apple-touch-icon(?:-precomposed)?[\"']", Pattern.CASE_INSENSITIVE);
                appleMatcher = applePattern.matcher(html);
                while (appleMatcher.find()) {
                    String resolved = resolveUrl(actualUrl, appleMatcher.group(1));
                    if (!iconCandidates.contains(resolved)) {
                        iconCandidates.add(resolved);
                    }
                }

                // 4. Standard Icons
                Pattern iconPattern = Pattern.compile("<link[^>]+rel=[\"'](?:shortcut )?icon[\"'][^>]*href=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
                Matcher iconMatcher = iconPattern.matcher(html);
                while (iconMatcher.find()) {
                    String resolved = resolveUrl(actualUrl, iconMatcher.group(1));
                    if (!iconCandidates.contains(resolved)) {
                        iconCandidates.add(resolved);
                    }
                }
                iconPattern = Pattern.compile("<link[^>]+href=[\"']([^\"']+)[\"'][^>]*rel=[\"'](?:shortcut )?icon[\"']", Pattern.CASE_INSENSITIVE);
                iconMatcher = iconPattern.matcher(html);
                while (iconMatcher.find()) {
                    String resolved = resolveUrl(actualUrl, iconMatcher.group(1));
                    if (!iconCandidates.contains(resolved)) {
                        iconCandidates.add(resolved);
                    }
                }

                // 5. Theme Color
                Pattern themePattern = Pattern.compile("<meta[^>]+name=[\"']theme-color[\"'][^>]*content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
                Matcher themeMatcher = themePattern.matcher(html);
                if (themeMatcher.find()) {
                    String color = themeMatcher.group(1).trim();
                    if (!color.isEmpty() && (manifest.themeColor == null || manifest.themeColor.isEmpty() || "#FFFFFF".equalsIgnoreCase(manifest.themeColor))) {
                        manifest.themeColor = color;
                    }
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            Log.d(TAG, "Network discovery note: " + e.getMessage());
        }
    }

    private static void parseManifestFromNetwork(String manifestUrl, PwaManifest manifest, List<String> iconCandidates) {
        try {
            URL url = new URL(manifestUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.connect();

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String l;
                while ((l = reader.readLine()) != null) sb.append(l);
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                if (json.has("name") && (manifest.name == null || manifest.name.isEmpty())) {
                    manifest.name = json.optString("name", "");
                }
                if (json.has("short_name") && (manifest.shortName == null || manifest.shortName.isEmpty())) {
                    manifest.shortName = json.optString("short_name", manifest.name);
                }
                if (json.has("start_url") && (manifest.startUrl == null || manifest.startUrl.isEmpty())) {
                    manifest.startUrl = resolveUrl(manifestUrl, json.optString("start_url", ""));
                }
                if (json.has("display")) {
                    manifest.display = json.optString("display", "standalone");
                }
                if (json.has("theme_color")) {
                    manifest.themeColor = json.optString("theme_color", "#FFFFFF");
                }
                if (json.has("background_color")) {
                    manifest.backgroundColor = json.optString("background_color", "#FFFFFF");
                }

                if (json.has("icons")) {
                    JSONArray icons = json.getJSONArray("icons");
                    for (int i = 0; i < icons.length(); i++) {
                        JSONObject ic = icons.getJSONObject(i);
                        String src = ic.optString("src", "");
                        if (!src.isEmpty()) {
                            String resolved = resolveUrl(manifestUrl, src);
                            if (!iconCandidates.contains(resolved)) {
                                // Add manifest icons with high priority
                                iconCandidates.add(0, resolved);
                            }
                        }
                    }
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            Log.d(TAG, "Manifest fetch note: " + e.getMessage());
        }
    }

    /**
     * Creates an adaptive launcher app icon matching official Firefox for Android:
     * - Generates a clean 192x192 canvas with smooth Material 3 rounded squircle bounds
     * - Preserves high-resolution edge-to-edge artwork
     * - Places glyphs and transparent icons cleanly onto an elegant themed or pure container
     */
    public static Bitmap createAdaptiveAppIcon(Context context, Bitmap rawIcon, String themeColorHex) {
        int targetSize = 192;
        Bitmap output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        if (rawIcon == null || rawIcon.isRecycled()) {
            Bitmap appIcon = BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher);
            if (appIcon != null) {
                return appIcon;
            }
            return output;
        }

        int bgColor = Color.TRANSPARENT;
        if (themeColorHex != null && !themeColorHex.trim().isEmpty() && !themeColorHex.equalsIgnoreCase("#FFFFFF")) {
            try {
                bgColor = Color.parseColor(themeColorHex.trim());
            } catch (Exception ignored) {}
        }

        boolean isHighRes = rawIcon.getWidth() >= 96 && rawIcon.getHeight() >= 96;
        boolean hasAlpha = rawIcon.hasAlpha();

        // Check if raw icon already has full solid opaque corners
        boolean isFullyOpaqueArtwork = false;
        if (!hasAlpha && isHighRes) {
            isFullyOpaqueArtwork = true;
        } else if (isHighRes) {
            try {
                int c1 = rawIcon.getPixel(2, 2);
                int c2 = rawIcon.getPixel(rawIcon.getWidth() - 3, 2);
                int c3 = rawIcon.getPixel(2, rawIcon.getHeight() - 3);
                int c4 = rawIcon.getPixel(rawIcon.getWidth() - 3, rawIcon.getHeight() - 3);
                if (Color.alpha(c1) == 255 && Color.alpha(c2) == 255 && Color.alpha(c3) == 255 && Color.alpha(c4) == 255) {
                    isFullyOpaqueArtwork = true;
                }
            } catch (Exception ignored) {}
        }

        float cornerRadius = targetSize * 0.22f;
        RectF rect = new RectF(0, 0, targetSize, targetSize);
        Path path = new Path();
        path.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW);

        if (isFullyOpaqueArtwork) {
            // Full bleed solid artwork (e.g. 512x512 maskable/opaque manifest icon)
            canvas.clipPath(path);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(rawIcon, null, new Rect(0, 0, targetSize, targetSize), paint);
            return output;
        }

        // Favicon or transparent icon: Draw clean background surface
        if (bgColor == Color.TRANSPARENT || bgColor == Color.WHITE) {
            bgColor = Color.parseColor("#F5F5F7");
        }

        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(bgColor);
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint);

        // Center and scale the icon cleanly with comfortable padding
        int iconPadding = (int) (targetSize * 0.16f);
        Rect destRect = new Rect(iconPadding, iconPadding, targetSize - iconPadding, targetSize - iconPadding);
        Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(rawIcon, null, destRect, iconPaint);

        return output;
    }

    /**
     * Installs PWA / website to Android Home Screen via ShortcutManagerCompat
     * targeting the dedicated standalone PetalPwaActivity shell with official Firefox-style
     * confirmation prompt showing app icon preview, editable/custom title, and origin URL.
     */
    public void installCurrentPwa(Activity activity) {
        if (activity == null) return;

        com.petal.browser.browser.AlbumController targetController = this.albumController;
        if (targetController == null && activity instanceof com.petal.browser.activity.BrowserActivity) {
            targetController = ((com.petal.browser.activity.BrowserActivity) activity).currentAlbumController;
        }

        String pageUrl = targetController != null ? targetController.getUrl() : (webView != null ? webView.getUrl() : null);
        if (pageUrl == null || pageUrl.trim().isEmpty() || "about:blank".equalsIgnoreCase(pageUrl.trim())) {
            if (targetController instanceof com.petal.browser.view.PetalGeckoView) {
                pageUrl = ((com.petal.browser.view.PetalGeckoView) targetController).getAlbumUrl();
            }
        }

        if (pageUrl == null || pageUrl.trim().isEmpty() || "about:blank".equalsIgnoreCase(pageUrl.trim()) || pageUrl.startsWith("petal://")) {
            activity.runOnUiThread(() -> PetalToast.show(activity, R.string.pwa_install_empty_page));
            return;
        }

        final com.petal.browser.browser.AlbumController finalController = targetController;
        final String finalPageUrl = pageUrl;

        new Thread(() -> {
            try {
                // Initialize working manifest
                PwaManifest workingManifest = new PwaManifest();
                if (currentManifest != null) {
                    workingManifest.name = currentManifest.name;
                    workingManifest.shortName = currentManifest.shortName;
                    workingManifest.startUrl = currentManifest.startUrl;
                    workingManifest.display = currentManifest.display;
                    workingManifest.themeColor = currentManifest.themeColor;
                    workingManifest.backgroundColor = currentManifest.backgroundColor;
                    workingManifest.iconUrl = currentManifest.iconUrl;
                }

                List<String> iconCandidates = new ArrayList<>();
                if (workingManifest.iconUrl != null && !workingManifest.iconUrl.isEmpty()) {
                    iconCandidates.add(resolveUrl(finalPageUrl, workingManifest.iconUrl));
                }

                // Discover manifest, touch icons, theme-color from HTML head if needed
                discoverManifestAndIcons(finalPageUrl, workingManifest, iconCandidates);

                // Add fallback icon candidates (domain touch-icon, favicon.ico, Google S2 Favicon service)
                String host = "";
                try {
                    host = new URL(finalPageUrl).getHost();
                } catch (Exception ignored) {}

                if (!host.isEmpty()) {
                    String origin = (finalPageUrl.startsWith("https") ? "https://" : "http://") + host;
                    iconCandidates.add(origin + "/apple-touch-icon.png");
                    iconCandidates.add(origin + "/apple-touch-icon-precomposed.png");
                    iconCandidates.add(origin + "/favicon.ico");
                    iconCandidates.add("https://www.google.com/s2/favicons?domain=" + host + "&sz=256");
                }

                // Determine title
                String rawTitle = null;
                if (!workingManifest.shortName.isEmpty()) {
                    rawTitle = workingManifest.shortName;
                } else if (!workingManifest.name.isEmpty()) {
                    rawTitle = workingManifest.name;
                }
                if (rawTitle == null || rawTitle.isEmpty()) {
                    rawTitle = finalController != null && finalController.getTitle() != null && !finalController.getTitle().isEmpty() ? finalController.getTitle() : (webView != null && webView.getTitle() != null && !webView.getTitle().isEmpty() ? webView.getTitle() : HelperUnit.domain(finalPageUrl));
                }
                if (rawTitle == null || rawTitle.isEmpty()) {
                    rawTitle = "Web App";
                }
                final String initialTitle = rawTitle;

                // Determine target start URL
                String candidateUrl = finalPageUrl;
                if (!workingManifest.startUrl.isEmpty()) {
                    candidateUrl = resolveUrl(finalPageUrl, workingManifest.startUrl);
                }
                final String targetUrl = candidateUrl;

                // Fetch high-fidelity website icon
                Bitmap rawBitmap = null;
                for (String candidate : iconCandidates) {
                    if (candidate != null && !candidate.isEmpty()) {
                        rawBitmap = fetchBitmap(candidate);
                        if (rawBitmap != null) {
                            break;
                        }
                    }
                }

                if (rawBitmap == null && finalController instanceof com.petal.browser.view.PetalGeckoView) {
                    rawBitmap = ((com.petal.browser.view.PetalGeckoView) finalController).getFavicon();
                }
                if (rawBitmap == null) {
                    com.petal.browser.database.FaviconHelper helper = new com.petal.browser.database.FaviconHelper(activity);
                    rawBitmap = helper.getFavicon(finalPageUrl);
                }

                String themeColorHex = workingManifest.themeColor != null && !workingManifest.themeColor.isEmpty() ? workingManifest.themeColor : "#FFFFFF";
                final Bitmap finalAdaptiveIcon = createAdaptiveAppIcon(activity, rawBitmap, themeColorHex);

                // Display official Firefox-style confirmation dialog on UI thread
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed())) {
                        return;
                    }

                    showInstallConfirmationDialog(activity, initialTitle, targetUrl, finalAdaptiveIcon, themeColorHex, workingManifest);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error preparing PWA install", e);
                activity.runOnUiThread(() -> PetalToast.show(activity, R.string.pwa_install_failed));
            }
        }).start();
    }

    /**
     * Renders an expressive Material 3 confirmation dialog matching Firefox WebAppShortcutManager.
     */
    private void showInstallConfirmationDialog(Activity activity, String defaultTitle, String targetUrl,
                                               Bitmap adaptiveIcon, String themeColorHex, PwaManifest manifest) {
        try {
            LayoutInflater inflater = LayoutInflater.from(activity);
            View dialogView = inflater.inflate(R.layout.dialog_pwa_install, null);

            ImageView iconView = dialogView.findViewById(R.id.dialog_pwa_icon);
            TextView titleView = dialogView.findViewById(R.id.dialog_pwa_title);
            TextView urlView = dialogView.findViewById(R.id.dialog_pwa_url);
            MaterialButton cancelButton = dialogView.findViewById(R.id.dialog_pwa_cancel);
            MaterialButton installButton = dialogView.findViewById(R.id.dialog_pwa_install);

            if (iconView != null && adaptiveIcon != null) {
                iconView.setImageBitmap(adaptiveIcon);
            }
            if (titleView != null) {
                titleView.setText(defaultTitle);
            }
            if (urlView != null) {
                String domainText = HelperUnit.domain(targetUrl);
                if (domainText == null || domainText.isEmpty()) domainText = targetUrl;
                urlView.setText(domainText);
            }

            AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                    .setView(dialogView)
                    .create();

            if (cancelButton != null) {
                cancelButton.setOnClickListener(v -> dialog.dismiss());
            }

            if (installButton != null) {
                installButton.setOnClickListener(v -> {
                    dialog.dismiss();
                    performShortcutPinning(activity, defaultTitle, targetUrl, adaptiveIcon, themeColorHex, manifest);
                });
            }

            dialog.show();
            HelperUnit.setupDialog(activity, dialog);
        } catch (Exception e) {
            Log.e(TAG, "Error showing install dialog, falling back to direct pinning", e);
            performShortcutPinning(activity, defaultTitle, targetUrl, adaptiveIcon, themeColorHex, manifest);
        }
    }

    /**
     * Executes the home screen shortcut creation, offline web archive snapshot,
     * and displays completion toast.
     */
    private void performShortcutPinning(Activity activity, String title, String targetUrl,
                                        Bitmap adaptiveIcon, String themeColorHex, PwaManifest manifest) {
        new Thread(() -> {
            try {
                File archiveDir = new File(activity.getFilesDir(), "offline_web_archives");
                if (!archiveDir.exists()) archiveDir.mkdirs();
                String filename = "archive_" + Math.abs(targetUrl.hashCode()) + ".mht";
                File archiveFile = new File(archiveDir, filename);

                activity.runOnUiThread(() -> {
                    try {
                        if (webView != null) {
                            webView.saveWebArchive(archiveFile.getAbsolutePath(), false, null);
                            com.petal.browser.engine.gecko.PetalEngineStore.addOfflineArchive(activity, targetUrl, archiveFile.getAbsolutePath(), title);
                        } else if (geckoView != null) {
                            try {
                                java.io.FileOutputStream fos = new java.io.FileOutputStream(archiveFile);
                                geckoView.printToPdf(fos, success -> {
                                    if (Boolean.TRUE.equals(success)) {
                                        com.petal.browser.engine.gecko.PetalEngineStore.addOfflineArchive(activity, targetUrl, archiveFile.getAbsolutePath(), title);
                                    }
                                    return kotlin.Unit.INSTANCE;
                                });
                            } catch (Exception ignored) {}
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "saveWebArchive: " + e.getMessage());
                    }
                });

                Intent shortcutIntent = new Intent("com.petal.browser.action.OPEN_PWA", Uri.parse(targetUrl));
                shortcutIntent.setComponent(new android.content.ComponentName(activity, PetalPwaActivity.class));
                shortcutIntent.putExtra(PetalPwaActivity.EXTRA_URL, targetUrl);
                shortcutIntent.putExtra(PetalPwaActivity.EXTRA_TITLE, title);
                shortcutIntent.putExtra(PetalPwaActivity.EXTRA_THEME_COLOR, themeColorHex);
                shortcutIntent.putExtra(PetalPwaActivity.EXTRA_DISPLAY, manifest != null ? manifest.display : "standalone");
                shortcutIntent.putExtra(PetalPwaActivity.EXTRA_OFFLINE_ARCHIVE, archiveFile.getAbsolutePath());
                shortcutIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

                IconCompat iconCompat = IconCompat.createWithBitmap(adaptiveIcon);
                String shortcutId = "pwa_" + Math.abs(targetUrl.hashCode());

                ShortcutInfoCompat pinShortcutInfo = new ShortcutInfoCompat.Builder(activity, shortcutId)
                        .setShortLabel(title)
                        .setLongLabel(manifest != null && !manifest.name.isEmpty() ? manifest.name : title)
                        .setIcon(iconCompat)
                        .setIntent(shortcutIntent)
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

                String successMsg = activity.getString(R.string.pwa_install_success, title);
                activity.runOnUiThread(() -> PetalToast.show(activity, successMsg));
            } catch (Exception e) {
                Log.e(TAG, "Error pinning PWA shortcut", e);
                activity.runOnUiThread(() -> PetalToast.show(activity, R.string.pwa_install_failed));
            }
        }).start();
    }

    private static Bitmap fetchBitmap(String urlStr) {
        if (urlStr == null || urlStr.isEmpty()) return null;
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36");
            conn.setRequestProperty("Accept", "image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setInstanceFollowRedirects(true);
            conn.setDoInput(true);
            conn.connect();
            if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 400) {
                InputStream input = conn.getInputStream();
                Bitmap b = BitmapFactory.decodeStream(input);
                input.close();
                return b;
            }
            return null;
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
                        String bestIcon = "";
                        int bestSize = 0;
                        for (int i = 0; i < icons.length(); i++) {
                            JSONObject ic = icons.getJSONObject(i);
                            String src = ic.optString("src", "");
                            String sizes = ic.optString("sizes", "0x0");
                            int sizeVal = 0;
                            try {
                                if (sizes.contains("x")) {
                                    sizeVal = Integer.parseInt(sizes.split("x")[0].trim());
                                }
                            } catch (Exception ignored) {}
                            if (bestIcon.isEmpty() || sizeVal >= bestSize) {
                                bestSize = sizeVal;
                                bestIcon = src;
                            }
                        }
                        if (bestIcon.isEmpty()) {
                            bestIcon = icons.getJSONObject(icons.length() - 1).optString("src", "");
                        }
                        manifest.iconUrl = resolveUrl(manifestUrl, bestIcon);
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
