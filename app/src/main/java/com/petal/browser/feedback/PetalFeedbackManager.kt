package com.petal.browser.feedback

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.google.android.play.core.review.ReviewManagerFactory
import com.petal.browser.BuildConfig
import com.petal.browser.R
import com.petal.browser.unit.PetalBrowserUtils
import com.petal.browser.update.PetalPlayUpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Coordinates Play Store In-App Reviews / store listing fallback, GitHub stargazer integration,
 * low-star private diagnostic feedback, and non-intrusive engagement prompt cadence.
 */
object PetalFeedbackManager {

    private const val TAG = "PetalFeedbackManager"

    const val PREF_LAUNCH_COUNT = "sp_feedback_launch_count"
    const val PREF_FIRST_LAUNCH_MS = "sp_feedback_first_launch_ms"
    const val PREF_LAST_PROMPT_MS = "sp_feedback_last_prompt_ms"
    const val PREF_PROMPT_COUNT = "sp_feedback_prompt_count"
    const val PREF_PLAY_RATED = "sp_feedback_play_rated"
    const val PREF_GITHUB_STARRED = "sp_feedback_github_starred"
    const val PREF_OPT_OUT = "sp_feedback_opt_out"
    const val PREF_LAST_STAR_RATING = "sp_feedback_last_star_rating"
    const val PREF_GITHUB_STARS_COUNT = "sp_feedback_github_stars_count"
    const val PREF_GITHUB_STARS_FETCHED_MS = "sp_feedback_github_stars_fetched_ms"

    const val MIN_LAUNCH_COUNT = 5
    const val MIN_INSTALL_AGE_MS = 3L * 24 * 60 * 60 * 1000L
    const val PROMPT_COOLDOWN_MS = 14L * 24 * 60 * 60 * 1000L
    const val MAX_LIFETIME_PROMPTS = 3
    const val GITHUB_STARS_CACHE_TTL_MS = 6L * 60 * 60 * 1000L

    const val GITHUB_REPO_SLUG = "shreyagarwal72/petal"
    const val GITHUB_REPO_URL = "https://github.com/shreyagarwal72/petal"
    const val GITHUB_RELEASES_URL = "https://github.com/shreyagarwal72/petal/releases"
    const val GITHUB_ISSUES_URL = "https://github.com/shreyagarwal72/petal/issues"
    const val GITHUB_REPO_API = "https://api.github.com/repos/shreyagarwal72/petal"
    const val SUPPORT_EMAIL = "hereyourchampion@gmail.com"

    private val STARGAZERS_REGEX = Regex(""""stargazers_count"\s*:\s*(\d+)""")

    @JvmField
    val isPromptSheetOpen = mutableStateOf(false)

    @JvmField
    val isAutoPromptSession = mutableStateOf(false)

    @JvmStatic
    @JvmOverloads
    fun openPromptSheet(autoPrompt: Boolean = false) {
        isAutoPromptSession.value = autoPrompt
        isPromptSheetOpen.value = true
    }

    @JvmStatic
    fun dismissPromptSheet() {
        isPromptSheetOpen.value = false
    }

    @JvmStatic
    @JvmOverloads
    fun recordLaunch(sp: SharedPreferences, nowMs: Long = System.currentTimeMillis()) {
        val currentLaunches = sp.getInt(PREF_LAUNCH_COUNT, 0)
        val editor = sp.edit().putInt(PREF_LAUNCH_COUNT, currentLaunches + 1)
        if (!sp.contains(PREF_FIRST_LAUNCH_MS) || sp.getLong(PREF_FIRST_LAUNCH_MS, 0L) <= 0L) {
            editor.putLong(PREF_FIRST_LAUNCH_MS, nowMs)
        }
        editor.apply()
    }

    @JvmStatic
    @JvmOverloads
    fun shouldAutoPrompt(sp: SharedPreferences, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (sp.getBoolean(PREF_OPT_OUT, false)) return false
        if (sp.getBoolean(PREF_PLAY_RATED, false)) return false
        if (sp.getInt(PREF_PROMPT_COUNT, 0) >= MAX_LIFETIME_PROMPTS) return false
        if (sp.getInt(PREF_LAUNCH_COUNT, 0) < MIN_LAUNCH_COUNT) return false

        val firstLaunch = sp.getLong(PREF_FIRST_LAUNCH_MS, 0L)
        if (firstLaunch <= 0L || (nowMs - firstLaunch) < MIN_INSTALL_AGE_MS) return false

        val lastPrompt = sp.getLong(PREF_LAST_PROMPT_MS, 0L)
        if (lastPrompt > 0L && (nowMs - lastPrompt) < PROMPT_COOLDOWN_MS) return false

        return true
    }

