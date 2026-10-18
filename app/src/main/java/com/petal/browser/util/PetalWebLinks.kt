package com.petal.browser.util

/**
 * Single source of truth for the Petal website and support links used inside the app.
 * Change [BASE_URL] if the website moves; every screen that links to it picks the change up.
 */
object PetalWebLinks {
    const val BASE_URL = "https://my-petal-browser.vercel.app"

    const val WEBSITE = BASE_URL
    const val DOWNLOAD = "$BASE_URL/download"
    const val FAQ = "$BASE_URL/faq"
    const val SUPPORT = "$BASE_URL/support"
    const val PRIVACY_POLICY = "$BASE_URL/privacy"
    const val TERMS = "$BASE_URL/terms"
    const val LICENSES = "$BASE_URL/licenses"
    const val CHANGELOG = "$BASE_URL/changelog"
    const val DESIGN = "$BASE_URL/design"
    const val ARCHITECTURE = "$BASE_URL/architecture"
    const val PRD = "$BASE_URL/prd"
    const val CODE_OF_CONDUCT = "$BASE_URL/code-of-conduct"
    const val GPL = "$BASE_URL/gpl"
}
