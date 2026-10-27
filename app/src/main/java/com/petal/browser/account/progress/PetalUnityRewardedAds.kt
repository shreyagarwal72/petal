package com.petal.browser.account.progress

import android.app.Activity
import android.content.Context
import android.preference.PreferenceManager
import android.util.Log
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions
import com.unity3d.ads.metadata.MetaData

/**
 * Optional rewarded ads through Unity Ads.
 * Supports REAL live monetization ads as well as opt-in test ads.
 * Unity is initialized and loaded only after the user opts in on the support card.
 * It is never touched from browser tabs, so private tabs are unaffected.
 */
object PetalUnityRewardedAds : PetalRewardedAds {
    private const val TAG = "PetalAds"
    private const val DEFAULT_GAME_ID = "800412724"
    private const val DEFAULT_PLACEMENT = "BP_Rewarded_Android"

    override val isAvailable: Boolean = true

    @Volatile override var isLoaded: Boolean = false
        private set

    @Volatile override var isLoading: Boolean = false
        private set

    @Volatile private var activePlacement: String = DEFAULT_PLACEMENT
    private var pendingShow: Pair<Activity, ((Boolean) -> Unit)>? = null
    private var pendingMessage: ((String) -> Unit)? = null

    private fun consented(ctx: Context) =
        PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean("pp_ads_consent", false)

    /**
     * Test mode is FALSE by default to request and display REAL ads from the ad network.
     * Can be set to true via "pp_ads_test_mode" preference for testing when needed.
     */
    fun isTestMode(ctx: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean("pp_ads_test_mode", false)

    fun setTestMode(ctx: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(ctx).edit().putBoolean("pp_ads_test_mode", enabled).apply()
    }

    private fun getGameId(ctx: Context): String =
        PreferenceManager.getDefaultSharedPreferences(ctx).getString("pp_unity_game_id", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_GAME_ID

    private fun getCandidatePlacements(ctx: Context): List<String> {
        val custom = PreferenceManager.getDefaultSharedPreferences(ctx).getString("pp_unity_placement", null)?.takeIf { it.isNotBlank() }
        val list = mutableListOf<String>()
        if (custom != null) list.add(custom)
        if (DEFAULT_PLACEMENT !in list) list.add(DEFAULT_PLACEMENT)
        if ("Rewarded_Android" !in list) list.add("Rewarded_Android")
        if ("rewardedVideo" !in list) list.add("rewardedVideo")
        return list
    }

    private fun applyConsentMetadata(context: Context) {
        try {
            val gdpr = MetaData(context)
            gdpr.set("gdpr.consent", true)
            gdpr.commit()

            val privacy = MetaData(context)
            privacy.set("privacy.consent", true)
            privacy.commit()

            val user = MetaData(context)
            user.set("user.nonbehavioral", false)
            user.commit()
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to apply ad consent metadata", e)
        }
    }

    override fun preload(context: Context) {
        val app = context.applicationContext
        if (!consented(app)) return
        if (isLoaded || isLoading) return
        ensureInitialized(app) {
            loadPlacement(app, 0)
        }
    }

    private fun ensureInitialized(app: Context, onReady: () -> Unit) {
        applyConsentMetadata(app)
        if (UnityAds.isInitialized) {
            onReady()
            return
        }
        val testMode = isTestMode(app)
        val gameId = getGameId(app)
        Log.d(TAG, "Initializing Unity Ads SDK for real ads: gameId=$gameId, testMode=$testMode")
        UnityAds.initialize(app, gameId, testMode, object : IUnityAdsInitializationListener {
            override fun onInitializationComplete() {
                Log.d(TAG, "Unity Ads SDK initialization complete (testMode=$testMode)")
                onReady()
            }

            override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError?, message: String?) {
                Log.e(TAG, "Unity Ads SDK initialization failed: $error - $message")
                isLoading = false
                isLoaded = false
                val errorMsg = message ?: error?.name ?: "Ad initialization error"
                pendingMessage?.invoke("Ad service unavailable: $errorMsg")
                pendingShow?.second?.invoke(false)
                pendingShow = null
                pendingMessage = null
            }
        })
    }

    private fun loadPlacement(context: Context, candidateIndex: Int) {
        val candidates = getCandidatePlacements(context)
        if (candidateIndex >= candidates.size) {
            Log.e(TAG, "All candidate placements failed to load for real ads")
            isLoading = false
            isLoaded = false
            pendingMessage?.invoke("No real ads filled right now. Please check connection and try again.")
            pendingShow?.second?.invoke(false)
            pendingShow = null
            pendingMessage = null
            return
        }

        val placement = candidates[candidateIndex]
        activePlacement = placement
        isLoading = true
        Log.d(TAG, "Loading real Unity Ad on placement: $placement (candidate index $candidateIndex of ${candidates.size})")

        UnityAds.load(placement, object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(placementId: String?) {
                Log.d(TAG, "Unity Ad successfully loaded: $placementId")
                isLoading = false
                isLoaded = true
                val pending = pendingShow
                if (pending != null) {
                    pendingShow = null
                    val msg = pendingMessage
                    pendingMessage = null
                    display(pending.first, msg, pending.second)
                }
            }

            override fun onUnityAdsFailedToLoad(
                placementId: String?,
                error: UnityAds.UnityAdsLoadError?,
                message: String?
            ) {
                Log.w(TAG, "Failed to load real ad on $placementId: $error - $message. Trying next candidate...")
                loadPlacement(context, candidateIndex + 1)
            }
        })
    }

