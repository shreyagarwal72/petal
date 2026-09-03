package com.petal.browser.data.repository

import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.GSFlexPreset
import com.petal.browser.ui.theme.ThemeConfig
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing Petal browser preferences and settings.
 * Mirrors RvSystem-Monitor's SettingsRepository architecture.
 */
interface SettingsRepository {
    // Appearance & Theming
    val appFont: Flow<AppFont>
    val fontWidth: Flow<Float>
    val fontWeight: Flow<Float>
    val fontRoundness: Flow<Float>
    val gsFlexPreset: Flow<GSFlexPreset>
    val colorStyle: Flow<ColorStyle>
    val paletteId: Flow<String>
    val dynamicColor: Flow<Boolean>
    val amoledMode: Flow<Boolean>
    val themeConfig: Flow<ThemeConfig>
    val floatingTabBar: Flow<Boolean>
    val expressiveColors: Flow<Boolean>
    val expressiveBgShapes: Flow<Boolean>
    val bgShapeChangeMode: Flow<String>
    val bgShapeRotationMin: Flow<Int>
    val highRefreshRate: Flow<Boolean>
    val customFontName: Flow<String>

    // Search & Homepage
    val searchEngineIndex: Flow<String>
    val homepageType: Flow<String>
    val customHomepageUrl: Flow<String>
    val backgroundPlay: Flow<Boolean>
    val autoPip: Flow<Boolean>
    val forceDarkMode: Flow<Boolean>
    val enableLiveSuggestions: Flow<Boolean>

    // Privacy & Security
    val adBlockEnabled: Flow<Boolean>
    val blockThirdPartyCookies: Flow<Boolean>
    val fingerprintProtection: Flow<Boolean>
    val webrtcProtection: Flow<Boolean>
    val dntGpc: Flow<Boolean>
    val trimReferrers: Flow<Boolean>
    val webauthnEnabled: Flow<Boolean>
    val httpsOnly: Flow<Boolean>
    val javaScriptEnabled: Flow<Boolean>
    val blockPopups: Flow<Boolean>
    val privateDnsMode: Flow<String>

    // Accessibility & Display
    val touchHaptics: Flow<Boolean>
    val predictiveBack: Flow<Boolean>
    val depthBlur: Flow<Boolean>
    val fontSizeScale: Flow<Float>
    val zoomLevelScale: Flow<Float>
    val forceZoom: Flow<Boolean>
    val readerModeDetection: Flow<Boolean>
    val caretBrowsing: Flow<Boolean>
    val touchpadSwipeNav: Flow<Boolean>

    // Experimental & Miscellaneous
    val appLanguage: Flow<String>
    val addressBarPosition: Flow<String>
    val appLockEnabled: Flow<Boolean>
    val appLockPasscode: Flow<String>
    val doubleBackExit: Flow<Boolean>
    val autoOpenApps: Flow<Boolean>
    val checkUpdateOnLaunch: Flow<Boolean>
    val torrentEngineMode: Flow<String>

    // Setters
    suspend fun setAppFont(font: AppFont)
    suspend fun setFontWidth(width: Float)
    suspend fun setFontWeight(weight: Float)
    suspend fun setFontRoundness(roundness: Float)
    suspend fun setGsFlexPreset(preset: GSFlexPreset)
    suspend fun setColorStyle(style: ColorStyle)
    suspend fun setPaletteId(id: String)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setAmoledMode(enabled: Boolean)
    suspend fun setThemeConfig(config: ThemeConfig)
    suspend fun setFloatingTabBar(enabled: Boolean)
    suspend fun setExpressiveColors(enabled: Boolean)
    suspend fun setExpressiveBgShapes(enabled: Boolean)
    suspend fun setBgShapeChangeMode(mode: String)
    suspend fun setBgShapeRotationMin(minutes: Int)
    suspend fun setHighRefreshRate(enabled: Boolean)
    suspend fun setCustomFontName(name: String)

    suspend fun setSearchEngineIndex(index: String)
    suspend fun setHomepageType(type: String)
    suspend fun setCustomHomepageUrl(url: String)
    suspend fun setBackgroundPlay(enabled: Boolean)
    suspend fun setAutoPip(enabled: Boolean)
    suspend fun setForceDarkMode(enabled: Boolean)
    suspend fun setEnableLiveSuggestions(enabled: Boolean)

    suspend fun setAdBlockEnabled(enabled: Boolean)
    suspend fun setBlockThirdPartyCookies(enabled: Boolean)
    suspend fun setFingerprintProtection(enabled: Boolean)
    suspend fun setWebrtcProtection(enabled: Boolean)
    suspend fun setDntGpc(enabled: Boolean)
    suspend fun setTrimReferrers(enabled: Boolean)
    suspend fun setWebauthnEnabled(enabled: Boolean)
    suspend fun setHttpsOnly(enabled: Boolean)
    suspend fun setJavaScriptEnabled(enabled: Boolean)
    suspend fun setBlockPopups(enabled: Boolean)
    suspend fun setPrivateDnsMode(mode: String)

    suspend fun setTouchHaptics(enabled: Boolean)
    suspend fun setPredictiveBack(enabled: Boolean)
    suspend fun setDepthBlur(enabled: Boolean)
    suspend fun setFontSizeScale(scale: Float)
    suspend fun setZoomLevelScale(scale: Float)
    suspend fun setForceZoom(enabled: Boolean)
    suspend fun setReaderModeDetection(enabled: Boolean)
    suspend fun setCaretBrowsing(enabled: Boolean)
    suspend fun setTouchpadSwipeNav(enabled: Boolean)

    suspend fun setAppLanguage(language: String)
    suspend fun setAddressBarPosition(position: String)
    suspend fun setAppLockEnabled(enabled: Boolean)
    suspend fun setAppLockPasscode(passcode: String)
    suspend fun setDoubleBackExit(enabled: Boolean)
    suspend fun setAutoOpenApps(enabled: Boolean)
    suspend fun setCheckUpdateOnLaunch(enabled: Boolean)
    suspend fun setTorrentEngineMode(mode: String)
}
