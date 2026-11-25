/*
 * PetalPrivacyShieldSheet.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive Privacy & Tracker Shield HUD bottom sheet for Petal Browser.
 * Hosted in PetalRootApp via PetalSheet and backed by official Mozilla
 * Android Components APIs (TrackingProtectionUseCases, SecurityInfo,
 * BrowserIcons, ContentBlocking database, and SitePermissions).
 */

package com.petal.browser.ui.components

import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Https
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.NoEncryption
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WifiProtectedSetup
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.activity.MainActivity
import com.petal.browser.browser.PetalAdBlockEngine
import com.petal.browser.browser.PetalTabViewController
import com.petal.browser.engine.gecko.PetalEngineStore
import com.petal.browser.engine.gecko.PetalGeckoRuntime
import com.petal.browser.engine.gecko.PetalSitePermissionsBridge
import com.petal.browser.extensions.PetalExtensionManager
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.motion.MotionGate
import com.petal.browser.privacy.PetalElementBlocker
import com.petal.browser.privacy.PetalElementZapper
import com.petal.browser.privacy.PetalHttpsOnlyWhitelist
import com.petal.browser.ui.containment.PetalAlertDialog
import com.petal.browser.ui.containment.PetalContainmentShapes
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.PetalGroupRow
import com.petal.browser.ui.containment.PetalHeroCard
import com.petal.browser.ui.containment.PetalSettingsToggleRow
import com.petal.browser.ui.containment.PetalSheet
import com.petal.browser.ui.containment.petalGroupPositionFor
import com.petal.browser.ui.containment.petalSheetSurface
import com.petal.browser.ui.kit.FilledTonalButton
import com.petal.browser.ui.kit.IconButton
import com.petal.browser.ui.kit.TextField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.SecurityInfo
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.concept.engine.EngineSession.TrackingProtectionPolicy.CookiePolicy
import mozilla.components.concept.engine.EngineSession.TrackingProtectionPolicy.TrackingCategory
import mozilla.components.concept.engine.content.blocking.TrackerLog
import mozilla.components.concept.engine.permission.SitePermissions

data class ShieldSheetRequest(
    val pageUrl: String,
    val onToggleAdBlock: (Boolean) -> Unit = {}
)

object PetalPrivacyShieldSheet {

    private val _request = MutableStateFlow<ShieldSheetRequest?>(null)
    val request: StateFlow<ShieldSheetRequest?> = _request.asStateFlow()

    @Volatile
    private var lastShowAtMs: Long = 0L

    @JvmStatic
    fun show(activity: ComponentActivity, pageUrl: String, onToggleAdBlock: (Boolean) -> Unit) {
        val now = SystemClock.uptimeMillis()
        if (_request.value != null || now - lastShowAtMs < 350L) return
        lastShowAtMs = now
        activity.runOnUiThread {
            if (_request.value != null) return@runOnUiThread
            _request.value = ShieldSheetRequest(
                pageUrl = pageUrl,
                onToggleAdBlock = onToggleAdBlock
            )
        }
    }

    @JvmStatic
    fun dismiss() {
        _request.value = null
    }
}

private enum class ShieldTileRole { Primary, Secondary, Tertiary, Neutral }

@Composable
private fun ShieldTileRole.container(): Color = when (this) {
    ShieldTileRole.Primary -> MaterialTheme.colorScheme.primaryContainer
    ShieldTileRole.Secondary -> MaterialTheme.colorScheme.secondaryContainer
    ShieldTileRole.Tertiary -> MaterialTheme.colorScheme.tertiaryContainer
    ShieldTileRole.Neutral -> MaterialTheme.colorScheme.surfaceContainerHighest
}

@Composable
private fun ShieldTileRole.content(): Color = when (this) {
    ShieldTileRole.Primary -> MaterialTheme.colorScheme.onPrimaryContainer
    ShieldTileRole.Secondary -> MaterialTheme.colorScheme.onSecondaryContainer
    ShieldTileRole.Tertiary -> MaterialTheme.colorScheme.onTertiaryContainer
    ShieldTileRole.Neutral -> MaterialTheme.colorScheme.onSurface
}

private enum class ConnectionStatus { SECURE, NOT_SECURE, UNKNOWN }

private data class ShieldTrackerSummary(
    val pageBlocked: Int,
    val ads: Int,
    val trackers: Int,
    val social: Int,
    val cookies: Int
)

