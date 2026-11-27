package com.petal.browser.data.repository

import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.CustomFontSettings
import com.petal.browser.ui.theme.FontAccentMode
import com.petal.browser.ui.theme.FontAxes
import com.petal.browser.ui.theme.FontState
import com.petal.browser.ui.theme.FontTier
import com.petal.browser.ui.theme.GSFlexPreset
import com.petal.browser.ui.theme.ThemeConfig
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing Petal browser preferences and settings.
 * Mirrors RvSystem-Monitor's SettingsRepository architecture.
 */
interface SettingsRepository {
    // Appearance & Theming
    val fontState: Flow<FontState>
    val appFont: Flow<AppFont>
    val gsFlexPreset: Flow<GSFlexPreset>
    val gsFlexSettings: Flow<com.petal.browser.ui.theme.GSFlexSettings>
    val nunitoWeight: Flow<Int>
    val customFontSettings: Flow<CustomFontSettings>
    val fontAccentMode: Flow<FontAccentMode>
    val readerFollowsAppFont: Flow<Boolean>
    val colorStyle: Flow<ColorStyle>
    val paletteId: Flow<String>
    val dynamicColor: Flow<Boolean>
    val amoledMode: Flow<Boolean>
    val themeConfig: Flow<ThemeConfig>
    val floatingTabBar: Flow<Boolean>
    val expressiveColors: Flow<Boolean>
    val expressiveBgShapes: Flow<Boolean>
    val expressiveShapes35: Flow<Boolean> get() = kotlinx.coroutines.flow.flowOf(true)
    val bgShapeChangeMode: Flow<String>
    val bgShapeRotationMin: Flow<Int>
    val highRefreshRate: Flow<Boolean>
    val customFontName: Flow<String>
    val launchStyle: Flow<String>
    val launchRippleEnabled: Flow<Boolean>
    val motionBlurEnabled: Flow<Boolean>
    val motionBlurScale: Flow<Float>
    val liquidGlassUnlocked: Flow<Boolean>
    val liquidGlassEnabled: Flow<Boolean>
    val liquidGlassAlpha: Flow<Float>
    val liquidGlassSheen: Flow<Float>
    /** Refraction strength multiplier (1 = as designed, 0 = lens off). */
    val liquidGlassRefraction: Flow<Float>
    /** Refraction width multiplier: how far in from the rim the glass bends. */
    val liquidGlassRefractionWidth: Flow<Float>
    val liquidGlassContainments: Flow<Boolean>
    val liquidGlassBottomNav: Flow<Boolean>
    val liquidGlassAddressBar: Flow<Boolean>
    val liquidGlassOverflowBlurBg: Flow<Boolean>
    val liquidGlassBgMode: Flow<String>
    val liquidGlassBgImageUri: Flow<String>
    val liquidGlassBgDim: Flow<Float>
    val liquidGlassBgBlur: Flow<Float>

    // Search & Homepage
    val searchEngineIndex: Flow<String>
    val homepageType: Flow<String>
    val customHomepageUrl: Flow<String>
    val backgroundPlay: Flow<Boolean>
    val autoPip: Flow<Boolean>
    val forceDarkMode: Flow<Boolean>
    val enableLiveSuggestions: Flow<Boolean>
    val searchSuggestionsPrivate: Flow<Boolean>
    val showSearchEngineSelectorInOmnibox: Flow<Boolean>

    // Privacy & Security
    val adBlockEnabled: Flow<Boolean>
    val blockThirdPartyCookies: Flow<Boolean>
    val totalCookieProtection: Flow<Boolean>
    val cookieBannerAutoReject: Flow<Boolean>
    val etpStrict: Flow<Boolean>
    val biometricTabLock: Flow<Boolean>
    val sessionPartitioning: Flow<Boolean>
    val fingerprintProtection: Flow<Boolean>
    val webrtcProtection: Flow<Boolean>
    val dntGpc: Flow<Boolean>
    val trimReferrers: Flow<Boolean>
    val webauthnEnabled: Flow<Boolean>
    val httpsOnly: Flow<Boolean>
    val javaScriptEnabled: Flow<Boolean>
    val blockPopups: Flow<Boolean>
    val openRedirectsInBackground: Flow<Boolean>
    val privacyProfile: Flow<String>
    val webglEnabled: Flow<Boolean>
    val privateDnsMode: Flow<String>
    val customDohUrl: Flow<String>
    val nextDnsProfileId: Flow<String>
    val bounceTracking: Flow<Boolean>
    val canvasLetterboxing: Flow<Boolean>
    val drmEnabled: Flow<Boolean>
    val loginAutofillEnabled: Flow<Boolean>
    val fetchShortcutFavicons: Flow<Boolean>
    val clearOnExit: Flow<Boolean>

