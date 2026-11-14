package com.petal.browser.ui.dialog

import android.app.Activity
import com.petal.browser.ui.components.PetalAlertDialogBuilder

/**
 * PetalSlowScriptDialog
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive dialog prompting user when a JavaScript script is hung
 * or taking too long (GeckoSession.ContentDelegate.onSlowScript).
 */
object PetalSlowScriptDialog {
    @JvmStatic
    fun show(
        activity: Activity,
        scriptFileName: String?,
        onWait: () -> Unit,
        onStop: () -> Unit
    ) {
        if (activity.isFinishing || (android.os.Build.VERSION.SDK_INT >= 17 && activity.isDestroyed)) {
            onWait()
            return
        }
        val displayScript = scriptFileName?.takeIf { it.isNotBlank() } ?: "A script on this page"
        PetalAlertDialogBuilder(activity)
            .setTitle("Unresponsive Script")
            .setMessage("$displayScript is taking too long to finish. Would you like to wait for it or stop it now?")
            .setPositiveButton("Wait") { dialog, _ ->
                dialog.dismiss()
                onWait()
            }
            .setNegativeButton("Stop") { dialog, _ ->
                dialog.dismiss()
                onStop()
            }
            .setCancelable(false)
            .show()
    }
}
