package com.petal.browser.extensions

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.petal.browser.activity.MainActivity
import com.petal.browser.engine.gecko.PetalEngineStore
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.webextension.Action
import mozilla.components.concept.engine.webextension.PermissionPromptResponse
import mozilla.components.concept.engine.webextension.WebExtension
import mozilla.components.concept.engine.webextension.WebExtensionDelegate

/**
 * PetalWebExtensionDelegate
 * ─────────────────────────────────────────────────────────────────────────
 * Petal's Mozilla Android Components [WebExtensionDelegate].
 *
 * It is registered through [PetalEngineStore.getStore], next to `WebExtensionSupport`, so every
 * Gecko extension event reaches both: `WebExtensionSupport` keeps `BrowserState.extensions` and
 * `TabSessionState.extensionState` current, and this class owns what only Petal can do:
 *  - install / update permission prompts as Petal Compose dialogs ([PetalExtensionManager.pendingPrompt]),
 *  - the `moz-extension://` popup page, loaded into an EngineSession and hosted by the popup UI,
 *  - tab open / close requests from extensions, adopted into the BrowserStore,
 *  - the user-intent gate that stops websites from waking an extension into popping up its pages.
 */
class PetalWebExtensionDelegate(private val context: Context) : WebExtensionDelegate {

    companion object {
        private const val TAG = "PetalWebExtDelegate"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun findMainActivity(): MainActivity? {
        var ctx: Context? = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is MainActivity) return ctx
            ctx = ctx.baseContext
        }
        return com.petal.browser.engine.gecko.PetalGeckoRuntime.getForegroundActivity() as? MainActivity
    }

    private fun nameOf(extension: WebExtension): String? =
        try { extension.getMetadata()?.name } catch (_: Throwable) { null }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onInstalled(extension: WebExtension) {
        Log.i(TAG, "Extension installed: ${extension.id}")
        PetalExtensionManager.onExtensionInstalled(extension.id, nameOf(extension))
    }

    override fun onUninstalled(extension: WebExtension) {
        Log.i(TAG, "Extension uninstalled: ${extension.id}")
        PetalExtensionManager.onExtensionUninstalled(extension.id, nameOf(extension))
    }

    override fun onEnabled(extension: WebExtension) {
        Log.i(TAG, "Extension enabled: ${extension.id}")
        PetalExtensionManager.refresh()
    }

    override fun onDisabled(extension: WebExtension) {
        Log.i(TAG, "Extension disabled: ${extension.id}")
        PetalExtensionManager.refresh()
    }

    override fun onExtensionListUpdated() {
        PetalExtensionManager.refresh()
    }

    // ── Tabs ─────────────────────────────────────────────────────────────────

    /**
     * An extension opened a tab (`browser.tabs.create`). Gecko already built [engineSession] for it;
     * it is adopted into the BrowserStore, which also makes WebExtensionSupport register it.
     */
    override fun onNewTab(
        extension: WebExtension,
        engineSession: EngineSession,
        active: Boolean,
        url: String,
        isPrivate: Boolean
    ) {
        mainHandler.post {
            try {
                openExtensionTab(extension, engineSession, url, active, isPrivate)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to handle onNewTab from extension ${extension.id}: ${t.message}", t)
            }
        }
    }

    /**
     * Adopts [engineSession] as a new store tab and returns its id. Also the body of
     * `WebExtensionSupport`'s `onNewTabOverride`.
     */
    fun openExtensionTab(
        extension: WebExtension?,
        engineSession: EngineSession,
        url: String,
        active: Boolean,
        isPrivate: Boolean
    ): String {
        // An extension may not open its own pages in front of an ordinary website without a user
        // gesture or a fresh install.
        if (url.startsWith("moz-extension://") && extension != null &&
            !PetalExtensionManager.isUserInitiatedOpen(extension.id)
        ) {
            Log.i(TAG, "Suppressed non-user tab open (${url.take(60)}) from ${extension.id}")
            try { engineSession.close() } catch (_: Throwable) {}
            return ""
        }
        val tabId = "tab_" + System.currentTimeMillis() + "_" + Math.abs(java.util.UUID.randomUUID().hashCode())
        PetalEngineStore.adoptPreparedSession(
            context = context,
            tabId = tabId,
            url = url.ifBlank { "about:blank" },
            title = extension?.let { nameOf(it) } ?: "",
            isIncognito = isPrivate,
            session = engineSession,
            select = active
        )
        if (active) selectInUi(tabId)
        return tabId
    }

    /** Selects [tabId] in the store and tells the activity's view model so the visible tab follows. */
    fun selectInUi(tabId: String) {
        PetalEngineStore.selectTab(context, tabId)
        findMainActivity()?.let { act -> act.runOnUiThread { act.viewModel.selectTab(tabId) } }
    }

    /** Closes the store tab with [tabId] (`browser.tabs.remove` on an existing tab). */
    fun closeTabById(tabId: String) {
        mainHandler.post {
            try {
                PetalEngineStore.getStore(context).dispatch(TabListAction.RemoveTabAction(tabId))
            } catch (t: Throwable) {
                Log.w(TAG, "Extension tab close request failed", t)
            }
        }
    }

    /** `browser.tabs.remove`: closes the store tab that owns [engineSession]. */
    fun onCloseTab(extension: WebExtension?, engineSession: EngineSession): Boolean {
        val store = PetalEngineStore.getStore(context)
        val tab = store.state.tabs.firstOrNull { it.engineState.engineSession === engineSession } ?: return false
        mainHandler.post {
            try {
                store.dispatch(TabListAction.RemoveTabAction(tab.id))
            } catch (t: Throwable) {
                Log.w(TAG, "Extension tab close request failed", t)
            }
        }
        return true
    }

    // ── Permission prompts (Petal Compose dialogs) ───────────────────────────

    override fun onInstallPermissionRequest(
        extension: WebExtension,
        permissions: List<String>,
        origins: List<String>,
        dataCollectionPermissions: List<String>,
        onPermissionsGranted: (PermissionPromptResponse) -> Unit
    ) {
        val name = nameOf(extension)
        if (PetalExtensionManager.shouldAutoConfirm(extension.id, name)) {
            Log.i(TAG, "Auto-granting permissions for 1-tap/curated extension install ${extension.id}")
            onPermissionsGranted(PermissionPromptResponse(true, true, true))
            return
        }
        PetalExtensionManager.showPrompt(
            extensionId = extension.id,
            name = name,
            permissions = permissions + dataCollectionPermissions,
            origins = origins,
            isUpdate = false
        ) { granted -> onPermissionsGranted(PermissionPromptResponse(granted, granted, granted)) }
    }

    override fun onUpdatePermissionRequest(
        extension: WebExtension,
        newPermissions: List<String>,
        newOrigins: List<String>,
        newDataCollectionPermissions: List<String>,
        onPermissionsGranted: (Boolean) -> Unit
    ) {
        PetalExtensionManager.showPrompt(
            extensionId = extension.id,
            name = nameOf(extension),
            permissions = newPermissions + newDataCollectionPermissions,
            origins = newOrigins,
            isUpdate = true,
            respond = onPermissionsGranted
        )
    }

    // ── Actions and popups ───────────────────────────────────────────────────

    override fun onBrowserActionDefined(extension: WebExtension, action: Action) {
        // BrowserState.extensions already holds the action; PetalExtensionManager publishes it per tab.
        Log.d(TAG, "Browser action defined for ${extension.id}")
    }

    override fun onPageActionDefined(extension: WebExtension, action: Action) {
        Log.d(TAG, "Page action defined for ${extension.id}")
    }

    /**
     * The toolbar button was pressed (or the extension called `action.openPopup`). The returned
     * session is the one Gecko loads the extension's own `moz-extension://` popup page into.
     */
    override fun onToggleActionPopup(
        extension: WebExtension,
        engineSession: EngineSession,
        action: Action,
        isPrivate: Boolean
    ): EngineSession? {
        val allowedInPrivate = isPrivate || try { extension.isAllowedInPrivateBrowsing() } catch (_: Throwable) { false }
        return PetalExtensionManager.createPopupEngineSession(extension.id, nameOf(extension), allowedInPrivate)
    }

    /** `runtime.openOptionsPage()`: opens the settings page in a browser tab, gated on user intent. */
    override fun onOpenOptionsPage(extension: WebExtension) {
        if (!PetalExtensionManager.isUserInitiatedOpen(extension.id)) {
            Log.i(TAG, "Suppressed non-user runtime.openOptionsPage() from ${extension.id}")
            return
        }
        mainHandler.post {
            try { PetalExtensionManager.openOptionsPage(extension.id, context) }
            catch (t: Throwable) { Log.w(TAG, "openOptionsPage failed for ${extension.id}", t) }
        }
    }
}
