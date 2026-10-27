package com.petal.browser.account.progress

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.preference.PreferenceManager
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
 *
 * Reliability notes:
 * - Every init/load attempt has a watchdog. If Unity never calls back (offline, blocked network, SDK
 *   hiccup) the state is reset and the user gets an error instead of a "Loading ad..." that never ends.
 * - Unity's real error (NO_FILL, placement not found, timeout, ...) is surfaced through [lastError] and
 *   the onMessage callback instead of being replaced by a generic message.
 * - Debuggable builds default to Unity test ads, because a new project/Game ID frequently has no real fill.
 */
object PetalUnityRewardedAds : PetalRewardedAds {
    private const val TAG = "PetalAds"
    private const val DEFAULT_GAME_ID = "800412724"
    private const val DEFAULT_PLACEMENT = "BP_Rewarded_Android"
    private const val KEY_TEST_MODE = "pp_ads_test_mode"

    /** Max time to wait for Unity to initialize or answer one load request before giving up. */
    private const val WATCHDOG_MS = 20_000L

    override val isAvailable: Boolean = true

    @Volatile override var isLoaded: Boolean = false
        private set

    @Volatile override var isLoading: Boolean = false
        private set

    /** Human-readable reason for the most recent failure, or null if the last attempt did not fail. */
    @Volatile override var lastError: String? = null
        private set

    @Volatile private var activePlacement: String = DEFAULT_PLACEMENT
    private var pendingShow: Pair<Activity, ((Boolean) -> Unit)>? = null
    private var pendingMessage: ((String) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())

    /** Bumped for every new attempt and on every failure; callbacks from older attempts are ignored. */
    private var attempt = 0
    private var watchdog: Runnable? = null
    private var lastLoadFailure: String? = null

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private fun consented(ctx: Context) =
        PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean("pp_ads_consent", false)

