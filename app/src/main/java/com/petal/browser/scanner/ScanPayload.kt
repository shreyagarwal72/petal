package com.petal.browser.scanner

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Sealed representation of scanned barcode / QR code payloads.
 * Pure-Kotlin, lightweight, and completely decoupled from Android framework APIs.
 */
sealed class ScanPayload {

    data class Url(
        val url: String,
        val displayUrl: String,
        val scheme: String,
        val host: String
    ) : ScanPayload()

    data class Email(
        val address: String,
        val subject: String? = null,
        val body: String? = null
    ) : ScanPayload()

    data class Phone(
        val number: String
    ) : ScanPayload()

    data class Sms(
        val number: String,
        val message: String? = null
    ) : ScanPayload()

    data class Geo(
        val latitude: Double,
        val longitude: Double,
        val query: String? = null
    ) : ScanPayload()

    data class Wifi(
        val ssid: String,
        val password: String? = null,
        val authType: String = "WPA",
        val hidden: Boolean = false
    ) : ScanPayload()

    data class Contact(
        val name: String,
        val phone: String? = null,
        val email: String? = null,
        val org: String? = null,
        val note: String? = null,
        val url: String? = null
    ) : ScanPayload()

    data class Calendar(
        val title: String,
        val startTime: Long? = null,
        val endTime: Long? = null,
        val location: String? = null,
        val description: String? = null
    ) : ScanPayload()

    data class Otp(
        val type: String,
        val secret: String,
        val issuer: String? = null,
        val account: String? = null,
        val digits: Int = 6,
        val period: Int = 30,
        val algorithm: String = "SHA1"
    ) : ScanPayload()

    data class Product(
        val code: String,
        val format: String
    ) : ScanPayload()

    data class Text(
        val content: String
    ) : ScanPayload()
}

/**
 * Pure Kotlin parser that inspects raw barcode content and converts it into a typed [ScanPayload].
 */
object ScanParser {

    private val BARE_EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val BARE_PHONE_REGEX = Regex("^\\+?[0-9\\s\\-().]{7,25}$")
    private val BARE_DOMAIN_REGEX = Regex("^(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}(?::\\d+)?(?:[/?#].*)?$")
    private val PRODUCT_FORMATS = setOf("EAN_8", "EAN_13", "UPC_A", "UPC_E")

    fun parse(rawText: String, formatName: String = "QR_CODE"): ScanPayload {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            return ScanPayload.Text("")
        }

        val upperFormat = formatName.uppercase(Locale.ROOT)
        if (upperFormat in PRODUCT_FORMATS) {
            return ScanPayload.Product(code = trimmed, format = upperFormat)
        }

        val lower = trimmed.lowercase(Locale.ROOT)

        // 1. OTP Auth: otpauth://totp/... or otpauth://hotp/...
        if (lower.startsWith("otpauth://")) {
            parseOtp(trimmed)?.let { return it }
        }

        // 2. Wi-Fi: WIFI:T:WPA;S:MySSID;P:pass;H:false;;
        if (trimmed.startsWith("WIFI:", ignoreCase = true)) {
            parseWifi(trimmed)?.let { return it }
        }

        // 3. vCard: BEGIN:VCARD ... END:VCARD
        if (trimmed.startsWith("BEGIN:VCARD", ignoreCase = true)) {
            parseVCard(trimmed)?.let { return it }
        }

        // 4. MeCard: MECARD:...
        if (trimmed.startsWith("MECARD:", ignoreCase = true)) {
            parseMeCard(trimmed)?.let { return it }
        }

        // 5. Calendar VEVENT: BEGIN:VEVENT or BEGIN:VCALENDAR
        if (trimmed.startsWith("BEGIN:VEVENT", ignoreCase = true) ||
            trimmed.startsWith("BEGIN:VCALENDAR", ignoreCase = true)
        ) {
            parseCalendar(trimmed)?.let { return it }
        }

        // 6. Email schemes: mailto:, MATMSG:, SMTP:
        if (lower.startsWith("mailto:")) {
            parseMailto(trimmed)?.let { return it }
        }
        if (trimmed.startsWith("MATMSG:", ignoreCase = true)) {
            parseMatMsg(trimmed)?.let { return it }
        }
        if (trimmed.startsWith("SMTP:", ignoreCase = true)) {
            parseSmtp(trimmed)?.let { return it }
        }

        // 7. Phone scheme: tel:
        if (lower.startsWith("tel:")) {
            val phone = trimmed.substring(4).trim()
            if (phone.isNotEmpty()) return ScanPayload.Phone(phone)
        }

        // 8. SMS schemes: smsto:, sms:, mmsto:
        if (lower.startsWith("smsto:") || lower.startsWith("sms:") || lower.startsWith("mmsto:")) {
            parseSms(trimmed)?.let { return it }
        }