private fun Modifier.shieldPressTracker(onPressedChange: (Boolean) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            onPressedChange(true)
            waitForUpOrCancellation(pass = PointerEventPass.Initial)
            onPressedChange(false)
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalPrivacyShieldBottomSheet(
    request: ShieldSheetRequest,
    activeTab: TabSessionState?,
    tabController: PetalTabViewController?,
    onToggleAdBlock: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()

    val pageUrl = activeTab?.content?.url?.takeIf { it.isNotBlank() } ?: request.pageUrl
    val isPrivate = activeTab?.content?.private == true || tabController?.isIncognito() == true

    val dismissWithAnimation: () -> Unit = {
        scope.launch {
            try {
                if (sheetState.currentValue != SheetValue.Hidden) {
                    sheetState.hide()
                }
            } finally {
                onDismiss()
            }
        }
    }

    PetalSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        PrivacyShieldContent(
            activity = activity,
            activeTab = activeTab,
            tabController = tabController,
            pageUrl = pageUrl,
            isPrivate = isPrivate,
            onDismiss = dismissWithAnimation,
            onReloadRequested = {
                if (activity is MainActivity) {
                    activity.reloadCurrentBrowserTab()
                } else {
                    tabController?.reload()
                }
            },
            onToggleAdBlock = onToggleAdBlock
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrivacyShieldContent(
    activity: ComponentActivity?,
    activeTab: TabSessionState?,
    tabController: PetalTabViewController?,
    pageUrl: String,
    isPrivate: Boolean,
    onDismiss: () -> Unit,
    onReloadRequested: () -> Unit,
    onToggleAdBlock: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val reduceMotion = rememberReduceMotion()
    val lowEnd = remember(context) { MotionGate.isLowEndDevice(context) }
    val trackingUseCases = remember(context) { PetalEngineStore.getTrackingProtectionUseCases(context) }

    val parsedUri = remember(pageUrl) {
        try {
            Uri.parse(pageUrl)
        } catch (_: Exception) {
            null
        }
    }
    val scheme = remember(parsedUri) { parsedUri?.scheme?.lowercase().orEmpty() }
    val isWebPage = scheme == "http" || scheme == "https"
    val isHttp = scheme == "http"
    val host = remember(parsedUri) { parsedUri?.host.orEmpty() }
    val cleanHost = remember(host) { host.removePrefix("www.") }
    val displayDomain = cleanHost.ifEmpty {
        pageUrl.takeIf { it.isNotBlank() } ?: stringResource(R.string.shield_current_website)
    }

    val connectionStatus = remember(activeTab?.content?.securityInfo, tabController?.currentSecurityInfo, scheme, pageUrl) {
        when (val info = activeTab?.content?.securityInfo ?: SecurityInfo.Unknown) {
            is SecurityInfo.Secure -> ConnectionStatus.SECURE
            is SecurityInfo.Insecure -> ConnectionStatus.NOT_SECURE
            SecurityInfo.Unknown -> when {
                tabController?.currentSecurityInfo?.isSecure == true -> ConnectionStatus.SECURE
                scheme == "https" -> ConnectionStatus.SECURE
                scheme == "http" -> ConnectionStatus.NOT_SECURE
                pageUrl.startsWith("petal://", ignoreCase = true) -> ConnectionStatus.SECURE
                else -> ConnectionStatus.UNKNOWN
            }
        }
    }

    // Live tracker counts from BrowserStore + Gecko ContentBlocking log + PetalAdBlockEngine
    val tabBlockedTrackers = activeTab?.trackingProtection?.blockedTrackers.orEmpty()
    val engineRevision by PetalAdBlockEngine.trackerRevision.collectAsStateWithLifecycle()
    var trackerLogs by remember(activeTab?.id, pageUrl) { mutableStateOf<List<TrackerLog>>(emptyList()) }
    var dbTotalBlocked by remember { mutableIntStateOf(0) }

    LaunchedEffect(activeTab?.id, pageUrl, tabBlockedTrackers.size, activeTab?.content?.loading) {
        val tabId = activeTab?.id
        if (tabId != null && isWebPage) {
            trackingUseCases.fetchTrackingLogs(
                tabId = tabId,
                onSuccess = { logs -> trackerLogs = logs },
                onError = {}
            )
        } else {
            trackerLogs = emptyList()
        }
    }

    LaunchedEffect(Unit) {
        trackingUseCases.fetchTotalTrackersBlocked(
            onSuccess = { sum -> dbTotalBlocked = sum },
            onError = {}
        )
    }

    val trackerSummary = remember(tabBlockedTrackers, trackerLogs, cleanHost, isPrivate, engineRevision, isWebPage) {
        if (!isWebPage) {
            ShieldTrackerSummary(0, 0, 0, 0, 0)
        } else {
            var storeAds = 0
            var storeTrackers = 0
            var storeSocial = 0
            var storeCookies = 0
            for (t in tabBlockedTrackers) {
                val cats = t.trackingCategories
                if (cats.contains(TrackingCategory.AD)) storeAds++
                if (cats.contains(TrackingCategory.ANALYTICS) ||
                    cats.contains(TrackingCategory.FINGERPRINTING) ||
                    cats.contains(TrackingCategory.CRYPTOMINING) ||
                    cats.contains(TrackingCategory.CONTENT) ||
                    cats.contains(TrackingCategory.EMAIL)
                ) {
                    storeTrackers++
                }
                if (cats.contains(TrackingCategory.SOCIAL) || cats.contains(TrackingCategory.MOZILLA_SOCIAL)) {
                    storeSocial++
                }
                if (t.cookiePolicies.any { it != CookiePolicy.ACCEPT_ALL }) {
                    storeCookies++
                }
            }

            var logAds = 0
            var logTrackers = 0
            var logSocial = 0
            var logCookies = 0
            for (entry in trackerLogs) {
                val cats = entry.blockedCategories
                if (cats.contains(TrackingCategory.AD)) logAds++
                if (cats.contains(TrackingCategory.ANALYTICS) ||
                    cats.contains(TrackingCategory.FINGERPRINTING) ||
                    cats.contains(TrackingCategory.CRYPTOMINING) ||
                    cats.contains(TrackingCategory.CONTENT) ||
                    cats.contains(TrackingCategory.EMAIL) ||
                    cats.contains(TrackingCategory.SCRIPTS_AND_SUB_RESOURCES)
                ) {
                    logTrackers++
                }
                if (cats.contains(TrackingCategory.SOCIAL) || cats.contains(TrackingCategory.MOZILLA_SOCIAL)) {
                    logSocial++
                }
                if (entry.cookiesHasBeenBlocked) {
                    logCookies++
                }
            }

            val engineCats = PetalAdBlockEngine.getCategoryCountsForDomain(cleanHost, isPrivate)
            val engineBlocked = PetalAdBlockEngine.getBlockedCountForDomain(cleanHost, isPrivate)

            val ads = maxOf(storeAds, logAds, engineCats.ads)
            val trackers = maxOf(storeTrackers, logTrackers, engineCats.analytics + engineCats.content)
            val social = maxOf(storeSocial, logSocial, engineCats.social)
            val cookies = maxOf(storeCookies, logCookies, engineCats.cookies)
            val pageTotal = maxOf(tabBlockedTrackers.size, engineBlocked, ads + trackers + social + cookies)
            ShieldTrackerSummary(
                pageBlocked = pageTotal,
                ads = ads,
                trackers = trackers,
                social = social,
                cookies = cookies
            )
        }
    }

    val totalThreatsStopped = remember(dbTotalBlocked, engineRevision) {
        maxOf(dbTotalBlocked.toLong(), PetalAdBlockEngine.getTotalBlockedCount())
    }

    val animatedPageBlocked by animateIntAsState(
        targetValue = trackerSummary.pageBlocked,
        animationSpec = if (reduceMotion || lowEnd) snap() else PetalSpring.expressiveBouncySpatial(),
        label = "shield_page_blocked"
    )

    // Per-site trust state: synced between PetalAdBlockEngine and Gecko ContentBlocking exception store.
    val storeExcluded = activeTab?.trackingProtection?.ignoredOnTrackingProtection == true
    var geckoExceptionPresent by remember(activeTab?.id, cleanHost) { mutableStateOf(storeExcluded) }
    LaunchedEffect(activeTab?.id, cleanHost, storeExcluded) {
        val tabId = activeTab?.id
        if (tabId != null && isWebPage) {
            trackingUseCases.containsException(tabId) { contains ->
                geckoExceptionPresent = contains
            }
        } else {
            geckoExceptionPresent = false
        }
    }

    var isSiteTrusted by remember(cleanHost, isPrivate, geckoExceptionPresent, storeExcluded) {
        mutableStateOf(
            isWebPage && (
                PetalAdBlockEngine.isDomainWhitelisted(cleanHost, isPrivate) ||
                    geckoExceptionPresent ||
                    storeExcluded
                )
        )
    }

    // Global preferences (keep existing preference keys and syncPreferences behavior)
    var globalAdBlock by remember {
        mutableStateOf(sp.getBoolean("sp_ad_block", sp.getBoolean("profileStandard_adBlock", true)))
    }
    var blockThirdPartyCookies by remember {
        mutableStateOf(sp.getBoolean("sp_block_third_party_cookies", !sp.getBoolean("profileStandard_cookiesThirdParty", false)))
    }
    var httpsOnly by remember {
        mutableStateOf(sp.getBoolean("sp_https_only", sp.getBoolean("profileStandard_httpsOnly", false)))
    }
    var fingerprintProtection by remember {
        mutableStateOf(sp.getBoolean("sp_fingerprint_protection", sp.getBoolean("profileStandard_fingerPrintProtection", false)))
    }
    var webrtcProtection by remember {
        mutableStateOf(sp.getBoolean("sp_webrtc_protection", sp.getBoolean("profileStandard_webrtcProtection", true)))
    }
    var dntGpc by remember {
        mutableStateOf(sp.getBoolean("sp_dnt_gpc", sp.getBoolean("profileStandard_dnt", true)))
    }

    var isHttpsBypassed by remember(cleanHost, isPrivate) {
        mutableStateOf(PetalHttpsOnlyWhitelist.isBypassed(context, cleanHost, isPrivate))
    }
    var pendingGlobalReload by remember { mutableStateOf(false) }
    var isMoreProtectionsExpanded by remember { mutableStateOf(false) }

    val installedExtensions by PetalExtensionManager.extensions.collectAsState()
    val pendingPrompt by PetalExtensionManager.pendingPrompt.collectAsState()
    val adguardExt = remember(installedExtensions) {
        installedExtensions.firstOrNull {
            it.id.equals("adguardadblocker@adguard.com", ignoreCase = true) ||
                it.id.equals("adguard-adblocker", ignoreCase = true) ||
                it.name.contains("AdGuard", ignoreCase = true)
        }
    }
    var isInstallingAdGuard by remember { mutableStateOf(false) }

    pendingPrompt?.let { prompt ->
        com.petal.browser.compose.extensions.InstallPermissionDialog(prompt = prompt)
    }

    var permissionSummary by remember(pageUrl, isPrivate) {
        mutableStateOf<List<PetalSitePermissionsBridge.SitePermissionSummary>>(emptyList())
    }
    LaunchedEffect(pageUrl, isPrivate) {
        permissionSummary = if (isWebPage) {
            PetalSitePermissionsBridge.getSitePermissionsSummary(context, pageUrl, isPrivate)
        } else {
            emptyList()
        }
    }

    var showBlockElementDialog by remember { mutableStateOf(false) }
    var customCssSelector by remember { mutableStateOf("") }

    val expandEnterSpec = remember(reduceMotion, lowEnd) {
        if (reduceMotion || lowEnd) snap<androidx.compose.ui.unit.IntSize>() else PetalSpring.expressiveBouncySpatial()
    }
    val expandExitSpec = remember(reduceMotion, lowEnd) {
        if (reduceMotion || lowEnd) snap<androidx.compose.ui.unit.IntSize>() else PetalSpring.calm()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Header: site icon, domain (1 line ellipsized), 1-line status, reload & close
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ShieldHeaderSiteIcon(
                tabIcon = activeTab?.content?.icon,
                isPrivate = isPrivate,
                isWebPage = isWebPage
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayDomain,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val statusIcon = when (connectionStatus) {
                        ConnectionStatus.SECURE -> Icons.Rounded.Lock
                        ConnectionStatus.NOT_SECURE -> Icons.Rounded.LockOpen
                        ConnectionStatus.UNKNOWN -> Icons.Rounded.HelpOutline
                    }
                    val statusTint = when (connectionStatus) {
                        ConnectionStatus.SECURE -> MaterialTheme.colorScheme.primary
                        ConnectionStatus.NOT_SECURE -> MaterialTheme.colorScheme.error
                        ConnectionStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val statusText = when (connectionStatus) {
                        ConnectionStatus.SECURE -> stringResource(R.string.shield_status_secure)
                        ConnectionStatus.NOT_SECURE -> stringResource(R.string.shield_status_not_secure)
                        ConnectionStatus.UNKNOWN -> stringResource(R.string.shield_status_unknown)
                    }
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusTint,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusTint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        pendingGlobalReload = false
                        onReloadRequested()
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.ui_reload),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.ui_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 2. Tracker strip: blocked on this page + total stopped + non-truncating category breakdown
        PetalHeroCard(
            modifier = Modifier.fillMaxWidth(),
            shape = PetalContainmentShapes.HeroInner,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.Baseline,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = animatedPageBlocked.toString(),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.shield_blocked_on_page),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = if (totalThreatsStopped > 0L) {
                            stringResource(R.string.shield_total_stopped_format, totalThreatsStopped.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                        } else {
                            stringResource(R.string.shield_no_trackers_detected)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ShieldCategoryChip(stringResource(R.string.shield_category_ads_format, trackerSummary.ads))
                    ShieldCategoryChip(stringResource(R.string.shield_category_trackers_format, trackerSummary.trackers))
                    ShieldCategoryChip(stringResource(R.string.shield_category_social_format, trackerSummary.social))
                    ShieldCategoryChip(stringResource(R.string.shield_category_cookies_format, trackerSummary.cookies))
                }
            }
        }

        // 3. 2x2 Toggle tiles (BetaToggleTile style)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val trustStateLabel = stringResource(
                    if (isSiteTrusted) R.string.shield_state_trusted else R.string.shield_state_protected
                )
                ShieldToggleTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Verified,
                    iconOff = Icons.Rounded.Shield,
                    label = stringResource(R.string.shield_tile_this_site),
                    stateText = trustStateLabel,
                    checked = isSiteTrusted,
                    enabled = isWebPage && cleanHost.isNotEmpty(),
                    role = ShieldTileRole.Tertiary,
                    reduceMotion = reduceMotion || lowEnd,
                    onToggle = { trusted ->
                        isSiteTrusted = trusted
                        geckoExceptionPresent = trusted
                        val tabId = activeTab?.id
                        if (trusted) {
                            PetalAdBlockEngine.addDomainToWhitelist(context, cleanHost, isPrivate = isPrivate)
                            if (tabId != null) {
                                trackingUseCases.addException(tabId, persistInPrivateMode = false)
                            }
                        } else {
                            PetalAdBlockEngine.removeDomainFromWhitelist(context, cleanHost, isPrivate = isPrivate)
                            if (tabId != null) {
                                trackingUseCases.removeException(tabId)
                            }
                        }
                        onReloadRequested()
                    }
                )

                ShieldToggleTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Shield,
                    iconOff = Icons.Rounded.GppMaybe,
                    label = stringResource(R.string.shield_tile_shield),
                    stateText = stringResource(if (globalAdBlock) R.string.menu_state_on else R.string.menu_state_off),
                    checked = globalAdBlock,
                    role = ShieldTileRole.Primary,
                    reduceMotion = reduceMotion || lowEnd,
                    onToggle = { checked ->
                        globalAdBlock = checked
                        sp.edit()
                            .putBoolean("sp_ad_block", checked)
                            .putBoolean("profileStandard_adBlock", checked)
                            .apply()
                        PetalAdBlockEngine.setAdBlockEnabled(context, checked)
                        PetalGeckoRuntime.syncPreferences(sp)
                        onToggleAdBlock(checked)
                        pendingGlobalReload = true
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ShieldToggleTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Cookie,
                    iconOff = Icons.Rounded.Cookie,
                    label = stringResource(R.string.shield_tile_third_party_cookies),
                    stateText = stringResource(if (blockThirdPartyCookies) R.string.menu_state_on else R.string.menu_state_off),
                    checked = blockThirdPartyCookies,
                    role = ShieldTileRole.Secondary,
                    reduceMotion = reduceMotion || lowEnd,
                    onToggle = { checked ->
                        blockThirdPartyCookies = checked
                        sp.edit()
                            .putBoolean("sp_block_third_party_cookies", checked)
                            .putBoolean("profileStandard_cookiesThirdParty", !checked)
                            .apply()
                        PetalGeckoRuntime.syncPreferences(sp)
                        pendingGlobalReload = true
                    }
                )

                ShieldToggleTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Https,
                    iconOff = Icons.Rounded.LockOpen,
                    label = stringResource(R.string.shield_tile_https_only),
                    stateText = stringResource(if (httpsOnly) R.string.menu_state_on else R.string.menu_state_off),
                    checked = httpsOnly,
                    role = ShieldTileRole.Secondary,
                    reduceMotion = reduceMotion || lowEnd,
                    onToggle = { checked ->
                        httpsOnly = checked
                        sp.edit()
                            .putBoolean("sp_https_only", checked)
                            .putBoolean("profileStandard_httpsOnly", checked)
                            .apply()
                        PetalGeckoRuntime.syncPreferences(sp)
                        pendingGlobalReload = true
                    }
                )
            }
        }

        // HTTP bypass row: shown only on HTTP when HTTPS-only is on; reloads immediately.
        if (isHttp && httpsOnly && cleanHost.isNotEmpty()) {
            PetalSettingsToggleRow(
                title = stringResource(R.string.shield_allow_http_domain),
                subtitle = if (isHttpsBypassed) {
                    stringResource(R.string.shield_allow_http_bypassed, cleanHost)
                } else {
                    stringResource(R.string.shield_allow_http_blocked)
                },
                icon = Icons.Rounded.NoEncryption,
                checked = isHttpsBypassed,
                onCheckedChange = { checked ->
                    isHttpsBypassed = checked
                    PetalHapticEngine.getInstance(context).selectionChanged(context)
                    if (checked) {
                        PetalHttpsOnlyWhitelist.addBypass(context, cleanHost, isPrivate = isPrivate)
                    } else {
                        PetalHttpsOnlyWhitelist.removeBypass(context, cleanHost, isPrivate = isPrivate)
                    }
                    onReloadRequested()
                },
                position = PetalGroupPosition.SINGLE
            )
        }

        // 4. "More protections" collapsible row (like BetaActionsGroup "More tools")
        val moreTotal = 1 + if (isMoreProtectionsExpanded) (if (isWebPage) 7 else 6) else 0
        Column(modifier = Modifier.fillMaxWidth()) {
            PetalGroupRow(
                icon = Icons.Rounded.Security,
                title = stringResource(R.string.shield_more_protections),
                subtitle = stringResource(R.string.shield_more_protections_subtitle),
                position = petalGroupPositionFor(0, moreTotal),
                onClick = { isMoreProtectionsExpanded = !isMoreProtectionsExpanded },
                trailing = {
                    Icon(
                        imageVector = if (isMoreProtectionsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )

            AnimatedVisibility(
                visible = isMoreProtectionsExpanded,
                enter = expandVertically(animationSpec = expandEnterSpec) + fadeIn(),
                exit = shrinkVertically(animationSpec = expandExitSpec) + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    var idx = 1
                    PetalSettingsToggleRow(
                        title = stringResource(R.string.shield_fingerprint_short),
                        subtitle = stringResource(R.string.shield_fingerprint_desc),
                        icon = Icons.Rounded.Fingerprint,
                        checked = fingerprintProtection,
                        onCheckedChange = { checked ->
                            fingerprintProtection = checked
                            PetalHapticEngine.getInstance(context).selectionChanged(context)
                            sp.edit()
                                .putBoolean("sp_fingerprint_protection", checked)
                                .putBoolean("profileStandard_fingerPrintProtection", checked)
                                .apply()
                            PetalGeckoRuntime.syncPreferences(sp)
                            pendingGlobalReload = true
                        },
                        position = petalGroupPositionFor(idx++, moreTotal),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    PetalSettingsToggleRow(
                        title = stringResource(R.string.shield_webrtc_short),
                        subtitle = stringResource(R.string.shield_webrtc_desc),
                        icon = Icons.Rounded.WifiProtectedSetup,
                        checked = webrtcProtection,
                        onCheckedChange = { checked ->
                            webrtcProtection = checked
                            PetalHapticEngine.getInstance(context).selectionChanged(context)
                            sp.edit()
                                .putBoolean("sp_webrtc_protection", checked)
                                .putBoolean("profileStandard_webrtcProtection", checked)
                                .apply()
                            PetalGeckoRuntime.syncPreferences(sp)
                            pendingGlobalReload = true
                        },
                        position = petalGroupPositionFor(idx++, moreTotal),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    PetalSettingsToggleRow(
                        title = stringResource(R.string.shield_dnt_gpc_short),
                        subtitle = stringResource(R.string.shield_dnt_gpc_desc),
                        icon = Icons.Rounded.Security,
                        checked = dntGpc,
                        onCheckedChange = { checked ->
                            dntGpc = checked
                            PetalHapticEngine.getInstance(context).selectionChanged(context)
                            sp.edit()
                                .putBoolean("sp_dnt_gpc", checked)
                                .putBoolean("profileStandard_dnt", checked)
                                .apply()
                            PetalGeckoRuntime.syncPreferences(sp)
                            pendingGlobalReload = true
                        },
                        position = petalGroupPositionFor(idx++, moreTotal),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    PetalGroupRow(
                        icon = Icons.Rounded.Bolt,
                        title = stringResource(R.string.shield_zap_element),
                        subtitle = stringResource(R.string.shield_zap_element_desc),
                        enabled = isWebPage,
                        position = petalGroupPositionFor(idx++, moreTotal),
                        onClick = {
                            onDismiss()
                            if (activity is MainActivity) {
                                PetalElementZapper.launch(activity, activity.currentAlbumController)
                            } else if (tabController != null) {
                                PetalElementZapper.launch(tabController)
                            }
                        },
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    PetalGroupRow(
                        icon = Icons.Rounded.Block,
                        title = stringResource(R.string.shield_block_element),
                        subtitle = stringResource(R.string.shield_block_element_desc),
                        enabled = isWebPage && cleanHost.isNotEmpty(),
                        position = petalGroupPositionFor(idx++, moreTotal),
                        onClick = { showBlockElementDialog = true },
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    // AdGuard add-on card: collapses to one line when installed.
                    if (adguardExt != null) {
                        PetalGroupRow(
                            icon = Icons.Rounded.Verified,
                            title = stringResource(R.string.shield_adguard_active),
                            subtitle = null,
                            position = petalGroupPositionFor(idx++, moreTotal),
                            onClick = {
                                onDismiss()
                                activity?.let { act ->
                                    PetalExtensionManager.triggerBrowserAction(adguardExt.id, act)
                                }
                            },
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    } else {
                        PetalGroupRow(
                            icon = Icons.Rounded.Extension,
                            title = if (isInstallingAdGuard) {
                                stringResource(R.string.shield_adguard_installing)
                            } else {
                                stringResource(R.string.shield_adguard_install_title)
                            },
                            subtitle = stringResource(R.string.shield_adguard_install_subtitle),
                            enabled = !isInstallingAdGuard,
                            position = petalGroupPositionFor(idx++, moreTotal),
                            onClick = {
                                isInstallingAdGuard = true
                                val adguardEntry = PetalExtensionManager.catalog.firstOrNull { it.id == "adguard-adblocker" }
                                val installUrl = adguardEntry?.downloadUrl
                                    ?: "https://addons.mozilla.org/firefox/downloads/latest/adguard-adblocker/latest.xpi"
                                val connectedMsg = context.getString(R.string.shield_adguard_connected)
                                val failedMsg = context.getString(R.string.shield_adguard_install_failed)
                                PetalExtensionManager.install(installUrl, autoConfirm = true) { success, msg ->
                                    isInstallingAdGuard = false
                                    Toast.makeText(
                                        context,
                                        msg ?: if (success) connectedMsg else failedMsg,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    // Site permissions summary row (from PetalSitePermissionsBridge)
                    if (isWebPage) {
                        val allowedCount = permissionSummary.count { it.status == SitePermissions.Status.ALLOWED }
                        val blockedCount = permissionSummary.count { it.status == SitePermissions.Status.BLOCKED }
                        val permSubtitle = if (allowedCount == 0 && blockedCount == 0) {
                            stringResource(R.string.shield_site_permissions_none)
                        } else {
                            stringResource(R.string.shield_site_permissions_summary_format, allowedCount, blockedCount)
                        }
                        PetalGroupRow(
                            icon = Icons.Rounded.Tune,
                            title = stringResource(R.string.shield_site_permissions_title),
                            subtitle = permSubtitle,
                            position = petalGroupPositionFor(idx, moreTotal),
                            onClick = {
                                if (activity != null) {
                                    onDismiss()
                                    val controller = (activity as? MainActivity)?.currentAlbumController ?: tabController
                                    PetalSiteInfoBridge.showSiteInfoBottomSheet(activity, controller) {}
                                }
                            },
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }

        // 5. Footer: "Reload to apply" pill for global changes
        AnimatedVisibility(
            visible = pendingGlobalReload,
            enter = expandVertically(animationSpec = expandEnterSpec) + fadeIn(),
            exit = shrinkVertically(animationSpec = expandExitSpec) + fadeOut()
        ) {
            FilledTonalButton(
                onClick = {
                    pendingGlobalReload = false
                    onReloadRequested()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp),
                shape = CircleShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.shield_reload_to_apply),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
    }

    if (showBlockElementDialog) {
        PetalAlertDialog(
            onDismissRequest = { showBlockElementDialog = false },
            title = stringResource(R.string.shield_block_element_dialog_title, cleanHost),
            message = stringResource(R.string.shield_block_element_dialog_message),
            confirmText = stringResource(R.string.shield_block_action),
            onConfirm = {
                if (customCssSelector.isNotBlank()) {
                    PetalElementBlocker.addSelector(context, cleanHost, customCssSelector.trim())
                    onReloadRequested()
                    showBlockElementDialog = false
                    customCssSelector = ""
                }
            },
            dismissText = stringResource(R.string.ui_cancel),
            onDismiss = { showBlockElementDialog = false },
            confirmEnabled = customCssSelector.isNotBlank(),
            content = {
                TextField(
                    value = customCssSelector,
                    onValueChange = { customCssSelector = it },
                    placeholder = { Text(stringResource(R.string.shield_block_element_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}

@Composable
private fun ShieldHeaderSiteIcon(
    tabIcon: Bitmap?,
    isPrivate: Boolean,
    isWebPage: Boolean
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (tabIcon != null && isWebPage && !isPrivate) {
                Image(
                    bitmap = tabIcon.asImageBitmap(),
                    contentDescription = stringResource(R.string.ui_site_favicon),
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
            } else {
                Icon(
                    imageVector = if (isPrivate) Icons.Rounded.VisibilityOff else Icons.Rounded.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ShieldCategoryChip(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShieldToggleTile(
    icon: ImageVector,
    iconOff: ImageVector,
    label: String,
    stateText: String,
    checked: Boolean,
    role: ShieldTileRole,
    reduceMotion: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val context = LocalContext.current
    var pressed by remember { mutableStateOf(false) }

    val container by animateColorAsState(
        targetValue = if (checked) role.container() else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = if (reduceMotion) snap() else PetalSpring.calm(),
        label = "ShieldToggleContainer"
    )
    val content by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            checked -> role.content()
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = if (reduceMotion) snap() else PetalSpring.calm(),
        label = "ShieldToggleContent"
    )
    val corner by animateDpAsState(
        targetValue = if (checked) 24.dp else 14.dp,
        animationSpec = PetalSpring.expressiveBouncySpatial<Dp>().orSnap(reduceMotion),
        label = "ShieldToggleCorner"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) PetalSpring.PRESS_SCALE_CARD else 1f,
        animationSpec = PetalSpring.expressiveBouncySpatial<Float>().orSnap(reduceMotion),
        label = "ShieldTogglePress"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (checked) 1.08f else 1f,
        animationSpec = PetalSpring.expressiveBouncySpatial<Float>().orSnap(reduceMotion),
        label = "ShieldToggleIconScale"
    )
    val shape = RoundedCornerShape(corner.coerceAtLeast(0.dp))

    Row(
        modifier = modifier
            .heightIn(min = 58.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .shieldPressTracker { pressed = it }
            .petalSheetSurface(shape = shape, color = container, onClick = null, sheen = 0.35f)
            .combinedClickable(
                enabled = enabled,
                role = Role.Switch,
                onClickLabel = label,
                onClick = {
                    PetalHapticEngine.getInstance(context).selectionChanged(context)
                    onToggle(!checked)
                }
            )
            .semantics(mergeDescendants = true) { stateDescription = stateText }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Crossfade(
            targetState = checked,
            animationSpec = if (reduceMotion) snap() else PetalSpring.calm(),
            label = "ShieldToggleIcon"
        ) { on ->
            Icon(
                imageVector = if (on) icon else iconOff,
                contentDescription = null,
                tint = content,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stateText,
                style = MaterialTheme.typography.labelSmall,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
