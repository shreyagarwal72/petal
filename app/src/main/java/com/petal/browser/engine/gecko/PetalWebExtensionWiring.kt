package com.petal.browser.engine.gecko

import android.content.Context
import android.util.Log
import com.petal.browser.extensions.PetalExtensionManager
import com.petal.browser.extensions.PetalWebExtensionDelegate
import mozilla.components.browser.engine.gecko.GeckoEngine
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.webextension.Action
import mozilla.components.concept.engine.webextension.PermissionPromptResponse
import mozilla.components.concept.engine.webextension.WebExtension
import mozilla.components.concept.engine.webextension.WebExtensionDelegate
import mozilla.components.concept.engine.webextension.WebExtensionRuntime
import mozilla.components.support.webextensions.WebExtensionSupport

/**
 * Wires Mozilla's [WebExtensionSupport] together with [PetalWebExtensionDelegate].
 *
 * Gecko accepts exactly one [WebExtensionDelegate] per runtime, and `WebExtensionSupport` registers
 * its own through `runtime.registerWebExtensionDelegate`. Registering Petal's delegate directly would
 * replace it and stop `BrowserState.extensions` / `TabSessionState.extensionState` from updating, so
 * `WebExtensionSupport` is handed a thin runtime wrapper whose `registerWebExtensionDelegate` installs
 * a composite: lifecycle and action events reach both delegates (Support first), while prompts, popups,
 * tab and options-page requests are answered by Petal alone.
 */
internal object PetalWebExtensionWiring {
    private const val TAG = "PetalWebExtWiring"

    fun initialize(context: Context, engine: GeckoEngine, store: BrowserStore) {
        val delegate = PetalWebExtensionDelegate(context)
        WebExtensionSupport.initialize(
            runtime = PetalWebExtensionRuntime(engine, delegate),
            store = store,
            // Extension-created tabs, and tabs an extension closes or selects through the tabs API.
            onNewTabOverride = { extension, engineSession, url, active, isPrivate ->
                delegate.openExtensionTab(extension, engineSession, url, active, isPrivate)
            },
            onCloseTabOverride = { _, tabId -> delegate.closeTabById(tabId) },
            onSelectTabOverride = { _, tabId -> delegate.selectInUi(tabId) },
            onUpdatePermissionRequest = { extension, newPermissions, newOrigins, newDataCollectionPermissions, onPermissionsGranted ->
                delegate.onUpdatePermissionRequest(
                    extension, newPermissions, newOrigins, newDataCollectionPermissions, onPermissionsGranted
                )
            },
            onExtensionsLoaded = { extensions ->
                Log.i(TAG, "Extensions loaded: ${extensions.size}")
                PetalExtensionManager.refresh()
            }
        )
        Log.i(TAG, "Initialized WebExtensionSupport with PetalWebExtensionDelegate")
    }

    private class PetalWebExtensionRuntime(
        private val engine: GeckoEngine,
        private val petal: PetalWebExtensionDelegate
    ) : WebExtensionRuntime by engine {
        override fun registerWebExtensionDelegate(webExtensionDelegate: WebExtensionDelegate) {
            engine.registerWebExtensionDelegate(PetalCompositeDelegate(webExtensionDelegate, petal))
        }
    }

    private class PetalCompositeDelegate(
        private val support: WebExtensionDelegate,
        private val petal: PetalWebExtensionDelegate
    ) : WebExtensionDelegate by support {

        // Lifecycle: Support records the extension in the store first, then Petal refreshes its UI state.
        override fun onInstalled(extension: WebExtension) { support.onInstalled(extension); petal.onInstalled(extension) }
        override fun onUninstalled(extension: WebExtension) { support.onUninstalled(extension); petal.onUninstalled(extension) }
        override fun onEnabled(extension: WebExtension) { support.onEnabled(extension); petal.onEnabled(extension) }
        override fun onDisabled(extension: WebExtension) { support.onDisabled(extension); petal.onDisabled(extension) }
        override fun onExtensionListUpdated() { support.onExtensionListUpdated(); petal.onExtensionListUpdated() }
        override fun onBrowserActionDefined(extension: WebExtension, action: Action) {
            support.onBrowserActionDefined(extension, action); petal.onBrowserActionDefined(extension, action)
        }
        override fun onPageActionDefined(extension: WebExtension, action: Action) {
            support.onPageActionDefined(extension, action); petal.onPageActionDefined(extension, action)
        }

        // Petal-only prompts, popups, and options pages (tabs flow through WebExtensionSupport overrides).
        override fun onInstallPermissionRequest(
            extension: WebExtension,
            permissions: List<String>,
            origins: List<String>,
            dataCollectionPermissions: List<String>,
            onConfirm: (PermissionPromptResponse) -> Unit
        ) = petal.onInstallPermissionRequest(extension, permissions, origins, dataCollectionPermissions, onConfirm)

        override fun onUpdatePermissionRequest(
            extension: WebExtension,
            newPermissions: List<String>,
            newOrigins: List<String>,
            newDataCollectionPermissions: List<String>,
            onPermissionsGranted: (Boolean) -> Unit
        ) = petal.onUpdatePermissionRequest(
            extension, newPermissions, newOrigins, newDataCollectionPermissions, onPermissionsGranted
        )

        override fun onToggleActionPopup(
            extension: WebExtension,
            engineSession: EngineSession,
            action: Action,
            isPrivate: Boolean
        ): EngineSession? =
            petal.onToggleActionPopup(extension, engineSession, action, isPrivate)

        override fun onOpenOptionsPage(extension: WebExtension) =
            petal.onOpenOptionsPage(extension)
    }
}
