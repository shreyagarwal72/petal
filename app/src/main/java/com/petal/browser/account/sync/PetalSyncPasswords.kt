package com.petal.browser.account.sync

import android.content.Context
import android.net.Uri
import com.petal.browser.passwords.PetalCredential
import com.petal.browser.passwords.PetalCredentialVault
import com.petal.browser.passwords.PetalLoginsProvider
import org.json.JSONArray
import org.json.JSONObject

/** A saved login as it travels in the sync file. Only ever written inside the passphrase-encrypted file. */
internal data class SyncPassword(
    val key: String,
    val id: String,
    val origin: String,
    val formAction: String?,
    val username: String,
    val password: String,
    val changedAt: Long
)

internal object PetalSyncPasswords {

    fun key(origin: String, username: String): String =
        origin.trim().lowercase() + "\n" + username.trim().lowercase()

    fun read(context: Context): List<SyncPassword> =
        PetalLoginsProvider.getAll(context)
            .filter { it.password.isNotBlank() }
            .map {
                SyncPassword(
                    key = key(it.originUrl, it.username),
                    id = it.id,
                    origin = it.originUrl,
                    formAction = it.formActionOrigin,
                    username = it.username,
                    password = it.password,
                    changedAt = it.updatedAt
                )
            }

    fun toJson(list: List<SyncPassword>): JSONArray {
        val arr = JSONArray()
        for (p in list) {
            arr.put(
                JSONObject().put("o", p.origin).put("f", p.formAction ?: JSONObject.NULL)
                    .put("n", p.username).put("p", p.password).put("ts", p.changedAt)
            )
        }
        return arr
    }

    /** Deletes logins another device removed. Returns how many were removed. */
    fun applyDeletes(context: Context, local: List<SyncPassword>, state: SyncCategoryState): Int {
        var n = 0
        for (p in local) if (state.isDeleted(p.key)) { PetalLoginsProvider.delete(context, p.id); n++ }
        return n
    }

    /** Adds logins this device lacks and takes newer passwords. Returns how many changed. */
    fun mergeRemote(context: Context, remote: JSONArray?, state: SyncCategoryState, local: List<SyncPassword>): Int {
        if (remote == null) return 0
        val byKey = local.associateBy { it.key }
        var changed = 0
        for (i in 0 until remote.length()) {
            val o = remote.optJSONObject(i) ?: continue
            val origin = o.optString("o", "")
            val user = o.optString("n", "")
            val pass = o.optString("p", "")
            if (origin.isBlank() || pass.isBlank()) continue
            val key = key(origin, user)
            if (state.isDeleted(key)) continue
            val ts = o.optLong("ts", 0L)
            val mine = byKey[key]
            if (mine == null) {
                val form = if (o.isNull("f")) null else o.optString("f").ifBlank { null }
                PetalLoginsProvider.save(
                    context,
                    PetalCredential(
                        id = "",
                        domain = runCatching { Uri.parse(origin).host }.getOrNull() ?: origin,
                        originUrl = origin,
                        username = user,
                        password = pass,
                        formActionOrigin = form
                    )
                )
                changed++
            } else if (ts > mine.changedAt && pass != mine.password) {
                PetalLoginsProvider.updatePassword(context, mine.id, pass)
                changed++
            }
        }
        return changed
    }

    /** The password manager screen keeps an in-memory copy; refresh it after sync changed storage. */
    fun refreshVault(context: Context) {
        try { PetalCredentialVault.reloadFromStorage(context) } catch (_: Throwable) {}
    }
}
