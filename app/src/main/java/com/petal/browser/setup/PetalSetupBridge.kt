package com.petal.browser.setup

import android.content.res.Configuration
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.ui.theme.*

object PetalSetupBridge {
    private var setupView: ComposeView? = null
    private var activeActivity: BrowserActivity? = null

    @JvmStatic
    fun createSetupView(activity: BrowserActivity, onFinished: () -> Unit): ComposeView {
        val model = ViewModelProvider(activity)[PetalSetupState::class.java]
        val view = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val sp = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
                var fontName by remember { mutableStateOf(sp.getString("sp_app_font", "PETAL") ?: "PETAL") }
                var styleName by remember { mutableStateOf(sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT") }
                var paletteId by remember { mutableStateOf(sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId) }
                var dynamicColor by remember { mutableStateOf(sp.getBoolean("useDynamicColor", isDynamicColorSupported)) }
                var amoled by remember { mutableStateOf(sp.getBoolean("sp_amoled", false)) }
                var fontWidth by remember { mutableFloatStateOf(sp.getFloat("sp_font_width", 92f)) }
                var fontWeight by remember { mutableIntStateOf(sp.getInt("sp_font_weight", 750)) }
                var fontRoundness by remember { mutableFloatStateOf(sp.getFloat("sp_font_roundness", 100f)) }

                DisposableEffect(sp) {
                    val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                        when (key) {
                            "sp_app_font" -> fontName = sp.getString("sp_app_font", "PETAL") ?: "PETAL"
                            "sp_color_style" -> styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                            "sp_palette_id" -> paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                            "useDynamicColor" -> dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)
                            "sp_amoled" -> amoled = sp.getBoolean("sp_amoled", false)
                            "sp_font_width" -> fontWidth = sp.getFloat("sp_font_width", 92f)
                            "sp_font_weight" -> fontWeight = sp.getInt("sp_font_weight", 750)
                            "sp_font_roundness" -> fontRoundness = sp.getFloat("sp_font_roundness", 100f)
                        }
                    }
                    sp.registerOnSharedPreferenceChangeListener(listener)
                    onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
                }

                val appFont = remember(fontName) { AppFont.fromName(fontName) }
                val colorStyle = remember(styleName) {
                    runCatching { ColorStyle.valueOf(styleName) }.getOrDefault(ColorStyle.TONAL_SPOT)
                }
                PetalExpressiveTheme(
                    darkTheme = isSystemInDarkTheme(), dynamicColor = dynamicColor,
                    useAmoled = amoled, appFont = appFont, fontWidth = fontWidth,
                    fontWeight = fontWeight, fontRoundness = fontRoundness,
                    colorStyle = colorStyle, paletteId = paletteId
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.activity.compose.LocalActivityResultRegistryOwner provides activity
                    ) {
                        PetalSetupScreen(activity, model, sp, onFinished)
                    }
                }
            }
        }
        return view
    }

    @JvmStatic
    fun showSetup(activity: BrowserActivity) {
        val sp = PreferenceManager.getDefaultSharedPreferences(activity)
        if (sp.getBoolean("sp_setup_v2_done", false) || activeActivity === activity) return
        if (activeActivity != null && !activeActivity!!.isChangingConfigurations) return
        activeActivity = activity
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (activeActivity === activity) {
                    activeActivity = null
                    setupView = null
                }
            }
        })
        showStage(activity)
    }

    private fun showStage(activity: BrowserActivity) {
        activity.runOnUiThread {
            try {
                activity.captureBrowserMainPreview()
                activity.isOverlayScreenShowing = true
                activity.clearContentFrameKeepingTabs()
                activity.findViewById<View>(com.petal.browser.R.id.appBar_buttons)?.visibility = View.GONE
                activity.findViewById<View>(com.petal.browser.R.id.bottom_nav_container)?.visibility = View.GONE
                activity.findViewById<View>(com.petal.browser.R.id.bottom_nav_compose)?.visibility = View.GONE
                activity.findViewById<View>(com.petal.browser.R.id.compose_address_bar)?.visibility = View.GONE
                activity.findViewById<View>(com.petal.browser.R.id.fab_bubble)?.visibility = View.GONE
                activity.hideRefreshAndProgressOverlays()
                val view = createSetupView(activity) { finishSetup(activity) }
                setupView = view
                activity.pendingOverlayBackAction = Runnable {
                    val model = ViewModelProvider(activity)[PetalSetupState::class.java]
                    model.previous()
                    showStage(activity)
                }
                activity.presentComposeScreen(view)
            } catch (e: Exception) {
                android.util.Log.e("PetalSetup", "Failed to present setup stage", e)
                activity.isOverlayScreenShowing = false
                activity.pendingOverlayBackAction = null
                activity.showAlbum(activity.currentAlbumController)
                activity.updatePersistentBottomNav()
                activeActivity = null
                setupView = null
            }
        }
    }

    private fun finishSetup(activity: BrowserActivity) {
        val sp = PreferenceManager.getDefaultSharedPreferences(activity)
        val language = ViewModelProvider(activity)[PetalSetupState::class.java].language.value
        com.petal.browser.unit.HelperUnit.setAppLanguage(activity, language)
        activity.pendingOverlayBackAction = null
        activity.isOverlayScreenShowing = false
        activity.showAlbum(activity.currentAlbumController)
        activity.updatePersistentBottomNav()
        sp.edit().putBoolean("sp_setup_v2_done", true).apply()
        activeActivity = null
        setupView = null
    }

    internal fun refreshStage(activity: BrowserActivity) = showStage(activity)

    internal fun localizedContext(context: android.content.Context, language: String): android.content.Context {
        val config = Configuration(context.resources.configuration)
        val locale = java.util.Locale.forLanguageTag(language)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}