    // Accessibility & Display
    val immersiveFullScreen: Flow<Boolean>
    val touchHaptics: Flow<Boolean>
    val scrollHaptics: Flow<Boolean>
    val predictiveBack: Flow<Boolean>
    val depthBlur: Flow<Boolean>
    val predictiveBackStyle: Flow<com.petal.browser.predictive.PetalBackStyle>
    val predictiveBackExitDirection: Flow<com.petal.browser.predictive.PetalBackExitDirection>
    val fontSizeScale: Flow<Float>
    val zoomLevelScale: Flow<Float>
    val forceZoom: Flow<Boolean>
    val readerModeDetection: Flow<Boolean>
    val caretBrowsing: Flow<Boolean>
    val touchpadSwipeNav: Flow<Boolean>
    val addressBarSwipeTabs: Flow<Boolean>
    val addressBarQuickActions: Flow<Boolean>
    val addressBarHeight: Flow<String>
    val addressBarAction: Flow<String>
    val addressBarStyle: Flow<String>
    val mergeLeading: Flow<String>
    val mergeTrailing: Flow<String>
    val mergeLayout: Flow<String>
    val barCollapseStyle: Flow<String>
    val hideBarsOnScroll: Flow<Boolean>
    val addressBarExtensionChips: Flow<Boolean>
    val showNavLabels: Flow<Boolean>
    val urlDisplayMode: Flow<String>
    val barProgressStyle: Flow<String>
    val classicOuterAction: Flow<String>
    val classicShowReload: Flow<Boolean>
    val navSearchStyle: Flow<String>
    val navSwipeTabs: Flow<Boolean>
    val navShowOnPages: Flow<Boolean>
    val navMidSlot: Flow<String>

    // Miscellaneous
    val appLanguage: Flow<String>
    val matchWebsiteLanguage: Flow<Boolean>
    val addressBarPosition: Flow<String>
    val appLockEnabled: Flow<Boolean>
    val appLockPasscode: Flow<String>
    val doubleBackExit: Flow<Boolean>
    val autoOpenApps: Flow<Boolean>
    val customTabsEnabled: Flow<Boolean>
    val customTabsEtp: Flow<Boolean>
    val newMainMenuBeta: Flow<Boolean>
    val checkUpdateOnLaunch: Flow<Boolean>
    val torrentEngineMode: Flow<String>
    val downloadManagerMode: Flow<String>
    val confirmDownloadDelete: Flow<Boolean>
    val deleteDownloadFile: Flow<Boolean>
    val autoPreviewDownloadedImages: Flow<Boolean>
    val liveUpdates: Flow<Boolean>
    val parallelDownloads: Flow<Boolean>
    val parallelConnections: Flow<Int>
    val smartDownloadRouting: Flow<Boolean>
    val maxConcurrentDownloads: Flow<Int>
    val wifiOnlyDownloads: Flow<Boolean>
    val downloadSpeedLimitKBps: Flow<Int>
    val verifyDownloadChecksums: Flow<Boolean>
    val postDownloadActionsJson: Flow<String>
    val offerClipboardImport: Flow<Boolean>

    // Apple Duo (BETA) Animation
    val appleDuoEnabled: Flow<Boolean>
    val appleDuoWebsites: Flow<Boolean>
    val appleDuoUseSensor: Flow<Boolean>
    val appleDuoManualTilt: Flow<Float>
    val appleDuoAutoRecenter: Flow<Boolean>
    val appleDuoEyeDistance: Flow<Float>
    val appleDuoBlurSpread: Flow<Float>
    val appleDuoDarkening: Flow<Float>