    /**
     * Test mode: an explicit "pp_ads_test_mode" preference always wins. Without one, debuggable builds
     * use Unity test ads (they fill reliably) and release builds request real ads.
     * Note: Unity applies this only on the first initialization in a process.
     */
    fun isTestMode(ctx: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        if (sp.contains(KEY_TEST_MODE)) return sp.getBoolean(KEY_TEST_MODE, false)
        return (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    fun setTestMode(ctx: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(ctx).edit().putBoolean(KEY_TEST_MODE, enabled).apply()
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

    // ---- watchdog / failure plumbing ------------------------------------------------------------

    private fun startWatchdog(token: Int, what: String) {
        cancelWatchdog()
        val r = Runnable {
            if (token == attempt && isLoading) {
                Log.w(TAG, "$what timed out after ${WATCHDOG_MS}ms")
                fail("$what timed out. Check your connection and try again.")
            }
        }
        watchdog = r
        main.postDelayed(r, WATCHDOG_MS)
    }

    private fun cancelWatchdog() {
        watchdog?.let { main.removeCallbacks(it) }
        watchdog = null
    }

    /** Ends the current attempt as failed, resets state so a retry is possible, and tells the waiter why. */
    private fun fail(message: String) {
        attempt++ // invalidate any in-flight Unity callbacks and watchdog of the failed attempt
        cancelWatchdog()
        isLoading = false
        isLoaded = false
        lastError = message
        Log.e(TAG, "Ad attempt failed: $message")
        val msg = pendingMessage
        val show = pendingShow
        pendingShow = null
        pendingMessage = null
        msg?.invoke(message)
        show?.second?.invoke(false)
    }

    private fun describeLoadFailure(): String {
        val f = lastLoadFailure ?: return "Ad failed to load."
        return if (f.startsWith("NO_FILL")) {
            "No ad available right now. Please try again in a minute."
        } else {
            "Ad failed to load: $f"
        }
    }

    // ---- loading --------------------------------------------------------------------------------

    override fun preload(context: Context) {
        val app = context.applicationContext
        if (!consented(app)) return
        if (isLoaded || isLoading) return
        beginLoad(app)
    }

    private fun beginLoad(app: Context) {
        isLoading = true
        isLoaded = false
        lastError = null
        lastLoadFailure = null
        val token = ++attempt
        startWatchdog(token, "Ad request")
        ensureInitialized(app, token) {
            loadPlacement(app, 0, token)
        }
    }

    private fun ensureInitialized(app: Context, token: Int, onReady: () -> Unit) {
        applyConsentMetadata(app)
        if (UnityAds.isInitialized) {
            onReady()
            return
        }
        val testMode = isTestMode(app)
        val gameId = getGameId(app)
        Log.d(TAG, "Initializing Unity Ads SDK: gameId=$gameId, testMode=$testMode")
        UnityAds.initialize(app, gameId, testMode, object : IUnityAdsInitializationListener {
            override fun onInitializationComplete() {
                onMain {
                    Log.d(TAG, "Unity Ads SDK initialization complete (testMode=$testMode)")
                    if (token == attempt) onReady()
                }
            }

            override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError?, message: String?) {
                onMain {
                    Log.e(TAG, "Unity Ads SDK initialization failed: $error - $message")
                    if (token == attempt) {
                        fail("Ad service unavailable: ${message ?: error?.name ?: "initialization error"}")
                    }
                }
            }
        })
    }

    private fun loadPlacement(context: Context, candidateIndex: Int, token: Int) {
        if (token != attempt) return
        val candidates = getCandidatePlacements(context)
        if (candidateIndex >= candidates.size) {
            Log.e(TAG, "All candidate placements failed to load. Last failure: $lastLoadFailure")
            fail(describeLoadFailure())
            return
        }

        val placement = candidates[candidateIndex]
        activePlacement = placement
        // Restart the watchdog for every placement so a slow first candidate can't starve the others.
        startWatchdog(token, "Ad request")
        Log.d(TAG, "Loading Unity Ad on placement: $placement (candidate ${candidateIndex + 1} of ${candidates.size})")

        UnityAds.load(placement, object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(placementId: String?) {
                onMain {
                    if (token != attempt) return@onMain
                    Log.d(TAG, "Unity Ad successfully loaded: $placementId")
                    cancelWatchdog()
                    isLoading = false
                    isLoaded = true
                    lastError = null
                    val pending = pendingShow
                    if (pending != null) {
                        pendingShow = null
                        val msg = pendingMessage
                        pendingMessage = null
                        val act = pending.first
                        if (act.isFinishing || act.isDestroyed) {
                            // The screen that asked for the ad is gone; keep the ad loaded for next time.
                            pending.second(false)
                        } else {
                            display(act, msg, pending.second)
                        }
                    }
                }
            }

            override fun onUnityAdsFailedToLoad(
                placementId: String?,
                error: UnityAds.UnityAdsLoadError?,
                message: String?
            ) {
                onMain {
                    if (token != attempt) return@onMain
                    val code = error?.name ?: "UNKNOWN"
                    lastLoadFailure = "$code${if (message.isNullOrBlank()) "" else ": $message"} (placement $placementId)"
                    Log.w(TAG, "Failed to load ad on $placementId: $lastLoadFailure")
                    // Trying other placements only helps for placement/fill problems. If the SDK isn't
                    // initialized or the request timed out, the next placement would fail the same way.
                    if (code == "INITIALIZE_FAILED" || code == "TIMEOUT") {
                        fail(describeLoadFailure())
                    } else {
                        loadPlacement(context, candidateIndex + 1, token)
                    }
                }
            }
        })
    }

    // ---- showing --------------------------------------------------------------------------------

    override fun show(activity: Activity, onResult: (Boolean) -> Unit) {
        show(activity, {}, onResult)
    }

    override fun show(activity: Activity, onMessage: (String) -> Unit, onResult: (Boolean) -> Unit) {
        val app = activity.applicationContext
        if (!consented(app)) {
            Log.w(TAG, "Show ad requested without consent")
            lastError = "Please enable ads first."
            onMessage("Please enable ads first.")
            onResult(false)
            return
        }

        if (isLoaded) {
            lastError = null
            display(activity, onMessage, onResult)
            return
        }

        pendingShow = activity to onResult
        pendingMessage = onMessage

        if (isLoading) {
            Log.d(TAG, "Ad is already loading, waiting...")
            onMessage("Loading ad, please wait a moment...")
            return
        }

        Log.d(TAG, "Ad not loaded; initiating load before show")
        onMessage("Loading ad...")
        beginLoad(app)
    }

    private fun display(activity: Activity, onMessage: ((String) -> Unit)?, onResult: (Boolean) -> Unit) {
        isLoaded = false
        Log.d(TAG, "Displaying Unity Ad on placement: $activePlacement")

        val showOptions = UnityAdsShowOptions()
        try {
            UnityAds.show(activity, activePlacement, showOptions, object : IUnityAdsShowListener {
                override fun onUnityAdsShowFailure(
                    placementId: String?,
                    error: UnityAds.UnityAdsShowError?,
                    message: String?
                ) {
                    onMain {
                        Log.e(TAG, "Unity Ad show failed on placement $placementId: $error - $message")
                        val reason = "Ad failed to show: ${message ?: error?.name ?: "Unknown error"}"
                        lastError = reason
                        onMessage?.invoke(reason)
                        onResult(false)
                        preload(activity)
                    }
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
                    onMain {
                        Log.d(TAG, "Unity Ad completed playback: $placementId, state: $state")
                        lastError = null
                        val completed = state == UnityAds.UnityAdsShowCompletionState.COMPLETED
                        onResult(completed)
                        preload(activity)
                    }
                }
            })
        } catch (t: Throwable) {
            Log.e(TAG, "UnityAds.show threw", t)
            val reason = "Ad failed to show: ${t.message ?: t.javaClass.simpleName}"
            lastError = reason
            onMessage?.invoke(reason)
            onResult(false)
        }
    }
}
