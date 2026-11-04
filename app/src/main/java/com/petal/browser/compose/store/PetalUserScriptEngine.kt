package com.petal.browser.compose.store

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * PetalUserScriptEngine
 * ─────────────────────────────────────────────────────────────────────────
 * Manages user-installed UserScripts (.user.js) with metadata parsing, matching,
 * and isolated execution.
 */
object PetalUserScriptEngine {

    private const val PREF_CUSTOM_USER_SCRIPTS = "sp_custom_user_scripts_json"
    private const val SCRIPTS_DIR = "user_scripts"

    data class UserScriptMeta(
        val id: String,
        val name: String,
        val author: String = "",
        val version: String = "1.0",
        val description: String = "",
        val matchPatterns: List<String> = emptyList(),
        val runAt: String = "document-idle", // document-start, document-end, document-idle
        val grants: List<String> = emptyList(),
        val isEnabled: Boolean = true,
        val fileName: String = ""
    )

    /**
     * Parses standard UserScript metadata header block:
     * // ==UserScript==
     * // @name ...
     * // @match ...
     * // @run-at ...
     * // ==/UserScript==
     */
    fun parseMetadata(scriptContent: String, scriptId: String): UserScriptMeta {
        var name = scriptId
        var author = ""
        var version = "1.0"
        var description = ""
        val matches = mutableListOf<String>()
        var runAt = "document-idle"
        val grants = mutableListOf<String>()

        val lines = scriptContent.lines()
        var insideBlock = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("// ==UserScript==", ignoreCase = true)) {
                insideBlock = true
                continue
            }
            if (trimmed.startsWith("// ==/UserScript==", ignoreCase = true)) {
                break
            }
            if (insideBlock && trimmed.startsWith("//")) {
                val directive = trimmed.substring(2).trim()
                if (directive.startsWith("@name", ignoreCase = true)) {
                    name = directive.substring(5).trim()
                } else if (directive.startsWith("@author", ignoreCase = true)) {
                    author = directive.substring(7).trim()
                } else if (directive.startsWith("@version", ignoreCase = true)) {
                    version = directive.substring(8).trim()
                } else if (directive.startsWith("@description", ignoreCase = true)) {
                    description = directive.substring(12).trim()
                } else if (directive.startsWith("@match", ignoreCase = true)) {
                    val pattern = directive.substring(6).trim()
                    if (pattern.isNotBlank()) matches.add(pattern)
                } else if (directive.startsWith("@include", ignoreCase = true)) {
                    val pattern = directive.substring(8).trim()
                    if (pattern.isNotBlank()) matches.add(pattern)
                } else if (directive.startsWith("@run-at", ignoreCase = true)) {
                    runAt = directive.substring(7).trim().lowercase()
                } else if (directive.startsWith("@grant", ignoreCase = true)) {
                    val grant = directive.substring(6).trim()
                    if (grant.isNotBlank()) grants.add(grant)
                }
            }
        }

        if (matches.isEmpty()) {
            matches.add("*://*/*")
        }

        return UserScriptMeta(
            id = scriptId,
            name = name,
            author = author,
            version = version,
            description = description,
            matchPatterns = matches,
            runAt = runAt,
            grants = grants,
            isEnabled = true,
            fileName = "$scriptId.user.js"
        )
    }

    private fun getStorageDir(context: Context): File {
        return File(context.filesDir, SCRIPTS_DIR).apply { mkdirs() }
    }

    fun getAllCustomScripts(context: Context): List<UserScriptMeta> {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val jsonStr = sp.getString(PREF_CUSTOM_USER_SCRIPTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<UserScriptMeta>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val matchArr = obj.optJSONArray("matchPatterns")
                val matches = mutableListOf<String>()
                if (matchArr != null) {
                    for (j in 0 until matchArr.length()) matches.add(matchArr.getString(j))
                }
                list.add(
                    UserScriptMeta(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        author = obj.optString("author", ""),
                        version = obj.optString("version", "1.0"),
                        description = obj.optString("description", ""),
                        matchPatterns = matches,
                        runAt = obj.optString("runAt", "document-idle"),
                        isEnabled = obj.optBoolean("isEnabled", true),
                        fileName = obj.optString("fileName", "")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveCustomScripts(context: Context, scripts: List<UserScriptMeta>) {
        val array = JSONArray()
        for (s in scripts) {
            val obj = JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("author", s.author)
                put("version", s.version)
                put("description", s.description)
                put("runAt", s.runAt)
                put("isEnabled", s.isEnabled)
                put("fileName", s.fileName)
                val mArr = JSONArray()
                s.matchPatterns.forEach { mArr.put(it) }
                put("matchPatterns", mArr)
            }
            array.put(obj)
        }
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(PREF_CUSTOM_USER_SCRIPTS, array.toString())
            .apply()
    }

    fun installScript(context: Context, scriptContent: String): UserScriptMeta {
        val id = "userscript_" + System.currentTimeMillis()
        val meta = parseMetadata(scriptContent, id)
        val file = File(getStorageDir(context), meta.fileName)
        file.writeText(scriptContent, Charsets.UTF_8)

        val current = getAllCustomScripts(context).toMutableList()
        current.removeAll { it.id == id }
        current.add(meta)
        saveCustomScripts(context, current)
        return meta
    }

    fun removeScript(context: Context, scriptId: String) {
        val current = getAllCustomScripts(context).toMutableList()
        val target = current.firstOrNull { it.id == scriptId }
        if (target != null) {
            File(getStorageDir(context), target.fileName).delete()
            current.remove(target)
            saveCustomScripts(context, current)
        }
    }

    fun toggleScript(context: Context, scriptId: String, enabled: Boolean) {
        val current = getAllCustomScripts(context).map {
            if (it.id == scriptId) it.copy(isEnabled = enabled) else it
        }
        saveCustomScripts(context, current)
    }

    fun getScriptContent(context: Context, meta: UserScriptMeta): String? {
        val file = File(getStorageDir(context), meta.fileName)
        return if (file.exists()) file.readText(Charsets.UTF_8) else null
    }

    /**
     * Finds all active custom scripts that match [pageUrl] and are scheduled for [runStage].
     * [runStage] is one of: "document-start", "document-end", "document-idle"
     */
    fun getActiveScriptsForUrl(context: Context, pageUrl: String, runStage: String): List<String> {
        val scripts = getAllCustomScripts(context).filter { it.isEnabled && it.runAt.equals(runStage, ignoreCase = true) }
        val matchingBodies = mutableListOf<String>()

        for (script in scripts) {
            val matches = script.matchPatterns.any { pattern ->
                PetalStoreCatalog.matchesPattern(pattern, pageUrl)
            }
            if (matches) {
                val content = getScriptContent(context, script)
                if (!content.isNullOrBlank()) {
                    matchingBodies.add(content)
                }
            }
        }
        return matchingBodies
    }
}
