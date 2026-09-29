package com.petal.browser.passwords

import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.UUID

/**
 * PetalCredentialImporter
 * Supports importing credentials from Chrome CSV, Firefox CSV, Bitwarden JSON,
 * 1Password CSV, Dashlane CSV, and Petal Vault JSON.
 */
object PetalCredentialImporter {

    private const val TAG = "PetalCredentialImport"
    private val gson = Gson()

    fun extractDomain(urlStr: String): String {
        return try {
            val uri = Uri.parse(urlStr)
            val host = uri.host
            if (!host.isNullOrBlank()) {
                host.removePrefix("www.")
            } else {
                urlStr.trim().removePrefix("https://").removePrefix("http://").removePrefix("www.").split("/")[0]
            }
        } catch (e: Exception) {
            urlStr.trim().removePrefix("https://").removePrefix("http://").removePrefix("www.").split("/")[0]
        }
    }

    /**
     * Parses standard CSV line respecting double quotes.
     */
    fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++ // Skip escaped quote
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    result.add(sb.toString().trim())
                    sb.clear()
                }
                else -> {
                    sb.append(c)
                }
            }
            i++
        }
        result.add(sb.toString().trim())
        return result
    }

    /**
     * Google Chrome CSV format:
     * "name","url","username","password","note"
     */
    fun importFromChromeCsv(csvContent: String): List<PetalCredential> {
        val list = mutableListOf<PetalCredential>()
        val lines = csvContent.lines()
        if (lines.isEmpty()) return list

        val header = parseCsvLine(lines[0]).map { it.lowercase() }
        val nameIdx = header.indexOf("name")
        val urlIdx = header.indexOf("url")
        val userIdx = header.indexOf("username")
        val passIdx = header.indexOf("password")
        val noteIdx = header.indexOf("note")

        val actualUrlIdx = if (urlIdx != -1) urlIdx else 1
        val actualUserIdx = if (userIdx != -1) userIdx else 2
        val actualPassIdx = if (passIdx != -1) passIdx else 3

        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            try {
                val tokens = parseCsvLine(line)
                val url = tokens.getOrNull(actualUrlIdx).orEmpty()
                val username = tokens.getOrNull(actualUserIdx).orEmpty()
                val password = tokens.getOrNull(actualPassIdx).orEmpty()
                val note = if (noteIdx != -1) tokens.getOrNull(noteIdx).orEmpty() else ""

                if (url.isNotBlank() && (username.isNotBlank() || password.isNotBlank())) {
                    val domain = extractDomain(url)
                    list.add(
                        PetalCredential(
                            id = UUID.randomUUID().toString(),
                            domain = domain,
                            originUrl = url,
                            username = username,
                            password = password,
                            notes = note
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing Chrome CSV line $i: ${e.message}")
            }
        }
        return list
    }

    /**
     * Mozilla Firefox CSV format:
     * "url","username","password","httpRealm","formActionOrigin","guid","timeCreated","timeLastUsed","timePasswordChanged"
     */
    fun importFromFirefoxCsv(csvContent: String): List<PetalCredential> {
        val list = mutableListOf<PetalCredential>()
        val lines = csvContent.lines()
        if (lines.isEmpty()) return list

        val header = parseCsvLine(lines[0]).map { it.lowercase() }
        val urlIdx = header.indexOf("url").let { if (it != -1) it else 0 }
        val userIdx = header.indexOf("username").let { if (it != -1) it else 1 }
        val passIdx = header.indexOf("password").let { if (it != -1) it else 2 }

        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            try {
                val tokens = parseCsvLine(line)
                val url = tokens.getOrNull(urlIdx).orEmpty()
                val username = tokens.getOrNull(userIdx).orEmpty()
                val password = tokens.getOrNull(passIdx).orEmpty()

                if (url.isNotBlank() && (username.isNotBlank() || password.isNotBlank())) {
                    val domain = extractDomain(url)
                    list.add(
                        PetalCredential(
                            id = UUID.randomUUID().toString(),
                            domain = domain,
                            originUrl = url,
                            username = username,
                            password = password
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing Firefox CSV line $i: ${e.message}")
            }
        }
        return list
    }

    /**
     * Bitwarden JSON format:
     * { "items": [ { "name": "...", "login": { "uris": [ { "uri": "..." } ], "username": "...", "password": "..." }, "notes": "..." } ] }
     */
    fun importFromBitwardenJson(jsonContent: String): List<PetalCredential> {
        val list = mutableListOf<PetalCredential>()
        try {
            val root = gson.fromJson(jsonContent, JsonObject::class.java)
            val items = root.getAsJsonArray("items") ?: return list

            for (itemElem in items) {
                try {
                    val item = itemElem.asJsonObject
                    val login = item.getAsJsonObject("login") ?: continue
                    val username = login.get("username")?.asString.orEmpty()
                    val password = login.get("password")?.asString.orEmpty()
                    val notes = item.get("notes")?.asString.orEmpty()

                    var url = ""
                    val urisArray = login.getAsJsonArray("uris")
                    if (urisArray != null && urisArray.size() > 0) {
                        url = urisArray[0].asJsonObject.get("uri")?.asString.orEmpty()
                    }
                    if (url.isBlank()) {
                        url = item.get("name")?.asString.orEmpty()
                    }

                    if (username.isNotBlank() || password.isNotBlank()) {
                        val domain = extractDomain(url)
                        list.add(
                            PetalCredential(
                                id = UUID.randomUUID().toString(),
                                domain = domain,
                                originUrl = url,
                                username = username,
                                password = password,
                                notes = notes
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing Bitwarden item: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bitwarden JSON parse failed", e)
        }
        return list
    }

    /**
     * 1Password CSV:
     * "Title","URL","Username","Password","Notes"
     */
    fun importFromOnePasswordCsv(csvContent: String): List<PetalCredential> {
        val list = mutableListOf<PetalCredential>()
        val lines = csvContent.lines()
        if (lines.isEmpty()) return list

        val header = parseCsvLine(lines[0]).map { it.lowercase() }
        val titleIdx = header.indexOf("title")
        val urlIdx = header.indexOf("url")
        val userIdx = header.indexOf("username")
        val passIdx = header.indexOf("password")
        val notesIdx = header.indexOf("notes")

        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            try {
                val tokens = parseCsvLine(line)
                val url = if (urlIdx != -1) tokens.getOrNull(urlIdx).orEmpty() else tokens.getOrNull(titleIdx).orEmpty()
                val username = if (userIdx != -1) tokens.getOrNull(userIdx).orEmpty() else ""
                val password = if (passIdx != -1) tokens.getOrNull(passIdx).orEmpty() else ""
                val notes = if (notesIdx != -1) tokens.getOrNull(notesIdx).orEmpty() else ""

                if (username.isNotBlank() || password.isNotBlank()) {
                    val domain = extractDomain(url)
                    list.add(
                        PetalCredential(
                            id = UUID.randomUUID().toString(),
                            domain = domain,
                            originUrl = url,
                            username = username,
                            password = password,
                            notes = notes
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing 1Password CSV line $i: ${e.message}")
            }
        }
        return list
    }

    /**
     * Dashlane CSV:
     * "username","password","title","url"
     */
    fun importFromDashlaneCsv(csvContent: String): List<PetalCredential> {
        val list = mutableListOf<PetalCredential>()
        val lines = csvContent.lines()
        if (lines.isEmpty()) return list

        val header = parseCsvLine(lines[0]).map { it.lowercase() }
        val userIdx = header.indexOf("username").let { if (it != -1) it else 0 }
        val passIdx = header.indexOf("password").let { if (it != -1) it else 1 }
        val urlIdx = header.indexOf("url").let { if (it != -1) it else 3 }

        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            try {
                val tokens = parseCsvLine(line)
                val username = tokens.getOrNull(userIdx).orEmpty()
                val password = tokens.getOrNull(passIdx).orEmpty()
                val url = tokens.getOrNull(urlIdx).orEmpty()

                if (username.isNotBlank() || password.isNotBlank()) {
                    val domain = extractDomain(url)
                    list.add(
                        PetalCredential(
                            id = UUID.randomUUID().toString(),
                            domain = domain,
                            originUrl = url,
                            username = username,
                            password = password
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing Dashlane CSV line $i: ${e.message}")
            }
        }
        return list
    }

    /**
     * Detects format automatically based on header contents or file extension.
     */
    fun detectAndImport(filename: String, content: String): Pair<String, List<PetalCredential>> {
        val lowerName = filename.lowercase()
        val lowerContent = content.take(1000).lowercase()

        return when {
            PetalCredentialVault.isEncryptedBackup(content) || lowerName.endsWith(".petal") -> {
                val imported = PetalCredentialVault.importEncrypted(content)
                "Encrypted Petal Backup ($imported imported)" to emptyList()
            }
            lowerName.endsWith(".json") || lowerContent.startsWith("{") || lowerContent.startsWith("[") -> {
                if (lowerContent.contains("\"items\"") && lowerContent.contains("\"login\"")) {
                    "Bitwarden JSON" to importFromBitwardenJson(content)
                } else {
                    // Try Petal Vault backup
                    val list = mutableListOf<PetalCredential>()
                    try {
                        val imported = PetalCredentialVault.importFromJson(content)
                        "Petal Vault Backup JSON ($imported imported)" to emptyList()
                    } catch (e: Exception) {
                        "JSON" to emptyList()
                    }
                }
            }
            lowerContent.contains("httprealm") || lowerContent.contains("formactionorigin") -> {
                "Mozilla Firefox CSV" to importFromFirefoxCsv(content)
            }
            lowerContent.contains("title") && lowerContent.contains("password") && lowerContent.contains("notes") -> {
                "1Password CSV" to importFromOnePasswordCsv(content)
            }
            lowerContent.contains("name,url,username,password") || (lowerContent.contains("url") && lowerContent.contains("username") && lowerContent.contains("password")) -> {
                "Google Chrome CSV" to importFromChromeCsv(content)
            }
            else -> {
                // Fallback to Chrome CSV parser for any standard CSV
                "Standard CSV" to importFromChromeCsv(content)
            }
        }
    }
}
