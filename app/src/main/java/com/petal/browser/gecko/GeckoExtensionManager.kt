/*
 * GeckoExtensionManager.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Scaffolds Mozilla WebExtension support via GeckoRuntime's
 * WebExtensionController API.
 *
 * Responsibilities:
 *   • Install built-in extensions bundled in app assets (assets/extensions/)
 *   • Install remote extensions from a .xpi URL
 *   • Implement WebExtensionController.PromptDelegate to handle runtime
 *     install and update permission prompts on the main thread
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.gecko

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

private const val TAG = "GeckoExtensionManager"

/**
 * Manages GeckoView WebExtension lifecycle for the Petal browser.
 *
 * Usage (call once after GeckoRuntime is initialised):
 * ```kotlin
 * GeckoExtensionManager.init(context)
 * ```
 */
object GeckoExtensionManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Lazily resolved controller from the shared runtime. */
    private val controller: WebExtensionController
        get() = GeckoRuntimeHolder.runtime.webExtensionController

    // ── Initialisation ────────────────────────────────────────────────────

    /**
     * Registers the [PetalExtensionPromptDelegate] and installs all built-in
     * extensions bundled under [assets/extensions/].
     *
     * @param context used to enumerate the assets directory.
     */
    fun init(context: Context) {
        if (!GeckoRuntimeHolder.isInitialized) {
            Log.w(TAG, "init() called before GeckoRuntime — skipping extension setup")
            return
        }
        controller.promptDelegate = PetalExtensionPromptDelegate()
        installBuiltInExtensions(context)
    }

    // ── Built-in extensions from assets ───────────────────────────────────

    /**
     * Scans [assets/extensions/] and installs any subfolder as a packaged
     * extension using the `resource://android/assets/extensions/<id>/`
     * protocol that GeckoView understands natively.
     */
    private fun installBuiltInExtensions(context: Context) {
        scope.launch {
            try {
                val extensionDirs = context.assets.list("extensions") ?: return@launch
                for (extId in extensionDirs) {
                    val uri = "resource://android/assets/extensions/$extId/"
                    installExtension(uri, builtIn = true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enumerate built-in extensions", e)
            }
        }
    }

    // ── Remote .xpi install ───────────────────────────────────────────────

    /**
     * Installs an extension from a remote .xpi file URL.
     *
     * This is a suspend-safe fire-and-forget call that chains GeckoResult
     * callbacks onto the Gecko main thread.
     *
     * @param xpiUrl HTTPS URL pointing to a signed .xpi WebExtension.
     */
    fun installFromRemoteXpi(xpiUrl: String) {
        installExtension(xpiUrl, builtIn = false)
    }

    // ── Core install logic ────────────────────────────────────────────────

    private fun installExtension(uri: String, builtIn: Boolean) {
        Log.i(TAG, "Installing extension: $uri (builtIn=$builtIn)")

        val result: GeckoResult<WebExtension> = if (builtIn) {
            controller.installBuiltIn(uri)
        } else {
            controller.install(uri)
        }

        result
            .then<WebExtension> { ext ->
                Log.i(TAG, "Extension installed: id=${ext?.id} name=${ext?.metaData?.name}")
                GeckoResult.fromValue(ext)
            }
            .exceptionally<WebExtension> { throwable ->
                Log.e(TAG, "Extension install failed for $uri", throwable)
                null
            }
    }

    // ── Uninstall ─────────────────────────────────────────────────────────

    /**
     * Uninstalls a previously installed extension.
     *
     * @param extension the [WebExtension] instance to remove.
     */
    fun uninstall(extension: WebExtension) {
        controller.uninstall(extension)
            .then<Void> {
                Log.i(TAG, "Extension uninstalled: ${extension.id}")
                GeckoResult.fromValue(null)
            }
            .exceptionally<Void> { throwable ->
                Log.e(TAG, "Extension uninstall failed", throwable)
                null
            }
    }
}

// ── Permission prompt delegate ────────────────────────────────────────────

/**
 * Handles GeckoView runtime permission prompts for extension install and
 * update events.
 *
 * For now, Petal auto-grants all install/update prompts because the only
 * extensions installed are either built-in (bundled with the app) or
 * explicitly requested by the user via [GeckoExtensionManager.installFromRemoteXpi].
 *
 * A future version can surface a Compose dialog before calling [allow()].
 */
private class PetalExtensionPromptDelegate : WebExtensionController.PromptDelegate {

    override fun onInstallPromptRequest(
        extension: WebExtension,
        permissions: Array<String>,
        origins: Array<String>,
        dataCollectionPermissions: Array<String>
    ): GeckoResult<WebExtension.PermissionPromptResponse> {
        Log.i(TAG, "onInstallPromptRequest: ${extension.id} — auto-granting")
        return GeckoResult.fromValue(
            WebExtension.PermissionPromptResponse(
                true,  // isPermissionsGranted
                true,  // isPrivateModeGranted
                false  // isTechnicalAndInteractionDataGranted
            )
        )
    }

    override fun onUpdatePrompt(
        extension: WebExtension,
        newPermissions: Array<String>,
        newOrigins: Array<String>,
        newDataCollectionPermissions: Array<String>
    ): GeckoResult<AllowOrDeny> {
        Log.i(
            TAG,
            "onUpdatePrompt: ${extension.id} → new permissions=${newPermissions.toList()}, " +
                "new origins=${newOrigins.toList()}"
        )
        // Auto-grant updates for built-in and user-approved extensions.
        return GeckoResult.allow()
    }
}
