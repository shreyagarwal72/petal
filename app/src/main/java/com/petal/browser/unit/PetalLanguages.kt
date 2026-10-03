package com.petal.browser.unit

/**
 * Registry of all languages supported by Petal Browser.
 * Each entry provides the BCP-47 tag, native script display name,
 * and English language name for search and clarity.
 */
data class PetalLanguage(
    val tag: String,
    val nativeName: String,
    val englishName: String
) {
    val displayLabel: String
        get() = if (tag == "system") {
            nativeName
        } else if (nativeName.equals(englishName, ignoreCase = true)) {
            nativeName
        } else {
            "$nativeName ($englishName)"
        }
}

object PetalLanguages {

    val ALL_LANGUAGES: List<PetalLanguage> = listOf(
        PetalLanguage("system", "System Default", "Follow Device Language"),
        PetalLanguage("en", "English", "English"),
        PetalLanguage("hi-Latn", "Hinglish", "Hindi in English"),
        PetalLanguage("hi", "हिन्दी", "Hindi"),
        PetalLanguage("es", "Español", "Spanish"),
        PetalLanguage("fr", "Français", "French"),
        PetalLanguage("de", "Deutsch", "German"),
        PetalLanguage("zh", "中文 (简体)", "Chinese Simplified"),
        PetalLanguage("zh-rTW", "中文 (繁體)", "Chinese Traditional"),
        PetalLanguage("ar", "العربية", "Arabic"),
        PetalLanguage("pt", "Português", "Portuguese"),
        PetalLanguage("ru", "Русский", "Russian"),
        PetalLanguage("ja", "日本語", "Japanese"),
        PetalLanguage("it", "Italiano", "Italian"),
        PetalLanguage("nl", "Nederlands", "Dutch"),
        PetalLanguage("pl", "Polski", "Polish"),
        PetalLanguage("tr", "Türkçe", "Turkish"),
        PetalLanguage("vi", "Tiếng Việt", "Vietnamese"),
        PetalLanguage("id", "Bahasa Indonesia", "Indonesian"),
        PetalLanguage("cs", "Čeština", "Czech"),
        PetalLanguage("el", "Ελληνικά", "Greek"),
        PetalLanguage("et", "Eesti", "Estonian"),
        PetalLanguage("fa", "فارسی", "Persian"),
        PetalLanguage("he", "עברית", "Hebrew"),
        PetalLanguage("hu", "Magyar", "Hungarian"),
        PetalLanguage("ro", "Română", "Romanian"),
        PetalLanguage("sv", "Svenska", "Swedish"),
        PetalLanguage("sr", "Српски", "Serbian Cyrillic"),
        PetalLanguage("b+sr+Latn", "Srpski", "Serbian Latin"),
        PetalLanguage("uk", "Українська", "Ukrainian")
    )

    fun findLanguage(tag: String?): PetalLanguage {
        if (tag.isNullOrBlank() || tag == "system") return ALL_LANGUAGES.first()
        return ALL_LANGUAGES.firstOrNull { it.tag.equals(tag, ignoreCase = true) }
            ?: ALL_LANGUAGES.first()
    }
}
