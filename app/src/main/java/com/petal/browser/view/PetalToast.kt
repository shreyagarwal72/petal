package com.petal.browser.view

import android.content.Context
import androidx.annotation.StringRes
import com.petal.browser.ui.components.PetalSnackbarDispatcher
import com.petal.browser.ui.components.PetalSnackbarType

/**
 * Petal's single source of truth for transient popup messages.
 * All messages dispatch directly through PetalSnackbarDispatcher to be displayed
 * via the Material 3 Expressive PetalSnackbar component.
 */
object PetalToast {

    const val LENGTH_SHORT = 0
    const val LENGTH_LONG = 1

    @JvmStatic
    fun show(context: Context?, @StringRes stringResId: Int) {
        if (context == null) return
        show(context, context.getString(stringResId))
    }

    @JvmStatic
    fun show(context: Context?, @StringRes stringResId: Int, duration: Int) {
        if (context == null) return
        show(context, context.getString(stringResId), duration)
    }

    @JvmStatic
    fun show(
        context: Context?,
        @StringRes stringResId: Int,
        duration: Int,
        actionLabel: String?,
        onAction: (() -> Unit)?
    ) {
        if (context == null) return
        show(context, context.getString(stringResId), duration, actionLabel, onAction)
    }

    @JvmStatic
    fun show(
        context: Context?,
        @StringRes stringResId: Int,
        duration: Int,
        actionLabel: String?,
        onAction: Runnable?
    ) {
        if (context == null) return
        val actionCallback: (() -> Unit)? = if (onAction != null) { { onAction.run() } } else null
        show(context, context.getString(stringResId), duration, actionLabel, actionCallback)
    }

    @JvmStatic
    fun show(context: Context?, text: String?) {
        show(context, text, LENGTH_SHORT)
    }

    @JvmStatic
    fun show(context: Context?, text: String?, duration: Int) {
        show(context, text, duration, null, null as (() -> Unit)?)
    }

    @JvmStatic
    fun show(
        context: Context?,
        text: String?,
        duration: Int,
        actionLabel: String?,
        onAction: (() -> Unit)?
    ) {
        if (text.isNullOrBlank()) return

        var type = PetalSnackbarType.INFO
        val lower = text.lowercase()
        if (lower.contains("failed") || lower.contains("error") || lower.contains("disabled") || lower.contains("crashed")) {
            type = PetalSnackbarType.WARNING
        } else if (lower.contains("success") || lower.contains("saved") || lower.contains("done") || lower.contains("installed") || lower.contains("enabled")) {
            type = PetalSnackbarType.SUCCESS
        }

        val durationMs = if (duration == LENGTH_LONG) 4500L else 3000L

        PetalSnackbarDispatcher.show(
            message = text,
            type = type,
            actionLabel = actionLabel,
            durationMs = durationMs,
            onAction = onAction
        )
    }

    @JvmStatic
    fun show(
        context: Context?,
        text: String?,
        duration: Int,
        actionLabel: String?,
        onAction: Runnable?
    ) {
        val actionCallback: (() -> Unit)? = if (onAction != null) { { onAction.run() } } else null
        show(context, text, duration, actionLabel, actionCallback)
    }

    @JvmStatic
    fun show(context: Context?, text: String?, type: PetalSnackbarType) {
        show(context, text, type, null, null as (() -> Unit)?)
    }

    @JvmStatic
    fun show(
        context: Context?,
        text: String?,
        type: PetalSnackbarType,
        actionLabel: String?,
        onAction: (() -> Unit)?
    ) {
        if (text.isNullOrBlank()) return

        val durationMs = 3200L

        PetalSnackbarDispatcher.show(
            message = text,
            type = type,
            actionLabel = actionLabel,
            durationMs = durationMs,
            onAction = onAction
        )
    }

    @JvmStatic
    fun show(
        context: Context?,
        text: String?,
        type: PetalSnackbarType,
        actionLabel: String?,
        onAction: Runnable?
    ) {
        val actionCallback: (() -> Unit)? = if (onAction != null) { { onAction.run() } } else null
        show(context, text, type, actionLabel, actionCallback)
    }
}
