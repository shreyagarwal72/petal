package com.petal.browser.engine.candy.blocking

data class BlockerSettings(
    val blockAdsAndTrackers: Boolean = true,
    val hideCookieConsent: Boolean = true,
    val blockThirdPartyCookies: Boolean = true,
)
