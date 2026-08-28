package com.petal.browser.activity

import android.os.Bundle
import androidx.activity.EdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.petal.browser.compose.settings.PetalSettingsBridge
import com.petal.browser.compose.settings.SettingsCategory
import com.petal.browser.unit.HelperUnit

class Settings_Activity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(HelperUnit.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HelperUnit.initTheme(this)
        EdgeToEdge.enable(this)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        var initialCategory = SettingsCategory.OVERVIEW
        val requestedCategory = intent?.getStringExtra(EXTRA_SETTINGS_CATEGORY)
        if (requestedCategory != null) {
            try {
                initialCategory = SettingsCategory.valueOf(requestedCategory)
            } catch (ignored: IllegalArgumentException) {
                // Unknown/typo'd category name - fall back to the overview rather than crash.
            }
        }

        setContentView(
            PetalSettingsBridge.createSettingsView(this, initialCategory) {
                finish()
            }
        )
    }

    companion object {
        /**
         * Optional String extra naming a [SettingsCategory] enum constant (e.g.
         * "API_INTEGRATIONS"). When present, Settings opens straight to that category's
         * page instead of the root category list.
         */
        const val EXTRA_SETTINGS_CATEGORY = "settings_category"
    }
}
