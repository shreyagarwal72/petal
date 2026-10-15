/*
 * PetalFilterSubscriptionManager.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Filter List Subscription & Custom Choice Manager for Petal Browser (Cromite-style).
 * Manages curated lists (EasyList, EasyPrivacy, Fanboy, uBlock, Peter Lowe) and user custom URLs.
 */

package com.petal.browser.browser

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PetalFilterSubscription(
    val id: String,
    val name: String,
    val description: String,
    val url: String,
    val isEnabled: Boolean,
    val isCustom: Boolean,
    val rulesCount: Int = 0,
    val lastUpdatedMs: Long = 0L,
    val etag: String? = null
)

object PetalFilterSubscriptionManager {

    private const val PREF_KEY_SUBSCRIPTIONS = "sp_adblock_subscriptions_v2"
    private const val PREF_KEY_CUSTOM_RULES = "sp_adblock_custom_user_rules"
    private const val RULES_DIR_NAME = "adblock_rules"

    private val isUpdating = AtomicBoolean(false)
    private val compiledRulesCache = ConcurrentHashMap.newKeySet<String>()

    val DEFAULT_SUBSCRIPTIONS = listOf(
        PetalFilterSubscription(
            id = "easylist",
            name = "EasyList",
            description = "Primary filter list blocking web banners, popups, and display ads.",
            url = "https://easylist.to/easylist/easylist.txt",
            isEnabled = true,
            isCustom = false
        ),
        PetalFilterSubscription(
            id = "easyprivacy",
            name = "EasyPrivacy",
            description = "Defends your privacy by blocking tracking scripts, web beacons, and telemetry.",
            url = "https://easylist.to/easylist/easyprivacy.txt",
            isEnabled = true,
            isCustom = false
        ),
        PetalFilterSubscription(
            id = "ublock_filters",
            name = "uBlock Origin Filters",
            description = "Defuses aggressive anti-adblock detection scripts and malvertising.",
            url = "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/filters.txt",
            isEnabled = true,
            isCustom = false
        ),
        PetalFilterSubscription(
            id = "fanboy_annoyance",
            name = "Fanboy Annoyances & Cookies",
            description = "Hides GDPR cookie consent dialogs, overlay nags, and in-page newsletter prompts.",
            url = "https://secure.fanboy.co.nz/fanboy-annoyance.txt",
            isEnabled = false,
            isCustom = false
        ),
        PetalFilterSubscription(
            id = "peter_lowe",
            name = "Peter Lowe's Ad & Tracker List",
            description = "Lightweight, highly optimized list targeting known ad & tracking servers.",
            url = "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
            isEnabled = true,
            isCustom = false
        )
    )

    fun getSubscriptions(context: Context): List<PetalFilterSubscription> {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext ?: context)
        val raw = sp.getString(PREF_KEY_SUBSCRIPTIONS, null) ?: return DEFAULT_SUBSCRIPTIONS

