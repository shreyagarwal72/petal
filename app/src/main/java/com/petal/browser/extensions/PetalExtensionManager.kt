package com.petal.browser.extensions

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.preference.PreferenceManager
import com.petal.browser.engine.gecko.PetalGeckoRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/**
 * PetalExtensionManager
 * ─────────────────────────────────────────────────────────────────────────
 * Real Firefox add-on support for Petal.
 *
 * Petal's rendering engine is Mozilla GeckoView (see [PetalGeckoRuntime]) - the exact same
 * engine that powers Firefox for Android. GeckoView exposes [WebExtensionController], the
 * very same WebExtension/API surface Firefox itself uses, which means signed `.xpi` packages
 * published on addons.mozilla.org (AMO) - the Firefox Add-ons store - install and run in Petal
 * completely unmodified. There is no shim, no polyfill, and no separate "Petal extension
 * format": an add-on installed here is byte-for-byte the same package Firefox would install,
 * so anything AMO marks as "Available on Firefox for Android" works here too.
 *
 * This object is a thin, app-facing wrapper around that controller:
 *  - install/uninstall/enable/disable, backed by GeckoView's own persistent extension store
 *    (extensions survive app restarts automatically - GeckoView owns that storage, Petal
 *    does not duplicate it)
 *  - a reactive [extensions] list for Compose UI
 *  - an install/permission confirmation flow surfaced as [pendingPrompt] so the UI can render
 *    a Material 3 dialog instead of Gecko's own native prompt
 *  - toolbar action (browser/page action) plumbing so extensions with a popup UI (uBlock
 *    Origin, Bitwarden, etc.) can present that popup
 */
object PetalExtensionManager {

    private const val TAG = "PetalExtensionManager"

    /** A permission confirmation the UI must resolve before install/update proceeds. */
    data class PendingPrompt(
        val extension: WebExtension,
        val permissions: List<String>,
        val origins: List<String>,
        val isUpdate: Boolean,
        val respond: (granted: Boolean) -> Unit
    )

    /** A request from an extension to show its browser/page action popup. */
    data class PendingPopup(
        val extensionId: String,
        val extensionName: String,
        val session: GeckoSession
    )

    data class InstalledExtension(
        val id: String,
        val name: String,
        val description: String,
        val version: String,
        val enabled: Boolean,
        val temporary: Boolean,
        val allowedInPrivateBrowsing: Boolean,
        val homepageUrl: String?,
        val amoListingUrl: String?,
        val optionsPageUrl: String?,
        val icon: Bitmap?,
        val raw: WebExtension
    )

    /** Small curated set of well-known, Android-compatible extensions from addons.mozilla.org. */
    data class CatalogEntry(
        val id: String,
        val name: String,
        val description: String,
        val amoSlug: String,
        val amoListingUrl: String
    ) {
        /** AMO's permanent "always the latest signed build" redirect for this add-on. */
        val downloadUrl: String get() = "https://addons.mozilla.org/firefox/downloads/latest/$amoSlug/latest.xpi"
    }

    val catalog: List<CatalogEntry> = listOf(
        CatalogEntry(
            id = "ublock-origin",
            name = "uBlock Origin",
            description = "Efficient, wide-spectrum ad & tracker content blocker.",
            amoSlug = "ublock-origin",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/ublock-origin/"
        ),
        CatalogEntry(
            id = "darkreader",
            name = "Dark Reader",
            description = "Dark mode for every website, with brightness & contrast controls.",
            amoSlug = "darkreader",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/darkreader/"
        ),
        CatalogEntry(
            id = "privacy-badger17",
            name = "Privacy Badger",
            description = "Automatically learns to block invisible trackers.",
            amoSlug = "privacy-badger17",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/privacy-badger17/"
        ),
        CatalogEntry(
            id = "bitwarden-password-manager",
            name = "Bitwarden Password Manager",
            description = "Secure password, passkey, and vault manager.",
            amoSlug = "bitwarden-password-manager",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/bitwarden-password-manager/"
        ),
        CatalogEntry(
            id = "istilldontcareaboutcookies",
            name = "I still don't care about cookies",
            description = "Automatically dismisses cookie consent popups.",
            amoSlug = "istilldontcareaboutcookies",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/istilldontcareaboutcookies/"
        )
    )

    private val _extensions = MutableStateFlow<List<InstalledExtension>>(emptyList())
    val extensions: StateFlow<List<InstalledExtension>> = _extensions.asStateFlow()

    private val _pendingPrompt = MutableStateFlow<PendingPrompt?>(null)
    val pendingPrompt: StateFlow<PendingPrompt?> = _pendingPrompt.asStateFlow()

