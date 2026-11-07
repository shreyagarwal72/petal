package com.petal.browser.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/**
 * Stage 7(j): Translation completeness test.
 * Verifies that all locale strings.xml files contain all keys defined in the base strings.xml.
 */
class TranslationCompletenessTest {

    @Test
    fun testAllLocalesHaveCompleteKeyCoverage() {
        val baseFile = File("src/main/res/values/strings.xml").let {
            if (it.exists()) it else File("app/src/main/res/values/strings.xml")
        }
        assertTrue("Base strings.xml must exist", baseFile.exists())

        val stringNamePattern = Pattern.compile("<string\\s+[^>]*?name=\"([^\"]+)\"")
        val baseContent = baseFile.readText()
        val baseMatcher = stringNamePattern.matcher(baseContent)
        val baseKeys = mutableSetOf<String>()
        while (baseMatcher.find()) {
            baseKeys.add(baseMatcher.group(1))
        }
        assertTrue("Base strings.xml should contain string entries", baseKeys.size > 1000)

        val resDir = baseFile.parentFile.parentFile
        val localeDirs = resDir.listFiles { dir ->
            dir.isDirectory && dir.name.startsWith("values-")
        } ?: emptyArray()

        assertTrue("Should have locale resource folders", localeDirs.isNotEmpty())

        val missingReport = StringBuilder()

        for (localeDir in localeDirs) {
            val localeStrings = File(localeDir, "strings.xml")
            if (!localeStrings.exists()) continue

            val locContent = localeStrings.readText()
            val locMatcher = stringNamePattern.matcher(locContent)
            val locKeys = mutableSetOf<String>()
            while (locMatcher.find()) {
                locKeys.add(locMatcher.group(1))
            }

            val missing = baseKeys.minus(locKeys)
            if (missing.isNotEmpty()) {
                missingReport.append("Locale ${localeDir.name} is missing ${missing.size} keys: ${missing.take(5)}...\n")
            }
        }

        assertTrue(
            "All locales must contain 100% of keys from base strings.xml.\n$missingReport",
            missingReport.isEmpty()
        )
    }
}