    override fun show(activity: Activity, onResult: (Boolean) -> Unit) {
        show(activity, {}, onResult)
    }

    override fun show(activity: Activity, onMessage: (String) -> Unit, onResult: (Boolean) -> Unit) {
        val app = activity.applicationContext
        if (!consented(app)) {
            Log.w(TAG, "Show ad requested without consent")
            onMessage("Please enable ads first.")
            onResult(false)
            return
        }

        if (isLoaded) {
            display(activity, onMessage, onResult)
            return
        }

        if (isLoading) {
            Log.d(TAG, "Ad is already loading, waiting...")
            onMessage("Loading real ad, please wait a moment...")
            pendingShow = activity to onResult
            pendingMessage = onMessage
            return
        }

        Log.d(TAG, "Real ad not loaded; initiating load before show")
        pendingShow = activity to onResult
        pendingMessage = onMessage
        isLoading = true
        onMessage("Loading real ad...")
        ensureInitialized(app) {
            loadPlacement(app, 0)
        }
    }

    private fun display(activity: Activity, onMessage: ((String) -> Unit)?, onResult: (Boolean) -> Unit) {
        isLoaded = false
        Log.d(TAG, "Displaying real Unity Ad on placement: $activePlacement")

        val showOptions = UnityAdsShowOptions()
        UnityAds.show(activity, activePlacement, showOptions, object : IUnityAdsShowListener {
            override fun onUnityAdsShowFailure(
                placementId: String?,
                error: UnityAds.UnityAdsShowError?,
                message: String?
            ) {
                Log.e(TAG, "Unity Ad show failed on placement $placementId: $error - $message")
                onMessage?.invoke("Ad failed to show: ${message ?: error?.name ?: "Unknown error"}")
                onResult(false)
                preload(activity)
            }

            override fun onUnityAdsShowStart(placementId: String?) {
                Log.d(TAG, "Real Unity Ad started playback: $placementId")
            }

            override fun onUnityAdsShowClick(placementId: String?) {
                Log.d(TAG, "Real Unity Ad clicked: $placementId")
            }

            override fun onUnityAdsShowComplete(
                placementId: String?,
                state: UnityAds.UnityAdsShowCompletionState?
            ) {
                Log.d(TAG, "Real Unity Ad completed playback: $placementId, state: $state")
                val completed = state == UnityAds.UnityAdsShowCompletionState.COMPLETED
                onResult(completed)
                preload(activity)
            }
        })
    }
}
