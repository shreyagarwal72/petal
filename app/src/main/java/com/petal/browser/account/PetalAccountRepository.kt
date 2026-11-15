package com.petal.browser.account

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.graphics.Color
import com.petal.browser.account.mozilla.FxaUi

/** What the account page actually shows: one resolved identity from local profile + Mozilla state. */
data class PetalAccount(
    val profile: PetalUserProfile,
    val fxa: FxaUi,
    val title: String,
    val subtitle: String,
    val isSignedIn: Boolean,
    val needsReauth: Boolean,
    val accent: Color?
)

object PetalAccountRepository {

    /** Pure merge of the local profile and the Mozilla account state; no side effects. */
    fun resolve(profile: PetalUserProfile, fxa: FxaUi): PetalAccount {
        val signedIn = fxa is FxaUi.SignedIn
        val email = (fxa as? FxaUi.SignedIn)?.email?.takeIf { it.isNotBlank() } ?: profile.email
        val subtitle = when (fxa) {
            is FxaUi.SignedIn -> email.ifBlank { "Mozilla account" }
            FxaUi.NeedsReauth -> "Mozilla account needs you to sign in again"
            FxaUi.Connecting -> "Signing in…"
            FxaUi.SignedOut -> "Local profile · not signed in"
        }
        return PetalAccount(
            profile = profile,
            fxa = fxa,
            title = profile.displayName,
            subtitle = subtitle,
            isSignedIn = signedIn,
            needsReauth = fxa == FxaUi.NeedsReauth,
            accent = parseAccent(profile.accentColorHex)
        )
    }

    fun parseAccent(hex: String?): Color? {
        if (hex.isNullOrBlank()) return null
        return runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
    }

    private fun Context.findActivity(): Activity? {
        var c: Context? = this
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }

    /**
     * Opens the App Lock settings page as its own decor overlay with its own predictive-back surface
     * (the same path the Java side uses), so it never nests inside another screen's back handling.
     */
    fun openAppLock(context: Context): Boolean {
        val activity = context.findActivity() ?: return false
        com.petal.browser.compose.security.PetalAppLockBridge.showConfig(activity, Runnable { })
        return true
    }
}
