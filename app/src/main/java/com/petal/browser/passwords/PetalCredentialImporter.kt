package com.petal.browser.passwords

import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.util.UUID

/**
 * Result data class for detected imports.
 */
data class DetectedImport(
    val formatName: String,
    val credentials: List<PetalCredential> = emptyList(),
    val directImportCount: Int = 0,
    val errorMessage: String? = null
)

/**
 * PetalCredentialImporter
 * Supports importing credentials from Chrome CSV, Firefox CSV, Bitwarden JSON,
 * 1Password CSV, Dashlane CSV, and Petal Vault JSON.
 *
 * Implements RFC-4180 compliant CSV parsing with support for multi-line notes,
 * embedded quotes, CRLF, and BOM stripping, while strictly preserving password spaces.
 */
object PetalCredentialImporter {

    private const val TAG = "PetalCredentialImport"
    private val gson = Gson()

    /**
     * Parses RFC-4180 CSV records (handling embedded newlines, quotes, CRLF) and strips UTF-8 BOM.
     * Note: Does NOT trim field values so passwords with spaces remain intact.
     */
    fun parseCsvRecords(csvContent: String): List<List<String>> {
        val clean = csvContent.removePrefix("\uFEFF")
        val records = mutableListOf<List<String>>()
        val currentRecord = mutableListOf<String>()
        val currentField = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < clean.length) {
            val c = clean[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < clean.length && clean[i + 1] == '"') {
                        currentField.append('"')
                        i++ // Skip escaped quote
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    currentRecord.add(currentField.toString())
                    currentField.clear()
                }
                (c == '\r' || c == '\n') && !inQuotes -> {
                    if (c == '\r' && i + 1 < clean.length && clean[i + 1] == '\n') {
                        i++ // Skip \r\n
                    }
                    currentRecord.add(currentField.toString())
                    currentField.clear()
                    if (currentRecord.any { it.isNotBlank() }) {
                        records.add(ArrayList(currentRecord))
                    }
                    currentRecord.clear()
                }
                else -> {
                    currentField.append(c)
                }
            }
            i++
        }
        if (currentField.isNotEmpty() || currentRecord.isNotEmpty()) {
            currentRecord.add(currentField.toString())
            if (currentRecord.any { it.isNotBlank() }) {
                records.add(currentRecord)
            }
        }
        return records
    }

    /**
     * Maps URLs (including android:// and app://) into a standardized domain or app URI.
     * Returns null if no usable domain could be determined (avoiding titles as domains).
     */
    fun extractDomain(urlStr: String): String? {
        val trimmed = urlStr.trim()
        if (trimmed.isBlank()) return null

        // android://hash@com.example.app/ or android://com.example.app
        if (trimmed.startsWith("android://")) {
            val path = trimmed.removePrefix("android://").substringBefore('/')
            val pkg = if (path.contains('@')) path.substringAfter('@').trim() else path.trim()
            return if (pkg.isNotBlank()) "app://$pkg" else null
        }
        if (trimmed.startsWith("app://")) {
            val pkg = trimmed.removePrefix("app://").trim().substringBefore('/')
            return if (pkg.isNotBlank()) "app://$pkg" else null
        }

        // Android package name heuristic (e.g. "org.mozilla.firefox" with no protocol or slash)
        if (trimmed.matches(Regex("""^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+$""")) && !trimmed.contains('/')) {
            val last = trimmed.substringAfterLast('.')
            val commonTlds = setOf("com", "org", "net", "edu", "gov", "io", "co", "app", "dev", "ai", "me", "info", "biz")
            if (!commonTlds.contains(last)) {
                return "app://$trimmed"
            }
        }

        return try {
            val uri = Uri.parse(trimmed)
            val host = uri.host
            if (!host.isNullOrBlank()) {
                host.removePrefix("www.").lowercase()
            } else {
                val candidate = trimmed.removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/').substringBefore(':')
                if (candidate.contains('.')) candidate.lowercase() else null
            }
        } catch (_: Exception) {
            val candidate = trimmed.removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/').substringBefore(':')
            if (candidate.contains('.')) candidate.lowercase() else null
        }
    }

    private fun JsonObject.getNullableString(key: String): String? {
        val elem = get(key)
        return if (elem != null && !elem.isJsonNull) {
            try { elem.asString } catch (_: Exception) { null }
        } else null
    }

    /**
     * Google Chrome CSV format:
     * "name","url","username","password","note"
     */
    fun importFromChromeCsv(csvContent: String): List<PetalCredential> {
        val list = mutableListOf<PetalCredential>()
        val records = parseCsvRecords(csvContent)
        if (records.isEmpty()) return list

        val header = records[0].map { it.trim().lowercase() }
        val nameIdx = header.indexOf("name")
        val urlIdx = header.indexOf("url")
        val userIdx = header.indexOf("username")
        val passIdx = header.indexOf("password")
        val noteIdx = header.indexOf("note")

        val actualUrlIdx = if (urlIdx != -1) urlIdx else if (nameIdx != -1) nameIdx else 1
        val actualUserIdx = if (userIdx != -1) userIdx else 2
        val actualPassIdx = if (passIdx != -1) passIdx else 3

        for (i in 1 until records.size) {
            val row = records[i]
            try {
                val rawUrl = row.getOrNull(actualUrlIdx).orEmpty().trim()
                val username = row.getOrNull(actualUserIdx).orEmpty().trim()
                // NEVER trim password
                val password = row.getOrNull(actualPassIdx).orEmpty()
                val note = if (noteIdx != -1) row.getOrNull(noteIdx).orEmpty() else ""

                val domain = extractDomain(rawUrl)
                if (!domain.isNullOrBlank() && (username.isNotBlank() || password.isNotBlank())) {
                    list.add(
                        PetalCredential(
                            id = UUID.randomUUID().toString(),
                            domain = domain,
                            originUrl = if (domain.startsWith("app://")) domain else (if (rawUrl.startsWith("http")) rawUrl else "https://$domain"),
                            username = username,
                            password = password,
                            notes = note
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing Chrome CSV record $i: ${e.message}")
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
        val records = parseCsvRecords(csvContent)
        if (records.isEmpty()) return list

        val header = records[0].map { it.trim().lowercase() }
        val urlIdx = header.indexOf("url").let { if (it != -1) it else 0 }
        val userIdx = header.indexOf("username").let { if (it != -1) it else 1 }
        val passIdx = header.indexOf("password").let { if (it != -1) it else 2 }
        val formActionIdx = header.indexOf("formactionorigin")

        for (i in 1 until records.size) {
            val row = records[i]
            try {
                val rawUrl = row.getOrNull(urlIdx).orEmpty().trim()
                val username = row.getOrNull(userIdx).orEmpty().trim()
                val password = row.getOrNull(passIdx).orEmpty()
                val formAction = if (formActionIdx != -1) row.getOrNull(formActionIdx)?.trim() else null

                val domain = extractDomain(rawUrl)
                if (!domain.isNullOrBlank() && (username.isNotBlank() || password.isNotBlank())) {
                    list.add(
                        PetalCredential(
                            id = UUID.randomUUID().toString(),
                            domain = domain,
                            originUrl = if (domain.startsWith("app://")) domain else (if (rawUrl.startsWith("http")) rawUrl else "https://$domain"),
                            username = username,
                            password = password,
                            formActionOrigin = formAction
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing Firefox CSV record $i: ${e.message}")
            }
        }
        return list
    }

    /**
     * Bitwarden JSON format:
     * Only import login items (type 1), parse all URIs safely.
     */
    fun importFromBitwardenJson(jsonContent: String): List<PetalCredential> {
        val list = mutableListOf<PetalCredential>()
        try {
            val root = gson.fromJson(jsonContent, JsonObject::class.java) ?: return list
            val items = root.getAsJsonArray("items") ?: return list

            for (itemElem in items) {
                try {
                    val item = itemElem.asJsonObject
                    // Bitwarden type 1 = Login
                    val type = item.get("type")?.let { if (!it.isJsonNull) it.asInt else 1 } ?: 1
                    if (type != 1 && !item.has("login")) continue

                    val login = item.getAsJsonObject("login") ?: continue
                    val username = login.getNullableString("username").orEmpty().trim()
                    val password = login.getNullableString("password").orEmpty()
                    val notes = item.getNullableString("notes").orEmpty()

                    // Collect all valid URLs from the URIs array
                    val candidateUrls = mutableListOf<String>()
                    val urisArray = login.getAsJsonArray("uris")
                    if (urisArray != null) {
                        for (uElem in urisArray) {
                            val uObj = uElem.asJsonObject
                            val uri = uObj.getNullableString("uri")
                            if (!uri.isNullOrBlank()) candidateUrls.add(uri)
                        }
                    }

                    if (candidateUrls.isEmpty()) {
                        val name = item.getNullableString("name").orEmpty()
                        if (name.contains('.')) candidateUrls.add(name)
                    }

                    if (candidateUrls.isEmpty() || (username.isBlank() && password.isBlank())) continue

                    val seenDomains = mutableSetOf<String>()
                    for (rawUrl in candidateUrls) {
                        val domain = extractDomain(rawUrl) ?: continue
                        if (!seenDomains.add(domain)) continue

                        list.add(
                            PetalCredential(
                                id = UUID.randomUUID().toString(),
                                domain = domain,
                                originUrl = if (domain.startsWith("app://")) domain else (if (rawUrl.startsWith("http")) rawUrl else "https://$domain"),
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
        val records = parseCsvRecords(csvContent)
        if (records.isEmpty()) return list

        val header = records[0].map { it.trim().lowercase() }
        val titleIdx = header.indexOf("title")
        val urlIdx = header.indexOf("url")
        val userIdx = header.indexOf("username")
        val passIdx = header.indexOf("password")
        val notesIdx = header.indexOf("notes")

        val actualUrlIdx = if (urlIdx != -1) urlIdx else titleIdx
        val actualUserIdx = if (userIdx != -1) userIdx else 2
        val actualPassIdx = if (passIdx != -1) passIdx else 3

        for (i in 1 until records.size) {
            val row = records[i]
            try {
                val rawUrl = row.getOrNull(actualUrlIdx).orEmpty().trim()
                val username = row.getOrNull(actualUserIdx).orEmpty().trim()
                val password = row.getOrNull(actualPassIdx).orEmpty()
                val notes = if (notesIdx != -1) row.getOrNull(notesIdx).orEmpty() else ""

                val domain = extractDomain(rawUrl)
                if (!domain.isNullOrBlank() && (username.isNotBlank() || password.isNotBlank())) {
                    list.add(
                        PetalCredential(
                            id = UUID.randomUUID().toString(),
                            domain = domain,
                            originUrl = if (domain.startsWith("app://")) domain else (if (rawUrl.startsWith("http")) rawUrl else "https://$domain"),
                            username = username,
                            password = password,
                            notes = notes
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing 1Password CSV record $i: ${e.message}")
            }
        }
        return list
    }

    /**
     * Dashlane CSV:
     * "username","password","title","url" / "secondary_url"
     */
    fun importFromDashlaneCsv(csvContent: String): List<PetalCredential> {
        val list = mutableListOf<PetalCredential>()
        val records = parseCsvRecords(csvContent)
        if (records.isEmpty()) return list

        val header = records[0].map { it.trim().lowercase() }
        val userIdx = header.indexOf("username").let { if (it != -1) it else 0 }
        val passIdx = header.indexOf("password").let { if (it != -1) it else 1 }
        val urlIdx = header.indexOf("url").let { if (it != -1) it else header.indexOf("secondary_url").let { s -> if (s != -1) s else 3 } }
        val noteIdx = header.indexOf("note").let { if (it != -1) it else header.indexOf("notes") }

        for (i in 1 until records.size) {
            val row = records[i]
            try {
                val username = row.getOrNull(userIdx).orEmpty().trim()
                val password = row.getOrNull(passIdx).orEmpty()
                val rawUrl = row.getOrNull(urlIdx).orEmpty().trim()
                val note = if (noteIdx != -1) row.getOrNull(noteIdx).orEmpty() else ""

                val domain = extractDomain(rawUrl)
                if (!domain.isNullOrBlank() && (username.isNotBlank() || password.isNotBlank())) {
                    list.add(
                        PetalCredential(
                            id = UUID.randomUUID().toString(),
                            domain = domain,
                            originUrl = if (domain.startsWith("app://")) domain else (if (rawUrl.startsWith("http")) rawUrl else "https://$domain"),
                            username = username,
                            password = password,
                            notes = note
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing Dashlane CSV record $i: ${e.message}")
            }
        }
        return list
    }

    /**
     * Detects format automatically based on header contents, structure or filename extension.
     */
    fun detectAndImport(filename: String, content: String): DetectedImport {
        val cleanContent = content.removePrefix("\uFEFF").trim()
        val lowerName = filename.lowercase()
        val lowerContent = cleanContent.take(2000).lowercase()

        return when {
            PetalCredentialVault.isEncryptedBackup(cleanContent) || lowerName.endsWith(".petal") -> {
                try {
                    val imported = PetalCredentialVault.importEncryptedOrThrow(cleanContent)
                    DetectedImport(
                        formatName = "Encrypted Petal Vault",
                        directImportCount = imported
                    )
                } catch (e: Exception) {
                    DetectedImport(
                        formatName = "Encrypted Petal Vault",
                        errorMessage = e.message ?: "Failed to decrypt backup"
                    )
                }
            }
            lowerName.endsWith(".json") || lowerContent.startsWith("{") || lowerContent.startsWith("[") -> {
                if (lowerContent.contains("\"items\"") && lowerContent.contains("\"login\"")) {
                    val creds = importFromBitwardenJson(cleanContent)
                    DetectedImport(formatName = "Bitwarden JSON", credentials = creds)
                } else {
                    try {
                        val imported = PetalCredentialVault.importFromJson(cleanContent)
                        DetectedImport(formatName = "Petal Vault JSON", directImportCount = imported)
                    } catch (e: Exception) {
                        DetectedImport(formatName = "JSON", errorMessage = e.message ?: "Invalid JSON backup")
                    }
                }
            }
            lowerContent.contains("httprealm") || lowerContent.contains("formactionorigin") -> {
                DetectedImport(formatName = "Mozilla Firefox CSV", credentials = importFromFirefoxCsv(cleanContent))
            }
            (lowerContent.contains("username") && lowerContent.contains("password") && lowerContent.contains("url") && lowerContent.contains("title")) -> {
                // Distinguish Dashlane vs 1Password
                if (lowerContent.contains("secondary_url") || lowerContent.startsWith("\"username\",\"password\"") || lowerContent.startsWith("username,password")) {
                    DetectedImport(formatName = "Dashlane CSV", credentials = importFromDashlaneCsv(cleanContent))
                } else {
                    DetectedImport(formatName = "1Password CSV", credentials = importFromOnePasswordCsv(cleanContent))
                }
            }
            lowerContent.contains("notes") && lowerContent.contains("title") && lowerContent.contains("password") -> {
                DetectedImport(formatName = "1Password CSV", credentials = importFromOnePasswordCsv(cleanContent))
            }
            lowerContent.contains("name,url,username,password") || (lowerContent.contains("url") && lowerContent.contains("username") && lowerContent.contains("password")) -> {
                DetectedImport(formatName = "Google Chrome CSV", credentials = importFromChromeCsv(cleanContent))
            }
            else -> {
                DetectedImport(formatName = "Standard CSV", credentials = importFromChromeCsv(cleanContent))
            }
        }
    }
}
