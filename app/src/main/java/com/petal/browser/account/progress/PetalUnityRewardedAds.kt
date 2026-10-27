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

/**
 * Optional rewarded ads through Unity Ads.
 * Unity is initialized and loaded only after the user opts in on the support card.
 * It is never touched from browser tabs, so private tabs are unaffected.
 */
object PetalUnityRewardedAds : PetalRewardedAds {
    private const val TAG = "PetalAds"
    private const val GAME_ID = "800412724"
    private const val PRIMARY_PLACEMENT = "BP_Rewarded_Android"
    private val CANDIDATE_PLACEMENTS = listOf(PRIMARY_PLACEMENT, "Rewarded_Android", "rewardedVideo")

    override val isAvailable: Boolean = true

    @Volatile override var isLoaded: Boolean = false
        private set

    @Volatile override var isLoading: Boolean = false
        private set

    @Volatile private var activePlacement: String = PRIMARY_PLACEMENT
    private var pendingShow: Pair<Activity, ((Boolean) -> Unit)>? = null
    private var pendingMessage: ((String) -> Unit)? = null

    private fun consented(ctx: Context) =
        PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean("pp_ads_consent", false)

    private fun isTestMode(ctx: Context): Boolean {
        // Test mode is enabled by default to allow reliable testing during development and testing APKs.
        // Set "pp_ads_test_mode" to false in preferences only when ready for live production monetization.
        return PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean("pp_ads_test_mode", true)
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
        if (UnityAds.isInitialized()) {
            onReady()
            return
        }
        val testMode = isTestMode(app)
        Log.d(TAG, "Initializing Unity Ads SDK: gameId=$GAME_ID, testMode=$testMode")
        UnityAds.initialize(app, GAME_ID, testMode, object : IUnityAdsInitializationListener {
            override fun onInitializationComplete() {
                Log.d(TAG, "Unity Ads SDK initialization complete")
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
        if (candidateIndex >= CANDIDATE_PLACEMENTS.size) {
            Log.e(TAG, "All candidate placements failed to load")
            isLoading = false
            isLoaded = false
            pendingMessage?.invoke("No ad available right now. Please check internet and try again.")
            pendingShow?.second?.invoke(false)
            pendingShow = null
            pendingMessage = null
            return
        }

        val placement = CANDIDATE_PLACEMENTS[candidateIndex]
        activePlacement = placement
        isLoading = true
        Log.d(TAG, "Loading Unity Ads placement: $placement (candidate index $candidateIndex)")

        UnityAds.load(placement, object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(placementId: String?) {
                Log.d(TAG, "Unity Ad loaded successfully: $placementId")
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
                Log.w(TAG, "Failed to load placement $placementId: $error - $message. Trying fallback candidate...")
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
            Log.d(TAG, "Ad is already loading, queuing show request")
            onMessage("Ad is loading, please wait a moment...")
            pendingShow = activity to onResult
            pendingMessage = onMessage
            return
        }

        Log.d(TAG, "Ad not loaded; initiating load before show")
        pendingShow = activity to onResult
        pendingMessage = onMessage
        isLoading = true
        onMessage("Loading ad...")
        ensureInitialized(app) {
            loadPlacement(app, 0)
        }
    }

    private fun display(activity: Activity, onMessage: ((String) -> Unit)?, onResult: (Boolean) -> Unit) {
        isLoaded = false
        Log.d(TAG, "Displaying Unity Ad on placement: $activePlacement")

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
                Log.d(TAG, "Unity Ad started playback: $placementId")
            }

            override fun onUnityAdsShowClick(placementId: String?) {
                Log.d(TAG, "Unity Ad clicked: $placementId")
            }

            override fun onUnityAdsShowComplete(
                placementId: String?,
                state: UnityAds.UnityAdsShowCompletionState?
            ) {
                Log.d(TAG, "Unity Ad completed playback: $placementId, state: $state")
                val completed = state == UnityAds.UnityAdsShowCompletionState.COMPLETED
                onResult(completed)
                preload(activity)
            }
        })
    }
}
