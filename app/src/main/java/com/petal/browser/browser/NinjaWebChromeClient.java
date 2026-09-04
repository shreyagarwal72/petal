package com.petal.browser.browser;

import static android.content.ContentValues.TAG;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.petal.browser.R;
import com.petal.browser.unit.BrowserUnit;
import com.petal.browser.unit.HelperUnit;
import com.petal.browser.view.NinjaToast;
import com.petal.browser.view.NinjaWebView;

public class NinjaWebChromeClient extends WebChromeClient {

    private final NinjaWebView ninjaWebView;

    public NinjaWebChromeClient(NinjaWebView ninjaWebView) {
        super();
        this.ninjaWebView = ninjaWebView;
    }

    @Override
    public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
        new MaterialAlertDialogBuilder(ninjaWebView.getContext())
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm())
                .show();
        return true;
    }

    @Override
    public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
        new MaterialAlertDialogBuilder(ninjaWebView.getContext())
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm())
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> result.cancel())
                .show();
        return true;
    }

    @Override
    public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, final JsPromptResult result) {
        final EditText input = new EditText(ninjaWebView.getContext());
        input.setText(defaultValue);
        FrameLayout container = new FrameLayout(ninjaWebView.getContext());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        int margin = (int) (20 * ninjaWebView.getContext().getResources().getDisplayMetrics().density);
        params.leftMargin = margin;
        params.rightMargin = margin;
        input.setLayoutParams(params);
        container.addView(input);

        new MaterialAlertDialogBuilder(ninjaWebView.getContext())
                .setTitle(message)
                .setView(container)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm(input.getText().toString()))
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> result.cancel())
                .show();
        return true;
    }
    @Override
    public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
        if (consoleMessage.message().contains("NotAllowedError: Write permission denied.")) {  //this error occurs when user copies to clipboard
            NinjaToast.show(ninjaWebView.getContext(), R.string.app_error_copy);
            return true;
        }
        return false;
    }

    @Override
    public void onProgressChanged(WebView view, int progress) {
        super.onProgressChanged(view, progress);
        String url = ninjaWebView.getUrl();
        String title = ninjaWebView.getTitle();
        ninjaWebView.updateTitle(progress);
        assert title != null;
        if (title.isEmpty()) ninjaWebView.updateTitle(HelperUnit.domain(url), url);
        else ninjaWebView.updateTitle(title,url);
    }
    @Override
    public boolean onCreateWindow(WebView view, boolean dialog, boolean userGesture, android.os.Message resultMsg) {
        if (!userGesture) {
            return false;
        }
        Context context = view.getContext();
        if (context instanceof com.petal.browser.activity.BrowserActivity) {
            com.petal.browser.activity.BrowserActivity activity = (com.petal.browser.activity.BrowserActivity) context;
            boolean isIncognito = ninjaWebView != null && ninjaWebView.isIncognito();
            NinjaWebView newTabWebView = activity.addAlbumForPopup(activity.getString(R.string.app_name), isIncognito);
            if (newTabWebView != null) {
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(newTabWebView);
                resultMsg.sendToTarget();
                return true;
            }
        }
        return false;
    }
    @Override
    public void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback) {
        NinjaWebView.getBrowserController().onShowCustomView(view, callback);
        super.onShowCustomView(view, callback);
    }
    @Override
    public void onHideCustomView() {
        NinjaWebView.getBrowserController().onHideCustomView();
        super.onHideCustomView();
    }
    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
        Context context = ninjaWebView.getContext();
        if (context instanceof com.petal.browser.activity.BrowserActivity) {
            ((com.petal.browser.activity.BrowserActivity) context).showFileChooser(filePathCallback, fileChooserParams);
            return true;
        } else if (NinjaWebView.getBrowserController() != null) {
            NinjaWebView.getBrowserController().showFileChooser(filePathCallback, fileChooserParams);
            return true;
        }
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
        }
        return false;
    }
    @Override
    public void onGeolocationPermissionsShowPrompt(final String origin, final GeolocationPermissions.Callback callback) {
        Activity activity = (Activity) ninjaWebView.getContext();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
        com.petal.browser.ui.components.PetalPermissionDialogBridge.showPermissionPrompt(
                activity,
                com.petal.browser.ui.components.PetalPermissionType.LOCATION,
                origin,
                () -> {
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_location", true).apply();
                    HelperUnit.grantPermissionsLoc(activity);
                    callback.invoke(origin, true, true);
                },
                () -> {
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_location", false).apply();
                    callback.invoke(origin, false, false);
                }
        );
    }

    @Override
    public void onPermissionRequest(final PermissionRequest request) {
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ninjaWebView.getContext());
        final Activity activity = (Activity) ninjaWebView.getContext();
        final String[] resources = request.getResources();
        final String originStr = (request.getOrigin() != null) ? request.getOrigin().toString() : "Webpage";

        for (final String resource : resources) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                com.petal.browser.ui.components.PetalPermissionDialogBridge.showPermissionPrompt(
                        activity,
                        com.petal.browser.ui.components.PetalPermissionType.CAMERA,
                        originStr,
                        () -> {
                            sp.edit().putBoolean(NinjaWebView.getProfile() + "_camera", true).apply();
                            HelperUnit.grantPermissionsCamera(activity);
                            if (ninjaWebView.getSettings().getMediaPlaybackRequiresUserGesture()) {
                                ninjaWebView.getSettings().setMediaPlaybackRequiresUserGesture(false);
                            }
                            request.grant(new String[]{resource});
                        },
                        () -> {
                            sp.edit().putBoolean(NinjaWebView.getProfile() + "_camera", false).apply();
                            request.deny();
                        }
                );
            } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                com.petal.browser.ui.components.PetalPermissionDialogBridge.showPermissionPrompt(
                        activity,
                        com.petal.browser.ui.components.PetalPermissionType.MICROPHONE,
                        originStr,
                        () -> {
                            sp.edit().putBoolean(NinjaWebView.getProfile() + "_microphone", true).apply();
                            HelperUnit.grantPermissionsMic(activity);
                            request.grant(new String[]{resource});
                        },
                        () -> {
                            sp.edit().putBoolean(NinjaWebView.getProfile() + "_microphone", false).apply();
                            request.deny();
                        }
                );
            } else if (PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(resource)) {
                com.petal.browser.ui.components.PetalPermissionDialogBridge.showPermissionPrompt(
                        activity,
                        com.petal.browser.ui.components.PetalPermissionType.DRM,
                        originStr,
                        () -> {
                            sp.edit().putBoolean(NinjaWebView.getProfile() + "_drm", true).apply();
                            request.grant(new String[]{resource});
                        },
                        () -> {
                            sp.edit().putBoolean(NinjaWebView.getProfile() + "_drm", false).apply();
                            request.deny();
                        }
                );
            }
        }
    }
    @Override
    public void onReceivedIcon(WebView view, Bitmap icon) {
        String url = ninjaWebView.getUrl();
        ImageView iv = ninjaWebView.getAlbumView().findViewById(R.id.item_icon);
        if (url == null) {
            iv.setImageResource(R.drawable.icon_image_broken);
        } else if (url.equals("about:blank")) {
            iv.setImageResource(R.drawable.icon_image_broken);
        } else if (BrowserUnit.isURL(url)) {
            ninjaWebView.setFavicon(icon);
            ninjaWebView.updateFavicon(ninjaWebView.getUrl());
        } else {
            iv.setImageResource(R.drawable.icon_image_broken);
        }
        super.onReceivedIcon(view, icon);
    }
    @Override
    public void onReceivedTitle(WebView view, String sTitle) {
        super.onReceivedTitle(view, sTitle);
        String url = ninjaWebView.getUrl();
        ImageView iv = ninjaWebView.getAlbumView().findViewById(R.id.item_icon);
        if (url == null) {
            iv.setImageResource(R.drawable.icon_image_broken);
        } else if (url.equals("about:blank")) {
            iv.setImageResource(R.drawable.icon_image_broken);
        } else if (BrowserUnit.isURL(url)) {
            ninjaWebView.updateFavicon(ninjaWebView.getUrl());
        } else {
            iv.setImageResource(R.drawable.icon_image_broken);
        }
    }
}