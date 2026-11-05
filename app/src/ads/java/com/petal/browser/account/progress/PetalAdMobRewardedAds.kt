package com.petal.browser.account.progress

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Optional rewarded ads through Google AdMob.
 * Supports REAL live monetization ads as well as opt-in test ads.
 * AdMob is initialized and loaded only after the user opts in on the support card.
 * It is never touched from browser tabs, so private tabs are unaffected.
 *
 * Reliability notes:
 * - Automatically falls back to Google official test ad unit ID on debuggable builds or when test mode is enabled.
 * - Watchdog resets state if network drops or ad load stalls.
 * - AdMob error codes (NO_FILL, NETWORK_ERROR, INTERNAL_ERROR) are translated to helpful user messages.
 */
object PetalAdMobRewardedAds : PetalRewardedAds {
    private const val TAG = "PetalAdMob"

    // Official Google Test Rewarded Ad Unit ID (safe for debug builds)
    private const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

    // Production Rewarded Ad Unit ID for Petal Browser
    private const val PROD_REWARDED_AD_UNIT_ID = "ca-app-pub-5680314597533936/6148934278"

    private const val KEY_TEST_MODE = "pp_ads_test_mode"
    private const val WATCHDOG_MS = 25_000L

    override val isAvailable: Boolean = true

    @Volatile
    override var isLoaded: Boolean = false
        private set

    @Volatile
    override var isLoading: Boolean = false
        private set

    @Volatile
    override var lastError: String? = null
        private set

    private var rewardedAd: RewardedAd? = null
    private var isInitialized = false

    private var pendingShow: Pair<Activity, ((Boolean) -> Unit)>? = null
    private var pendingMessage: ((String) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var attempt = 0
    private var watchdog: Runnable? = null

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private fun consented(ctx: Context) =
        PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean("pp_ads_consent", false)

    fun isTestMode(ctx: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        if (sp.contains(KEY_TEST_MODE)) return sp.getBoolean(KEY_TEST_MODE, false)
        // Default to test mode in debuggable builds to avoid AdMob policy violations
        if ((ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) return true
        return sp.getBoolean(KEY_TEST_MODE, false)
    }

    fun setTestMode(ctx: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(ctx).edit().putBoolean(KEY_TEST_MODE, enabled).apply()
    }

    fun getRewardedAdUnitId(ctx: Context): String {
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        val custom = sp.getString("pp_admob_rewarded_unit_id", null)?.takeIf { it.isNotBlank() }
        if (custom != null) return custom
        return if (isTestMode(ctx)) TEST_REWARDED_AD_UNIT_ID else PROD_REWARDED_AD_UNIT_ID
    }

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

    private fun fail(message: String) {
        attempt++
        cancelWatchdog()
        isLoading = false
        isLoaded = false
        rewardedAd = null
        lastError = message
        Log.e(TAG, "AdMob attempt failed: $message")
        val msg = pendingMessage
        val show = pendingShow
        pendingShow = null
        pendingMessage = null
        msg?.invoke(message)
        show?.second?.invoke(false)
    }

    private fun ensureInitialized(context: Context, onReady: () -> Unit) {
        if (isInitialized) {
            onReady()
            return
        }
        MobileAds.initialize(context) { status ->
            onMain {
                isInitialized = true
                Log.d(TAG, "Google AdMob SDK initialized: $status")
                onReady()
            }
        }
    }

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
        val token = ++attempt
        startWatchdog(token, "Ad request")

        ensureInitialized(app) {
            if (token != attempt) return@ensureInitialized
            val adUnitId = getRewardedAdUnitId(app)
            Log.d(TAG, "Loading AdMob Rewarded Ad with unit ID: $adUnitId (testMode=${isTestMode(app)})")

            val adRequest = AdRequest.Builder().build()
            RewardedAd.load(app, adUnitId, adRequest, object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    onMain {
                        if (token != attempt) return@onMain
                        cancelWatchdog()
                        rewardedAd = ad
                        isLoading = false
                        isLoaded = true
                        lastError = null
                        Log.d(TAG, "AdMob Rewarded Ad successfully loaded")

                        val pending = pendingShow
                        if (pending != null) {
                            pendingShow = null
                            val msg = pendingMessage
                            pendingMessage = null
                            val act = pending.first
                            if (act.isFinishing || act.isDestroyed) {
                                pending.second(false)
                            } else {
                                display(act, msg, pending.second)
                            }
                        }
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    onMain {
                        if (token != attempt) return@onMain
                        val code = loadAdError.code
                        val msg = loadAdError.message
                        Log.w(TAG, "AdMob failed to load: code=$code, msg=$msg")
                        val friendlyMessage = when (code) {
                            AdRequest.ERROR_CODE_NO_FILL -> "No ad available right now (inventory filled). Please try again in a few moments."
                            AdRequest.ERROR_CODE_NETWORK_ERROR -> "Network error loading ad. Check your connection or ad-blocker."
                            else -> "Ad failed to load: $msg"
                        }
                        fail(friendlyMessage)
                    }
                }
            })
        }
    }

    override fun show(activity: Activity, onResult: (Boolean) -> Unit) {
        show(activity, {}, onResult)
    }

    override fun show(activity: Activity, onMessage: (String) -> Unit, onResult: (Boolean) -> Unit) {
        val app = activity.applicationContext
        if (!consented(app)) {
            lastError = "Please enable ads first."
            onMessage("Please enable ads first.")
            onResult(false)
            return
        }

        if (isLoaded && rewardedAd != null) {
            lastError = null
            display(activity, onMessage, onResult)
            return
        }

        pendingShow = activity to onResult
        pendingMessage = onMessage

        if (isLoading) {
            onMessage("Loading ad, please wait a moment...")
            return
        }

        onMessage("Loading ad...")
        beginLoad(app)
    }

    private fun display(activity: Activity, onMessage: ((String) -> Unit)?, onResult: (Boolean) -> Unit) {
        val ad = rewardedAd
        if (ad == null) {
            fail("Ad is not ready to display.")
            return
        }

        isLoaded = false
        var userEarnedReward = false

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "AdMob Rewarded Ad opened full screen")
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                onMain {
                    Log.e(TAG, "AdMob failed to show: ${adError.message}")
                    rewardedAd = null
                    val reason = "Ad failed to show: ${adError.message}"
                    lastError = reason
                    onMessage?.invoke(reason)
                    onResult(false)
                    preload(activity)
                }
            }

            override fun onAdDismissedFullScreenContent() {
                onMain {
                    Log.d(TAG, "AdMob Rewarded Ad dismissed. Reward earned: $userEarnedReward")
                    rewardedAd = null
                    lastError = null
                    onResult(userEarnedReward)
                    preload(activity)
                }
            }
        }

        try {
            ad.show(activity) { rewardItem ->
                userEarnedReward = true
                Log.d(TAG, "User earned reward: amount=${rewardItem.amount}, type=${rewardItem.type}")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Exception during ad.show", t)
            rewardedAd = null
            val reason = "Ad failed to show: ${t.message ?: t.javaClass.simpleName}"
            lastError = reason
            onMessage?.invoke(reason)
            onResult(false)
        }
    }
}
