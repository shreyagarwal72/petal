package com.petal.browser.view;

import android.app.Activity;
import android.content.Context;
import androidx.annotation.StringRes;

import com.petal.browser.ui.components.PetalSnackbarDispatcher;
import com.petal.browser.ui.components.PetalSnackbarType;

/**
 * Petal's single source of truth for transient popup messages.
 * All messages dispatch directly through PetalSnackbarDispatcher to be displayed
 * via the Material 3 Expressive PetalSnackbar component.
 */
public final class PetalToast {

    public static final int LENGTH_SHORT = 0;
    public static final int LENGTH_LONG = 1;

    private PetalToast() {}

    public static void show(Context context, @StringRes int stringResId) {
        if (context == null) return;
        show(context, context.getString(stringResId));
    }

    public static void show(Context context, String text) {
        show(context, text, LENGTH_SHORT);
    }

    public static void show(Context context, String text, int duration) {
        show(context, text, duration, null, null);
    }

    public static void show(Context context, String text, int duration, String actionLabel, Runnable onAction) {
        if (text == null || text.trim().isEmpty()) return;

        // Auto-infer message type from text if appropriate
        PetalSnackbarType type = PetalSnackbarType.INFO;
        String lower = text.toLowerCase();
        if (lower.contains("failed") || lower.contains("error") || lower.contains("disabled") || lower.contains("crashed")) {
            type = PetalSnackbarType.WARNING;
        } else if (lower.contains("success") || lower.contains("saved") || lower.contains("done") || lower.contains("installed") || lower.contains("enabled")) {
            type = PetalSnackbarType.SUCCESS;
        }

        long durationMs = (duration == LENGTH_LONG) ? 4500L : 3000L;
        kotlin.jvm.functions.Function0<kotlin.Unit> actionCallback = onAction != null ? () -> {
            onAction.run();
            return kotlin.Unit.INSTANCE;
        } : null;

        PetalSnackbarDispatcher.INSTANCE.show(
                text,
                type,
                actionLabel,
                durationMs,
                actionCallback
        );
    }

    public static void show(Context context, String text, PetalSnackbarType type) {
        show(context, text, type, null, null);
    }

    public static void show(Context context, String text, PetalSnackbarType type, String actionLabel, Runnable onAction) {
        if (text == null || text.trim().isEmpty()) return;

        long durationMs = 3200L;
        kotlin.jvm.functions.Function0<kotlin.Unit> actionCallback = onAction != null ? () -> {
            onAction.run();
            return kotlin.Unit.INSTANCE;
        } : null;

        PetalSnackbarDispatcher.INSTANCE.show(
                text,
                type,
                actionLabel,
                durationMs,
                actionCallback
        );
    }
}