    private val _pendingPopup = MutableStateFlow<PendingPopup?>(null)
    val pendingPopup: StateFlow<PendingPopup?> = _pendingPopup.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    @Volatile private var attached = false
    @Volatile private var appContext: Context? = null

    /** Idempotent - safe to call from every Activity#onCreate. */
    @JvmStatic
    @Synchronized
    fun attach(context: Context) {
        appContext = context.applicationContext
        if (attached) {
            refresh()
            return
        }
        attached = true

        val runtime = PetalGeckoRuntime.getOrCreate(context.applicationContext)
        val controller = runtime.webExtensionController

        controller.setPromptDelegate(object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>,
                dataCollectionPermissions: Array<out String>
            ): GeckoResult<WebExtension.PermissionPromptResponse>? {
                val result = GeckoResult<WebExtension.PermissionPromptResponse>()
                _pendingPrompt.value = PendingPrompt(
                    extension = extension,
                    permissions = permissions.toList(),
                    origins = origins.toList(),
                    isUpdate = false
                ) { granted ->
                    _pendingPrompt.value = null
                    result.complete(
                        WebExtension.PermissionPromptResponse(
                            granted, granted, granted
                        )
                    )
                }
                return result
            }

            override fun onUpdatePrompt(
                extension: WebExtension,
                newPermissions: Array<out String>,
                newOrigins: Array<out String>,
                newDataCollectionPermissions: Array<out String>
            ): GeckoResult<AllowOrDeny>? {
                val result = GeckoResult<AllowOrDeny>()
                _pendingPrompt.value = PendingPrompt(
                    extension = extension,
                    permissions = newPermissions.toList(),
                    origins = newOrigins.toList(),
                    isUpdate = true
                ) { granted ->
                    _pendingPrompt.value = null
                    result.complete(if (granted) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
                }
                return result
            }

            override fun onOptionalPrompt(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>,
                dataCollectionPermissions: Array<out String>
            ): GeckoResult<AllowOrDeny>? {
                // Runtime-requested optional permissions (permissions.request()) - allow
                // silently for already-installed, user-approved extensions.
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }
        })

        controller.setAddonManagerDelegate(object : WebExtensionController.AddonManagerDelegate {
            override fun onInstalled(extension: WebExtension) {
                attachActionDelegate(extension)
                refresh()
            }

            override fun onInstallationFailed(
                extension: WebExtension?,
                installException: WebExtension.InstallException
            ) {
                _busy.value = false
                _lastError.value = describeInstallError(installException)
                Log.w(TAG, "Extension installation failed: ${installException.code}")
            }

            override fun onUninstalled(extension: WebExtension) {
                refresh()
            }

            override fun onEnabling(extension: WebExtension) { refresh() }
            override fun onEnabled(extension: WebExtension) { refresh() }
            override fun onDisabling(extension: WebExtension) { refresh() }
            override fun onDisabled(extension: WebExtension) { refresh() }
            override fun onReady(extension: WebExtension) {
                attachActionDelegate(extension)
                refresh()
            }
        })

