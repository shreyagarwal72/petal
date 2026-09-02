/*
 * GeckoExtensionManager.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Scaffolds Mozilla WebExtension support via GeckoRuntime's
 * WebExtensionController API.
 *
 * Responsibilities:
 *   • Install built-in extensions bundled in app assets (assets/extensions/)
 *   • Install remote extensions from a .xpi URL with state tracking & error handling
 *   • Implement WebExtensionController.PromptDelegate with in-app confirmation dialogs
 *     styled with Petal's Material 3 Expressive Design System
 *   • Track download, installation states and active extension list via StateFlow
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.gecko

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.lang.ref.WeakReference

private const val TAG = "GeckoExtensionManager"

/**
 * State of an extension installation attempt.
 */
sealed class ExtensionInstallState {
    object Idle : ExtensionInstallState()
    data class Installing(val xpiUrl: String) : ExtensionInstallState()
    data class Success(val extension: WebExtension) : ExtensionInstallState()
    data class Error(val xpiUrl: String, val message: String) : ExtensionInstallState()
}

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

    private var appContextRef: WeakReference<Context>? = null

    private val _installedExtensions = MutableStateFlow<List<WebExtension>>(emptyList())
    val installedExtensions: StateFlow<List<WebExtension>> = _installedExtensions.asStateFlow()

    private val _installState = MutableStateFlow<ExtensionInstallState>(ExtensionInstallState.Idle)
    val installState: StateFlow<ExtensionInstallState> = _installState.asStateFlow()

    /** Lazily resolved controller from the shared runtime. */
    private val controller: WebExtensionController
        get() = GeckoRuntimeHolder.runtime.webExtensionController

    // ── Initialisation ────────────────────────────────────────────────────

    /**
     * Registers the [PetalExtensionPromptDelegate] and installs all built-in
     * extensions bundled under [assets/extensions/].
     *
     * @param context used to enumerate the assets directory and show dialogs.
     */
    fun init(context: Context) {
        val appCtx = context.applicationContext
        appContextRef = WeakReference(appCtx)

        if (!GeckoRuntimeHolder.isInitialized) {
            Log.w(TAG, "init() called before GeckoRuntime — skipping extension setup")
            return
        }

        controller.promptDelegate = PetalExtensionPromptDelegate {
            appContextRef?.get() ?: appCtx
        }

        refreshInstalledExtensions()
        installBuiltInExtensions(appCtx)
    }

    /**
     * Refreshes the active WebExtension list from GeckoView.
     */
    fun refreshInstalledExtensions() {
        if (!GeckoRuntimeHolder.isInitialized) return
        try {
            controller.list().then<List<WebExtension>> { list ->
                val nonNullList = list?.filterNotNull() ?: emptyList()
                _installedExtensions.value = nonNullList
                Log.i(TAG, "Installed extensions refreshed: count=${nonNullList.size}")
                GeckoResult.fromValue(nonNullList)
            }.exceptionally<List<WebExtension>> { error ->
                Log.e(TAG, "Failed to refresh installed extensions", error)
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing extensions", e)
        }
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
     * Asynchronously installs an extension from a remote .xpi file URL,
     * tracking installation and error states, and refreshing active extensions.
     *
     * @param xpiUrl HTTPS URL pointing to a signed .xpi WebExtension release file.
     * @param onComplete Optional callback on main thread with the result.
     */
    fun installRemoteExtension(xpiUrl: String, onComplete: ((Result<WebExtension>) -> Unit)? = null) {
        _installState.value = ExtensionInstallState.Installing(xpiUrl)

        scope.launch {
            try {
                if (!GeckoRuntimeHolder.isInitialized) {
                    val error = IllegalStateException("GeckoRuntime is not initialized")
                    _installState.value = ExtensionInstallState.Error(xpiUrl, error.message ?: "Runtime error")
                    onComplete?.invoke(Result.failure(error))
                    return@launch
                }

                Log.i(TAG, "Starting remote .xpi extension installation: $xpiUrl")

                controller.install(xpiUrl)
                    .then<WebExtension> { ext ->
                        if (ext != null) {
                            Log.i(TAG, "Remote extension installed successfully: ${ext.id} (${ext.metaData?.name})")
                            _installState.value = ExtensionInstallState.Success(ext)
                            refreshInstalledExtensions()
                            onComplete?.invoke(Result.success(ext))
                        } else {
                            val error = RuntimeException("Extension installation returned null")
                            _installState.value = ExtensionInstallState.Error(xpiUrl, error.message ?: "Unknown error")
                            onComplete?.invoke(Result.failure(error))
                        }
                        GeckoResult.fromValue(ext)
                    }
                    .exceptionally<WebExtension> { throwable ->
                        val msg = throwable?.message ?: "Failed to install remote extension"
                        Log.e(TAG, "Extension install failed for $xpiUrl: $msg", throwable)
                        _installState.value = ExtensionInstallState.Error(xpiUrl, msg)
                        onComplete?.invoke(Result.failure(throwable ?: RuntimeException(msg)))
                        null
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during installRemoteExtension", e)
                _installState.value = ExtensionInstallState.Error(xpiUrl, e.message ?: "Installation exception")
                onComplete?.invoke(Result.failure(e))
            }
        }
    }

    /**
     * Backward-compatible alias for installing remote .xpi.
     */
    fun installFromRemoteXpi(xpiUrl: String) {
        installRemoteExtension(xpiUrl)
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
                refreshInstalledExtensions()
                GeckoResult.fromValue(ext)
            }
            .exceptionally<WebExtension> { throwable ->
                Log.e(TAG, "Extension install failed for $uri", throwable)
                null
            }
    }

    // ── Enable / Disable / Uninstall ──────────────────────────────────────

    /**
     * Enables a disabled WebExtension.
     */
    fun enable(extension: WebExtension) {
        controller.enable(extension, WebExtensionController.EnableSource.USER)
            .then<WebExtension> { ext ->
                refreshInstalledExtensions()
                GeckoResult.fromValue(ext)
            }
            .exceptionally<WebExtension> { throwable ->
                Log.e(TAG, "Failed to enable extension: ${extension.id}", throwable)
                null
            }
    }

    /**
     * Disables an active WebExtension.
     */
    fun disable(extension: WebExtension) {
        controller.disable(extension, WebExtensionController.EnableSource.USER)
            .then<WebExtension> { ext ->
                refreshInstalledExtensions()
                GeckoResult.fromValue(ext)
            }
            .exceptionally<WebExtension> { throwable ->
                Log.e(TAG, "Failed to disable extension: ${extension.id}", throwable)
                null
            }
    }

    /**
     * Uninstalls a previously installed extension.
     *
     * @param extension the [WebExtension] instance to remove.
     */
    fun uninstall(extension: WebExtension, onComplete: (() -> Unit)? = null) {
        controller.uninstall(extension)
            .then<Void> {
                Log.i(TAG, "Extension uninstalled: ${extension.id}")
                refreshInstalledExtensions()
                onComplete?.invoke()
                GeckoResult.fromValue(null)
            }
            .exceptionally<Void> { throwable ->
                Log.e(TAG, "Extension uninstall failed", throwable)
                null
            }
    }

    /**
     * Checks if an extension is currently installed by GUID or ID.
     */
    fun isExtensionInstalled(guidOrId: String): Boolean {
        return _installedExtensions.value.any { it.id == guidOrId || it.metaData?.name == guidOrId }
    }
}

// ── Permission prompt delegate ────────────────────────────────────────────

/**
 * Handles GeckoView runtime permission prompts for extension install and
 * update events with an in-app Material 3 confirmation dialog.
 */
private class PetalExtensionPromptDelegate(
    private val contextProvider: () -> Context
) : WebExtensionController.PromptDelegate {

    override fun onInstallPromptRequest(
        extension: WebExtension,
        permissions: Array<String>,
        origins: Array<String>,
        dataCollectionPermissions: Array<String>
    ): GeckoResult<WebExtension.PermissionPromptResponse> {
        val result = GeckoResult<WebExtension.PermissionPromptResponse>()
        val context = contextProvider()
        val extName = extension.metaData?.name ?: extension.id

        Log.i(TAG, "onInstallPromptRequest: Requesting permission for extension $extName")

        CoroutineScope(Dispatchers.Main.immediate).launch {
            GeckoExtensionPermissionDialogBridge.showPrompt(
                context = context,
                extensionName = extName,
                permissions = permissions.toList(),
                origins = origins.toList(),
                isUpdate = false,
                onAllow = {
                    Log.i(TAG, "onInstallPromptRequest: Allowed by user for $extName")
                    result.complete(
                        WebExtension.PermissionPromptResponse(
                            true,
                            true,
                            false
                        )
                    )
                },
                onDeny = {
                    Log.i(TAG, "onInstallPromptRequest: Denied by user for $extName")
                    result.complete(
                        WebExtension.PermissionPromptResponse(
                            false,
                            false,
                            false
                        )
                    )
                }
            )
        }

        return result
    }

    override fun onUpdatePrompt(
        extension: WebExtension,
        newPermissions: Array<String>,
        newOrigins: Array<String>,
        newDataCollectionPermissions: Array<String>
    ): GeckoResult<AllowOrDeny> {
        val result = GeckoResult<AllowOrDeny>()
        val context = contextProvider()

        val extName = extension.metaData?.name ?: extension.id

        Log.i(
            TAG,
            "onUpdatePrompt: Requesting update permissions for $extName: ${newPermissions.toList()}"
        )

        CoroutineScope(Dispatchers.Main.immediate).launch {
            GeckoExtensionPermissionDialogBridge.showPrompt(
                context = context,
                extensionName = extName,
                permissions = newPermissions.toList(),
                origins = newOrigins.toList(),
                isUpdate = true,
                onAllow = {
                    Log.i(TAG, "onUpdatePrompt: Allowed update for $extName")
                    result.complete(AllowOrDeny.ALLOW)
                },
                onDeny = {
                    Log.i(TAG, "onUpdatePrompt: Denied update for $extName")
                    result.complete(AllowOrDeny.DENY)
                }
            )
        }

        return result
    }
}
