// SPDX-License-Identifier: GPL-3.0-only
package com.petal.browser.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.petal.browser.animation.predictiveback.PredictiveBackAnimation
import com.petal.browser.animation.predictiveback.PredictiveBackExitDirection
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.GSFlexPreset
import com.petal.browser.ui.theme.ThemeConfig
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for Petal Browser preferences.
 * Provides typed, observable (StateFlow), and Java-friendly (@JvmStatic) preference access.
 */
object AppPreferences {

    // --- PREFERENCE KEYS ---
    const val KEY_APP_FONT = "sp_app_font"
    const val KEY_GS_FLEX_PRESET = "sp_gs_flex_preset"
    const val KEY_FONT_WIDTH = "sp_font_width"
    const val KEY_FONT_WEIGHT = "sp_font_weight"
    const val KEY_FONT_ROUNDNESS = "sp_font_roundness"
    const val KEY_COLOR_STYLE = "sp_color_style"
    const val KEY_PALETTE_ID = "sp_palette_id"
    const val KEY_THEME_CONFIG = "sp_theme_config"
    const val KEY_AMOLED = "sp_amoled"
    const val KEY_USE_DYNAMIC_COLOR = "useDynamicColor"
    const val KEY_EXPRESSIVE_COLORS = "sp_expressive_colors"

    const val KEY_PRIVATE_DNS_MODE = "sp_private_dns_mode"
    const val KEY_APP_LANGUAGE = "sp_app_language"

    const val KEY_HOME_TYPE = "sp_home_type"
    const val KEY_CUSTOM_HOMEPAGE_URL = "sp_custom_homepage_url"
    const val KEY_BACKGROUND_PLAY = "sp_background_play"
    const val KEY_AUTO_PIP = "sp_auto_pip"
    const val KEY_FORCE_DARK_MODE = "sp_force_dark_mode"

    const val KEY_AD_BLOCK = "sp_ad_block"
    const val KEY_HTTPS_ONLY = "sp_https_only"
    const val KEY_JAVASCRIPT = "sp_javascript"
    const val KEY_BLOCK_POPUPS = "sp_block_popups"
    const val KEY_AUTO_OPEN_APPS = "sp_auto_open_apps"
    const val KEY_CHECK_UPDATE_ON_LAUNCH = "sp_check_update_on_launch"
    const val KEY_TOUCH_HAPTICS = "sp_touch_haptics"
    const val KEY_PREDICTIVE_BACK_ANIM = "sp_predictive_back_anim"
    const val KEY_PREDICTIVE_BACK_EXIT_DIR = "sp_predictive_back_exit_dir"
    const val KEY_ADDRESS_BAR_POSITION = "sp_address_bar_position"
    const val KEY_FONT_SIZE_SCALE = "sp_font_size_scale"
    const val KEY_ZOOM_LEVEL_SCALE = "sp_zoom_level_scale"
    const val KEY_SEARCH_ENGINE = "sp_search_engine"

    const val KEY_SCREEN_ON = "sp_screenOn"
    const val KEY_RESTORE_TABS = "sp_restoreTabs"
    const val KEY_RELOAD_TABS = "sp_reloadTabs"
    const val KEY_RESTORE_ON_RESTART = "sp_restoreOnRestart"
    const val KEY_WELCOME_SHOWN = "sp_welcome_shown"
    const val KEY_SEARCH_ENGINE_CHOSEN = "sp_search_engine_chosen"
    const val KEY_FAVORITE_URL = "favoriteURL"
    const val KEY_PROFILE = "profile"

