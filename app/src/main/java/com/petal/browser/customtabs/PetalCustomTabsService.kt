package com.petal.browser.customtabs

import android.net.Uri
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsService
import androidx.browser.customtabs.CustomTabsSessionToken

import java.util.concurrent.ConcurrentHashMap

/**
 * PetalCustomTabsService
 * ─────────────────────────────────────────────────────────────────────────
 * Bound service implementing the AndroidX Custom Tabs protocol ([CustomTabsService]).
 * Enables external third-party apps to discover Petal as an official Custom Tabs provider
 * and establish warm sessions.
 *
 * Implements Mozilla Firefox Fenix session tokens and GeckoView asynchronous warmup.
 */
class PetalCustomTabsService : CustomTabsService() {

    companion object {
        private val activeSessions = ConcurrentHashMap.newKeySet<CustomTabsSessionToken>()

        fun hasActiveSession(token: CustomTabsSessionToken): Boolean = activeSessions.contains(token)
    }

    override fun warmup(flags: Long): Boolean {
        java.util.concurrent.Executors.newSingleThreadExecutor().execute {
            try {
                if (com.petal.browser.engine.gecko.PetalGeckoRuntime.isGeckoAvailable(applicationContext)) {
                    com.petal.browser.engine.gecko.PetalGeckoRuntime.getOrCreate(applicationContext)
                    com.petal.browser.engine.gecko.PetalEngineStore.getEngine(applicationContext)
                }
            } catch (_: Throwable) {}
        }
        return true
    }

    override fun newSession(sessionToken: CustomTabsSessionToken): Boolean {
        activeSessions.add(sessionToken)
        return true
    }

    override fun mayLaunchUrl(
        sessionToken: CustomTabsSessionToken,
        url: Uri?,
        extras: Bundle?,
        otherLikelyBundles: MutableList<Bundle>?
    ): Boolean {
        // Speculatively warm up GeckoRuntime if needed
        warmup(0L)
        return true
    }

    override fun cleanUpSession(sessionToken: CustomTabsSessionToken): Boolean {
        activeSessions.remove(sessionToken)
        return super.cleanUpSession(sessionToken)
    }

    override fun extraCommand(commandName: String, args: Bundle?): Bundle? {
        return null
    }

    override fun updateVisuals(sessionToken: CustomTabsSessionToken, bundle: Bundle?): Boolean {
        return false
    }

    override fun requestPostMessageChannel(
        sessionToken: CustomTabsSessionToken,
        postMessageOrigin: Uri
    ): Boolean {
        return false
    }

    override fun postMessage(
        sessionToken: CustomTabsSessionToken,
        message: String,
        extras: Bundle?
    ): Int {
        return CustomTabsService.RESULT_FAILURE_DISALLOWED
    }

    override fun validateRelationship(
        sessionToken: CustomTabsSessionToken,
        relation: Int,
        origin: Uri,
        extras: Bundle?
    ): Boolean {
        return false
    }

    override fun receiveFile(
        sessionToken: CustomTabsSessionToken,
        uri: Uri,
        purpose: Int,
        extras: Bundle?
    ): Boolean {
        return false
    }
}