        // 9. Geo scheme: geo:
        if (lower.startsWith("geo:")) {
            parseGeo(trimmed)?.let { return it }
        }

        // 10. Web URLs: http://, https://, ftp://
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("ftp://")) {
            parseStandardUrl(trimmed)?.let { return it }
        }

        // 11. Bare email
        if (BARE_EMAIL_REGEX.matches(trimmed)) {
            return ScanPayload.Email(address = trimmed)
        }

        // 12. Bare domain (e.g., example.com, sub.domain.org/path?q=1)
        if (BARE_DOMAIN_REGEX.matches(trimmed) && !trimmed.contains(" ") && !trimmed.contains("\n")) {
            val fullUrl = "https://$trimmed"
            parseStandardUrl(fullUrl, originalDisplay = trimmed)?.let { return it }
        }

        // 13. Bare phone number (must contain at least 7 digits)
        val digitCount = trimmed.count { it.isDigit() }
        if (digitCount in 7..20 && BARE_PHONE_REGEX.matches(trimmed)) {
            return ScanPayload.Phone(number = trimmed)
        }

        // 14. 1D Product code fallback if purely numeric
        if (digitCount == trimmed.length && (trimmed.length in 8..14) && upperFormat != "QR_CODE") {
            return ScanPayload.Product(code = trimmed, format = upperFormat)
        }

        // Fallback: Text
        return ScanPayload.Text(content = trimmed)
    }

    private fun parseOtp(uriString: String): ScanPayload.Otp? {
        // e.g. otpauth://totp/Example:alice@google.com?secret=JBSWY3DPEHPK3PXP&issuer=Example&digits=6&period=30
        return try {
            val withoutPrefix = uriString.substring(10)
            val slashIdx = withoutPrefix.indexOf('/')
            val type = if (slashIdx != -1) withoutPrefix.substring(0, slashIdx).lowercase(Locale.ROOT) else "totp"
            val rest = if (slashIdx != -1) withoutPrefix.substring(slashIdx + 1) else withoutPrefix

            val qIdx = rest.indexOf('?')
            val rawLabel = if (qIdx != -1) rest.substring(0, qIdx) else rest
            val query = if (qIdx != -1) rest.substring(qIdx + 1) else ""

            val params = parseQueryParams(query)
            val secret = params["secret"] ?: return null
            var issuer = params["issuer"]
            var account = urlDecode(rawLabel)

            if (account.contains(":")) {
                val parts = account.split(":", limit = 2)
                if (issuer == null || issuer.isBlank()) {
                    issuer = parts[0].trim()
                }
                account = parts[1].trim()
            }

            val digits = params["digits"]?.toIntOrNull() ?: 6
            val period = params["period"]?.toIntOrNull() ?: 30
            val algorithm = params["algorithm"]?.uppercase(Locale.ROOT) ?: "SHA1"

            ScanPayload.Otp(
                type = type,
                secret = secret,
                issuer = issuer,
                account = account.ifEmpty { null },
                digits = digits,
                period = period,
                algorithm = algorithm
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseWifi(text: String): ScanPayload.Wifi? {
        // WIFI:T:WPA;S:MySSID;P:mypassword;H:false;;
        return try {
            val content = text.substring(5)
            val tokens = splitEscapedSemicolons(content)
            var ssid = ""
            var password: String? = null
            var authType = "nopass"
            var hidden = false

            for (token in tokens) {
                val colonIdx = token.indexOf(':')
                if (colonIdx == -1) continue
                val key = token.substring(0, colonIdx).uppercase(Locale.ROOT)
                val value = unescapeWifi(token.substring(colonIdx + 1))
                when (key) {
                    "S" -> ssid = value
                    "P" -> password = value
                    "T" -> authType = value
                    "H" -> hidden = value.equals("true", ignoreCase = true)
                }
            }

            if (ssid.isEmpty()) return null
            ScanPayload.Wifi(
                ssid = ssid,
                password = password,
                authType = authType,
                hidden = hidden
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun splitEscapedSemicolons(s: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var escaped = false
        for (c in s) {
            if (escaped) {
                sb.append(c)
                escaped = false
            } else if (c == '\\') {
                escaped = true
                sb.append(c)
            } else if (c == ';') {
                if (sb.isNotEmpty()) {
                    result.add(sb.toString())
                    sb.setLength(0)
                }
            } else {
                sb.append(c)
            }
        }
        if (sb.isNotEmpty()) {
            result.add(sb.toString())
        }
        return result
    }

    private fun unescapeWifi(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                val next = s[i + 1]
                if (next == '\\' || next == ';' || next == ':' || next == ',') {
                    sb.append(next)
                    i += 2
                    continue
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun parseVCard(text: String): ScanPayload.Contact? {
        return try {
            var fullName: String? = null
            var lastName = ""
            var firstName = ""
            var phone: String? = null
            var email: String? = null
            var org: String? = null
            var note: String? = null
            var url: String? = null

            val lines = text.lines()
            for (line in lines) {
                val colonIdx = line.indexOf(':')
                if (colonIdx == -1) continue
                val key = line.substring(0, colonIdx).uppercase(Locale.ROOT)
                val value = line.substring(colonIdx + 1).trim()

                when {
                    key == "FN" -> fullName = value
                    key == "N" -> {
                        val parts = value.split(";")
                        if (parts.isNotEmpty()) lastName = parts[0].trim()
                        if (parts.size > 1) firstName = parts[1].trim()
                    }
                    key.startsWith("TEL") && phone == null -> phone = value
                    key.startsWith("EMAIL") && email == null -> email = value
                    key.startsWith("ORG") && org == null -> org = value
                    key.startsWith("NOTE") && note == null -> note = value
                    key.startsWith("URL") && url == null -> url = value
                }
            }

            val resolvedName = fullName?.ifEmpty { null }
                ?: listOf(firstName, lastName).filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { null }
                ?: "Contact"

            ScanPayload.Contact(
                name = resolvedName,
                phone = phone,
                email = email,
                org = org,
                note = note,
                url = url
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseMeCard(text: String): ScanPayload.Contact? {
        return try {
            val content = text.substring(7)
            val tokens = splitEscapedSemicolons(content)
            var name: String? = null
            var phone: String? = null
            var email: String? = null
            var org: String? = null
            var note: String? = null
            var url: String? = null

            for (token in tokens) {
                val colonIdx = token.indexOf(':')
                if (colonIdx == -1) continue
                val key = token.substring(0, colonIdx).uppercase(Locale.ROOT)
                val value = unescapeWifi(token.substring(colonIdx + 1)).trim()
                when (key) {
                    "N" -> name = value.replace(",", " ")
                    "TEL" -> if (phone == null) phone = value
                    "EMAIL" -> if (email == null) email = value
                    "ORG" -> if (org == null) org = value
                    "NOTE" -> if (note == null) note = value
                    "URL" -> if (url == null) url = value
                }
            }

            ScanPayload.Contact(
                name = name ?: "Contact",
                phone = phone,
                email = email,
                org = org,
                note = note,
                url = url
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseCalendar(text: String): ScanPayload.Calendar? {
        return try {
            var summary: String? = null
            var dtStart: Long? = null
            var dtEnd: Long? = null
            var location: String? = null
            var description: String? = null

            val lines = text.lines()
            for (line in lines) {
                val colonIdx = line.indexOf(':')
                if (colonIdx == -1) continue
                val key = line.substring(0, colonIdx).uppercase(Locale.ROOT)
                val value = line.substring(colonIdx + 1).trim()

                when {
                    key.startsWith("SUMMARY") -> summary = value
                    key.startsWith("DTSTART") -> dtStart = parseDateTime(value)
                    key.startsWith("DTEND") -> dtEnd = parseDateTime(value)
                    key.startsWith("LOCATION") -> location = value
                    key.startsWith("DESCRIPTION") -> description = value
                }
            }

            if (summary == null && dtStart == null) return null

            ScanPayload.Calendar(
                title = summary ?: "Event",
                startTime = dtStart,
                endTime = dtEnd,
                location = location,
                description = description
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseDateTime(raw: String): Long? {
        val clean = raw.trim()
        val formats = listOf(
            "yyyyMMdd'T'HHmmss'Z'",
            "yyyyMMdd'T'HHmmss",
            "yyyyMMdd"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = if (pattern.endsWith("'Z'")) TimeZone.getTimeZone("UTC") else TimeZone.getDefault()
                }
                val date = sdf.parse(clean)
                if (date != null) return date.time
            } catch (_: Exception) {}
        }
        return null
    }

    private fun parseMailto(text: String): ScanPayload.Email? {
        return try {
            val withoutScheme = text.substring(7)
            val qIdx = withoutScheme.indexOf('?')
            val address = if (qIdx != -1) withoutScheme.substring(0, qIdx) else withoutScheme
            val query = if (qIdx != -1) withoutScheme.substring(qIdx + 1) else ""
            val params = parseQueryParams(query)

            ScanPayload.Email(
                address = address.trim(),
                subject = params["subject"],
                body = params["body"]
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseMatMsg(text: String): ScanPayload.Email? {
        // MATMSG:TO:alice@example.com;SUB:Hello;BODY:World;;
        return try {
            val content = text.substring(7)
            val tokens = splitEscapedSemicolons(content)
            var to = ""
            var sub: String? = null
            var body: String? = null

            for (token in tokens) {
                val colonIdx = token.indexOf(':')
                if (colonIdx == -1) continue
                val key = token.substring(0, colonIdx).uppercase(Locale.ROOT)
                val value = token.substring(colonIdx + 1)
                when (key) {
                    "TO" -> to = value
                    "SUB" -> sub = value
                    "BODY" -> body = value
                }
            }

            if (to.isEmpty()) return null
            ScanPayload.Email(address = to, subject = sub, body = body)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSmtp(text: String): ScanPayload.Email? {
        // SMTP:alice@example.com:subject:body
        return try {
            val parts = text.substring(5).split(":", limit = 3)
            val address = parts[0].trim()
            val subject = parts.getOrNull(1)?.ifEmpty { null }
            val body = parts.getOrNull(2)?.ifEmpty { null }
            if (address.isEmpty()) return null
            ScanPayload.Email(address = address, subject = subject, body = body)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSms(text: String): ScanPayload.Sms? {
        return try {
            val colonIdx = text.indexOf(':')
            val rest = text.substring(colonIdx + 1)
            val qIdx = rest.indexOf('?')
            val secondColonIdx = rest.indexOf(':')

            var number: String
            var message: String? = null

            if (qIdx != -1) {
                number = rest.substring(0, qIdx)
                val query = rest.substring(qIdx + 1)
                val params = parseQueryParams(query)
                message = params["body"]
            } else if (secondColonIdx != -1) {
                number = rest.substring(0, secondColonIdx)
                message = rest.substring(secondColonIdx + 1)
            } else {
                number = rest
            }

            number = number.trim()
            if (number.isEmpty()) return null
            ScanPayload.Sms(number = number, message = message?.let { urlDecode(it) })
        } catch (_: Exception) {
            null
        }
    }

    private fun parseGeo(text: String): ScanPayload.Geo? {
        return try {
            val rest = text.substring(4) // after geo:
            val qIdx = rest.indexOf('?')
            val coordsPart = if (qIdx != -1) rest.substring(0, qIdx) else rest
            val queryPart = if (qIdx != -1) rest.substring(qIdx + 1) else null

            val coords = coordsPart.split(",")
            val lat = coords[0].toDoubleOrNull() ?: 0.0
            val lng = coords.getOrNull(1)?.toDoubleOrNull() ?: 0.0

            var query: String? = null
            if (!queryPart.isNullOrBlank()) {
                val params = parseQueryParams(queryPart)
                query = params["q"] ?: queryPart
            }

            ScanPayload.Geo(latitude = lat, longitude = lng, query = query)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseStandardUrl(rawUrl: String, originalDisplay: String? = null): ScanPayload.Url? {
        return try {
            val schemeEnd = rawUrl.indexOf("://")
            if (schemeEnd == -1) return null

            val scheme = rawUrl.substring(0, schemeEnd).lowercase(Locale.ROOT)
            val afterScheme = rawUrl.substring(schemeEnd + 3)

            val hostEndIdx = listOf(
                afterScheme.indexOf('/'),
                afterScheme.indexOf('?'),
                afterScheme.indexOf('#')
            ).filter { it != -1 }.minOrNull() ?: afterScheme.length

            var hostWithPort = afterScheme.substring(0, hostEndIdx)
            val atIdx = hostWithPort.indexOf('@')
            if (atIdx != -1) {
                hostWithPort = hostWithPort.substring(atIdx + 1)
            }

            val colonIdx = hostWithPort.indexOf(':')
            val host = (if (colonIdx != -1) hostWithPort.substring(0, colonIdx) else hostWithPort).lowercase(Locale.ROOT)

            if (host.isEmpty()) return null

            ScanPayload.Url(
                url = rawUrl,
                displayUrl = originalDisplay ?: rawUrl,
                scheme = scheme,
                host = host
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (query.isEmpty()) return map
        val pairs = query.split('&')
        for (pair in pairs) {
            val eqIdx = pair.indexOf('=')
            if (eqIdx != -1) {
                val key = urlDecode(pair.substring(0, eqIdx)).lowercase(Locale.ROOT)
                val value = urlDecode(pair.substring(eqIdx + 1))
                map[key] = value
            } else if (pair.isNotEmpty()) {
                val key = urlDecode(pair).lowercase(Locale.ROOT)
                map[key] = ""
            }
        }
        return map
    }

    private fun urlDecode(s: String): String {
        return try {
            java.net.URLDecoder.decode(s, "UTF-8")
        } catch (_: Exception) {
            s
        }
    }
}
