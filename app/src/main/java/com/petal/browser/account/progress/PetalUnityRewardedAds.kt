package com.petal.browser.account.progress

import android.app.Activity
import android.content.Context
import android.preference.PreferenceManager
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions

/**
 * Optional rewarded ads through Unity Ads.
 * Unity is NOT started at app launch (its startup hook is removed in the manifest).
 * It starts only when the user taps "Watch an ad" after agreeing on the support card.
 * It is never touched from browser tabs, so private tabs are unaffected.
 */
object PetalUnityRewardedAds : PetalRewardedAds {
    private const val GAME_ID = "800412724"
    private const val PLACEMENT = "BP_Rewarded_Android"
    // Keep true while testing. Set to false only for the release you publish.
    private val TEST_MODE = com.petal.browser.BuildConfig.DEBUG

    override val isAvailable: Boolean = true

    private fun consented(ctx: Context) =
        PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean("pp_ads_consent", false)

    override fun show(activity: Activity, onResult: (Boolean) -> Unit) {
        val app = activity.applicationContext
        if (!consented(app)) { onResult(false); return }
        if (UnityAds.isInitialized) { load(activity, onResult); return }
        UnityAds.initialize(app, GAME_ID, TEST_MODE, object : IUnityAdsInitializationListener {
            override fun onInitializationComplete() = load(activity, onResult)
            override fun onInitializationFailed(e: UnityAds.UnityAdsInitializationError?, m: String?) = onResult(false)
        })
    }

    private fun load(activity: Activity, onResult: (Boolean) -> Unit) {
        UnityAds.load(PLACEMENT, object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(p: String?) = display(activity, onResult)
            override fun onUnityAdsFailedToLoad(p: String?, e: UnityAds.UnityAdsLoadError?, m: String?) = onResult(false)
        })
    }

    private fun display(activity: Activity, onResult: (Boolean) -> Unit) {
        UnityAds.show(activity, PLACEMENT, UnityAdsShowOptions(), object : IUnityAdsShowListener {
            override fun onUnityAdsShowFailure(p: String?, e: UnityAds.UnityAdsShowError?, m: String?) = onResult(false)
            override fun onUnityAdsShowStart(p: String?) = Unit
            override fun onUnityAdsShowClick(p: String?) = Unit
            // A skipped ad earns nothing.
            override fun onUnityAdsShowComplete(p: String?, s: UnityAds.UnityAdsShowCompletionState?) =
                onResult(s == UnityAds.UnityAdsShowCompletionState.COMPLETED)
        })
    }
}
