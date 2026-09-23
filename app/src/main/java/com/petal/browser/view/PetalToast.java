package com.petal.browser.view;

import android.content.Context;
import androidx.annotation.StringRes;

/**
 * Petal's pure Material 3 Expressive transient toast/snackbar dispatching facade.
 * Replaces the legacy NinjaToast component with Petal branding while preserving
 * seamless backwards compatibility across the codebase.
 */
public final class PetalToast {

    private PetalToast() {}

    public static void show(Context context, @StringRes int stringResId) {
        NinjaToast.show(context, stringResId);
    }

    public static void show(Context context, String text) {
        NinjaToast.show(context, text);
    }

    public static void show(Context context, String text, int duration) {
        NinjaToast.show(context, text, duration);
    }
}
