package com.petal.browser.account.sync

import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.InputType
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.petal.browser.activity.MainActivity
import com.petal.browser.passwords.PetalLoginsProvider
import com.petal.browser.unit.PetalBackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Opens a `.psync` file straight from a file manager, cloud app, share sheet or the account page. The
 * file is decrypted with its passphrase and then either added to this device (the default, nothing is
 * deleted) or used to restore it (what's on this device is replaced by what's in the file). The file
 * itself is never modified, and ongoing sync is a separate step.
 */
object PetalSyncImport {
    const val MIME = "application/vnd.petal.sync"
    private const val MAX_BYTES = 100L * 1024 * 1024
    private val LISTS = listOf("bookmarks", "history", "start_sites", "saved_sites", "trusted_sites", "protected_sites")

    /** True when the intent data is a Petal sync file, by type, by name, or by the provider's display name. */
    fun isSyncFile(context: Context, uri: Uri, type: String?): Boolean {
        if (uri.scheme != "content" && uri.scheme != "file") return false
        if (type == MIME) return true
        if (uri.path?.endsWith(".psync", ignoreCase = true) == true) return true
        if (uri.scheme == "content" && (type == null || type == "application/octet-stream")) {
            return displayName(context, uri)?.endsWith(".psync", ignoreCase = true) == true
        }
        return false
    }

    private fun displayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (_: Exception) { null }

    fun handle(activity: MainActivity, uri: Uri) {
        val name = displayName(activity, uri) ?: uri.lastPathSegment ?: "sync file"
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val bytes = try {
                activity.contentResolver.openInputStream(uri)?.use { s ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = s.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) throw java.io.IOException("too large")
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                }
            } catch (_: Exception) { null }

            if (bytes == null || !PetalSyncCrypto.isPetalFile(bytes)) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(activity, "That isn't a valid Petal sync file.", Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            // If this device already has a sync passphrase, try it first so the user isn't asked again.
            val saved = PetalSyncVault.load(activity)
            val unlocked = saved?.let { runCatching { PetalSyncCrypto.decrypt(bytes, it) }.getOrNull() }
            withContext(Dispatchers.Main) { showImportDialog(activity, bytes, name, unlocked) }
        }
    }

    private fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    private fun showImportDialog(activity: MainActivity, bytes: ByteArray, name: String, unlocked: ByteArray?) {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 24), dp(activity, 8), dp(activity, 24), 0)
        }
        box.addView(TextView(activity).apply { text = "What should Petal do with \"$name\"?" })

        val addChoice = RadioButton(activity).apply {
            id = View.generateViewId()
            text = "Add to this device\nKeeps what you have and adds what's missing. Nothing is deleted."
            isChecked = true
        }
        val restoreChoice = RadioButton(activity).apply {
            id = View.generateViewId()
            text = "Restore from this file\nReplaces what's on this device with what's in the file."
        }
        val group = RadioGroup(activity).apply {
            orientation = RadioGroup.VERTICAL
            addView(addChoice)
            addView(restoreChoice)
        }
        box.addView(group)

        val field = EditText(activity).apply {
            hint = "Sync passphrase"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            visibility = if (unlocked != null) View.GONE else View.VISIBLE
        }
        box.addView(field)
        val passwords = CheckBox(activity).apply { text = "Include saved passwords (if the file has any)" }
        box.addView(passwords)

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Open Petal sync file")
            .setView(box)
            .setPositiveButton("Add", null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            group.setOnCheckedChangeListener { _, checkedId ->
                button.text = if (checkedId == restoreChoice.id) "Restore" else "Add"
            }

            fun runImport(replace: Boolean) {
                val typed = field.text.toString()
                button.isEnabled = false
                activity.lifecycleScope.launch(Dispatchers.IO) {
                    val plain = unlocked ?: try {
                        PetalSyncCrypto.decrypt(bytes, typed.toCharArray())
                    } catch (_: IllegalArgumentException) { null }
                    if (plain == null) {
                        withContext(Dispatchers.Main) { field.error = "Wrong passphrase"; button.isEnabled = true }
                        return@launch
                    }
                    val result = try {
                        apply(activity, JSONObject(String(plain, Charsets.UTF_8)), passwords.isChecked, replace)
                    } catch (e: Exception) { "Import failed: ${e.message ?: e.javaClass.simpleName}" to false }
                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                        showResult(activity, result.first, result.second, replace, if (unlocked == null) typed else null)
                    }
                }
            }

            button.setOnClickListener {
                if (unlocked == null && field.text.isNullOrEmpty()) { field.error = "Enter the passphrase"; return@setOnClickListener }
                val replace = group.checkedRadioButtonId == restoreChoice.id
                if (!replace) { runImport(false); return@setOnClickListener }
                // Restoring is destructive, so ask once more.
                AlertDialog.Builder(activity)
                    .setTitle("Replace what's on this device?")
                    .setMessage(
                        "Bookmarks, history, home shortcuts and site rules on this device will be replaced by the ones in the file" +
                            (if (passwords.isChecked) ", and saved passwords too" else "") +
                            ". This can't be undone.\n\nIf sync is on, your other devices get these changes on the next sync."
                    )
                    .setPositiveButton("Restore") { _, _ -> runImport(true) }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
        dialog.show()
    }

    /** @return a message, and whether it succeeded */
    private fun apply(context: Context, json: JSONObject, includePasswords: Boolean, replace: Boolean): Pair<String, Boolean> {
        val additions = JSONObject()
        for (k in LISTS) json.opt(k)?.let { additions.put(k, it) }

        if (replace) {
            // Only lists the file actually contains are replaced; anything it doesn't mention is left alone.
            PetalBackupManager.clearForRestore(
                context,
                bookmarks = json.has("bookmarks"),
                history = json.has("history"),
                startSites = json.has("start_sites"),
                savedSites = json.has("saved_sites") || json.has("trusted_sites") || json.has("protected_sites")
            )
        }
        var summary = PetalBackupManager.mergeSyncSnapshot(context, additions, true, true, true, true)
            .removePrefix("Restored: ").removeSuffix(".")
        if (includePasswords && json.has("passwords")) {
            if (replace) PetalLoginsProvider.clear(context)
            val n = PetalSyncPasswords.mergeRemote(context, json.optJSONArray("passwords"), SyncCategoryState(), PetalSyncPasswords.read(context))
            PetalSyncPasswords.refreshVault(context)
            summary += ", $n password${if (n == 1) "" else "s"}"
        }
        return (if (replace) "Restored: $summary." else "Added: $summary.") to true
    }

    private fun showResult(activity: MainActivity, message: String, ok: Boolean, restored: Boolean, typedPassphrase: String?) {
        val extra = when {
            !ok -> ""
            PetalFolderSync.configured.value -> if (restored) "\n\nYour other devices will get these changes on the next sync." else ""
            else -> "\n\nTo keep this device in sync, choose a folder in Account & Sync."
        }
        val builder = AlertDialog.Builder(activity)
            .setTitle(if (!ok) "Import failed" else if (restored) "Restored" else "Imported")
            .setMessage(message + extra)
            .setNegativeButton(android.R.string.ok, null)
        if (ok && !PetalFolderSync.configured.value) {
            builder.setPositiveButton("Set up sync") { _, _ ->
                // Pre-fill the passphrase on the setup screen so it only needs a folder.
                PetalFolderSync.importedPassphrase = typedPassphrase?.takeIf { it.isNotEmpty() }
                activity.viewModel.openAccountSync()
            }
        }
        builder.show()
    }
}
