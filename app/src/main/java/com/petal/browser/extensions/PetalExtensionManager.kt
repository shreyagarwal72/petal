package com.petal.browser.extensions

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
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
        val hasPopup: Boolean,
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
        /**
         * AMO's permanent "always the latest signed build" redirect for this add-on.
         * NOTE: this must be the "/firefox/downloads/latest/" path - AMO has no
         * "/android/downloads/" endpoint. Firefox and Firefox for Android share the
         * same add-on catalog/redirect; only the *listing* pages have an "/android/"
         * variant, not the download links.
         */
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
        ),
        CatalogEntry(
            id = "youtube-high-definition",
            name = "YouTube High Definition",
            description = "Automatically plays YouTube videos in HD, resizes the player, and adds auto-stop and mute.",
            amoSlug = "youtube-high-definition",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/youtube-high-definition/"
        ),
        CatalogEntry(
            id = "view-page-archive",
            name = "Web Archives",
            description = "View archived and cached versions of web pages, such as the Wayback Machine and Archive.is.",
            amoSlug = "view-page-archive",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/view-page-archive/"
        ),
        CatalogEntry(
            id = "tomato-clock",
            name = "Tomato Clock",
            description = "A simple Pomodoro-style timer for managing your productivity.",
            amoSlug = "tomato-clock",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/tomato-clock/"
        ),
        CatalogEntry(
            id = "video-background-play-fix",
            name = "Video Background Play Fix",
            description = "Keeps videos playing in the background by blocking the Page Visibility and Fullscreen APIs.",
            amoSlug = "video-background-play-fix",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/video-background-play-fix/"
        ),
        CatalogEntry(
            id = "google-search-fixer",
            name = "Google Search Fixer",
            description = "Overrides the user-agent on Google Search so it serves the Chrome-style search experience.",
            amoSlug = "google-search-fixer",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/google-search-fixer/"
        ),
        CatalogEntry(
            id = "cookie-editor",
            name = "Cookie-Editor",
            description = "Create, edit, search, and delete cookies for the current tab.",
            amoSlug = "cookie-editor",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/cookie-editor/"
        ),
        CatalogEntry(
            id = "adguard-adblocker",
            name = "AdGuard AdBlocker",
            description = "Blocks ads and pop-ups on Facebook, YouTube, and every other website.",
            amoSlug = "adguard-adblocker",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/adguard-adblocker/"
        )
    )

    /** AMO's Android extensions catalog - opened by "Find more extensions". */
    const val amoAndroidBrowseUrl = "https://addons.mozilla.org/en-US/android/"

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

    // Tracks whether GeckoView has reported a browser/page action for an extension.
    // The UI only exposes “Open extension” for extensions that actually have an action.
    private val actionExtensions = mutableSetOf<String>()

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
                actionExtensions.remove(extension.id)
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
            WebExtension.InstallException.ErrorCodes.ERROR_UNEXPECTED_ADDON_TYPE,
            WebExtension.InstallException.ErrorCodes.ERROR_UNSUPPORTED_ADDON_TYPE ->
                "This Firefox add-on type is not supported on Android. Choose an extension listed for Firefox Android."
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
            override fun onBrowserAction(
                ext: WebExtension,
                session: GeckoSession?,
                action: WebExtension.Action
            ) {
                if (session == null) {
                    actionExtensions.add(ext.id)
                    refresh()
                }
            }

            override fun onPageAction(
                ext: WebExtension,
                session: GeckoSession?,
                action: WebExtension.Action
            ) {
                if (session == null) {
                    actionExtensions.add(ext.id)
                    refresh()
                }
            }

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
        if (appContext == null) return null
        // GeckoView calls onOpenPopup/onTogglePopup again on repeated toolbar taps
        // before this method's previous GeckoResult has necessarily finished being
        // consumed. Without this guard, a second call created and opened a brand
        // new GeckoSession while the first one was still live, and Gecko's own
        // internal session-claiming logic would then hit an already-claimed
        // session and crash with "Must use an unopened GeckoSession instance".
        // Always close out any existing popup session for this (or any other)
        // extension first, so there's never more than one open at a time.
        _pendingPopup.value?.session?.let { existing ->
            try {
                existing.setActive(false)
                existing.close()
            } catch (ignored: Exception) {}
        }
        _pendingPopup.value = null

        // IMPORTANT: return an unopened session. GeckoView's Action.openPopup(...)
        // owns the popup URI and opens/loads this session. Opening it here races Gecko's
        // popup setup and can result in a blank popup.
        val popupSession = GeckoSession()
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

    /**
     * Manually triggers the browser-action (toolbar popup) for an installed extension from the UI.
     *
     * GeckoView's [WebExtension] has no public API to programmatically fire a toolbar-button
     * click - [WebExtension.ActionDelegate.onOpenPopup]/`onTogglePopup` are callbacks Gecko
     * invokes on its own schedule, not methods we can call into. There is also no manifest
     * field exposed on [WebExtension.MetaData] that tells us whether a browser_action or
     * page_action exists. Since we can't detect or trigger this from app code, we open the
     * same popup session path used by [attachActionDelegate]'s real callbacks directly; if the
     * extension has no popup UI, Gecko simply won't have anything to show and this is a no-op
     * from the user's perspective beyond the session being created.
     */
    fun triggerBrowserAction(extensionId: String) {
        val ext = _extensions.value.find { it.id == extensionId } ?: run {
            _lastError.value = "Extension not found."
            return
        }
        if (!ext.enabled || !ext.hasPopup) {
            _lastError.value = "This extension doesn't provide an openable popup."
            return
        }
        openPopupSession(ext.raw)
    }

    /**
     * Installs a `.xpi` file the user opened from *outside* the browser - tapped in Downloads,
     * a file manager, shared from another app, etc. The incoming [uri] is almost always a
     * `content://` Uri whose read permission is only granted for the lifetime of the original
     * VIEW/SEND intent; GeckoView's native (Necko) downloader has no access to that grant and
     * can't resolve arbitrary content providers, so the bytes are copied into the app's own
     * cache directory first and a plain `file://` path (which GeckoView can always read) is
     * installed instead. This is what makes `.xpi` files "just work" natively from anywhere on
     * the device, not only from https:// download links followed inside Petal itself.
     */
    @JvmStatic
    @JvmOverloads
    fun installFromContentUri(context: Context, uri: Uri, onResult: (success: Boolean, message: String?) -> Unit = { _, _ -> }) {
        val appCtx = context.applicationContext
        appContext = appCtx
        _busy.value = true
        _lastError.value = null
        try {
            val resolver = appCtx.contentResolver
            var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "extension.xpi"
            var mimeType: String? = null

            if (uri.scheme.equals("content", ignoreCase = true)) {
                try {
                    resolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIdx != -1) cursor.getString(nameIdx)?.let { displayName = it }
                            val typeIdx = cursor.getColumnIndex("mime_type")
                            if (typeIdx != -1) cursor.getString(typeIdx)?.let { mimeType = it }
                        }
                    }
                } catch (ignored: Exception) {}
                mimeType = mimeType ?: resolver.getType(uri)
            }

            val isXpi = displayName.endsWith(".xpi", ignoreCase = true) ||
                mimeType.equals("application/x-xpinstall", ignoreCase = true)
            if (!isXpi) {
                val message = "Please select a Firefox extension file (.xpi)."
                _busy.value = false
                _lastError.value = message
                onResult(false, message)
                return
            }

            if (!displayName.endsWith(".xpi", ignoreCase = true)) displayName = "$displayName.xpi"

            val cacheDir = File(appCtx.cacheDir, "extension-installs").apply { mkdirs() }
            val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val destFile = File(cacheDir, "install_${System.currentTimeMillis()}_$safeName")

            val input = if (uri.scheme.equals("file", ignoreCase = true)) {
                java.io.FileInputStream(File(requireNotNull(uri.path)))
            } else {
                resolver.openInputStream(uri)
            }
            if (input == null) {
                _busy.value = false
                val message = "Petal couldn't access that file."
                _lastError.value = message
                onResult(false, message)
                return
            }

            input.use { stream ->
                destFile.outputStream().use { output -> stream.copyTo(output) }
            }

            // GeckoView explicitly distinguishes local-file installation from an add-on
            // manager/remote installation. Use the correct method so imported .xpi files
            // follow the native local-package path.
            install(
                Uri.fromFile(destFile).toString(),
                WebExtensionController.INSTALLATION_METHOD_FROM_FILE
            ) { success, message ->
                try { destFile.delete() } catch (ignored: Exception) {}
                onResult(success, message)
            }
        } catch (e: Exception) {
            _busy.value = false
            val message = "Couldn't read that extension file."
            _lastError.value = message
            Log.w(TAG, "installFromContentUri failed", e)
            onResult(false, message)
        }
    }

    /** Installs a `.xpi` from any https URL (an AMO listing's download link, or a direct file). */
    fun install(uri: String, onResult: (success: Boolean, message: String?) -> Unit = { _, _ -> }) {
        install(uri, WebExtensionController.INSTALLATION_METHOD_MANAGER, onResult)
    }

    private fun install(
        uri: String,
        installationMethod: String,
        onResult: (success: Boolean, message: String?) -> Unit = { _, _ -> }
    ) {
        val installUri = normalizeInstallUri(uri)
        if (installUri == null) {
            val message = "Use a secure .xpi download link or an addons.mozilla.org add-on page."
            _lastError.value = message
            onResult(false, message)
            return
        }
        val ctx = appContext ?: return
        val controller = PetalGeckoRuntime.getOrCreate(ctx).webExtensionController
        _busy.value = true
        _lastError.value = null
        controller.install(installUri, installationMethod)
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

    private fun normalizeInstallUri(value: String): String? {
        val parsed = try { Uri.parse(value.trim()) } catch (_: Exception) { return null }

        // A local `.xpi` package already resolved to a real file path (see
        // installFromContentUri, used when the user opens a downloaded/shared .xpi from
        // outside the browser) - GeckoView's installer reads these directly. Never rewrite
        // this into an AMO url.
        if (parsed.scheme.equals("file", ignoreCase = true)) {
            return parsed.toString()
        }

        if (!parsed.scheme.equals("https", ignoreCase = true)) return null

        // Already a real, resolved package link (e.g. GeckoView's own onExternalResponse
        // handed us the final "/firefox/downloads/file/<id>/name.xpi" URL after following
        // AMO's redirect chain, or the user gave a direct .xpi link). Never rewrite this -
        // it is already correct, and reconstructing a "slug" out of its path segments
        // (previous bug: grabbed the numeric file id and built a dead /android/downloads/
        // URL from it) just replaces a working link with a broken one.
        if (parsed.path?.endsWith(".xpi", ignoreCase = true) == true) {
            return parsed.toString()
        }

        val host = parsed.host?.lowercase() ?: return null
        if (host != "addons.mozilla.org" && host != "www.addons.mozilla.org") return null

        // An AMO *listing* page (.../addon/<slug>/ or .../android/addon/<slug>/) - resolve
        // it to the permanent "latest signed build" redirect. This is always the
        // "/firefox/downloads/latest/" path: AMO has no "/android/downloads/" endpoint:
        // Firefox and Firefox for Android share one add-on catalog, and only the listing
        // pages (not downloads) have a separate "/android/" URL variant.
        val segments = parsed.pathSegments
        val addonIndex = segments.indexOf("addon")
        val slug = segments.getOrNull(addonIndex + 1)?.takeIf { it.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9_-]*")) }
        return if (addonIndex >= 0 && slug != null) {
            "https://addons.mozilla.org/firefox/downloads/latest/$slug/latest.xpi"
        } else null
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
            hasPopup = actionExtensions.contains(ext.id),
            icon = null,
            raw = ext
        )
    }
}
