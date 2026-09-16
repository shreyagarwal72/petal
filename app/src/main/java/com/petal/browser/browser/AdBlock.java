package com.petal.browser.browser;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Modern facade bridging legacy calls to {@link PetalAdBlockEngine}.
 * Eliminates legacy file I/O, heavy HashSet allocations, and redundant network downloads.
 */
public class AdBlock {

    public AdBlock(Context context) {
        if (context != null) {
            PetalAdBlockEngine.ensureInitialized(context.getApplicationContext());
        }
    }

    public static String getHostsDate(Context context) {
        return "Petal AdBlock Engine (Active)";
    }

    public static void downloadHosts(final Context context) {
        if (context != null) {
            PetalAdBlockEngine.ensureInitialized(context.getApplicationContext());
        }
    }

    public static String getAdHidingScript() {
        return PetalAdBlockEngine.getuBlockCosmeticAndScriptletPayload("");
    }

    public boolean isAd(@Nullable String url) {
        if (url == null || url.length() < 8) return false;
        Context appCtx = com.petal.browser.PetalApplication.getInstance();
        if (appCtx != null) {
            return PetalAdBlockEngine.shouldBlockUrl(appCtx, url, null);
        }
        return false;
    }
}
