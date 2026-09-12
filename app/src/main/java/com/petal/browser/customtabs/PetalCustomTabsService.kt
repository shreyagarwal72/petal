package com.petal.browser.customtabs

import android.net.Uri
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsService
import androidx.browser.customtabs.CustomTabsSessionToken

/**
 * PetalCustomTabsService
 * ─────────────────────────────────────────────────────────────────────────
 * Bound service implementing the AndroidX Custom Tabs protocol ([CustomTabsService]).
 * Enables external third-party apps to discover Petal as an official Custom Tabs provider
 * and establish warm sessions.
 *
 * Implements [newSession] returning true, and provides no-op implementations for
 * [warmup] and [mayLaunchUrl] that return true, as GeckoView initialization is managed
 * by PetalGeckoRuntime on demand.
 */
class PetalCustomTabsService : CustomTabsService() {

    override fun warmup(flags: Long): Boolean {
        return true
    }

    override fun newSession(sessionToken: CustomTabsSessionToken): Boolean {
        return true
    }

    override fun mayLaunchUrl(
        sessionToken: CustomTabsSessionToken,
        url: Uri?,
        extras: Bundle?,
        otherLikelyBundles: MutableList<Bundle>?
    ): Boolean {
        return true
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