        return try {
            val jsonArray = JSONArray(raw)
            val list = mutableListOf<PetalFilterSubscription>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    PetalFilterSubscription(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        description = obj.optString("description", ""),
                        url = obj.getString("url"),
                        isEnabled = obj.optBoolean("isEnabled", true),
                        isCustom = obj.optBoolean("isCustom", false),
                        rulesCount = obj.optInt("rulesCount", 0),
                        lastUpdatedMs = obj.optLong("lastUpdatedMs", 0L),
                        etag = obj.optString("etag").takeIf { it.isNotEmpty() }
                    )
                )
            }
            if (list.isEmpty()) DEFAULT_SUBSCRIPTIONS else list
        } catch (_: Exception) {
            DEFAULT_SUBSCRIPTIONS
        }
    }

    fun saveSubscriptions(context: Context, subscriptions: List<PetalFilterSubscription>) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext ?: context)
        val jsonArray = JSONArray()
        for (sub in subscriptions) {
            val obj = JSONObject().apply {
                put("id", sub.id)
                put("name", sub.name)
                put("description", sub.description)
                put("url", sub.url)
                put("isEnabled", sub.isEnabled)
                put("isCustom", sub.isCustom)
                put("rulesCount", sub.rulesCount)
                put("lastUpdatedMs", sub.lastUpdatedMs)
                sub.etag?.let { put("etag", it) }
            }
            jsonArray.put(obj)
        }
        sp.edit().putString(PREF_KEY_SUBSCRIPTIONS, jsonArray.toString()).apply()
    }

    fun toggleSubscription(context: Context, id: String, isEnabled: Boolean) {
        val current = getSubscriptions(context).toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            current[index] = current[index].copy(isEnabled = isEnabled)
            saveSubscriptions(context, current)
            reloadActiveRules(context)
        }
    }

    fun addCustomSubscription(context: Context, name: String, url: String): Result<PetalFilterSubscription> {
        val trimmedUrl = url.trim()
        if (!trimmedUrl.startsWith("http://", ignoreCase = true) && !trimmedUrl.startsWith("https://", ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("URL must start with http:// or https://"))
        }

        val current = getSubscriptions(context).toMutableList()
        if (current.any { it.url.equals(trimmedUrl, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("Subscription URL already exists"))
        }

        val customSub = PetalFilterSubscription(
            id = "custom_" + System.currentTimeMillis(),
            name = name.ifBlank { "Custom Filter List" },
            description = "User-added custom filter subscription: $trimmedUrl",
            url = trimmedUrl,
            isEnabled = true,
            isCustom = true,
            lastUpdatedMs = 0L
        )

        current.add(customSub)
        saveSubscriptions(context, current)
        return Result.success(customSub)
    }

    fun removeCustomSubscription(context: Context, id: String) {
        val current = getSubscriptions(context).toMutableList()
        current.removeAll { it.id == id && it.isCustom }
        saveSubscriptions(context, current)

        // Delete cache file
        val rulesDir = getRulesDir(context)
        File(rulesDir, "$id.txt").delete()
        reloadActiveRules(context)
    }

    fun getCustomUserRules(context: Context): String {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext ?: context)
        return sp.getString(PREF_KEY_CUSTOM_RULES, "") ?: ""
    }

    fun saveCustomUserRules(context: Context, rules: String) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext ?: context)
        sp.edit().putString(PREF_KEY_CUSTOM_RULES, rules).apply()
        reloadActiveRules(context)
    }

    private fun getRulesDir(context: Context): File {
        val dir = File(context.filesDir, RULES_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun updateAllSubscriptions(
        context: Context,
        force: Boolean = false,
        onProgress: ((completed: Int, total: Int, currentName: String) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        if (!isUpdating.compareAndSet(false, true)) return@withContext 0

        val current = getSubscriptions(context).toMutableList()
        val rulesDir = getRulesDir(context)
        var updatedCount = 0

        try {
            for ((index, sub) in current.withIndex()) {
                onProgress?.invoke(index, current.size, sub.name)
                if (!sub.isEnabled && !force) continue

                val cachedFile = File(rulesDir, "${sub.id}.txt")
                val (newEtag, ruleCount) = fetchAndCacheList(sub.url, cachedFile, if (force) null else sub.etag)

                if (ruleCount > 0) {
                    current[index] = sub.copy(
                        rulesCount = ruleCount,
                        lastUpdatedMs = System.currentTimeMillis(),
                        etag = newEtag ?: sub.etag
                    )
                    updatedCount++
                } else if (cachedFile.exists()) {
                    current[index] = sub.copy(
                        rulesCount = countRulesInFile(cachedFile),
                        lastUpdatedMs = System.currentTimeMillis()
                    )
                }
            }
            saveSubscriptions(context, current)
            reloadActiveRules(context)
        } finally {
            isUpdating.set(false)
            onProgress?.invoke(current.size, current.size, "Done")
        }

        updatedCount
    }

    private fun fetchAndCacheList(urlString: String, outputFile: File, etag: String?): Pair<String?, Int> {
        return try {
            val connection = URI(urlString).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; PetalBrowser/1.0)")
            etag?.let { connection.setRequestProperty("If-None-Match", it) }

            connection.connect()
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED) {
                connection.disconnect()
                return Pair(etag, countRulesInFile(outputFile))
            }

            if (code != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                return Pair(null, 0)
            }

            val newEtag = connection.getHeaderField("ETag")
            val tempFile = File(outputFile.parentFile, "${outputFile.name}.tmp")
            var lines = 0

            connection.inputStream.bufferedReader().use { reader ->
                tempFile.bufferedWriter().use { writer ->
                    reader.forEachLine { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("!") && !trimmed.startsWith("#")) {
                            writer.write(trimmed)
                            writer.newLine()
                            lines++
                        }
                    }
                }
            }
            connection.disconnect()

            if (tempFile.exists() && tempFile.length() > 0) {
                if (outputFile.exists()) outputFile.delete()
                tempFile.renameTo(outputFile)
            } else {
                tempFile.delete()
            }

            Pair(newEtag, lines)
        } catch (_: Exception) {
            Pair(null, 0)
        }
    }

    private fun countRulesInFile(file: File): Int {
        if (!file.exists()) return 0
        var count = 0
        try {
            file.forEachLine { line ->
                if (line.isNotBlank()) count++
            }
        } catch (_: Exception) {}
        return count
    }

    fun reloadActiveRules(context: Context) {
        val rulesDir = getRulesDir(context)
        val activeSubscriptions = getSubscriptions(context).filter { it.isEnabled }
        val newSet = HashSet<String>()

        for (sub in activeSubscriptions) {
            val file = File(rulesDir, "${sub.id}.txt")
            if (file.exists()) {
                try {
                    file.forEachLine { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("!") && !trimmed.startsWith("#")) {
                            // Extract domain or pattern if hosts format
                            val parts = trimmed.split(Regex("\\s+"))
                            if (parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1")) {
                                newSet.add(parts[1].lowercase())
                            } else {
                                val clean = trimmed.removePrefix("||").removeSuffix("^")
                                if (clean.isNotEmpty() && !clean.contains('/')) {
                                    newSet.add(clean.lowercase())
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // Add custom user rules
        val customRules = getCustomUserRules(context)
        if (customRules.isNotBlank()) {
            customRules.lineSequence().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("!") && !trimmed.startsWith("#")) {
                    val clean = trimmed.removePrefix("||").removeSuffix("^")
                    if (clean.isNotEmpty()) newSet.add(clean.lowercase())
                }
            }
        }

        compiledRulesCache.clear()
        compiledRulesCache.addAll(newSet)
    }

    fun isDomainInActiveSubscriptions(domain: String): Boolean {
        if (domain.isBlank() || compiledRulesCache.isEmpty()) return false
        val clean = domain.lowercase().removePrefix("www.")
        return compiledRulesCache.contains(clean) || compiledRulesCache.any { clean.endsWith(".$it") }
    }
}
