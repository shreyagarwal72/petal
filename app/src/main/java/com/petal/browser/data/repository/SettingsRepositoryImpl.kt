package com.petal.browser.data.repository

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.petal.browser.predictive.PetalPredictiveJunction
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.GSFlexPreset
import com.petal.browser.ui.theme.ThemeConfig
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val application: Application
) : SettingsRepository {

    private val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)

    private fun <T> preferenceFlow(key: String, getter: () -> T): Flow<T> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) {
                trySend(getter())
            }
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }.onStart { emit(getter()) }.distinctUntilChanged()

    // ── Appearance & Theming ──────────────────────────────────────────────────
    override val appFont: Flow<AppFont> = preferenceFlow("sp_app_font") {
        AppFont.fromName(sp.getString("sp_app_font", "PETAL"))
    }

    override val fontWidth: Flow<Float> = preferenceFlow("sp_font_width") {
        sp.getFloat("sp_font_width", 92f)
    }

    override val fontWeight: Flow<Float> = preferenceFlow("sp_font_weight") {
        sp.getInt("sp_font_weight", 750).toFloat()
    }

    override val fontRoundness: Flow<Float> = preferenceFlow("sp_font_roundness") {
        sp.getFloat("sp_font_roundness", 100f)
    }

    override val gsFlexPreset: Flow<GSFlexPreset> = preferenceFlow("sp_gs_flex_preset") {
        try {
            GSFlexPreset.valueOf(sp.getString("sp_gs_flex_preset", "PETAL") ?: "PETAL")
        } catch (e: Exception) {
            GSFlexPreset.PETAL
        }
    }

    override val colorStyle: Flow<ColorStyle> = preferenceFlow("sp_color_style") {
        try {
            ColorStyle.valueOf(sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT")
        } catch (e: Exception) {
            ColorStyle.TONAL_SPOT
        }
    }

    override val paletteId: Flow<String> = preferenceFlow("sp_palette_id") {
        sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
    }

    override val dynamicColor: Flow<Boolean> = preferenceFlow("useDynamicColor") {
        sp.getBoolean("useDynamicColor", isDynamicColorSupported)
    }

    override val amoledMode: Flow<Boolean> = preferenceFlow("sp_amoled") {
        sp.getBoolean("sp_amoled", false)
    }

    override val themeConfig: Flow<ThemeConfig> = preferenceFlow("sp_theme_config") {
        try {
            ThemeConfig.valueOf(sp.getString("sp_theme_config", "FOLLOW_SYSTEM") ?: "FOLLOW_SYSTEM")
        } catch (e: Exception) {
            ThemeConfig.FOLLOW_SYSTEM
        }
    }

    override val floatingTabBar: Flow<Boolean> = preferenceFlow("sp_floating_tab_bar") {
        sp.getBoolean("sp_floating_tab_bar", true)
    }

    override val expressiveColors: Flow<Boolean> = preferenceFlow("sp_expressive_colors") {
        sp.getBoolean("sp_expressive_colors", false)
    }

    override val expressiveBgShapes: Flow<Boolean> = preferenceFlow("sp_expressive_bg_shapes") {
        sp.getBoolean("sp_expressive_bg_shapes", true)
    }

    override val bgShapeChangeMode: Flow<String> = preferenceFlow("sp_bg_shape_change_mode") {
        sp.getString("sp_bg_shape_change_mode", "ALWAYS") ?: "ALWAYS"
    }

    override val bgShapeRotationMin: Flow<Int> = preferenceFlow("sp_bg_shape_rotation_min") {
        sp.getInt("sp_bg_shape_rotation_min", 5)
    }

    override val highRefreshRate: Flow<Boolean> = preferenceFlow("sp_high_refresh_rate") {
        sp.getBoolean("sp_high_refresh_rate", true)
    }

    override val customFontName: Flow<String> = preferenceFlow("sp_custom_font_name") {
        sp.getString("sp_custom_font_name", "No font file selected") ?: "No font file selected"
    }

    // ── Search & Homepage ─────────────────────────────────────────────────────
    override val searchEngineIndex: Flow<String> = preferenceFlow("sp_search_engine") {
        sp.getString("sp_search_engine", "0") ?: "0"
    }

    override val homepageType: Flow<String> = preferenceFlow("sp_home_type") {
        sp.getString("sp_home_type", "0") ?: "0"
    }

    override val customHomepageUrl: Flow<String> = preferenceFlow("sp_custom_homepage_url") {
        sp.getString("sp_custom_homepage_url", "https://google.com") ?: "https://google.com"
    }

    override val backgroundPlay: Flow<Boolean> = preferenceFlow("sp_background_play") {
        sp.getBoolean("sp_background_play", false)
    }

    override val autoPip: Flow<Boolean> = preferenceFlow("sp_auto_pip") {
        sp.getBoolean("sp_auto_pip", true)
    }

    override val forceDarkMode: Flow<Boolean> = preferenceFlow("sp_force_dark_mode") {
        sp.getBoolean("sp_force_dark_mode", false)
    }

    override val enableLiveSuggestions: Flow<Boolean> = preferenceFlow("sp_enable_live_suggestions") {
        sp.getBoolean("sp_enable_live_suggestions", true)
    }

    // ── Privacy & Security ────────────────────────────────────────────────────
    override val adBlockEnabled: Flow<Boolean> = preferenceFlow("sp_ad_block") {
        sp.getBoolean("sp_ad_block", true)
    }

    override val blockThirdPartyCookies: Flow<Boolean> = preferenceFlow("sp_block_third_party_cookies") {
        sp.getBoolean("sp_block_third_party_cookies", false)
    }

    override val fingerprintProtection: Flow<Boolean> = preferenceFlow("sp_fingerprint_protection") {
        sp.getBoolean("sp_fingerprint_protection", true)
    }

    override val webrtcProtection: Flow<Boolean> = preferenceFlow("sp_webrtc_protection") {
        sp.getBoolean("sp_webrtc_protection", true)
    }

    override val dntGpc: Flow<Boolean> = preferenceFlow("sp_dnt_gpc") {
        sp.getBoolean("sp_dnt_gpc", true)
    }

    override val trimReferrers: Flow<Boolean> = preferenceFlow("sp_trim_referrers") {
        sp.getBoolean("sp_trim_referrers", true)
    }

    override val webauthnEnabled: Flow<Boolean> = preferenceFlow("sp_webauthn_enabled") {
        sp.getBoolean("sp_webauthn_enabled", true)
    }

    override val httpsOnly: Flow<Boolean> = preferenceFlow("sp_https_only") {
        sp.getBoolean("sp_https_only", true)
    }

    override val javaScriptEnabled: Flow<Boolean> = preferenceFlow("sp_javascript") {
        sp.getBoolean("sp_javascript", true)
    }

    override val blockPopups: Flow<Boolean> = preferenceFlow("sp_block_popups") {
        sp.getBoolean("sp_block_popups", true)
    }

    override val privateDnsMode: Flow<String> = preferenceFlow("sp_private_dns_mode") {
        sp.getString("sp_private_dns_mode", "OFF") ?: "OFF"
    }

    // ── Accessibility & Display ───────────────────────────────────────────────
    override val touchHaptics: Flow<Boolean> = preferenceFlow("sp_touch_haptics") {
        sp.getBoolean("sp_touch_haptics", true)
    }

    override val predictiveBack: Flow<Boolean> = preferenceFlow(PetalPredictiveJunction.KEY_PREDICTIVE_BACK_ENABLED) {
        sp.getBoolean(PetalPredictiveJunction.KEY_PREDICTIVE_BACK_ENABLED, true)
    }

    override val depthBlur: Flow<Boolean> = preferenceFlow(PetalPredictiveJunction.KEY_DEPTH_BLUR_ENABLED) {
        sp.getBoolean(PetalPredictiveJunction.KEY_DEPTH_BLUR_ENABLED, true)
    }

    override val fontSizeScale: Flow<Float> = preferenceFlow("sp_font_size_scale") {
        sp.getFloat("sp_font_size_scale", 1.0f)
    }

    override val zoomLevelScale: Flow<Float> = preferenceFlow("sp_zoom_level_scale") {
        sp.getFloat("sp_zoom_level_scale", 1.0f)
    }

    override val forceZoom: Flow<Boolean> = preferenceFlow("sp_force_enable_zoom") {
        sp.getBoolean("sp_force_enable_zoom", true)
    }

    override val readerModeDetection: Flow<Boolean> = preferenceFlow("sp_reader_mode_detection") {
        sp.getBoolean("sp_reader_mode_detection", true)
    }

    override val caretBrowsing: Flow<Boolean> = preferenceFlow("sp_caret_browsing") {
        sp.getBoolean("sp_caret_browsing", false)
    }

    override val touchpadSwipeNav: Flow<Boolean> = preferenceFlow("sp_touchpad_swipe_nav") {
        sp.getBoolean("sp_touchpad_swipe_nav", true)
    }

    override val addressBarSwipeTabs: Flow<Boolean> = preferenceFlow("sp_address_bar_swipe_tabs") {
        sp.getBoolean("sp_address_bar_swipe_tabs", true)
    }

    override val addressBarQuickActions: Flow<Boolean> = preferenceFlow("sp_address_bar_quick_actions") {
        sp.getBoolean("sp_address_bar_quick_actions", true)
    }

    // ── Experimental & Miscellaneous ──────────────────────────────────────────
    override val appLanguage: Flow<String> = preferenceFlow("sp_app_language") {
        sp.getString("sp_app_language", "system") ?: "system"
    }

    override val addressBarPosition: Flow<String> = preferenceFlow("sp_address_bar_position") {
        sp.getString("sp_address_bar_position", "TOP") ?: "TOP"
    }

    override val appLockEnabled: Flow<Boolean> = preferenceFlow("sp_app_lock_enabled") {
        sp.getBoolean("sp_app_lock_enabled", false)
    }

    override val appLockPasscode: Flow<String> = preferenceFlow("sp_app_lock_passcode") {
        sp.getString("sp_app_lock_passcode", "") ?: ""
    }

    override val doubleBackExit: Flow<Boolean> = preferenceFlow("sp_double_back_exit") {
        sp.getBoolean("sp_double_back_exit", true)
    }

    override val autoOpenApps: Flow<Boolean> = preferenceFlow("sp_auto_open_apps") {
        sp.getBoolean("sp_auto_open_apps", false)
    }

    override val checkUpdateOnLaunch: Flow<Boolean> = preferenceFlow("sp_check_update_on_launch") {
        sp.getBoolean("sp_check_update_on_launch", true)
    }

    override val torrentEngineMode: Flow<String> = preferenceFlow("sp_torrent_engine") {
        sp.getString("sp_torrent_engine", "1DM") ?: "1DM"
    }

    override val downloadManagerMode: Flow<String> = preferenceFlow(com.petal.browser.unit.ExternalDownloadManagerHelper.PREF_DOWNLOAD_MANAGER_MODE) {
        sp.getString(com.petal.browser.unit.ExternalDownloadManagerHelper.PREF_DOWNLOAD_MANAGER_MODE, com.petal.browser.unit.ExternalDownloadManagerHelper.MODE_IN_APP) ?: com.petal.browser.unit.ExternalDownloadManagerHelper.MODE_IN_APP
    }

    // ── Setters ───────────────────────────────────────────────────────────────
    override suspend fun setAppFont(font: AppFont) {
        sp.edit().putString("sp_app_font", font.name).apply()
    }

    override suspend fun setFontWidth(width: Float) {
        sp.edit().putFloat("sp_font_width", width).apply()
    }

    override suspend fun setFontWeight(weight: Float) {
        sp.edit().putInt("sp_font_weight", weight.toInt()).apply()
    }

    override suspend fun setFontRoundness(roundness: Float) {
        sp.edit().putFloat("sp_font_roundness", roundness).apply()
    }

    override suspend fun setGsFlexPreset(preset: GSFlexPreset) {
        sp.edit().putString("sp_gs_flex_preset", preset.name).apply()
    }

    override suspend fun setColorStyle(style: ColorStyle) {
        sp.edit().putString("sp_color_style", style.name).apply()
    }

    override suspend fun setPaletteId(id: String) {
        sp.edit().putString("sp_palette_id", id).apply()
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        sp.edit().putBoolean("useDynamicColor", enabled).apply()
    }

    override suspend fun setAmoledMode(enabled: Boolean) {
        sp.edit().putBoolean("sp_amoled", enabled).apply()
    }

    override suspend fun setThemeConfig(config: ThemeConfig) {
        sp.edit().putString("sp_theme_config", config.name).apply()
    }

    override suspend fun setFloatingTabBar(enabled: Boolean) {
        sp.edit().putBoolean("sp_floating_tab_bar", enabled).apply()
    }

    override suspend fun setExpressiveColors(enabled: Boolean) {
        sp.edit().putBoolean("sp_expressive_colors", enabled).apply()
    }

    override suspend fun setExpressiveBgShapes(enabled: Boolean) {
        sp.edit().putBoolean("sp_expressive_bg_shapes", enabled).apply()
    }

    override suspend fun setBgShapeChangeMode(mode: String) {
        sp.edit().putString("sp_bg_shape_change_mode", mode).apply()
    }

    override suspend fun setBgShapeRotationMin(minutes: Int) {
        sp.edit().putInt("sp_bg_shape_rotation_min", minutes).apply()
    }

    override suspend fun setHighRefreshRate(enabled: Boolean) {
        sp.edit().putBoolean("sp_high_refresh_rate", enabled).apply()
    }

    override suspend fun setCustomFontName(name: String) {
        sp.edit().putString("sp_custom_font_name", name).apply()
    }

    override suspend fun setSearchEngineIndex(index: String) {
        sp.edit().putString("sp_search_engine", index).apply()
    }

    override suspend fun setHomepageType(type: String) {
        sp.edit().putString("sp_home_type", type).apply()
    }

    override suspend fun setCustomHomepageUrl(url: String) {
        sp.edit().putString("sp_custom_homepage_url", url).apply()
    }

    override suspend fun setBackgroundPlay(enabled: Boolean) {
        sp.edit().putBoolean("sp_background_play", enabled).apply()
    }

    override suspend fun setAutoPip(enabled: Boolean) {
        sp.edit().putBoolean("sp_auto_pip", enabled).apply()
    }

    override suspend fun setForceDarkMode(enabled: Boolean) {
        sp.edit().putBoolean("sp_force_dark_mode", enabled).apply()
    }

    override suspend fun setEnableLiveSuggestions(enabled: Boolean) {
        sp.edit().putBoolean("sp_enable_live_suggestions", enabled).apply()
    }

    override suspend fun setAdBlockEnabled(enabled: Boolean) {
        sp.edit().putBoolean("sp_ad_block", enabled)
            .putBoolean("profileStandard_adBlock", enabled).apply()
    }

    override suspend fun setBlockThirdPartyCookies(enabled: Boolean) {
        sp.edit().putBoolean("sp_block_third_party_cookies", enabled)
            .putBoolean("profileStandard_cookiesThirdParty", !enabled).apply()
    }

    override suspend fun setFingerprintProtection(enabled: Boolean) {
        sp.edit().putBoolean("sp_fingerprint_protection", enabled)
            .putBoolean("profileStandard_fingerPrintProtection", enabled).apply()
    }

    override suspend fun setWebrtcProtection(enabled: Boolean) {
        sp.edit().putBoolean("sp_webrtc_protection", enabled)
            .putBoolean("profileStandard_webrtcProtection", enabled).apply()
    }

    override suspend fun setDntGpc(enabled: Boolean) {
        sp.edit().putBoolean("sp_dnt_gpc", enabled)
            .putBoolean("profileStandard_dnt", enabled).apply()
    }

    override suspend fun setTrimReferrers(enabled: Boolean) {
        sp.edit().putBoolean("sp_trim_referrers", enabled).apply()
    }

    override suspend fun setWebauthnEnabled(enabled: Boolean) {
        sp.edit().putBoolean("sp_webauthn_enabled", enabled).apply()
    }

    override suspend fun setHttpsOnly(enabled: Boolean) {
        sp.edit().putBoolean("sp_https_only", enabled)
            .putBoolean("profileStandard_httpsOnly", enabled).apply()
    }

    override suspend fun setJavaScriptEnabled(enabled: Boolean) {
        sp.edit().putBoolean("sp_javascript", enabled)
            .putBoolean("profileStandard_javascript", enabled).apply()
    }

    override suspend fun setBlockPopups(enabled: Boolean) {
        sp.edit().putBoolean("sp_block_popups", enabled)
            .putBoolean("profileStandard_javascriptPopUp", enabled).apply()
    }

    override suspend fun setPrivateDnsMode(mode: String) {
        sp.edit().putString("sp_private_dns_mode", mode).apply()
    }

    override suspend fun setTouchHaptics(enabled: Boolean) {
        sp.edit().putBoolean("sp_touch_haptics", enabled).apply()
    }

    override suspend fun setPredictiveBack(enabled: Boolean) {
        PetalPredictiveJunction.setPredictiveBackEnabled(sp, enabled)
    }

    override suspend fun setDepthBlur(enabled: Boolean) {
        PetalPredictiveJunction.setDepthBlurEnabled(sp, enabled)
    }

    override suspend fun setFontSizeScale(scale: Float) {
        sp.edit().putFloat("sp_font_size_scale", scale).apply()
    }

    override suspend fun setZoomLevelScale(scale: Float) {
        sp.edit().putFloat("sp_zoom_level_scale", scale).apply()
    }

    override suspend fun setForceZoom(enabled: Boolean) {
        sp.edit().putBoolean("sp_force_enable_zoom", enabled).apply()
    }

    override suspend fun setReaderModeDetection(enabled: Boolean) {
        sp.edit().putBoolean("sp_reader_mode_detection", enabled).apply()
    }

    override suspend fun setCaretBrowsing(enabled: Boolean) {
        sp.edit().putBoolean("sp_caret_browsing", enabled).apply()
    }

    override suspend fun setTouchpadSwipeNav(enabled: Boolean) {
        sp.edit().putBoolean("sp_touchpad_swipe_nav", enabled).apply()
    }

    override suspend fun setAddressBarSwipeTabs(enabled: Boolean) {
        sp.edit().putBoolean("sp_address_bar_swipe_tabs", enabled).apply()
    }

    override suspend fun setAddressBarQuickActions(enabled: Boolean) {
        sp.edit().putBoolean("sp_address_bar_quick_actions", enabled).apply()
    }

    override suspend fun setAppLanguage(language: String) {
        sp.edit().putString("sp_app_language", language).apply()
    }

    override suspend fun setAddressBarPosition(position: String) {
        sp.edit().putString("sp_address_bar_position", position).apply()
    }

    override suspend fun setAppLockEnabled(enabled: Boolean) {
        sp.edit().putBoolean("sp_app_lock_enabled", enabled).apply()
    }

    override suspend fun setAppLockPasscode(passcode: String) {
        sp.edit().putString("sp_app_lock_passcode", passcode).apply()
    }

    override suspend fun setDoubleBackExit(enabled: Boolean) {
        sp.edit().putBoolean("sp_double_back_exit", enabled).apply()
    }

    override suspend fun setAutoOpenApps(enabled: Boolean) {
        sp.edit().putBoolean("sp_auto_open_apps", enabled).apply()
    }

    override suspend fun setCheckUpdateOnLaunch(enabled: Boolean) {
        sp.edit().putBoolean("sp_check_update_on_launch", enabled).apply()
    }

    override suspend fun setTorrentEngineMode(mode: String) {
        sp.edit().putString("sp_torrent_engine", mode).apply()
    }

    override suspend fun setDownloadManagerMode(mode: String) {
        sp.edit().putString(com.petal.browser.unit.ExternalDownloadManagerHelper.PREF_DOWNLOAD_MANAGER_MODE, mode).apply()
    }
}
