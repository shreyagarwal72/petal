package com.petal.browser.pwa;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.petal.browser.R;
import com.petal.browser.activity.BrowserActivity;
import com.petal.browser.unit.PetalAndroidUtils;
import com.petal.browser.view.PetalToast;
import com.petal.browser.browser.PetalTabViewController;
import com.petal.browser.engine.gecko.PetalEngineStore;

import java.io.File;

/**
 * PetalPwaActivity
 *
 * Dedicated standalone application shell for Progressive Web Apps installed from Petal
 * or launched via external Web App intents. Runs isolated in its own task window (documentLaunchMode="intoExisting"),
 * with full GeckoView rendering, service worker offline persistence, adaptive status/nav bar coloring,
 * and zero browser UI chrome for a true native-app experience.
 */
public class PetalPwaActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "pwa_url";
    public static final String EXTRA_TITLE = "pwa_title";
    public static final String EXTRA_THEME_COLOR = "pwa_theme_color";
    public static final String EXTRA_BG_COLOR = "pwa_background_color";
    public static final String EXTRA_DISPLAY = "pwa_display";
    public static final String EXTRA_OFFLINE_ARCHIVE = "offline_archive_path";

    private View pwaRoot;
    private FrameLayout contentFrame;
    private View progressBarComposeView;
    private View fabBubbleView;
    private int fabBaseMarginPx = 0;
    private PetalTabViewController geckoView;
    private String pwaUrl = "about:blank";
    private String pwaTitle = "Web App";
    private String themeColorHex = "#FFFFFF";
    private String displayMode = "standalone";
    private String offlineArchivePath = null;
    private String pwaTabId = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        com.petal.browser.unit.PetalAndroidUtils.initTheme(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        setContentView(R.layout.activity_pwa);

        pwaRoot = findViewById(R.id.pwa_root);
        contentFrame = findViewById(R.id.pwa_content_frame);
        progressBarComposeView = findViewById(R.id.pwa_progress_bar_compose);
        fabBubbleView = findViewById(R.id.pwa_fab_bubble);
        if (fabBubbleView != null && fabBubbleView.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            fabBaseMarginPx = ((ViewGroup.MarginLayoutParams) fabBubbleView.getLayoutParams()).bottomMargin;
        }
        handleIntent(getIntent());

        applyWindowConfiguration();
        setupWindowInsets();
        setupGeckoView();
        setupBackNavigation();
        setupFabMenu();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
        applyWindowConfiguration();
        if (pwaRoot != null) {
            ViewCompat.requestApplyInsets(pwaRoot);
        }
        if (geckoView != null && pwaUrl != null && !pwaUrl.isEmpty() && !"about:blank".equalsIgnoreCase(pwaUrl)) {
            geckoView.loadUrl(pwaUrl);
        }
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;

        Uri dataUri = intent.getData();
        if (dataUri != null) {
            pwaUrl = dataUri.toString();
        } else if (intent.hasExtra(EXTRA_URL)) {
            pwaUrl = intent.getStringExtra(EXTRA_URL);
        } else if (intent.hasExtra("url")) {
            pwaUrl = intent.getStringExtra("url");
        }

        if (intent.hasExtra(EXTRA_TITLE)) {
            pwaTitle = intent.getStringExtra(EXTRA_TITLE);
            setTitle(pwaTitle);
        }
        if (intent.hasExtra(EXTRA_THEME_COLOR)) {
            themeColorHex = intent.getStringExtra(EXTRA_THEME_COLOR);
        }
        if (intent.hasExtra(EXTRA_DISPLAY)) {
            displayMode = intent.getStringExtra(EXTRA_DISPLAY);
        }
        if (intent.hasExtra(EXTRA_OFFLINE_ARCHIVE)) {
            offlineArchivePath = intent.getStringExtra(EXTRA_OFFLINE_ARCHIVE);
        }
    }

    private void applyWindowConfiguration() {
        try {
            int parsedThemeColor = Color.WHITE;
            if (themeColorHex != null && !themeColorHex.trim().isEmpty()) {
                parsedThemeColor = Color.parseColor(themeColorHex);
            }

            // Standalone / Minimal UI: Apply window theme color and adjust system bar icons
            getWindow().setStatusBarColor(parsedThemeColor);
            getWindow().setNavigationBarColor(parsedThemeColor);

            boolean isDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            // Contrast luminance check
            double darkness = 1 - (0.299 * Color.red(parsedThemeColor) + 0.587 * Color.green(parsedThemeColor) + 0.114 * Color.blue(parsedThemeColor)) / 255;
            boolean lightStatusIcons = darkness >= 0.5;

            WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                    .setAppearanceLightStatusBars(!lightStatusIcons);
            WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                    .setAppearanceLightNavigationBars(!lightStatusIcons);

            // Take manual control of insets in every display mode (not just fullscreen) so we
            // can pad the web content / progress bar / FAB ourselves below. This also matters
            // because apps targeting Android 15+ get edge-to-edge enforced automatically, which
            // would otherwise draw the WebView content and controls straight under the bars.
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

            boolean isFullscreen = isPwaFullscreenMode();
            WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            if (insetsController != null) {
                if (isFullscreen) {
                    insetsController.hide(WindowInsetsCompat.Type.systemBars());
                    insetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                } else {
                    insetsController.show(WindowInsetsCompat.Type.systemBars());
                }
            } else if (isFullscreen) {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                );
            } else {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }

            if (fabBubbleView != null) {
                fabBubbleView.setVisibility(isFullscreen ? View.GONE : View.VISIBLE);
                fabBubbleView.setAlpha(isFullscreen ? 0f : 0.4f);
            }
        } catch (Exception e) {
            // Fallback gracefully
        }
    }

    private boolean isPwaFullscreenMode() {
        return "fullscreen".equalsIgnoreCase(displayMode)
                || PreferenceManager.getDefaultSharedPreferences(this).getBoolean("sp_pwa_default_fullscreen", true)
                || PreferenceManager.getDefaultSharedPreferences(this).getBoolean("sp_immersive_fullscreen", false);
    }

    /**
     * Standalone / minimal-ui PWAs keep the status and nav bars visible (tinted with the app's
     * theme color), so the web content, the loading indicator and the FAB bubble all need to be
     * padded/margined by the current system bar insets to avoid sitting underneath them.
     * Fullscreen-mode PWAs intentionally draw edge-to-edge, so no padding is applied there.
     */
    private void setupWindowInsets() {
        if (pwaRoot == null) return;

        ViewCompat.setOnApplyWindowInsetsListener(pwaRoot, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            boolean isFullscreen = isPwaFullscreenMode();
            boolean isBarsVisible = windowInsets.isVisible(WindowInsetsCompat.Type.systemBars());

            int topInset = isFullscreen ? 0 : systemBars.top;
            int bottomInset = isFullscreen ? 0 : systemBars.bottom;

            if (contentFrame != null) {
                contentFrame.setPadding(
                        contentFrame.getPaddingLeft(), topInset,
                        contentFrame.getPaddingRight(), bottomInset);
            }
            if (progressBarComposeView != null) {
                progressBarComposeView.setPadding(
                        progressBarComposeView.getPaddingLeft(), topInset,
                        progressBarComposeView.getPaddingRight(), progressBarComposeView.getPaddingBottom());
            }
            if (fabBubbleView != null) {
                if (isFullscreen) {
                    if (isBarsVisible) {
                        fabBubbleView.setVisibility(View.VISIBLE);
                        fabBubbleView.animate().alpha(0.85f).setDuration(220).start();
                    } else {
                        fabBubbleView.animate().alpha(0f).setDuration(220).withEndAction(() -> {
                            if (fabBubbleView != null && isPwaFullscreenMode()) {
                                fabBubbleView.setVisibility(View.GONE);
                            }
                        }).start();
                    }
                } else {
                    fabBubbleView.setVisibility(View.VISIBLE);
                    fabBubbleView.setAlpha(0.4f);
                }
                if (fabBubbleView.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams fabParams =
                            (ViewGroup.MarginLayoutParams) fabBubbleView.getLayoutParams();
                    fabParams.bottomMargin = fabBaseMarginPx + bottomInset;
                    fabBubbleView.setLayoutParams(fabParams);
                }
            }

            return windowInsets;
        });
        ViewCompat.requestApplyInsets(pwaRoot);
    }

    private void setupGeckoView() {
        if (contentFrame == null) return;

        // Load offline archive or target web app URL
        String targetUrl = pwaUrl;
        if (offlineArchivePath != null && !PetalAndroidUtils.isNetworkAvailable(this)) {
            File archive = new File(offlineArchivePath);
            if (archive.exists()) {
                targetUrl = "file://" + archive.getAbsolutePath();
            }
        }

        String effectiveUrl = (targetUrl != null && !targetUrl.isEmpty() && !"about:blank".equalsIgnoreCase(targetUrl))
                ? targetUrl : "about:blank";
        pwaTabId = "pwa_" + System.currentTimeMillis();
        kotlin.Pair<mozilla.components.browser.state.state.TabSessionState, mozilla.components.concept.engine.EngineSession> sessionPair =
            PetalEngineStore.createTabSession(
                this,
                pwaTabId,
                effectiveUrl,
                pwaTitle != null ? pwaTitle : "Petal PWA",
                false,
                true
            );

        geckoView = new PetalTabViewController(this);
        geckoView.bindTab(sessionPair.getFirst());
        geckoView.attachLifecycle(this);
        geckoView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        contentFrame.addView(geckoView);

        if (!"about:blank".equalsIgnoreCase(effectiveUrl)) {
            geckoView.loadUrl(effectiveUrl);
        }
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (geckoView != null && geckoView.canGoBack()) {
                    geckoView.goBack(true);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void setupFabMenu() {
        View fabBubble = findViewById(R.id.pwa_fab_bubble);
        if (fabBubble == null) return;

        fabBubble.setOnClickListener(v -> showPwaQuickActions());
    }

    private void showPwaQuickActions() {
        String[] options = new String[]{
                "Refresh",
                "Share App Link",
                "Open in Petal Browser",
                "Close App"
        };

        new com.petal.browser.ui.components.PetalAlertDialogBuilder(this)
                .setTitle(pwaTitle)
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            if (geckoView != null) geckoView.reload();
                            break;
                        case 1:
                            Intent share = new Intent(Intent.ACTION_SEND);
                            share.setType("text/plain");
                            share.putExtra(Intent.EXTRA_TEXT, pwaUrl);
                            startActivity(Intent.createChooser(share, "Share Web App"));
                            break;
                        case 2:
                            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(pwaUrl));
                            browserIntent.setComponent(new android.content.ComponentName(this, BrowserActivity.class));
                            browserIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(browserIntent);
                            finish();
                            break;
                        case 3:
                            finish();
                            break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onResume() {
        com.petal.browser.engine.gecko.PetalGeckoRuntime.setForegroundActivity(this);
        super.onResume();
        if (geckoView != null) geckoView.activate();
    }

    @Override
    protected void onPause() {
        if (com.petal.browser.engine.gecko.PetalGeckoRuntime.getForegroundActivity() == this) {
            com.petal.browser.engine.gecko.PetalGeckoRuntime.setForegroundActivity(null);
        }
        super.onPause();
        if (geckoView != null) geckoView.deactivate();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (com.petal.browser.engine.gecko.PetalGeckoRuntime.handleActivityResult(requestCode, resultCode, data)) {
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        com.petal.browser.ui.components.PetalRuntimePermissionBridge.handlePermissionsResult(requestCode, permissions, grantResults);
    }

    public void showFileChooser(android.webkit.ValueCallback<android.net.Uri[]> filePathCallback,
                                android.webkit.WebChromeClient.FileChooserParams fileChooserParams) {
        com.petal.browser.compose.file.PetalUploadChooser.show(
                this,
                filePathCallback,
                fileChooserParams,
                () -> {
                    android.util.Log.e("PetalPwaActivity", "Petal pickers failed to open");
                    return kotlin.Unit.INSTANCE;
                }
        );
    }

    @Override
    protected void onDestroy() {
        if (geckoView != null) {
            try {
                contentFrame.removeView(geckoView);
                geckoView.destroy();
            } catch (Exception ignored) {}
            geckoView = null;
        }
        if (pwaTabId != null) {
            com.petal.browser.engine.gecko.PetalEngineStore.removeTab(this, pwaTabId);
            pwaTabId = null;
        }
        super.onDestroy();
    }
}
