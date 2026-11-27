package com.petal.browser.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.petal.browser.activity.MainActivity
import com.petal.browser.activity.PetalQuickSearchActivity
import com.petal.browser.lens.PetalScannerActivity

/**
 * Single router mapping widget and tile actions to the right Activity (Brave/Chrome style).
 *
 * - Search, Voice, and widget Incognito search open [PetalQuickSearchActivity] directly so
 *   the full browser task isn't brought forward until the user actually submits a search/URL.
 * - Lens/QR opens [PetalScannerActivity] directly without bouncing through [MainActivity].
 * - In-browser tabs/destinations (New Tab, Bookmarks, Downloads, AI Search) route to [MainActivity].
 */
object PetalWidgetIntentRouter {

    fun intentForAction(context: Context, action: String?): Intent {
        return when (action) {
            PetalSearchWidgetProvider.ACTION_OPEN_SEARCH, null -> {
                Intent(context, PetalQuickSearchActivity::class.java).apply {
                    this.action = PetalSearchWidgetProvider.ACTION_OPEN_SEARCH
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }

            PetalSearchWidgetProvider.ACTION_OPEN_VOICE -> {
                Intent(context, PetalQuickSearchActivity::class.java).apply {
                    this.action = PetalSearchWidgetProvider.ACTION_OPEN_VOICE
                    putExtra(PetalQuickSearchActivity.EXTRA_START_VOICE, true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }

            PetalSearchWidgetProvider.ACTION_OPEN_INCOGNITO -> {
                Intent(context, PetalQuickSearchActivity::class.java).apply {
                    this.action = PetalSearchWidgetProvider.ACTION_OPEN_INCOGNITO
                    putExtra(PetalQuickSearchActivity.EXTRA_INCOGNITO, true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }

            PetalSearchWidgetProvider.ACTION_OPEN_LENS,
            PetalSearchWidgetProvider.ACTION_OPEN_SNAP_CAMERA -> {
                Intent(context, PetalScannerActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }

            else -> {
                Intent(context, MainActivity::class.java).apply {
                    this.action = action
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            }
        }
    }

    fun pendingIntentForAction(context: Context, action: String?): PendingIntent {
        val intent = intentForAction(context, action)
        val requestCode = (action ?: PetalSearchWidgetProvider.ACTION_OPEN_SEARCH).hashCode()
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
