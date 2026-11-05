package com.petal.browser.account.progress

/**
 * Initializes AdMob rewarded ads provider for flavors that include Google Play Services (github, play).
 */
object PetalAdInit {
    fun init() {
        PetalAds.provider = PetalAdMobRewardedAds
    }
}
