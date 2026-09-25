package com.petal.browser.extensions

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.browser.BrowserContainer
import com.petal.browser.view.PetalGeckoView
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.webextension.Action
import mozilla.components.concept.engine.webextension.WebExtension
import mozilla.components.concept.engine.webextension.WebExtensionDelegate

/**
 * PetalWebExtensionDelegate
 * ─────────────────────────────────────────────────────────────────────────
 * Mozilla Android Components [WebExtensionDelegate] implementation for Petal Browser.
 *
 * Handles extension-driven tab lifecycle, action buttons (browser actions,
 * page actions, and extension popups), and permission change requests.
 * Enables extensions like Bitwarden, KeePassXC, Dark Reader, and uBlock Origin
 * to open popup windows, dismiss tabs, and toggle extension actions.
 */
class PetalWebExtensionDelegate(private val context: Context) : WebExtensionDelegate {

    companion object {
        private const val TAG = "PetalWebExtDelegate"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onInstalled(extension: WebExtension) {
        Log.i(TAG, "Extension installed: ${extension.id}")
        PetalExtensionManager.refresh()
    }

    override fun onUninstalled(extension: WebExtension) {
        Log.i(TAG, "Extension uninstalled: ${extension.id}")
        PetalExtensionManager.refresh()
    }

    override fun onEnabled(extension: WebExtension) {
        Log.i(TAG, "Extension enabled: ${extension.id}")
        PetalExtensionManager.refresh()
    }

    override fun onDisabled(extension: WebExtension) {
        Log.i(TAG, "Extension disabled: ${extension.id}")
        PetalExtensionManager.refresh()
    }

    override fun onNewTab(
        extension: WebExtension?,
        engineSession: EngineSession,
        active: Boolean,
        url: String
    ): Boolean {
        mainHandler.post {
            try {
                val act = com.petal.browser.util.BrowserActivityExtensions.findBrowserActivity(context)
                if (act is BrowserActivity) {
                    val tabId = "tab_ext_${System.currentTimeMillis()}_${Math.abs(url.hashCode())}"
                    val sessionPair = com.petal.browser.engine.gecko.PetalEngineStore.createTabSession(
                        context = act,
                        tabId = tabId,
                        url = url.ifBlank { "about:blank" },
                        title = extension?.name ?: act.getString(com.petal.browser.R.string.app_name),
                        isIncognito = false,
                        select = active
                    )
                    val geckoView = com.petal.browser.controller.BrowserWebViewController.createAndConfigureGeckoView(
                        activity = act,
                        title = extension?.name ?: act.getString(com.petal.browser.R.string.app_name),
                        url = url,
                        foreground = active,
                        isIncognito = false,
                        adoptedSession = null,
                        engineSession = engineSession
                    )
                    geckoView.setTabId(tabId)
                    geckoView.setBrowserController(act)
                    BrowserContainer.add(geckoView)
                    if (active) {
                        act.showAlbum(geckoView)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to handle onNewTab from extension ${extension?.id}: ${t.message}", t)
            }
        }
        return true
    }

    override fun onCloseTab(extension: WebExtension?, session: EngineSession): Boolean {
        mainHandler.post {
            try {
                val act = com.petal.browser.util.BrowserActivityExtensions.findBrowserActivity(context)
                if (act is BrowserActivity) {
                    val target = BrowserContainer.list().firstOrNull {
                        (it as? PetalGeckoView)?.engineSession === session
                    }
                    if (target != null) {
                        act.removeAlbum(target)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to handle onCloseTab from extension ${extension?.id}: ${t.message}", t)
            }
        }
        return true
    }

    override fun onToggleActionButton(
        extension: WebExtension,
        action: Action,
        session: EngineSession?
    ): EngineSession? {
        mainHandler.post {
            try {
                PetalExtensionManager.triggerBrowserAction(extension.id, context)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to toggle action button for ${extension.id}: ${t.message}", t)
            }
        }
        return null
    }

    override fun onExtensionListUpdated() {
        PetalExtensionManager.refresh()
    }
}
