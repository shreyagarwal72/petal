package com.petal.browser.view;

import android.content.Context;
import androidx.annotation.StringRes;

/**
 * Legacy alias for PetalToast.
 * All toast / snackbar dispatches now route directly through PetalToast.
 *
 * @deprecated Use {@link PetalToast} instead.
 */
@Deprecated
public class NinjaToast {

    public static void show(Context context, @StringRes int stringResId) {
        PetalToast.show(context, stringResId);
    }

    public static void show(Context context, String text) {
        PetalToast.show(context, text);
    }

    public static void show(Context context, String text, int duration) {
        PetalToast.show(context, text, duration);
    }
}
