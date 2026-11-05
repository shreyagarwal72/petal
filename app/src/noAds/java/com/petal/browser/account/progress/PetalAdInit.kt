package com.petal.browser.account.progress

/**
 * Clean no-op ad provider initializer for 100% FOSS builds (F-Droid).
 */
object PetalAdInit {
    fun init() {
        PetalAds.provider = PetalNoAds
    }
}
