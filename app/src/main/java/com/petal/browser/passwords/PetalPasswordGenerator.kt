package com.petal.browser.passwords

import java.security.SecureRandom

/**
 * PetalPasswordGenerator
 *
 * Configurable, high-entropy password generator supporting Brave-like presets:
 * - MEMORABLE (words / hyphen-delimited)
 * - BALANCED (16 chars, mixed case, numbers, common symbols)
 * - HIGH_ENTROPY (24 chars, all character classes)
 * - PIN_ONLY (Numeric digits)
 * - CUSTOM (User-defined length and character sets)
 */
object PetalPasswordGenerator {

    enum class Preset(val displayName: String, val description: String) {
        BALANCED("Strong (16 chars)", "Recommended for most logins"),
        HIGH_ENTROPY("Maximum Security (24 chars)", "Complex alphanumeric & symbols"),
        PIN("Numeric PIN (6 digits)", "Fast 6-digit secure PIN"),
        CUSTOM("Custom Preset", "Adjust length and character sets")
    }

    private const val UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ" // Omitted easily confused: I, O
    private const val LOWERCASE = "abcdefghijkmnopqrstuvwxyz" // Omitted easily confused: l
    private const val DIGITS = "23456789" // Omitted easily confused: 0, 1
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{}<>?"

    private val secureRandom = SecureRandom()

    fun generate(
        length: Int = 16,
        includeUppercase: Boolean = true,
        includeLowercase: Boolean = true,
        includeDigits: Boolean = true,
        includeSymbols: Boolean = true
    ): String {
        val pool = StringBuilder()
        val mandatory = mutableListOf<Char>()

        if (includeUppercase) {
            pool.append(UPPERCASE)
            mandatory.add(UPPERCASE[secureRandom.nextInt(UPPERCASE.length)])
        }
        if (includeLowercase) {
            pool.append(LOWERCASE)
            mandatory.add(LOWERCASE[secureRandom.nextInt(LOWERCASE.length)])
        }
        if (includeDigits) {
            pool.append(DIGITS)
            mandatory.add(DIGITS[secureRandom.nextInt(DIGITS.length)])
        }
        if (includeSymbols) {
            pool.append(SYMBOLS)
            mandatory.add(SYMBOLS[secureRandom.nextInt(SYMBOLS.length)])
        }

        if (pool.isEmpty()) {
            pool.append(LOWERCASE).append(DIGITS)
            mandatory.add(LOWERCASE[secureRandom.nextInt(LOWERCASE.length)])
        }

        val poolStr = pool.toString()
        val result = CharArray(length.coerceAtLeast(mandatory.size))

        for (i in mandatory.indices) {
            result[i] = mandatory[i]
        }
        for (i in mandatory.size until result.size) {
            result[i] = poolStr[secureRandom.nextInt(poolStr.length)]
        }

        // Fisher-Yates shuffle to randomize mandatory character positions
        for (i in result.indices.reversed()) {
            val j = secureRandom.nextInt(i + 1)
            val temp = result[i]
            result[i] = result[j]
            result[j] = temp
        }

        return String(result)
    }

    fun generateByPreset(preset: Preset): String {
        return when (preset) {
            Preset.BALANCED -> generate(length = 16, includeUppercase = true, includeLowercase = true, includeDigits = true, includeSymbols = true)
            Preset.HIGH_ENTROPY -> generate(length = 24, includeUppercase = true, includeLowercase = true, includeDigits = true, includeSymbols = true)
            Preset.PIN -> generate(length = 6, includeUppercase = false, includeLowercase = false, includeDigits = true, includeSymbols = false)
            Preset.CUSTOM -> generate(length = 16)
        }
    }
}