    @JvmStatic
    @JvmOverloads
    fun markPromptShown(sp: SharedPreferences, nowMs: Long = System.currentTimeMillis()) {
        val count = sp.getInt(PREF_PROMPT_COUNT, 0)
        sp.edit()
            .putLong(PREF_LAST_PROMPT_MS, nowMs)
            .putInt(PREF_PROMPT_COUNT, count + 1)
            .apply()
    }

    @JvmStatic
    fun markOptOut(sp: SharedPreferences) {
        sp.edit().putBoolean(PREF_OPT_OUT, true).apply()
    }

    @JvmStatic
    fun markPlayRated(sp: SharedPreferences, stars: Int) {
        val clamped = stars.coerceIn(1, 5)
        sp.edit()
            .putBoolean(PREF_PLAY_RATED, true)
            .putInt(PREF_LAST_STAR_RATING, clamped)
            .apply()
    }

    @JvmStatic
    fun markGitHubStarred(sp: SharedPreferences) {
        sp.edit().putBoolean(PREF_GITHUB_STARRED, true).apply()
    }

    @JvmStatic
    fun isPositiveRating(stars: Int): Boolean = stars >= 4

    @JvmStatic
    fun parseGitHubStarsCount(jsonBody: String?): Int? {
        if (jsonBody.isNullOrBlank()) return null
        val match = STARGAZERS_REGEX.find(jsonBody) ?: return null
        return match.groupValues[1].toIntOrNull()?.takeIf { it >= 0 }
    }

    @JvmStatic
    @JvmOverloads
    fun isGitHubStarsCacheFresh(sp: SharedPreferences, nowMs: Long = System.currentTimeMillis()): Boolean {
        val cached = sp.getInt(PREF_GITHUB_STARS_COUNT, -1)
        val fetchedAt = sp.getLong(PREF_GITHUB_STARS_FETCHED_MS, 0L)
        return cached >= 0 && fetchedAt > 0L && (nowMs - fetchedAt) < GITHUB_STARS_CACHE_TTL_MS
    }

    @JvmStatic
    fun cachedGitHubStars(sp: SharedPreferences): Int? =
        sp.getInt(PREF_GITHUB_STARS_COUNT, -1).takeIf { it >= 0 }

    @JvmStatic
    fun cacheGitHubStars(sp: SharedPreferences, count: Int, nowMs: Long = System.currentTimeMillis()) {
        if (count < 0) return
        sp.edit()
            .putInt(PREF_GITHUB_STARS_COUNT, count)
            .putLong(PREF_GITHUB_STARS_FETCHED_MS, nowMs)
            .apply()
    }

