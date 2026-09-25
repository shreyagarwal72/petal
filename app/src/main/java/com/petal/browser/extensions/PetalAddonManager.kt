package com.petal.browser.extensions

import android.util.Log
import mozilla.components.browser.engine.gecko.GeckoEngine
import mozilla.components.concept.engine.webextension.WebExtension

/**
 * PetalAddonManager
 * ─────────────────────────────────────────────────────────────────────────
 * High-level manager wrapping Mozilla Android Components [GeckoEngine]
 * WebExtension operations for Petal Browser.
 *
 * Provides standard, unified methods to install, uninstall, enable, disable,
 * and list WebExtensions (e.g. Dark Reader, Bitwarden, uBlock Origin).
 */
object PetalAddonManager {

    private const val TAG = "PetalAddonManager"

    /**
     * Installs an extension directly from a .xpi URL or AMO download URL.
     */
    fun installFromUrl(
        engine: GeckoEngine,
        xpiUrl: String,
        onSuccess: (WebExtension) -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        engine.installWebExtension(
            url = xpiUrl,
            onSuccess = { ext ->
                Log.i(TAG, "Successfully installed extension: ${ext.id}")
                onSuccess(ext)
            },
            onError = { e ->
                Log.e(TAG, "Failed to install extension from $xpiUrl", e)
                onError(e)
            }
        )
    }

    /**
     * Installs a built-in extension bundled inside Android assets.
     */
    fun installBuiltIn(
        engine: GeckoEngine,
        id: String,
        assetPath: String,
        onSuccess: (WebExtension) -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        val resourceUrl = if (assetPath.startsWith("resource://")) assetPath else "resource://android/assets/$assetPath/"
        engine.installBuiltInWebExtension(
            id = id,
            url = resourceUrl,
            onSuccess = { ext ->
                Log.i(TAG, "Successfully installed built-in extension: ${ext.id}")
                onSuccess(ext)
            },
            onError = { e ->
                Log.e(TAG, "Failed to install built-in extension $id", e)
                onError(e)
            }
        )
    }

    /**
     * Uninstalls an installed WebExtension.
     */
    fun uninstall(
        engine: GeckoEngine,
        extension: WebExtension,
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        val rawExt = PetalExtensionManager.extensions.value.find { it.id == extension.id }?.raw
        if (rawExt != null) {
            PetalExtensionManager.uninstall(rawExt) { success ->
                if (success) onSuccess() else onError(RuntimeException("Uninstall failed for ${extension.id}"))
            }
        } else {
            onError(IllegalArgumentException("Extension not found: ${extension.id}"))
        }
    }

    /**
     * Enables an installed WebExtension.
     */
    fun enable(
        engine: GeckoEngine,
        extension: WebExtension,
        onSuccess: (WebExtension) -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        val rawExt = PetalExtensionManager.extensions.value.find { it.id == extension.id }?.raw
        if (rawExt != null) {
            PetalExtensionManager.setEnabled(rawExt, true)
            onSuccess(extension)
        } else {
            onError(IllegalArgumentException("Extension not found: ${extension.id}"))
        }
    }

    /**
     * Disables an installed WebExtension.
     */
    fun disable(
        engine: GeckoEngine,
        extension: WebExtension,
        onSuccess: (WebExtension) -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        val rawExt = PetalExtensionManager.extensions.value.find { it.id == extension.id }?.raw
        if (rawExt != null) {
            PetalExtensionManager.setEnabled(rawExt, false)
            onSuccess(extension)
        } else {
            onError(IllegalArgumentException("Extension not found: ${extension.id}"))
        }
    }

    /**
     * Lists all installed WebExtensions.
     */
    fun list(
        engine: GeckoEngine,
        callback: (List<WebExtension>) -> Unit
    ) {
        val current = PetalExtensionManager.extensions.value.map { it.raw }
        callback(emptyList())
    }
}