    // Setters
    suspend fun setAppFont(font: AppFont)
    suspend fun setGsFlexPreset(preset: GSFlexPreset)
    suspend fun setGsFlexSettings(settings: com.petal.browser.ui.theme.GSFlexSettings)
    suspend fun setGsFlexAxis(tier: FontTier, axis: FontAxes.Axis, value: Float)
    suspend fun setAllGsFlexAxes(axis: FontAxes.Axis, value: Float)
    suspend fun resetGsFlexToPreset(preset: GSFlexPreset)
    suspend fun setNunitoWeight(weight: Int)
    suspend fun setCustomFontAxis(tier: FontTier, axis: FontAxes.Axis, value: Float)
    suspend fun setAllCustomFontAxes(axis: FontAxes.Axis, value: Float)
    suspend fun setCustomFontSettings(settings: CustomFontSettings)
    suspend fun setFontAccentMode(mode: FontAccentMode)
    suspend fun setReaderFollowsAppFont(enabled: Boolean)
    suspend fun resetAllFontSettings()
    suspend fun setColorStyle(style: ColorStyle)
    suspend fun setPaletteId(id: String)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setAmoledMode(enabled: Boolean)
    suspend fun setThemeConfig(config: ThemeConfig)
    suspend fun setFloatingTabBar(enabled: Boolean)
    suspend fun setExpressiveColors(enabled: Boolean)
    suspend fun setExpressiveBgShapes(enabled: Boolean)
    suspend fun setExpressiveShapes35(enabled: Boolean)
    suspend fun setBgShapeChangeMode(mode: String)
    suspend fun setBgShapeRotationMin(minutes: Int)
    suspend fun setHighRefreshRate(enabled: Boolean)
    suspend fun setCustomFontName(name: String)
    suspend fun setLaunchStyle(style: String)
    suspend fun setLaunchRippleEnabled(enabled: Boolean)
    suspend fun setMotionBlurEnabled(enabled: Boolean)
    suspend fun setMotionBlurScale(scale: Float)
    suspend fun setLiquidGlassUnlocked(unlocked: Boolean)
    suspend fun setLiquidGlassEnabled(enabled: Boolean)
    suspend fun setLiquidGlassAlpha(alpha: Float)
    suspend fun setLiquidGlassSheen(sheen: Float)
    suspend fun setLiquidGlassRefraction(strength: Float)
    suspend fun setLiquidGlassRefractionWidth(width: Float)
    suspend fun setLiquidGlassContainments(enabled: Boolean)
    suspend fun setLiquidGlassBottomNav(enabled: Boolean)
    suspend fun setLiquidGlassAddressBar(enabled: Boolean)
    suspend fun setLiquidGlassOverflowBlurBg(enabled: Boolean)
    suspend fun setLiquidGlassBgMode(mode: String)
    suspend fun setLiquidGlassBgImageUri(uri: String)
    suspend fun setLiquidGlassBgDim(dim: Float)
    suspend fun setLiquidGlassBgBlur(blur: Float)

    suspend fun setSearchEngineIndex(index: String)
    suspend fun setHomepageType(type: String)
    suspend fun setCustomHomepageUrl(url: String)
    suspend fun setBackgroundPlay(enabled: Boolean)
    suspend fun setAutoPip(enabled: Boolean)
    suspend fun setForceDarkMode(enabled: Boolean)
    suspend fun setEnableLiveSuggestions(enabled: Boolean)
    suspend fun setSearchSuggestionsPrivate(enabled: Boolean)
    suspend fun setShowSearchEngineSelectorInOmnibox(enabled: Boolean)

    suspend fun setAdBlockEnabled(enabled: Boolean)
    suspend fun setBlockThirdPartyCookies(enabled: Boolean)
    suspend fun setTotalCookieProtection(enabled: Boolean)
    suspend fun setCookieBannerAutoReject(enabled: Boolean)
    suspend fun setEtpStrict(enabled: Boolean)
    suspend fun setBiometricTabLock(enabled: Boolean)
    suspend fun setSessionPartitioning(enabled: Boolean)
    suspend fun setFingerprintProtection(enabled: Boolean)
    suspend fun setWebrtcProtection(enabled: Boolean)
    suspend fun setDntGpc(enabled: Boolean)
    suspend fun setTrimReferrers(enabled: Boolean)
    suspend fun setWebauthnEnabled(enabled: Boolean)
    suspend fun setHttpsOnly(enabled: Boolean)
    suspend fun setJavaScriptEnabled(enabled: Boolean)
    suspend fun setBlockPopups(enabled: Boolean)
    suspend fun setOpenRedirectsInBackground(enabled: Boolean)
    suspend fun setPrivateDnsMode(mode: String)
    suspend fun setCustomDohUrl(url: String)
    suspend fun setNextDnsProfileId(id: String)
    suspend fun setPrivacyProfile(profile: String)
    suspend fun setWebglEnabled(enabled: Boolean)
    suspend fun setBounceTracking(enabled: Boolean)
    suspend fun setCanvasLetterboxing(enabled: Boolean)
    suspend fun setDrmEnabled(enabled: Boolean)
    suspend fun setLoginAutofillEnabled(enabled: Boolean)
    suspend fun setFetchShortcutFavicons(enabled: Boolean)
    suspend fun setClearOnExit(enabled: Boolean)
    suspend fun setCrossSiteTracking(strict: Boolean)
    suspend fun resetPrivacyToRecommended()