    @JvmStatic
    fun isPlayStoreInstalled(context: Context): Boolean {
        if (BuildConfig.IS_PLAY_STORE_BUILD) return true
        return try {
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
            installer == "com.android.vending"
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun fetchGitHubStarsIfNeeded(
        sp: SharedPreferences,
        force: Boolean = false
    ): Int? {
        if (!force && isGitHubStarsCacheFresh(sp)) {
            return cachedGitHubStars(sp)
        }
        val fetched = withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(GITHUB_REPO_API).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 4000
                    readTimeout = 4000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "Petal-Browser")
                }
                if (conn.responseCode in 200..299) {
                    val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                    parseGitHubStarsCount(body)
                } else {
                    null
                }
            } catch (t: Throwable) {
                Log.d(TAG, "GitHub stars fetch skipped: ${t.message}")
                null
            } finally {
                conn?.disconnect()
            }
        }
        if (fetched != null) {
            cacheGitHubStars(sp, fetched)
            return fetched
        }
        return cachedGitHubStars(sp)
    }

    @JvmStatic
    fun requestPlayReview(
        activity: Activity?,
        fallbackContext: Context,
        onCompleted: (openedStoreFallback: Boolean) -> Unit = {}
    ) {
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            openPlayStoreListing(fallbackContext)
            onCompleted(true)
            return
        }
        try {
            val manager = ReviewManagerFactory.create(fallbackContext.applicationContext)
            manager.requestReviewFlow().addOnCompleteListener { requestTask ->
                if (requestTask.isSuccessful && !activity.isFinishing && !activity.isDestroyed) {
                    val reviewInfo = requestTask.result
                    manager.launchReviewFlow(activity, reviewInfo)
                        .addOnCompleteListener { launchTask ->
                            if (launchTask.isSuccessful) {
                                onCompleted(false)
                            } else {
                                openPlayStoreListing(fallbackContext)
                                onCompleted(true)
                            }
                        }
                } else {
                    openPlayStoreListing(fallbackContext)
                    onCompleted(true)
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "In-app review unavailable, falling back to Play listing: ${t.message}")
            openPlayStoreListing(fallbackContext)
            onCompleted(true)
        }
    }

    @JvmStatic
    fun openPlayStoreListing(context: Context) {
        try {
            if (com.petal.browser.util.PetalStoreLinks.openPlayStore(context)) return
            PetalPlayUpdateManager.getInstance(context).openPlayStore(context)
        } catch (_: Throwable) {
            openUrl(context, "https://play.google.com/store/apps/details?id=${context.packageName}")
        }
    }

    @JvmStatic
    fun openGitHubRepo(context: Context) {
        openUrl(context, GITHUB_REPO_URL)
    }

    @JvmStatic
    fun openGitHubReleases(context: Context) {
        openUrl(context, GITHUB_RELEASES_URL)
    }

    @JvmStatic
    fun openGitHubIssues(context: Context) {
        openUrl(context, GITHUB_ISSUES_URL)
    }

    @JvmStatic
    fun openUrl(context: Context, url: String) {
        // GitHub links open straight in the GitHub app when it's installed.
        if (com.petal.browser.util.PetalStoreLinks.openGitHub(context, url)) return
        try {
            PetalBrowserUtils.intentURL(context, Uri.parse(url))
        } catch (_: Throwable) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Throwable) {}
        }
    }

    @JvmStatic
    fun buildDiagnosticFeedbackBody(
        verName: String,
        verCode: Int,
        stars: Int,
        userComment: String,
        buildType: String = BuildConfig.BUILD_TYPE,
        osRelease: String = Build.VERSION.RELEASE ?: "unknown",
        sdkInt: Int = Build.VERSION.SDK_INT,
        manufacturer: String = Build.MANUFACTURER ?: "",
        model: String = Build.MODEL ?: ""
    ): String {
        val ratingLine = if (stars in 1..5) "Rating: $stars / 5 stars" else "Rating: Not selected"
        val noteBlock = userComment.trim().ifEmpty { "[Please describe your feedback or issue here]" }
        return """
            Hi Vanshu,

            $noteBlock

            $ratingLine

            ────────────────────────────────────────
            Diagnostic Info:
            App Version: $verName ($verCode)
            Build Type: $buildType
            Web Engine: Mozilla GeckoView 161.0 Beta
            Android OS: $osRelease (API $sdkInt)
            Device: ${"$manufacturer $model".trim()}
            ────────────────────────────────────────
        """.trimIndent()
    }

    @JvmStatic
    fun sendFeedbackReport(context: Context, stars: Int = 0, userComment: String = "") {
        val pInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val verName = pInfo?.versionName ?: "4.9"
        @Suppress("DEPRECATION")
        val verCode = pInfo?.versionCode ?: 490
        val subject = if (stars in 1..5) {
            "Petal Browser Feedback (${stars}★) [v$verName]"
        } else {
            "Petal Browser Feedback [v$verName]"
        }
        val body = buildDiagnosticFeedbackBody(
            verName = verName,
            verCode = verCode,
            stars = stars,
            userComment = userComment
        )
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$SUPPORT_EMAIL")
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        try {
            val chooser = Intent.createChooser(emailIntent, context.getString(R.string.about_feedback_bug_report)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Throwable) {
            val issueUrl = "$GITHUB_REPO_URL/issues/new?title=" +
                Uri.encode(subject) +
                "&body=" + Uri.encode(body)
            openUrl(context, issueUrl)
        }
    }

    @JvmStatic
    fun copyGitHubUrl(context: Context) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("GitHub Repository", GITHUB_REPO_URL))
        } catch (_: Throwable) {}
    }
}