    private var sharedPreferences: SharedPreferences? = null

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
        when (key) {
            KEY_APP_FONT -> _appFontFlow.value = getAppFont()
            KEY_GS_FLEX_PRESET -> _gsFlexPresetFlow.value = getGsFlexPreset()
            KEY_FONT_WIDTH -> _fontWidthFlow.value = getFontWidth()
            KEY_FONT_WEIGHT -> _fontWeightFlow.value = getFontWeight()
            KEY_FONT_ROUNDNESS -> _fontRoundnessFlow.value = getFontRoundness()
            KEY_COLOR_STYLE -> _colorStyleFlow.value = getColorStyle()
            KEY_PALETTE_ID -> _paletteIdFlow.value = getPaletteId()
            KEY_THEME_CONFIG -> _themeConfigFlow.value = getThemeConfig()
            KEY_AMOLED -> _isAmoledFlow.value = isAmoled()
            KEY_USE_DYNAMIC_COLOR -> _isDynamicColorFlow.value = isDynamicColor()
            KEY_EXPRESSIVE_COLORS -> _isExpressiveColorsFlow.value = isExpressiveColors()
            KEY_PRIVATE_DNS_MODE -> _privateDnsModeFlow.value = getPrivateDnsMode()
            KEY_APP_LANGUAGE -> _appLanguageFlow.value = getAppLanguage()
            KEY_HOME_TYPE -> _homeTypeFlow.value = getHomeType()
            KEY_CUSTOM_HOMEPAGE_URL -> _customHomepageUrlFlow.value = getCustomHomepageUrl()
            KEY_BACKGROUND_PLAY -> _isBackgroundPlayFlow.value = isBackgroundPlay()
            KEY_AUTO_PIP -> _isAutoPipFlow.value = isAutoPip()
            KEY_FORCE_DARK_MODE -> _isForceDarkModeFlow.value = isForceDarkMode()
            KEY_AD_BLOCK -> _isAdBlockFlow.value = isAdBlock()
            KEY_HTTPS_ONLY -> _isHttpsOnlyFlow.value = isHttpsOnly()
            KEY_JAVASCRIPT -> _isJavaScriptFlow.value = isJavaScript()
            KEY_BLOCK_POPUPS -> _isBlockPopupsFlow.value = isBlockPopups()
            KEY_AUTO_OPEN_APPS -> _isAutoOpenAppsFlow.value = isAutoOpenApps()
            KEY_CHECK_UPDATE_ON_LAUNCH -> _isCheckUpdateOnLaunchFlow.value = isCheckUpdateOnLaunch()
            KEY_TOUCH_HAPTICS -> _isTouchHapticsFlow.value = isTouchHaptics()
            KEY_PREDICTIVE_BACK_ANIM -> _predictiveBackAnimFlow.value = getPredictiveBackAnim()
            KEY_PREDICTIVE_BACK_EXIT_DIR -> _predictiveBackExitDirFlow.value = getPredictiveBackExitDir()
            KEY_ADDRESS_BAR_POSITION -> _addressBarPositionFlow.value = getAddressBarPosition()
            KEY_FONT_SIZE_SCALE -> _fontSizeScaleFlow.value = getFontSizeScale()
            KEY_ZOOM_LEVEL_SCALE -> _zoomLevelScaleFlow.value = getZoomLevelScale()
            KEY_SEARCH_ENGINE -> _searchEngineFlow.value = getSearchEngine()
        }
    }

    @JvmStatic
    @Synchronized
    fun init(context: Context) {
        if (sharedPreferences == null) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            sharedPreferences = prefs
            prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
            updateAllFlows()
        }
    }

    private fun getPrefs(context: Context? = null): SharedPreferences {
        sharedPreferences?.let { return it }
        if (context != null) {
            init(context)
            return sharedPreferences!!
        }
        throw IllegalStateException("AppPreferences must be initialized with init(context) before access.")
    }

    private fun updateAllFlows() {
        _appFontFlow.value = getAppFont()
        _gsFlexPresetFlow.value = getGsFlexPreset()
        _fontWidthFlow.value = getFontWidth()
        _fontWeightFlow.value = getFontWeight()
        _fontRoundnessFlow.value = getFontRoundness()
        _colorStyleFlow.value = getColorStyle()
        _paletteIdFlow.value = getPaletteId()
        _themeConfigFlow.value = getThemeConfig()
        _isAmoledFlow.value = isAmoled()
        _isDynamicColorFlow.value = isDynamicColor()
        _isExpressiveColorsFlow.value = isExpressiveColors()
        _privateDnsModeFlow.value = getPrivateDnsMode()
        _appLanguageFlow.value = getAppLanguage()
        _homeTypeFlow.value = getHomeType()
        _customHomepageUrlFlow.value = getCustomHomepageUrl()
        _isBackgroundPlayFlow.value = isBackgroundPlay()
        _isAutoPipFlow.value = isAutoPip()
        _isForceDarkModeFlow.value = isForceDarkMode()
        _isAdBlockFlow.value = isAdBlock()
        _isHttpsOnlyFlow.value = isHttpsOnly()
        _isJavaScriptFlow.value = isJavaScript()
        _isBlockPopupsFlow.value = isBlockPopups()
        _isAutoOpenAppsFlow.value = isAutoOpenApps()
        _isCheckUpdateOnLaunchFlow.value = isCheckUpdateOnLaunch()
        _isTouchHapticsFlow.value = isTouchHaptics()
        _predictiveBackAnimFlow.value = getPredictiveBackAnim()
        _predictiveBackExitDirFlow.value = getPredictiveBackExitDir()
        _addressBarPositionFlow.value = getAddressBarPosition()
        _fontSizeScaleFlow.value = getFontSizeScale()
        _zoomLevelScaleFlow.value = getZoomLevelScale()
        _searchEngineFlow.value = getSearchEngine()
    }

    // --- StateFlow backing fields ---
    private val _appFontFlow = MutableStateFlow(AppFont.GS_FLEX)
    val appFontFlow: StateFlow<AppFont> = _appFontFlow.asStateFlow()

    private val _gsFlexPresetFlow = MutableStateFlow(GSFlexPreset.DEFAULT)
    val gsFlexPresetFlow: StateFlow<GSFlexPreset> = _gsFlexPresetFlow.asStateFlow()

    private val _fontWidthFlow = MutableStateFlow(100f)
    val fontWidthFlow: StateFlow<Float> = _fontWidthFlow.asStateFlow()

    private val _fontWeightFlow = MutableStateFlow(400f)
    val fontWeightFlow: StateFlow<Float> = _fontWeightFlow.asStateFlow()

    private val _fontRoundnessFlow = MutableStateFlow(0f)
    val fontRoundnessFlow: StateFlow<Float> = _fontRoundnessFlow.asStateFlow()

    private val _colorStyleFlow = MutableStateFlow(ColorStyle.TONAL_SPOT)
    val colorStyleFlow: StateFlow<ColorStyle> = _colorStyleFlow.asStateFlow()

    private val _paletteIdFlow = MutableStateFlow(defaultPaletteId)
    val paletteIdFlow: StateFlow<String> = _paletteIdFlow.asStateFlow()

    private val _themeConfigFlow = MutableStateFlow(ThemeConfig.FOLLOW_SYSTEM)
    val themeConfigFlow: StateFlow<ThemeConfig> = _themeConfigFlow.asStateFlow()

    private val _isAmoledFlow = MutableStateFlow(false)
    val isAmoledFlow: StateFlow<Boolean> = _isAmoledFlow.asStateFlow()

    private val _isDynamicColorFlow = MutableStateFlow(isDynamicColorSupported)
    val isDynamicColorFlow: StateFlow<Boolean> = _isDynamicColorFlow.asStateFlow()

    private val _isExpressiveColorsFlow = MutableStateFlow(false)
    val isExpressiveColorsFlow: StateFlow<Boolean> = _isExpressiveColorsFlow.asStateFlow()

    private val _privateDnsModeFlow = MutableStateFlow("OFF")
    val privateDnsModeFlow: StateFlow<String> = _privateDnsModeFlow.asStateFlow()

    private val _appLanguageFlow = MutableStateFlow("system")
    val appLanguageFlow: StateFlow<String> = _appLanguageFlow.asStateFlow()

    private val _homeTypeFlow = MutableStateFlow("0")
    val homeTypeFlow: StateFlow<String> = _homeTypeFlow.asStateFlow()

    private val _customHomepageUrlFlow = MutableStateFlow("https://google.com")
    val customHomepageUrlFlow: StateFlow<String> = _customHomepageUrlFlow.asStateFlow()

    private val _isBackgroundPlayFlow = MutableStateFlow(false)
    val isBackgroundPlayFlow: StateFlow<Boolean> = _isBackgroundPlayFlow.asStateFlow()

    private val _isAutoPipFlow = MutableStateFlow(true)
    val isAutoPipFlow: StateFlow<Boolean> = _isAutoPipFlow.asStateFlow()

    private val _isForceDarkModeFlow = MutableStateFlow(false)
    val isForceDarkModeFlow: StateFlow<Boolean> = _isForceDarkModeFlow.asStateFlow()

    private val _isAdBlockFlow = MutableStateFlow(true)
    val isAdBlockFlow: StateFlow<Boolean> = _isAdBlockFlow.asStateFlow()

    private val _isHttpsOnlyFlow = MutableStateFlow(true)
    val isHttpsOnlyFlow: StateFlow<Boolean> = _isHttpsOnlyFlow.asStateFlow()

    private val _isJavaScriptFlow = MutableStateFlow(true)
    val isJavaScriptFlow: StateFlow<Boolean> = _isJavaScriptFlow.asStateFlow()

    private val _isBlockPopupsFlow = MutableStateFlow(true)
    val isBlockPopupsFlow: StateFlow<Boolean> = _isBlockPopupsFlow.asStateFlow()

    private val _isAutoOpenAppsFlow = MutableStateFlow(true)
    val isAutoOpenAppsFlow: StateFlow<Boolean> = _isAutoOpenAppsFlow.asStateFlow()

    private val _isCheckUpdateOnLaunchFlow = MutableStateFlow(true)
    val isCheckUpdateOnLaunchFlow: StateFlow<Boolean> = _isCheckUpdateOnLaunchFlow.asStateFlow()

    private val _isTouchHapticsFlow = MutableStateFlow(true)
    val isTouchHapticsFlow: StateFlow<Boolean> = _isTouchHapticsFlow.asStateFlow()

    private val _predictiveBackAnimFlow = MutableStateFlow(PredictiveBackAnimation.AOSP)
    val predictiveBackAnimFlow: StateFlow<PredictiveBackAnimation> = _predictiveBackAnimFlow.asStateFlow()

    private val _predictiveBackExitDirFlow = MutableStateFlow(PredictiveBackExitDirection.ALWAYS_RIGHT)
    val predictiveBackExitDirFlow: StateFlow<PredictiveBackExitDirection> = _predictiveBackExitDirFlow.asStateFlow()

    private val _addressBarPositionFlow = MutableStateFlow("TOP")
    val addressBarPositionFlow: StateFlow<String> = _addressBarPositionFlow.asStateFlow()

    private val _fontSizeScaleFlow = MutableStateFlow(1.0f)
    val fontSizeScaleFlow: StateFlow<Float> = _fontSizeScaleFlow.asStateFlow()

    private val _zoomLevelScaleFlow = MutableStateFlow(1.0f)
    val zoomLevelScaleFlow: StateFlow<Float> = _zoomLevelScaleFlow.asStateFlow()

    private val _searchEngineFlow = MutableStateFlow("0")
    val searchEngineFlow: StateFlow<String> = _searchEngineFlow.asStateFlow()

    // --- Typed Getters & Setters ---

    @JvmStatic fun getAppFont(context: Context? = null): AppFont {
        val str = getPrefs(context).getString(KEY_APP_FONT, "GS_FLEX") ?: "GS_FLEX"
        return try { AppFont.valueOf(str) } catch (e: Exception) { AppFont.GS_FLEX }
    }
    @JvmStatic fun setAppFont(font: AppFont, context: Context? = null) {
        getPrefs(context).edit().putString(KEY_APP_FONT, font.name).apply()
    }

    @JvmStatic fun getGsFlexPreset(context: Context? = null): GSFlexPreset {
        val str = getPrefs(context).getString(KEY_GS_FLEX_PRESET, "DEFAULT") ?: "DEFAULT"
        return try { GSFlexPreset.valueOf(str) } catch (e: Exception) { GSFlexPreset.DEFAULT }
    }
    @JvmStatic fun setGsFlexPreset(preset: GSFlexPreset, context: Context? = null) {
        getPrefs(context).edit().putString(KEY_GS_FLEX_PRESET, preset.name).apply()
    }

    @JvmStatic fun getFontWidth(context: Context? = null): Float = getPrefs(context).getFloat(KEY_FONT_WIDTH, 100f)
    @JvmStatic fun setFontWidth(width: Float, context: Context? = null) {
        getPrefs(context).edit().putFloat(KEY_FONT_WIDTH, width).apply()
    }

    @JvmStatic fun getFontWeight(context: Context? = null): Float = getPrefs(context).getInt(KEY_FONT_WEIGHT, 400).toFloat()
    @JvmStatic fun setFontWeight(weight: Float, context: Context? = null) {
        getPrefs(context).edit().putInt(KEY_FONT_WEIGHT, weight.toInt()).apply()
    }

    @JvmStatic fun getFontRoundness(context: Context? = null): Float = getPrefs(context).getFloat(KEY_FONT_ROUNDNESS, 0f)
    @JvmStatic fun setFontRoundness(roundness: Float, context: Context? = null) {
        getPrefs(context).edit().putFloat(KEY_FONT_ROUNDNESS, roundness).apply()
    }

    @JvmStatic fun getColorStyle(context: Context? = null): ColorStyle {
        val str = getPrefs(context).getString(KEY_COLOR_STYLE, "TONAL_SPOT") ?: "TONAL_SPOT"
        return try { ColorStyle.valueOf(str) } catch (e: Exception) { ColorStyle.TONAL_SPOT }
    }
    @JvmStatic fun setColorStyle(style: ColorStyle, context: Context? = null) {
        getPrefs(context).edit().putString(KEY_COLOR_STYLE, style.name).apply()
    }

    @JvmStatic fun getPaletteId(context: Context? = null): String = getPrefs(context).getString(KEY_PALETTE_ID, defaultPaletteId) ?: defaultPaletteId
    @JvmStatic fun setPaletteId(id: String, context: Context? = null) {
        getPrefs(context).edit().putString(KEY_PALETTE_ID, id).apply()
    }

    @JvmStatic fun getThemeConfig(context: Context? = null): ThemeConfig {
        val str = getPrefs(context).getString(KEY_THEME_CONFIG, "FOLLOW_SYSTEM") ?: "FOLLOW_SYSTEM"
        return try { ThemeConfig.valueOf(str) } catch (e: Exception) { ThemeConfig.FOLLOW_SYSTEM }
    }
    @JvmStatic fun setThemeConfig(config: ThemeConfig, context: Context? = null) {
        getPrefs(context).edit().putString(KEY_THEME_CONFIG, config.name).apply()
    }

    @JvmStatic fun isAmoled(context: Context? = null): Boolean = getPrefs(context).getBoolean(KEY_AMOLED, false)
    @JvmStatic fun setAmoled(enabled: Boolean, context: Context? = null) {
        getPrefs(context).edit().putBoolean(KEY_AMOLED, enabled).apply()
    }

    @JvmStatic fun isDynamicColor(context: Context? = null): Boolean = getPrefs(context).getBoolean(KEY_USE_DYNAMIC_COLOR, isDynamicColorSupported)
    @JvmStatic fun setDynamicColor(enabled: Boolean, context: Context? = null) {
        getPrefs(context).edit().putBoolean(KEY_USE_DYNAMIC_COLOR, enabled).apply()
    }

    @JvmStatic fun isExpressiveColors(context: Context? = null): Boolean = getPrefs(context).getBoolean(KEY_EXPRESSIVE_COLORS, false)
    @JvmStatic fun setExpressiveColors(enabled: Boolean, context: Context? = null) {
        getPrefs(context).edit().putBoolean(KEY_EXPRESSIVE_COLORS, enabled).apply()
    }

    @JvmStatic fun getPrivateDnsMode(context: Context? = null): String = getPrefs(context).getString(KEY_PRIVATE_DNS_MODE, "OFF") ?: "OFF"
    @JvmStatic fun setPrivateDnsMode(mode: String, context: Context? = null) {
        getPrefs(context).edit().putString(KEY_PRIVATE_DNS_MODE, mode).apply()
    }

    @JvmStatic fun getAppLanguage(context: Context? = null): String = getPrefs(context).getString(KEY_APP_LANGUAGE, "system") ?: "system"
    @JvmStatic fun setAppLanguage(lang: String, context: Context? = null) {
        getPrefs(context).edit().putString(KEY_APP_LANGUAGE, lang).apply()
    }

    @JvmStatic fun getHomeType(context: Context? = null): String = getPrefs(context).getString(KEY_HOME_TYPE, "0") ?: "0"
    @JvmStatic fun setHomeType(type: String, context: Context? = null) {
        getPrefs(context).edit().putString(KEY_HOME_TYPE, type).apply()
    }

    @JvmStatic fun getCustomHomepageUrl(context: Context? = null): String = getPrefs(context).getString(KEY_CUSTOM_HOMEPAGE_URL, "https://google.com") ?: "https://google.com"
    @JvmStatic fun setCustomHomepageUrl(url: String, context: Context? = null) {
        getPrefs(context).edit().putString(KEY_CUSTOM_HOMEPAGE_URL, url).apply()
    }

    @JvmStatic fun isBackgroundPlay(context: Context? = null): Boolean = getPrefs(context).getBoolean(KEY_BACKGROUND_PLAY, false)
    @JvmStatic fun setBackgroundPlay(enabled: Boolean, context: Context? = null) {
        getPrefs(context).edit().putBoolean(KEY_BACKGROUND_PLAY, enabled).apply()
    }

    @JvmStatic fun isAutoPip(context: Context? = null): Boolean = getPrefs(context).getBoolean(KEY_AUTO_PIP, true)
    @JvmStatic fun setAutoPip(enabled: Boolean, context: Context? = null) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_PIP, enabled).apply()
    }

    @JvmStatic fun isForceDarkMode(context: Context? = null): Boolean = getPrefs(context).getBoolean(KEY_FORCE_DARK_MODE, false)
    @JvmStatic fun setForceDarkMode(enabled: Boolean, context: Context? = null) {
        getPrefs(context).edit().putBoolean(KEY_FORCE_DARK_MODE, enabled).apply()
    }

    @JvmStatic fun isAdBlock(context: Context? = null): Boolean = getPrefs(context).getBoolean(KEY_AD_BLOCK, true)
    @JvmStatic fun setAdBlock(enabled: Boolean, context: Context? = null) {
        getPrefs(context).edit().putBoolean(KEY_AD_BLOCK, enabled).apply()
    }

    @JvmStatic fun isHttpsOnly(context: Context? = null): Boolean = getPrefs(context).getBoolean(KEY_HTTPS_ONLY, true)
    @JvmStatic fun setHttpsOnly(enabled: Boolean, context: Context? = null) {
        getPrefs(context).edit().putBoolean(KEY_HTTPS_ONLY, enabled).apply()
    }

    @JvmStatic fun isJavaScript(context: Context? = null): Boolean = getPrefs(context).getBoolean(KEY_JAVASCRIPT, true)
    @JvmStatic fun setJavaScript(enabled: Boolean, context: Context? = null) {
        getPrefs(context).edit().putBoolean(KEY_JAVASCRIPT, enabled).apply()
    }

    @JvmStatic fun isBlockPopups(context: Context? = null): Boolean = getPrefs(context).getBoolean(KEY_BLOCK_POPUPS, true)
    @JvmStatic fun setBlockPopups(enabled: Boolean, context: Context? = null) {
        getPrefs(context).edit().putBoolean(KEY_BLOCK_POPUPS, enabled).apply()
    }

    @JvmStatic fun isAutoOpenApps(context: Context? = null): Boolean = getPrefs(context).getBoolean(KEY_AUTO_OPEN_APPS, true)
    @JvmStatic fun setAutoOpenApps(enabled: Boolean, context: Context? = null) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_OPEN_APPS, enabled).apply()
    }

    @JvmStatic fun isCheckUpdateOnLaunch(context: Context? = null): Boolean = getPrefs(context).getBoolean(KEY_CHECK_UPDATE_ON_LAUNCH, true)
    @JvmStatic fun setCheckUpdateOnLaunch(enabled: Boolean, context: Context? = null) {
        getPrefs(context).edit().putBoolean(KEY_CHECK_UPDATE_ON_LAUNCH, enabled).apply()
    }

    @JvmStatic fun isTouchHaptics(context: Context? = null): Boolean = getPrefs(context).getBoolean(KEY_TOUCH_HAPTICS, true)
    @JvmStatic fun setTouchHaptics(enabled: Boolean, context: Context? = null) {
        getPrefs(context).edit().putBoolean(KEY_TOUCH_HAPTICS, enabled).apply()
    }

    @JvmStatic fun getPredictiveBackAnim(context: Context? = null): PredictiveBackAnimation {
        val str = getPrefs(context).getString(KEY_PREDICTIVE_BACK_ANIM, PredictiveBackAnimation.AOSP.value) ?: PredictiveBackAnimation.AOSP.value
        return PredictiveBackAnimation.fromValueOrDefault(str)
    }
    @JvmStatic fun setPredictiveBackAnim(anim: PredictiveBackAnimation, context: Context? = null) {
        getPrefs(context).edit().putString(KEY_PREDICTIVE_BACK_ANIM, anim.value).apply()
    }

    @JvmStatic fun getPredictiveBackExitDir(context: Context? = null): PredictiveBackExitDirection {
        val str = getPrefs(context).getString(KEY_PREDICTIVE_BACK_EXIT_DIR, PredictiveBackExitDirection.ALWAYS_RIGHT.value) ?: PredictiveBackExitDirection.ALWAYS_RIGHT.value
        return PredictiveBackExitDirection.fromValueOrDefault(str)
    }
    @JvmStatic fun setPredictiveBackExitDir(dir: PredictiveBackExitDirection, context: Context? = null) {
        getPrefs(context).edit().putString(KEY_PREDICTIVE_BACK_EXIT_DIR, dir.value).apply()
    }

    @JvmStatic fun getAddressBarPosition(context: Context? = null): String = getPrefs(context).getString(KEY_ADDRESS_BAR_POSITION, "TOP") ?: "TOP"
    @JvmStatic fun setAddressBarPosition(position: String, context: Context? = null) {
        getPrefs(context).edit().putString(KEY_ADDRESS_BAR_POSITION, position).apply()
    }

    @JvmStatic fun getFontSizeScale(context: Context? = null): Float = getPrefs(context).getFloat(KEY_FONT_SIZE_SCALE, 1.0f)
    @JvmStatic fun setFontSizeScale(scale: Float, context: Context? = null) {
        getPrefs(context).edit().putFloat(KEY_FONT_SIZE_SCALE, scale).apply()
    }

    @JvmStatic fun getZoomLevelScale(context: Context? = null): Float = getPrefs(context).getFloat(KEY_ZOOM_LEVEL_SCALE, 1.0f)
    @JvmStatic fun setZoomLevelScale(scale: Float, context: Context? = null) {
        getPrefs(context).edit().putFloat(KEY_ZOOM_LEVEL_SCALE, scale).apply()
    }

    @JvmStatic fun getSearchEngine(context: Context? = null): String = getPrefs(context).getString(KEY_SEARCH_ENGINE, "0") ?: "0"
    @JvmStatic fun setSearchEngine(engine: String, context: Context? = null) {
        getPrefs(context).edit().putString(KEY_SEARCH_ENGINE, engine).apply()
    }

    @JvmStatic fun isScreenOn(context: Context? = null): Boolean = getPrefs(context).getBoolean(KEY_SCREEN_ON, false)
    @JvmStatic fun setScreenOn(enabled: Boolean, context: Context? = null) {
        getPrefs(context).edit().putBoolean(KEY_SCREEN_ON, enabled).apply()
    }

    @JvmStatic fun isRestoreTabs(context: Context? = null): Boolean = getPrefs(context).getBoolean(KEY_RESTORE_TABS, false)
    @JvmStatic fun isReloadTabs(context: Context? = null): Boolean = getPrefs(context).getBoolean(KEY_RELOAD_TABS, false)
    @JvmStatic fun isRestoreOnRestart(context: Context? = null): Boolean = getPrefs(context).getBoolean(KEY_RESTORE_ON_RESTART, false)

    @JvmStatic fun isWelcomeShown(context: Context? = null): Boolean = getPrefs(context).getBoolean(KEY_WELCOME_SHOWN, false)
    @JvmStatic fun setWelcomeShown(shown: Boolean, context: Context? = null) {
        getPrefs(context).edit().putBoolean(KEY_WELCOME_SHOWN, shown).apply()
    }

    @JvmStatic fun isSearchEngineChosen(context: Context? = null): Boolean = getPrefs(context).getBoolean(KEY_SEARCH_ENGINE_CHOSEN, false)
    @JvmStatic fun setSearchEngineChosen(chosen: Boolean, context: Context? = null) {
        getPrefs(context).edit().putBoolean(KEY_SEARCH_ENGINE_CHOSEN, chosen).apply()
    }

    @JvmStatic fun getFavoriteUrl(context: Context? = null): String = getPrefs(context).getString(KEY_FAVORITE_URL, "about:blank") ?: "about:blank"
    @JvmStatic fun getProfile(context: Context? = null): String = getPrefs(context).getString(KEY_PROFILE, "profileStandard") ?: "profileStandard"
    @JvmStatic fun setProfile(profile: String, context: Context? = null) {
        getPrefs(context).edit().putString(KEY_PROFILE, profile).apply()
    }
}
