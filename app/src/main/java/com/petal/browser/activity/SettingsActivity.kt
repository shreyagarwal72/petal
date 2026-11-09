package com.petal.browser.activity

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.petal.browser.R
import com.petal.browser.compose.settings.PetalSettingsBridge
import com.petal.browser.compose.settings.SettingsCategory
import com.petal.browser.unit.PetalAndroidUtils
import com.petal.browser.unit.PetalBrowserUtils
import com.petal.browser.unit.PetalHighRefreshRateManager
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
open class SettingsActivity : AppCompatActivity() {

    companion object {
        /**
         * Optional String extra naming a [SettingsCategory] enum constant (e.g.
         * "API_INTEGRATIONS"). When present, Settings opens straight to that category's
         * page instead of the root category list so the user lands directly on the actual
         * page they asked for, not a list they then have to tap through themselves.
         */
        const val EXTRA_SETTINGS_CATEGORY = "settings_category"
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(PetalAndroidUtils.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PetalHighRefreshRateManager.applyHighRefreshRate(this)
        PetalAndroidUtils.initTheme(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        var initialCategory = SettingsCategory.OVERVIEW
        val requestedCategory = intent?.getStringExtra(EXTRA_SETTINGS_CATEGORY)
        if (requestedCategory != null) {
            try {
                initialCategory = SettingsCategory.valueOf(requestedCategory)
            } catch (_: IllegalArgumentException) {
                // Unknown/typo'd category name - fall back to the overview rather than crash.
            }
        }

        setContentView(PetalSettingsBridge.createSettingsView(this, initialCategory) {
            finish()
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_help, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == android.R.id.home) {
            finish()
        } else if (menuItem.itemId == R.id.menu_help) {
            val webpage = Uri.parse("https://github.com/shreyagarwal72/petal")
            PetalBrowserUtils.intentURL(this, webpage)
        }
        return true
    }
}