    suspend fun setTouchHaptics(enabled: Boolean)
    suspend fun setImmersiveFullScreen(enabled: Boolean)
    suspend fun setScrollHaptics(enabled: Boolean)
    suspend fun setPredictiveBack(enabled: Boolean)
    suspend fun setDepthBlur(enabled: Boolean)
    suspend fun setPredictiveBackStyle(style: com.petal.browser.predictive.PetalBackStyle)
    suspend fun setPredictiveBackExitDirection(direction: com.petal.browser.predictive.PetalBackExitDirection)
    suspend fun setFontSizeScale(scale: Float)
    suspend fun setZoomLevelScale(scale: Float)
    suspend fun setForceZoom(enabled: Boolean)
    suspend fun setReaderModeDetection(enabled: Boolean)
    suspend fun setCaretBrowsing(enabled: Boolean)
    suspend fun setTouchpadSwipeNav(enabled: Boolean)
    suspend fun setAddressBarSwipeTabs(enabled: Boolean)
    suspend fun setAddressBarQuickActions(enabled: Boolean)
    suspend fun setAddressBarHeight(height: String)
    suspend fun setAddressBarAction(action: String)
    suspend fun setAddressBarStyle(style: String)
    suspend fun setMergeLeading(leading: String)
    suspend fun setMergeTrailing(trailing: String)
    suspend fun setMergeLayout(layout: String)
    suspend fun setBarCollapseStyle(style: String)
    suspend fun setHideBarsOnScroll(enabled: Boolean)
    suspend fun setAddressBarExtensionChips(enabled: Boolean)
    suspend fun setShowNavLabels(enabled: Boolean)
    suspend fun setUrlDisplayMode(mode: String)
    suspend fun setBarProgressStyle(style: String)
    suspend fun setClassicOuterAction(action: String)
    suspend fun setClassicShowReload(enabled: Boolean)
    suspend fun setNavSearchStyle(style: String)
    suspend fun setNavSwipeTabs(enabled: Boolean)
    suspend fun setNavShowOnPages(enabled: Boolean)
    suspend fun setNavMidSlot(slot: String)

    suspend fun setAppLanguage(language: String)
    suspend fun setMatchWebsiteLanguage(enabled: Boolean)
    suspend fun setAddressBarPosition(position: String)
    suspend fun setAppLockEnabled(enabled: Boolean)
    suspend fun setAppLockPasscode(passcode: String)
    suspend fun setDoubleBackExit(enabled: Boolean)
    suspend fun setAutoOpenApps(enabled: Boolean)
    suspend fun setCustomTabsEnabled(enabled: Boolean)
    suspend fun setCustomTabsEtp(enabled: Boolean)
    suspend fun setNewMainMenuBeta(enabled: Boolean)
    suspend fun setCheckUpdateOnLaunch(enabled: Boolean)
    suspend fun setTorrentEngineMode(mode: String)
    suspend fun setDownloadManagerMode(mode: String)
    suspend fun setParallelDownloads(enabled: Boolean)
    suspend fun setParallelConnections(count: Int)
    suspend fun setSmartDownloadRouting(enabled: Boolean)
    suspend fun setMaxConcurrentDownloads(count: Int)
    suspend fun setWifiOnlyDownloads(enabled: Boolean)
    suspend fun setDownloadSpeedLimitKBps(kbps: Int)
    suspend fun setVerifyDownloadChecksums(enabled: Boolean)
    suspend fun setPostDownloadActionsJson(json: String)
    suspend fun setOfferClipboardImport(enabled: Boolean)
    suspend fun setConfirmDownloadDelete(enabled: Boolean)
    suspend fun setDeleteDownloadFile(enabled: Boolean)
    suspend fun setAutoPreviewDownloadedImages(enabled: Boolean)
    suspend fun setLiveUpdates(enabled: Boolean)

    suspend fun setAppleDuoEnabled(enabled: Boolean)
    suspend fun setAppleDuoWebsites(enabled: Boolean)
    suspend fun setAppleDuoUseSensor(enabled: Boolean)
    suspend fun setAppleDuoManualTilt(tilt: Float)
    suspend fun setAppleDuoAutoRecenter(enabled: Boolean)
    suspend fun setAppleDuoEyeDistance(distance: Float)
    suspend fun setAppleDuoBlurSpread(spread: Float)
    suspend fun setAppleDuoDarkening(darkening: Float)
}
