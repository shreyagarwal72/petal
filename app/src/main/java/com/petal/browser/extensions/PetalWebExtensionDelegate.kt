package com.petal.browser.extensions

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.browser.BrowserContainer
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
 */
class PetalWebExtensionDelegate(private val context: Context) : WebExtensionDelegate {

    companion object {
        private const val TAG = "PetalWebExtDelegate"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun findBrowserActivity(): BrowserActivity? {
        var ctx: Context? = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is BrowserActivity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

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
        extension: WebExtension,
        engineSession: EngineSession,
        active: Boolean,
        url: String,
        isPrivate: Boolean
    ) {
        mainHandler.post {
            try {
                val act = findBrowserActivity() ?: return@post
                val title = extension.getMetadata()?.name ?: act.getString(com.petal.browser.R.string.app_name)
                val targetUrl = url.ifBlank { "about:blank" }
                act.runOnUiThread {
                    act.addAlbum(title, targetUrl, active, isPrivate)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to handle onNewTab from extension ${extension.id}: ${t.message}", t)
            }
        }
    }

    fun onToggleActionButton(
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
