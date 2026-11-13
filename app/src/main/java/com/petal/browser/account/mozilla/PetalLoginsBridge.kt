package com.petal.browser.account.mozilla

import android.content.Context
import android.net.Uri
import com.petal.browser.passwords.PetalCredential
import com.petal.browser.passwords.PetalCredentialVault
import mozilla.components.concept.storage.LoginEntry
import mozilla.components.service.sync.logins.SyncableLoginsStorage
import java.util.UUID

/**
 * Mirrors Petal's encrypted password vault with Mozilla's SyncableLoginsStorage, the store the official
 * "passwords" sync engine uses. Same three-way merge as bookmarks (keyed by origin + username, remembering only
 * those keys, never passwords, between runs). If both sides changed a password, the newer change wins.
 */
internal class PetalLoginsBridge(private val context: Context, val logins: SyncableLoginsStorage) {

    private val state = context.getSharedPreferences("petal_fx_sync_state", Context.MODE_PRIVATE)

    private fun origin(c: PetalCredential): String {
        val raw = c.originUrl.ifBlank { "https://" + c.domain }
        val u = Uri.parse(raw)
        return if (u.scheme != null && u.host != null) "${u.scheme}://${u.host}" else raw
    }

    private fun key(origin: String, username: String) = origin.lowercase() + "|" + username.lowercase()

    private fun entry(c: PetalCredential, origin: String) = LoginEntry(
        origin = origin,
        formActionOrigin = c.formActionOrigin ?: origin,
        username = c.username,
        password = c.password
    )

    suspend fun reconcile() {
        PetalCredentialVault.init(context)
        val local = PetalCredentialVault.getAll().filter { it.password.isNotEmpty() }
            .associateBy { key(origin(it), it.username) }
        val remote = logins.list().associateBy { key(it.origin, it.username) }
        val last = state.getStringSet("login_snapshot", emptySet()) ?: emptySet()
        val firstRun = !state.contains("login_snapshot")
        val final = HashSet<String>()

        for ((k, c) in local) {
            val r = remote[k]
            if (r == null) {
                if (!firstRun && k in last) PetalCredentialVault.delete(c.id) // removed on another device
                else { try { logins.add(entry(c, origin(c))); final += k } catch (_: Throwable) {} }
            } else {
                final += k
                if (r.password != c.password) {
                    if (c.updatedAt > r.timePasswordChanged) {
                        try { logins.update(r.guid, entry(c, origin(c))) } catch (_: Throwable) {}
                    } else PetalCredentialVault.updatePassword(c.id, r.password)
                }
            }
        }
        for ((k, r) in remote) {
            if (k in local) continue
            if (!firstRun && k in last) { try { logins.delete(r.guid) } catch (_: Throwable) {} } // removed in Petal
            else {
                val host = Uri.parse(r.origin).host ?: r.origin
                PetalCredentialVault.save(
                    PetalCredential(
                        id = UUID.randomUUID().toString(), domain = host, originUrl = r.origin,
                        username = r.username, password = r.password,
                        formActionOrigin = r.formActionOrigin
                    )
                )
                final += k
            }
        }
        state.edit().putStringSet("login_snapshot", final).apply()
    }
}
