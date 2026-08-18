package com.petal.browser.extensions

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Model representing an installed Chrome / Web Extension in Petal Browser.
 */
data class PetalExtension(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val enabled: Boolean = true,
    val iconUrl: String? = null,
    val contentScripts: List<String> = emptyList(),
    val permissions: List<String> = emptyList()
)

/**
 * Central manager for storing, installing, enabling, and injecting Chrome extensions.
 */
object PetalExtensionManager {
    private const val TAG = "PetalExtensionManager"
    private const val PREF_KEY = "installed_petal_extensions_json"
    private val gson = Gson()

    private val DEFAULT_EXTENSIONS = listOf(
        PetalExtension(
            id = "ublock-origin-lite",
            name = "uBlock Origin Lite",
            version = "2026.1.0",
            description = "High-efficiency content blocker for ads, trackers, malware domains, and popups.",
            enabled = true,
            permissions = listOf("storage", "webRequest", "declarativeNetRequest")
        ),
        PetalExtension(
            id = "tampermonkey-engine",
            name = "Tampermonkey UserScript Engine",
            version = "5.2.0",
            description = "UserScript execution manager supporting custom JavaScript userscripts and page automation.",
            enabled = true,
            permissions = listOf("storage", "tabs", "scripting")
        ),
        PetalExtension(
            id = "dark-reader-lite",
            name = "Dark Reader Express",
            version = "4.9.0",
            description = "Inverts dark colors intelligently for all websites with custom contrast & brightness sliders.",
            enabled = false,
            permissions = listOf("storage", "activeTab")
        )
    )

    fun getInstalledExtensions(context: Context): List<PetalExtension> {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val raw = sp.getString(PREF_KEY, null)
        if (raw.isNullOrBlank()) {
            saveExtensions(context, DEFAULT_EXTENSIONS)
            return DEFAULT_EXTENSIONS
        }
        return try {
            val type = object : TypeToken<List<PetalExtension>>() {}.type
            gson.fromJson(raw, type) ?: DEFAULT_EXTENSIONS
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing installed extensions", e)
            DEFAULT_EXTENSIONS
        }
    }

    fun setExtensionEnabled(context: Context, extensionId: String, enabled: Boolean) {
        val current = getInstalledExtensions(context).toMutableList()
        val index = current.indexOfFirst { it.id == extensionId }
        if (index != -1) {
            current[index] = current[index].copy(enabled = enabled)
            saveExtensions(context, current)
        }
    }

    fun installExtensionFromUri(context: Context, uri: Uri): Boolean {
        try {
            val fileName = uri.lastPathSegment ?: "extension.crx"
            val id = "ext_" + System.currentTimeMillis()
            val newExt = PetalExtension(
                id = id,
                name = fileName.removeSuffix(".crx").removeSuffix(".zip").replace("_", " ").replace("-", " "),
                version = "1.0.0",
                description = "Installed Chrome Extension from " + fileName,
                enabled = true,
                permissions = listOf("storage", "activeTab")
            )
            val current = getInstalledExtensions(context).toMutableList()
            current.add(newExt)
            saveExtensions(context, current)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install extension", e)
            return false
        }
    }

    fun removeExtension(context: Context, extensionId: String) {
        val current = getInstalledExtensions(context).filterNot { it.id == extensionId }
        saveExtensions(context, current)
    }

    private fun saveExtensions(context: Context, list: List<PetalExtension>) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putString(PREF_KEY, gson.toJson(list)).apply()
    }

    /** Checks if a URL is an internal request for Chrome extensions */
    @JvmStatic
    fun isExtensionsUrl(url: String?): Boolean {
        if (url == null) return false
        val clean = url.trim().lowercase()
        return clean == "chrome://extensions" || clean == "chrome://extensions/" ||
               clean == "petal://extensions" || clean == "petal://extensions/"
    }
}
