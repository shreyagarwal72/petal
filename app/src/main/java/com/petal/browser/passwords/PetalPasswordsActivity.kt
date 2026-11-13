package com.petal.browser.passwords

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.unit.PetalAndroidUtils
import com.petal.browser.unit.PetalHighRefreshRateManager

/**
 * Standalone activity for Petal Passwords & Autofill Control Center.
 * Pointed to by [PetalAutofillService] settingsActivity in petal_autofill_service_config.xml.
 */
class PetalPasswordsActivity : FragmentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(PetalAndroidUtils.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PetalHighRefreshRateManager.applyHighRefreshRate(this)
        PetalAndroidUtils.initTheme(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            PetalExpressiveTheme {
                PetalPasswordsScreen(
                    activity = this,
                    onNavigateBack = { finish() }
                )
            }
        }
    }
}
