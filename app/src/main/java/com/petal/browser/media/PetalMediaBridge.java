package com.petal.browser.media;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Rational;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import androidx.preference.PreferenceManager;

/**
 * PetalMediaBridge
 * Connects WebView HTML5 media elements (audio/video) with MediaSessionService and PiP mode.
 * Provides JavaScript injection to monitor play, pause, timeupdate, and fullscreen video state.
 */
public class PetalMediaBridge {

    private static final String JS_INTERFACE_NAME = "PetalMediaInterface";

    public static final String MEDIA_JS_INJECTION =
            "(function() {" +
            "   try {" +
            "       Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });" +
            "       Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });" +
            "       window.addEventListener('visibilitychange', function(e) { e.stopImmediatePropagation(); }, true);" +
            "       document.addEventListener('visibilitychange', function(e) { e.stopImmediatePropagation(); }, true);" +
            "   } catch(e) {}" +
            "   if (window.petalMediaInjected) return;" +
            "   window.petalMediaInjected = true;" +
            "   function hookMediaElements() {" +
            "       var mediaEls = document.querySelectorAll('video, audio');" +
            "       for (var i = 0; i < mediaEls.length; i++) {" +
            "           var el = mediaEls[i];" +
            "           if (el.dataset.petalHooked) continue;" +
            "           el.dataset.petalHooked = 'true';" +
            "           el.addEventListener('play', function() {" +
            "               if (window." + JS_INTERFACE_NAME + ") {" +
            "                   window." + JS_INTERFACE_NAME + ".onMediaStateChanged(true, this.title || document.title, this.currentTime * 1000, (this.duration || 0) * 1000);" +
            "               }" +
            "           });" +
            "           el.addEventListener('pause', function() {" +
            "               if (window." + JS_INTERFACE_NAME + ") {" +
            "                   window." + JS_INTERFACE_NAME + ".onMediaStateChanged(false, this.title || document.title, this.currentTime * 1000, (this.duration || 0) * 1000);" +
            "               }" +
            "           });" +
            "           el.addEventListener('timeupdate', function() {" +
            "               if (!this.paused && window." + JS_INTERFACE_NAME + ") {" +
            "                   window." + JS_INTERFACE_NAME + ".onMediaProgress(this.currentTime * 1000, (this.duration || 0) * 1000);" +
            "               }" +
            "           });" +
            "           el.addEventListener('loadedmetadata', function() {" +
            "               if (window." + JS_INTERFACE_NAME + ") {" +
            "                   window." + JS_INTERFACE_NAME + ".onVideoDimensions(this.videoWidth || 0, this.videoHeight || 0);" +
            "               }" +
            "           });" +
            "           el.addEventListener('resize', function() {" +
            "               if (window." + JS_INTERFACE_NAME + ") {" +
            "                   window." + JS_INTERFACE_NAME + ".onVideoDimensions(this.videoWidth || 0, this.videoHeight || 0);" +
            "               }" +
            "           });" +
            "           if (el.videoWidth && el.videoHeight && window." + JS_INTERFACE_NAME + ") {" +
            "               window." + JS_INTERFACE_NAME + ".onVideoDimensions(el.videoWidth, el.videoHeight);" +
            "           }" +
            "       }" +
            "   }" +
            "   document.addEventListener('DOMNodeInserted', hookMediaElements);" +
            "   hookMediaElements();" +
            "})();";

    private final Context context;
    private final WebView webView;
    private MediaStateListener listener;

    public interface MediaStateListener {
        void onMediaPlay(String title, long positionMs, long durationMs);
        void onMediaPause(long positionMs, long durationMs);
        void onMediaProgress(long positionMs, long durationMs);
        default void onMediaPlayingStateChanged(boolean isPlaying) {}
        default void onVideoDimensionsChanged(int width, int height) {}
    }

    public PetalMediaBridge(Context context, WebView webView, MediaStateListener listener) {
        this.context = context;
        this.webView = webView;
        this.listener = listener;

        webView.addJavascriptInterface(new MediaJsInterface(), JS_INTERFACE_NAME);
    }

    public void injectMediaHooks() {
        if (webView != null) {
            webView.evaluateJavascript(MEDIA_JS_INJECTION, null);
        }
    }

    public void playMedia() {
        if (webView != null) {
            webView.evaluateJavascript(
                    "var els = document.querySelectorAll('video, audio'); for(var i=0; i<els.length; i++) { els[i].play(); }", null
            );
        }
    }

    public void pauseMedia() {
        if (webView != null) {
            webView.evaluateJavascript(
                    "var els = document.querySelectorAll('video, audio'); for(var i=0; i<els.length; i++) { els[i].pause(); }", null
            );
        }
    }

    public void seekMediaTo(long positionMs) {
        if (webView != null) {
            double seconds = positionMs / 1000.0;
            webView.evaluateJavascript(
                    "var els = document.querySelectorAll('video, audio'); for(var i=0; i<els.length; i++) { els[i].currentTime = " + seconds + "; }", null
            );
        }
    }

    /**
     * Checks if Auto-PiP on home gesture is enabled and attempts entering PiP mode.
     */
    public static boolean enterPipIfSupported(Activity activity, View customView) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity == null) {
            return false;
        }
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
        boolean autoPip = sp.getBoolean("sp_auto_pip", true);
        if (!autoPip) return false;

        try {
            PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();
            if (customView != null && customView.getWidth() > 0 && customView.getHeight() > 0) {
                Rational aspectRatio = new Rational(customView.getWidth(), customView.getHeight());
                builder.setAspectRatio(aspectRatio);
            } else {
                builder.setAspectRatio(new Rational(16, 9));
            }
            return activity.enterPictureInPictureMode(builder.build());
        } catch (Exception e) {
            return false;
        }
    }

    private class MediaJsInterface {
        @JavascriptInterface
        public void triggerPip() {
            if (context instanceof Activity) {
                Activity act = (Activity) context;
                act.runOnUiThread(() -> {
                    enterPipIfSupported(act, webView);
                });
            }
        }

        @JavascriptInterface
        public void onMediaStateChanged(boolean isPlaying, String title, double positionMs, double durationMs) {
            if (listener != null) {
                listener.onMediaPlayingStateChanged(isPlaying);
                if (isPlaying) {
                    listener.onMediaPlay(title, (long) positionMs, (long) durationMs);
                } else {
                    listener.onMediaPause((long) positionMs, (long) durationMs);
                }
            }
        }

        @JavascriptInterface
        public void onMediaProgress(double positionMs, double durationMs) {
            if (listener != null) {
                listener.onMediaProgress((long) positionMs, (long) durationMs);
            }
        }

        @JavascriptInterface
        public void onVideoDimensions(int width, int height) {
            if (context instanceof Activity) {
                Activity act = (Activity) context;
                act.runOnUiThread(() -> {
                    if (listener != null) {
                        listener.onVideoDimensionsChanged(width, height);
                    }
                });
            }
        }
    }
}
