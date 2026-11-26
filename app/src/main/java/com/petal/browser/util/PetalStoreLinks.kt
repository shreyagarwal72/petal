package com.petal.browser.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens Google Play and GitHub links directly in their own apps instead of routing through a
 * browser (Petal itself is often the default browser and would just re-open the link in a tab).
 * Every function returns false when the target app can't handle the link so callers can fall
 * back to a normal URL.
 */
object PetalStoreLinks {
    const val PLAY_STORE_PACKAGE = "com.android.vending"
    const val GITHUB_PACKAGE = "com.github.android"

    private fun start(context: Context, uri: String, pkg: String): Boolean = try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                .setPackage(pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    /** Opens this app's (or [packageName]'s) listing straight in the Play Store app. */
    @JvmStatic
    @JvmOverloads
    fun openPlayStore(context: Context, packageName: String = context.packageName): Boolean =
        start(context, "market://details?id=$packageName", PLAY_STORE_PACKAGE) ||
            start(context, "https://play.google.com/store/apps/details?id=$packageName", PLAY_STORE_PACKAGE)

    /** Opens a github.com URL (repo, releases, issues...) straight in the GitHub app. */
    @JvmStatic
    fun openGitHub(context: Context, url: String): Boolean =
        isGitHubUrl(url) && start(context, url, GITHUB_PACKAGE)

    @JvmStatic
    fun isGitHubUrl(url: String): Boolean {
        val host = runCatching { Uri.parse(url).host }.getOrNull()?.lowercase() ?: return false
        return host == "github.com" || host == "www.github.com"
    }
}
