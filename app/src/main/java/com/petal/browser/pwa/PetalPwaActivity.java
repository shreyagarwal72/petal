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
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.petal.browser.R;
import com.petal.browser.activity.BrowserActivity;
import com.petal.browser.unit.HelperUnit;
import com.petal.browser.view.PetalToast;
import com.petal.browser.view.PetalGeckoView;

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
    private PetalGeckoView geckoView;
    private String pwaUrl = "about:blank";
    private String pwaTitle = "Web App";
    private String themeColorHex = "#FFFFFF";
    private String displayMode = "standalone";
    private String offlineArchivePath = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

            boolean isFullscreen = "fullscreen".equalsIgnoreCase(displayMode);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    if (isFullscreen) {
                        controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                        controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                    } else {
                        controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    }
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
        } catch (Exception e) {
            // Fallback gracefully
        }
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
            boolean isFullscreen = "fullscreen".equalsIgnoreCase(displayMode);

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
            if (fabBubbleView != null && fabBubbleView.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams fabParams =
                        (ViewGroup.MarginLayoutParams) fabBubbleView.getLayoutParams();
                fabParams.bottomMargin = fabBaseMarginPx + bottomInset;
                fabBubbleView.setLayoutParams(fabParams);
            }

            return windowInsets;
        });
        ViewCompat.requestApplyInsets(pwaRoot);
    }

    private void setupGeckoView() {
        if (contentFrame == null) return;

        geckoView = new PetalGeckoView(this);
        geckoView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        contentFrame.addView(geckoView);

        // Load offline archive or target web app URL
        String targetUrl = pwaUrl;
        if (offlineArchivePath != null && !HelperUnit.isNetworkAvailable(this)) {
            File archive = new File(offlineArchivePath);
            if (archive.exists()) {
                targetUrl = "file://" + archive.getAbsolutePath();
            }
        }

        if (targetUrl != null && !targetUrl.isEmpty() && !"about:blank".equalsIgnoreCase(targetUrl)) {
            geckoView.loadUrl(targetUrl);
        }
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (geckoView != null && geckoView.canGoBack()) {
                    geckoView.goBack();
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

        new MaterialAlertDialogBuilder(this)
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
        super.onResume();
        if (geckoView != null) geckoView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (geckoView != null) geckoView.onPause();
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
        super.onDestroy();
    }
}
