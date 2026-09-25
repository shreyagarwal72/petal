package com.petal.browser.extensions

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.petal.browser.R
import mozilla.components.concept.engine.webextension.WebExtension

/**
 * PetalExtensionPermissionDialog
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 dialog prompting users before granting permissions to WebExtensions.
 */
object PetalExtensionPermissionDialog {

    fun show(
        context: Context,
        extensionName: String,
        permissions: List<String>,
        origins: List<String> = emptyList(),
        isUpdate: Boolean = false,
        onResponse: (granted: Boolean) -> Unit
    ) {
        val messageBuilder = StringBuilder()
        if (isUpdate) {
            messageBuilder.append("An update for ").append(extensionName).append(" requires new permissions:\n\n")
        } else {
            messageBuilder.append(extensionName).append(" needs your permission to:\n\n")
        }

        if (permissions.isNotEmpty()) {
            permissions.forEach { perm ->
                messageBuilder.append("• ").append(formatPermissionName(perm)).append("\n")
            }
        }

        if (origins.isNotEmpty()) {
            if (permissions.isNotEmpty()) messageBuilder.append("\n")
            messageBuilder.append("Access data on:\n")
            origins.forEach { origin ->
                messageBuilder.append("• ").append(origin).append("\n")
            }
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(if (isUpdate) "Update Permissions" else "Add Extension")
            .setMessage(messageBuilder.toString().trimEnd())
            .setPositiveButton(if (isUpdate) "Update" else "Add") { dialog, _ ->
                dialog.dismiss()
                onResponse(true)
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
                onResponse(false)
            }
            .setOnCancelListener {
                onResponse(false)
            }
            .show()
    }

    private fun formatPermissionName(permission: String): String {
        return when (permission) {
            "tabs" -> "Access browser tabs and URLs"
            "cookies" -> "Access and manage site cookies"
            "history" -> "Access browsing history"
            "bookmarks" -> "Access and modify bookmarks"
            "downloads" -> "Manage and open downloads"
            "storage" -> "Store unlimited data locally"
            "webRequest", "webRequestBlocking" -> "Inspect and block network requests"
            "notifications" -> "Display notifications"
            "clipboardRead" -> "Read data from the clipboard"
            "clipboardWrite" -> "Copy data to the clipboard"
            "privacy" -> "Modify browser privacy settings"
            "management" -> "Manage installed extensions"
            else -> permission.replaceFirstChar { it.uppercase() }
        }
    }
}