        refresh()
    }

    private fun describeInstallError(e: WebExtension.InstallException): String {
        return when (e.code) {
            WebExtension.InstallException.ErrorCodes.ERROR_NETWORK_FAILURE ->
                "Couldn't download the extension - check your connection and try again."
            WebExtension.InstallException.ErrorCodes.ERROR_INCORRECT_HASH ->
                "The downloaded file didn't match its expected signature."
            WebExtension.InstallException.ErrorCodes.ERROR_CORRUPT_FILE ->
                "That extension package is corrupt or not a valid .xpi file."
            WebExtension.InstallException.ErrorCodes.ERROR_FILE_ACCESS ->
                "Petal couldn't access that file."
            WebExtension.InstallException.ErrorCodes.ERROR_SIGNEDSTATE_REQUIRED ->
                "That extension isn't signed by Mozilla, so it can't be installed."
            WebExtension.InstallException.ErrorCodes.ERROR_UNEXPECTED_ADDON_TYPE ->
                "That AMO listing isn't an installable extension."
            WebExtension.InstallException.ErrorCodes.ERROR_INCORRECT_CERTIFICATE ->
                "The extension's signature couldn't be verified."
            WebExtension.InstallException.ErrorCodes.ERROR_BLOCKLISTED ->
                "This extension has been blocklisted by Mozilla for safety reasons."
            WebExtension.InstallException.ErrorCodes.ERROR_INCOMPATIBLE ->
                "This extension isn't compatible with Petal's engine version."
            WebExtension.InstallException.ErrorCodes.ERROR_USER_CANCELED ->
                "Installation was canceled."
            else -> "Couldn't install that extension (error ${e.code})."
        }
    }

    /** Attaches the popup/action bridge to a given extension so its toolbar button works. */
    private fun attachActionDelegate(extension: WebExtension) {
        extension.setActionDelegate(object : WebExtension.ActionDelegate {
            override fun onOpenPopup(
                ext: WebExtension,
                action: WebExtension.Action
            ): GeckoResult<GeckoSession>? = openPopupSession(ext)

            override fun onTogglePopup(
                ext: WebExtension,
                action: WebExtension.Action
            ): GeckoResult<GeckoSession>? = openPopupSession(ext)
        })
    }

    private fun openPopupSession(extension: WebExtension): GeckoResult<GeckoSession>? {
        val ctx = appContext ?: return null
        val runtime = PetalGeckoRuntime.getOrCreate(ctx)
        val popupSession = GeckoSession()
        popupSession.open(runtime)
        _pendingPopup.value = PendingPopup(
            extensionId = extension.id,
            extensionName = extension.metaData.name ?: extension.id,
            session = popupSession
        )
        return GeckoResult.fromValue(popupSession)
    }

    fun dismissPopup() {
        _pendingPopup.value?.session?.let { session ->
            try {
                session.setActive(false)
                session.close()
            } catch (ignored: Exception) {}
        }
        _pendingPopup.value = null
    }

    /** Installs a `.xpi` from any https URL (an AMO listing's download link, or a direct file). */
    fun install(uri: String, onResult: (success: Boolean, message: String?) -> Unit = { _, _ -> }) {
        val ctx = appContext ?: return
        val controller = PetalGeckoRuntime.getOrCreate(ctx).webExtensionController
        _busy.value = true
        _lastError.value = null
        controller.install(uri, WebExtensionController.INSTALLATION_METHOD_MANAGER)
            .accept({ extension ->
                _busy.value = false
                if (extension != null) {
                    attachActionDelegate(extension)
                }
                refresh()
                onResult(true, extension?.metaData?.name ?: "Extension installed")
            }, { throwable ->
                _busy.value = false
                val message = if (throwable is WebExtension.InstallException) {
                    describeInstallError(throwable)
                } else {
                    throwable?.message ?: "Installation failed."
                }
                _lastError.value = message
                onResult(false, message)
            })
    }

    fun uninstall(extension: WebExtension, onResult: (Boolean) -> Unit = {}) {
        val ctx = appContext ?: return
        val controller = PetalGeckoRuntime.getOrCreate(ctx).webExtensionController
        controller.uninstall(extension)
            .accept({
                refresh()
                onResult(true)
            }, {
                onResult(false)
            })
    }

    fun setEnabled(extension: WebExtension, enabled: Boolean) {
        val ctx = appContext ?: return
        val controller = PetalGeckoRuntime.getOrCreate(ctx).webExtensionController
        val op = if (enabled) {
            controller.enable(extension, WebExtensionController.EnableSource.USER)
        } else {
            controller.disable(extension, WebExtensionController.EnableSource.USER)
        }
        op.accept({ refresh() }, { refresh() })
    }

    fun setAllowedInPrivateBrowsing(extension: WebExtension, allowed: Boolean) {
        val ctx = appContext ?: return
        val controller = PetalGeckoRuntime.getOrCreate(ctx).webExtensionController
        controller.setAllowedInPrivateBrowsing(extension, allowed)
            .accept({ refresh() }, { refresh() })
    }

    fun refresh() {
        val ctx = appContext ?: return
        val controller = PetalGeckoRuntime.getOrCreate(ctx).webExtensionController
        controller.list().accept({ list ->
            val items = (list ?: emptyList()).map { ext -> toInstalled(ext) }
            _extensions.value = items
            // Load icons asynchronously and patch them in once ready (best-effort, 96px).
            list?.forEach { ext ->
                try {
                    ext.metaData.icon.getBitmap(96).accept({ bmp ->
                        if (bmp != null) {
                            _extensions.value = _extensions.value.map {
                                if (it.id == ext.id) it.copy(icon = bmp) else it
                            }
                        }
                    }, { /* icon optional */ })
                } catch (ignored: Exception) {}
            }
        }, {
            Log.w(TAG, "Failed to list extensions", it)
        })
    }

    private fun toInstalled(ext: WebExtension): InstalledExtension {
        val meta = ext.metaData
        return InstalledExtension(
            id = ext.id,
            name = meta.name ?: ext.id,
            description = meta.description ?: "",
            version = meta.version,
            enabled = meta.enabled,
            temporary = meta.temporary,
            allowedInPrivateBrowsing = meta.allowedInPrivateBrowsing,
            homepageUrl = meta.homepageUrl,
            amoListingUrl = meta.amoListingUrl,
            optionsPageUrl = meta.optionsPageUrl,
            icon = null,
            raw = ext
        )
    }
}
