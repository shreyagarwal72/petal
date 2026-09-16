package com.petal.browser.browser;

import android.content.Context;
import androidx.annotation.Nullable;
import com.petal.browser.PetalApplication;
import com.petal.browser.engine.petal.blocking.ContentBlocker;

/**
 * Modern Cookie & Consent Banner Blocker for Petal Browser.
 * Integrates directly with {@link PetalAdBlockEngine} and {@link ContentBlocker},
 * removing slow runtime HTTP downloads and Toast popups on first launch.
 */
public class BannerBlock {

    public BannerBlock(Context context) {
        // Initialization handled via ContentBlocker and PetalAdBlockEngine
        if (context != null) {
            PetalAdBlockEngine.ensureInitialized(context.getApplicationContext());
        }
    }

    public static void downloadBanners(final Context context) {
        // Obsolete network download replaced by modern bundled ContentBlocker
        if (context != null) {
            PetalAdBlockEngine.ensureInitialized(context.getApplicationContext());
        }
    }

    @Nullable
    public static String getBannerBlockScriptPageStarted() {
        return null;
    }

    @Nullable
    public static String getBannerBlockScriptPageFinished() {
        try {
            Context appCtx = PetalApplication.getInstance();
            if (appCtx != null) {
                ContentBlocker blocker = PetalAdBlockEngine.getContentBlocker(appCtx);
                if (blocker != null) {
                    return blocker.getConsentScript();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
