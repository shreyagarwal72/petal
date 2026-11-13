/*
 * FxAccountManager.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Petal's Mozilla/Firefox Account facade. All real work (OAuth + PKCE, scoped keys, token
 * refresh, device registration) is done by Mozilla's official FxaAccountManager inside
 * [PetalFxaServices]. This class only keeps Petal's UI-facing API stable.
 *
 * There are NO fabricated sessions: Signed-in means Mozilla's servers authenticated the user.
 */
package com.petal.browser.account.mozilla

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

sealed class FxaState {
    object SignedOut : FxaState()
    object SigningIn : FxaState()
    data class SignedIn(
        val email: String,
        val displayName: String? = null,
        val avatarUrl: String? = null,
        val uid: String? = null
    ) : FxaState()
    data class Error(val message: String) : FxaState()
}

enum class SyncEngine {
    BOOKMARKS, HISTORY, TABS
}

class FxAccountManager private constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _accountState = MutableStateFlow<FxaState>(FxaState.SignedOut)
    val accountState: StateFlow<FxaState> = _accountState.asStateFlow()

    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null
    @Volatile private var pendingState: String? = null
    @Volatile private var started = false

    internal fun services(context: Context): PetalFxaServices = PetalFxaServices.get(context)

    /** Call once from MainActivity. Restores the persisted Mozilla session, if any. */
    @Synchronized
    fun initialize(context: Context) {
        if (started) return
        started = true
        val ctx = context.applicationContext
        appContext = ctx
        prefs = ctx.getSharedPreferences("petal_fx_ui_prefs", Context.MODE_PRIVATE)
        // Tokens from the old hand-rolled implementation are worthless (never valid with Mozilla): wipe them.
        ctx.getSharedPreferences("petal_fx_sync_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        java.io.File(ctx.filesDir, "fxa_sync_snapshot").deleteRecursively()

        val svc = services(ctx)
        scope.launch { svc.state.collect { _accountState.value = it } }
        svc.start()
        PetalMozillaSyncManager.getInstance().attach(ctx)
    }

    // ── Sign in ───────────────────────────────────────────────────────────────────────────────

    /**
     * Starts Mozilla's real sign-in flow. Returns the accounts.firefox.com URL to show in the
     * in-app sheet, or null if Mozilla could not be reached. Pass the "Connect another device"
     * URL from desktop Firefox as [pairingUrl] to sign in by pairing.
     */
    suspend fun beginSignIn(context: Context, pairingUrl: String? = null): String? {
        val svc = services(context)
        val url = svc.beginSignIn(pairingUrl)
        pendingState = url?.let { extractQueryParam(it, "state") }
        return url
    }

    fun isRedirectUrl(url: String): Boolean = url.startsWith(PetalFxaServices.REDIRECT_URL)

    /** True while a sign-in started by this app is waiting for its redirect. */
    fun hasPendingAuth(state: String?): Boolean = !state.isNullOrBlank() && state == pendingState

    /** Completes sign-in from the redirect URL. Returns false (and sets an Error state) on failure. */
    suspend fun finishSignInFromRedirect(context: Context, redirectUrl: String): Boolean {
        val code = extractQueryParam(redirectUrl, "code")
        val state = extractQueryParam(redirectUrl, "state")
        if (code.isNullOrBlank() || state.isNullOrBlank()) return false
        pendingState = null
        return services(context).finishSignIn(code, state, extractQueryParam(redirectUrl, "action"))
    }

    fun cancelSignIn(context: Context) {
        pendingState = null
        services(context).cancelSignIn()
    }

    /** True for the QR/link desktop Firefox shows under "Connect another device". */
    fun isFirefoxPairingUrl(url: String): Boolean {
        val lower = url.trim().lowercase()
        return (lower.startsWith("https://firefox.com/pair") ||
                lower.startsWith("https://accounts.firefox.com/pair") ||
                lower.startsWith("https://accounts.firefox.com/connect_another_device")) &&
                (lower.contains("channel_id=") || lower.contains("channel=") || lower.contains("#"))
    }

    fun extractQueryParam(url: String, paramName: String): String? {
        val queryPart = when {
            url.contains("?") -> url.substringAfter("?").substringBefore("#")
            url.contains("#") -> url.substringAfter("#")
            else -> ""
        }
        for (pair in queryPart.split("&")) {
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2 && parts[0].equals(paramName, ignoreCase = true)) {
                return try {
                    java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name())
                } catch (_: Exception) { parts[1] }
            }
        }
        return null
    }

    fun logout() {
        val ctx = appContext ?: return
        pendingState = null
        services(ctx).signOut()
        prefs?.edit()?.remove(KEY_LAST_SYNC_TIME)?.apply()
        // Forget the local "what we last synced" snapshot so a different account starts clean.
        ctx.getSharedPreferences("petal_fx_sync_state", Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun getEmail(): String? = (accountState.value as? FxaState.SignedIn)?.email?.takeIf { it.isNotBlank() }
    fun getUserId(): String? = (accountState.value as? FxaState.SignedIn)?.uid

    // ── Sync engine toggles (stored by the adapter) ──────────────────────────────────────────

    fun isEngineEnabled(engine: SyncEngine): Boolean =
        appContext?.let { services(it).isEngineEnabled(engine) } ?: true

    fun setEngineEnabled(engine: SyncEngine, enabled: Boolean) {
        appContext?.let { services(it).setEngineEnabled(engine, enabled) }
    }

    fun getLastSyncTime(): Long = prefs?.getLong(KEY_LAST_SYNC_TIME, 0L) ?: 0L
    fun setLastSyncTime(timestamp: Long) { prefs?.edit()?.putLong(KEY_LAST_SYNC_TIME, timestamp)?.apply() }

    fun getDeviceName(): String = PetalFxaServices.deviceName()

    companion object {
        private const val KEY_LAST_SYNC_TIME = "fxa_last_sync_time"

        @Volatile
        private var instance: FxAccountManager? = null

        fun getInstance(): FxAccountManager =
            instance ?: synchronized(this) { instance ?: FxAccountManager().also { instance = it } }
    }
}
