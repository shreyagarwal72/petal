package com.petal.browser.activity

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.compose.incognito.PetalIncognitoSessionManager
import com.petal.browser.compose.security.PetalAppLockBridge
import com.petal.browser.compose.settings.SettingsCategory
import com.petal.browser.security.BiometricLockManager
import com.petal.browser.ui.components.PetalOmniboxCommand
import com.petal.browser.ui.components.PetalOmniboxPage
import com.petal.browser.ui.components.PetalVoiceSearchSheet
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.unit.PetalAndroidUtils
import com.petal.browser.unit.PetalBrowserUtils
import com.petal.browser.unit.PetalHighRefreshRateManager
import com.petal.browser.widget.PetalSearchWidgetProvider
import java.util.function.Consumer

/**
 * Lightweight standalone Quick Search Activity (Chrome/Brave `SearchActivity` parity).
 *
 * Launched by home-screen search widgets, voice search buttons, private search buttons,
 * Quick Settings search/voice tiles, launcher shortcuts, and system [Intent.ACTION_WEB_SEARCH].
 * Keeps `MainActivity` undisturbed in the background while the user searches; pressing Back
 * returns straight to the launcher/caller, and submitting hands off cleanly to a new tab
 * in [MainActivity].
 */
class PetalQuickSearchActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INCOGNITO = "com.petal.browser.extra.QUICK_SEARCH_INCOGNITO"
        const val EXTRA_START_VOICE = "com.petal.browser.extra.QUICK_SEARCH_START_VOICE"
        const val EXTRA_INITIAL_QUERY = "com.petal.browser.extra.QUICK_SEARCH_INITIAL_QUERY"
        const val EXTRA_OPEN_IN_NEW_TAB = "com.petal.browser.extra.OPEN_IN_NEW_TAB"
    }

    private var isIncognitoState by mutableStateOf(false)
    private var startVoiceState by mutableStateOf(false)
    private var initialQueryState by mutableStateOf("")
    private var launchGeneration by mutableIntStateOf(0)
    private var isUnlockedState by mutableStateOf(false)
    private var unlockedAppLockThisSession = false
    private var lockPromptActive = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(PetalAndroidUtils.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        PetalAndroidUtils.initTheme(this)
        PetalHighRefreshRateManager.applyHighRefreshRate(this)

        applyIntentParams(intent)

        setContent {
            PetalExpressiveTheme {
                if (isUnlockedState) {
                    key(launchGeneration) {
                        if (startVoiceState) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.45f))
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) { finish() }
                            ) {
                                PetalVoiceSearchSheet(
                                    onResult = { spoken ->
                                        val trimmed = spoken.trim()
                                        if (trimmed.isNotBlank()) {
                                            launchQueryInBrowser(trimmed)
                                        } else {
                                            finish()
                                        }
                                    },
                                    onDismiss = { finish() }
                                )
                            }
                        } else {
                            PetalOmniboxPage(
                                handleBackInternally = true,
                                activity = this@PetalQuickSearchActivity,
                                initialQuery = initialQueryState,
                                isIncognito = isIncognitoState,
                                onQuerySubmitted = { query ->
                                    launchQueryInBrowser(query)
                                },
                                onBackPress = { finish() },
                                onCommand = { command ->
                                    handleSlashCommand(command)
                                }
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    )
                }
            }
        }

        evaluateAccessAndUnlock()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntentParams(intent)
        launchGeneration++
        evaluateAccessAndUnlock()
    }

    override fun onStart() {
        super.onStart()
        if (!isUnlockedState && !lockPromptActive) {
            evaluateAccessAndUnlock()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations &&
            !isFinishing &&
            !BiometricLockManager.isPromptActive &&
            PetalAppLockBridge.isAppLockConfiguredAndEnabled(this)
        ) {
            isUnlockedState = false
            unlockedAppLockThisSession = false
        }
    }

    private fun applyIntentParams(intent: Intent?) {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val fromActionIncognito = intent?.action == PetalSearchWidgetProvider.ACTION_OPEN_INCOGNITO
        val fromActionVoice = intent?.action == PetalSearchWidgetProvider.ACTION_OPEN_VOICE
        isIncognitoState = (intent?.getBooleanExtra(EXTRA_INCOGNITO, false) == true) || fromActionIncognito
        startVoiceState = (intent?.getBooleanExtra(EXTRA_START_VOICE, false) == true) || fromActionVoice
        initialQueryState = intent?.getStringExtra(EXTRA_INITIAL_QUERY)
            ?: intent?.getStringExtra(SearchManager.QUERY)
            ?: ""

        if (isIncognitoState || sp.getBoolean("sp_app_lock_enabled", false)) {
            PetalIncognitoSessionManager.enableIncognitoSecurity(this)
        } else {
            PetalIncognitoSessionManager.disableIncognitoSecurity(this)
        }
    }

    private fun evaluateAccessAndUnlock() {
        if (isFinishing || isDestroyed || lockPromptActive) return

        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val appLockRequired = PetalAppLockBridge.isAppLockConfiguredAndEnabled(this) && !unlockedAppLockThisSession
        val incognitoBiometricRequired = isIncognitoState &&
            sp.getBoolean("sp_biometric_incognito_tab_lock", false) &&
            BiometricLockManager.canAuthenticate(this)

        when {
            appLockRequired -> {
                isUnlockedState = false
                lockPromptActive = true
                window.decorView.post {
                    if (isFinishing || isDestroyed) {
                        lockPromptActive = false
                        return@post
                    }
                    PetalAppLockBridge.showLockOverlay(
                        activity = this,
                        onUnlocked = Runnable {
                            lockPromptActive = false
                            unlockedAppLockThisSession = true
                            PetalAppLockBridge.grantTransientHandoffUnlock()
                            if (isIncognitoState) {
                                PetalIncognitoSessionManager.enableIncognitoSecurity(this)
                            }
                            isUnlockedState = true
                        },
                        onCancel = Runnable {
                            lockPromptActive = false
                            finish()
                        }
                    )
                }
            }

            incognitoBiometricRequired -> {
                isUnlockedState = false
                lockPromptActive = true
                BiometricLockManager.authenticate(
                    activity = this,
                    title = getString(R.string.ui_slash_unlock_title),
                    subtitle = getString(R.string.ui_slash_unlock_subtitle),
                    onSuccess = Runnable {
                        lockPromptActive = false
                        PetalIncognitoSessionManager.enableIncognitoSecurity(this)
                        isUnlockedState = true
                    },
                    onError = Consumer {
                        lockPromptActive = false
                        finish()
                    }
                )
            }

            else -> {
                isUnlockedState = true
            }
        }
    }

    private fun launchQueryInBrowser(rawQuery: String) {
        val trimmed = rawQuery.trim()
        if (trimmed.isBlank()) {
            finish()
            return
        }
        val wrapped = PetalBrowserUtils.queryWrapper(this, trimmed)
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val stripTracking = sp.getBoolean("sp_strip_tracking_parameters", true)
        val finalUrl = if (
            stripTracking &&
            (wrapped.startsWith("http://", ignoreCase = true) || wrapped.startsWith("https://", ignoreCase = true))
        ) {
            PetalBrowserUtils.cleanTrackingParams(wrapped) ?: wrapped
        } else {
            wrapped
        }

        if (unlockedAppLockThisSession) {
            PetalAppLockBridge.grantTransientHandoffUnlock()
        }

        val browserIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(finalUrl)
            putExtra(EXTRA_OPEN_IN_NEW_TAB, true)
            putExtra("com.petal.browser.extra.OPEN_PRIVATE_TAB", isIncognitoState)
            putExtra("petal_force_full_browser", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(browserIntent)
        finish()
    }

    private fun handleSlashCommand(command: PetalOmniboxCommand) {
        if (unlockedAppLockThisSession) {
            PetalAppLockBridge.grantTransientHandoffUnlock()
        }
        if (command == PetalOmniboxCommand.SETTINGS) {
            startActivity(
                Intent(this, SettingsActivity::class.java).apply {
                    putExtra(SettingsActivity.EXTRA_SETTINGS_CATEGORY, SettingsCategory.OVERVIEW.name)
                }
            )
            finish()
            return
        }
        val mappedAction = when (command) {
            PetalOmniboxCommand.TABS -> PetalSearchWidgetProvider.ACTION_OPEN_TABS
            PetalOmniboxCommand.NEW_TAB -> PetalSearchWidgetProvider.ACTION_OPEN_NEW_TAB
            PetalOmniboxCommand.NEW_INCOGNITO_TAB -> PetalSearchWidgetProvider.ACTION_OPEN_INCOGNITO
            PetalOmniboxCommand.HISTORY -> PetalSearchWidgetProvider.ACTION_OPEN_HISTORY
            PetalOmniboxCommand.BOOKMARKS -> PetalSearchWidgetProvider.ACTION_OPEN_BOOKMARKS
            PetalOmniboxCommand.DOWNLOADS -> PetalSearchWidgetProvider.ACTION_OPEN_DOWNLOADS
            PetalOmniboxCommand.READER_MODE -> null
            PetalOmniboxCommand.SETTINGS -> null
        }
        if (mappedAction != null) {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    action = mappedAction
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            )
        }
        finish()
    }
}
