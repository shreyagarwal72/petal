package com.petal.browser.ui.components

import com.petal.browser.ui.containment.liquidGlassChrome
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.petal.browser.ui.containment.PetalHeroCard
import com.petal.browser.ui.containment.PetalContainmentShapes
import com.petal.browser.ui.containment.rememberPetalGroupPressScale
import com.petal.browser.ui.containment.petalGroupPositionFor
import com.petal.browser.ui.containment.petalGroupShape
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.PetalMaterialShapes
import com.petal.browser.haptics.PetalHapticEngine
import androidx.compose.ui.text.style.TextAlign
import com.petal.browser.view.PetalToast
import com.petal.browser.compose.downloads.formatBytes
import com.petal.browser.compose.downloads.formatSpeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petal.browser.unit.PetalUpdateManager
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

data class PetalUpdateInfo(
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val releaseUrl: String = "https://github.com/shreyagarwal72/petal/releases",
    val isUpdateAvailable: Boolean = false
)

object PetalUpdateSheetBridge {
    private val executor = Executors.newSingleThreadExecutor()

    @JvmStatic
    fun checkForUpdates(activity: ComponentActivity, isLaunchCheck: Boolean) {
        try {
            com.petal.browser.update.PetalPlayUpdateManager.getInstance(activity).checkForUpdates(activity, isLaunchCheck)
        } catch (e: Exception) {
            android.util.Log.w("PetalUpdateSheetBridge", "Could not check for Play Store updates", e)
        }
    }

    @JvmStatic
    fun showChangelogHistorySheet(activity: ComponentActivity) {
        executor.execute {
            var releases: List<PetalUpdateInfo>? = null
            try {
                val url = URL("https://api.github.com/repos/shreyagarwal72/petal/releases")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.setRequestProperty("User-Agent", "PetalBrowserApp")
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                if (conn.responseCode == 200) {
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(conn.inputStream))
                    val sb = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line)
                    }
                    reader.close()
                    val jsonArray = com.google.gson.JsonParser.parseString(sb.toString()).asJsonArray
                    val fetchedReleases = mutableListOf<PetalUpdateInfo>()
                    for (i in 0 until jsonArray.size()) {
                        val obj = jsonArray.get(i).asJsonObject
                        val tag = obj.get("tag_name")?.asString ?: ""
                        val body = obj.get("body")?.asString ?: ""
                        val htmlUrl = obj.get("html_url")?.asString ?: "https://github.com/shreyagarwal72/petal/releases"
                        fetchedReleases.add(PetalUpdateInfo(versionName = tag, releaseNotes = body, downloadUrl = "", releaseUrl = htmlUrl, isUpdateAvailable = false))
                    }
                    if (fetchedReleases.isNotEmpty()) {
                        releases = fetchedReleases
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (releases == null || releases.isEmpty()) {
                releases = getFallbackReleases(activity)
            }

            val finalReleases = releases
            activity.runOnUiThread {
                if (!activity.isFinishing) {
                    showChangelogDialog(activity, finalReleases)
                }
            }
        }
    }

    private fun getFallbackReleases(activity: ComponentActivity): List<PetalUpdateInfo> {
        val currentVer = try {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
        } catch (_: Exception) {
            "2.1"
        }

        return listOf(
            PetalUpdateInfo(
                versionName = "v3.5",
                releaseNotes = """
* **🚀 Smart Omnibox Engine Quick-Switcher**: Instant horizontal chips for Google, DuckDuckGo, Brave, Wikipedia, GitHub, YouTube, and Reddit right inside the search overlay.
* **📋 Smart Clipboard Paste & Search Action Pill**: Automatic clipboard detection with one-tap "Search clipboard" or "Open link" actions and direct input insertion.
* **📍 Haptic Fast-Scrubber & Jump-to-Top/Bottom Pill**: Edge-docked floating capsule indicating live scroll progress with smooth animated jump-to-top and jump-to-bottom actions.
* **⚡ Thumb-Zone Radial Speed Dial Arc**: Long-pressing Home or Menu on the floating bottom bar fans out an ergonomic 5-action one-handed quick launcher with tactile haptics.
* **🛡️ Privacy & Tracker Shield HUD**: Direct tap on the address bar security lock or favicon launches a live privacy diagnostic sheet with tracker counters and whitelist controls.
* **🎵 Persistent Floating Media Island**: Floating bottom pill with play/pause, seek, mute, and speed controls for background and foreground web media.
* **↔️ Edge-to-Edge Tab Switching**: Smooth horizontal swipe gestures directly across the floating bottom navigation bar to switch between adjacent tabs.
* **🔗 Clean Link Parameter Stripper**: One-tap removal of invasive tracking queries (utm_, fbclid, gclid, etc.) from address bar and context menu.
* **🖤 AMOLED High-Contrast Engine**: Deep pitch-black surfaces with refined subtle surface borders and low-power rendering.
* **🔄 Smooth Pull-to-Refresh & Back Gestures**: Fixed session tracking, gesture-exclusion zone management, and seamless Predictive Back support across all pages.
                """.trimIndent(),
                downloadUrl = "https://github.com/shreyagarwal72/petal/releases",
                releaseUrl = "https://github.com/shreyagarwal72/petal/releases"
            ),
            PetalUpdateInfo(
                versionName = "v$currentVer",
                releaseNotes = """
* **Compose Ripple Modernization & Compile Hotfix**: Migrated deprecated `rememberRipple` indication to official Material 3 `ripple()` API in `PetalBottomNavBar.kt`.
* **First-Launch Update Changelog Showcase**: One-time interactive popup on app update highlighting new features and categorized changelogs.
* **Full Official v2.0 Changelog History**: Complete feature documentation and offline changelog history integration.
                """.trimIndent(),
                downloadUrl = "https://github.com/shreyagarwal72/petal/releases",
                releaseUrl = "https://github.com/shreyagarwal72/petal/releases"
            ),
            PetalUpdateInfo(
                versionName = "v2.0",
                releaseNotes = """
* **✨ Real-Time Vector Shape Morphing Background (`androidx.graphics.shapes.Morph`)**: Organic vector shape morphing between all 35 official Material 3 Expressive shapes with luminous gradient halos and kinetic breathing.
* **🎨 Floating Bottom Navigation Bar (Zenith Parity)**: 68dp height, stadium pill shape, 36dp bottom offset, 26dp icons, and dynamic indicator selection.
* **📦 PixelPlayer Expressive Containment System**: Elevated surface container cards with 16dp rounded corners and animated tactile switch thumbs.
* **🖼️ Profile Picture & Interactive Cropper Overhaul**: Circular crop viewport with real-time pinch-to-zoom (1.0x - 4.0x), pan gesture alignment, 90° rotation, and immediate Coil cache updates.
* **🗂️ Clean Settings Categorization & Miscellaneous Hub**: Intuitive reorganization into Appearance & Theme, Accessibility, Privacy & Security, Experimental, and Miscellaneous.
* **⚡ Complete Kotlin & Jetpack Compose Modernization (100% Kotlin)**: Pure Material 3 Expressive UI, predictive back navigation with 24dp background depth blur, and adaptive dynamic theming.
* **🌐 Google Lens & In-App Multi-Volume Media Picker**: Full Google Lens search integration across Omnibox search bar, context menus, and home widgets.
* **🔒 Advanced Privacy, Passkeys & High-Performance Web Engine**: Full WebAuthn & biometric passkey support, AdBlock Trie filtering engine, and 120Hz/144Hz high refresh rate hardware pacing.
                """.trimIndent(),
                downloadUrl = "https://github.com/shreyagarwal72/petal/releases",
                releaseUrl = "https://github.com/shreyagarwal72/petal/releases/tag/v2.0.0"
            ),
            PetalUpdateInfo(
                versionName = "v1.9.9",
                releaseNotes = "* **Material 3 Expressive Container Colors**: High-contrast container elevation model with dynamic palette switching.\n* **Universal Touch Haptics Engine**: Multi-tiered vibration execution for tactile feedback on all Android devices.\n* **Dynamic Per-App Language & Natural Hinglish**: Full conversational Hinglish translations across all string resources.\n* **High Refresh Rate (120Hz/144Hz+) Native Synchronization**: Hardware display mode locking and frame pacing.",
                downloadUrl = "https://github.com/shreyagarwal72/petal/releases",
                releaseUrl = "https://github.com/shreyagarwal72/petal/releases/tag/v1.9.9"
            ),
            PetalUpdateInfo(
                versionName = "v1.9.8",
                releaseNotes = "* **100% Java-to-Kotlin Migration**: Complete codebase conversion to Kotlin with zero remaining Java files.\n* **35 Material 3 Expressive Shapes**: Full system-wide catalog integration across all UI components.\n* **120Hz Smooth Scrolling Physics**: Nested scrolling and hardware-accelerated rendering.",
                downloadUrl = "https://github.com/shreyagarwal72/petal/releases",
                releaseUrl = "https://github.com/shreyagarwal72/petal/releases/tag/v1.9.8"
            ),
            PetalUpdateInfo(
                versionName = "v1.9.4",
                releaseNotes = "* **Google Lens Integration**: In-app camera capture, photo picker, and context menu search.\n* **Multi-Engine Torrent & Magnet Downloader**: 1DM integration, in-app native streamer, and system downloader.\n* **Dynamic Morphing Background Timer**: Periodic ambient background shape rotation with customizable interval.",
                downloadUrl = "https://github.com/shreyagarwal72/petal/releases",
                releaseUrl = "https://github.com/shreyagarwal72/petal/releases/tag/v1.9.4"
            )
        )
    }

    private fun showChangelogDialog(activity: ComponentActivity, releases: List<PetalUpdateInfo>) {
        activity.runOnUiThread {
            try {
                var composeView: ComposeView? = null
                composeView = ComposeView(activity).apply {
                    setViewTreeLifecycleOwner(activity)
                    setViewTreeViewModelStoreOwner(activity)
                    setViewTreeSavedStateRegistryOwner(activity)
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                    setContent {
                        PetalExpressiveTheme {
                            var showSheet by remember { mutableStateOf(true) }
                            if (showSheet) {
                                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                                com.petal.browser.ui.containment.PetalSheet(
                                    onDismissRequest = {
                                        showSheet = false
                                        val parent = composeView?.parent as? android.view.ViewGroup
                                        parent?.removeView(composeView)
                                    },
                                    sheetState = sheetState,
                                ) {
                                    PetalChangelogHistorySheetContent(
                                        releases = releases,
                                        onDismiss = {
                                            showSheet = false
                                            val parent = composeView?.parent as? android.view.ViewGroup
                                            parent?.removeView(composeView)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                val rootView = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
                rootView?.addView(
                    composeView,
                    android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @JvmStatic
    fun showWhatsNewUpdateDialog(activity: ComponentActivity) {
        executor.execute {
            var releases: List<PetalUpdateInfo>? = null
            try {
                val url = URL("https://api.github.com/repos/shreyagarwal72/petal/releases")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.setRequestProperty("User-Agent", "PetalBrowserApp")
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                if (conn.responseCode == 200) {
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(conn.inputStream))
                    val sb = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line)
                    }
                    reader.close()
                    val jsonArray = com.google.gson.JsonParser.parseString(sb.toString()).asJsonArray
                    val fetchedReleases = mutableListOf<PetalUpdateInfo>()
                    for (i in 0 until jsonArray.size()) {
                        val obj = jsonArray.get(i).asJsonObject
                        val tag = obj.get("tag_name")?.asString ?: ""
                        val body = obj.get("body")?.asString ?: ""
                        val htmlUrl = obj.get("html_url")?.asString ?: "https://github.com/shreyagarwal72/petal/releases"
                        fetchedReleases.add(PetalUpdateInfo(versionName = tag, releaseNotes = body, downloadUrl = "", releaseUrl = htmlUrl, isUpdateAvailable = false))
                    }
                    if (fetchedReleases.isNotEmpty()) {
                        releases = fetchedReleases
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (releases == null || releases.isEmpty()) {
                releases = getFallbackReleases(activity)
            }

            val finalReleases = releases
            activity.runOnUiThread {
                if (!activity.isFinishing) {
                    showChangelogDialog(activity, finalReleases)
                }
            }
        }
    }

    @JvmStatic
    fun showUpdateSheet(activity: ComponentActivity, updateInfo: PetalUpdateInfo) {
        activity.runOnUiThread {
            try {
                var composeView: ComposeView? = null
                composeView = ComposeView(activity).apply {
                    setViewTreeLifecycleOwner(activity)
                    setViewTreeViewModelStoreOwner(activity)
                    setViewTreeSavedStateRegistryOwner(activity)
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                    setContent {
                        PetalExpressiveTheme {
                            var showSheet by remember { mutableStateOf(true) }
                            if (showSheet) {
                                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                                val dismissAction: () -> Unit = {
                                    val isDownloading = PetalUpdateManager.downloadState.value is PetalUpdateManager.UpdateDownloadState.Downloading
                                    if (isDownloading) {
                                        PetalToast.show(activity, "Update downloading in background. You can keep browsing.")
                                    }
                                    showSheet = false
                                    val parent = composeView?.parent as? android.view.ViewGroup
                                    parent?.removeView(composeView)
                                }
                                com.petal.browser.ui.containment.PetalSheet(
                                    onDismissRequest = dismissAction,
                                    sheetState = sheetState,
                                    dragHandle = null,
                                ) {
                                    PetalUpdateSheetContent(
                                        updateInfo = updateInfo,
                                        onDismiss = dismissAction
                                    )
                                }
                            }
                        }
                    }
                }
                val rootView = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
                rootView?.addView(
                    composeView,
                    android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun isNewerVersion(latestTag: String, currentVer: String): Boolean {
        val cleanLatest = latestTag.trim().replace(Regex("^[vV]"), "")
        val cleanCurrent = currentVer.trim().replace(Regex("^[vV]"), "")
        val lParts = cleanLatest.split(".")
        val cParts = cleanCurrent.split(".")
        val maxLen = maxOf(lParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val lNum = lParts.getOrNull(i)?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: 0
            val cNum = cParts.getOrNull(i)?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: 0
            if (lNum > cNum) return true
            if (lNum < cNum) return false
        }
        return false
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PetalUpdateSheetContent(
    updateInfo: PetalUpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val currentVersion = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "4.4"
        } catch (_: Exception) {
            "4.4"
        }
    }

    val liveUpdateState by PetalUpdateManager.downloadState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        PetalUpdateManager.initialize(context)
    }

    var fetchedNotes by remember(updateInfo.releaseNotes) { mutableStateOf<String?>(null) }
    var isFetchingNotes by remember(updateInfo.releaseNotes) { mutableStateOf(false) }

    LaunchedEffect(updateInfo.releaseNotes) {
        val notesStr = updateInfo.releaseNotes.trim()
        if (notesStr.startsWith("http://") || notesStr.startsWith("https://")) {
            isFetchingNotes = true
            fetchedNotes = withContext(Dispatchers.IO) {
                fetchMarkdownFromUrl(notesStr)
            }
            isFetchingNotes = false
        } else {
            fetchedNotes = notesStr
            isFetchingNotes = false
        }
    }

    val isGlass = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_containments")
    Surface(
        color = if (isGlass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Bar: Centered drag handle & Top-right Cross (✕) button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                )

                IconButton(
                    onClick = {
                        PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                        onDismiss()
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Status-driven Hero styling (Orion-Store Expressive look)
            val (heroTint, heroIcon, eyebrowText) = when (val state = liveUpdateState) {
                is PetalUpdateManager.UpdateDownloadState.Downloading -> {
                    Triple(MaterialTheme.colorScheme.primary, Icons.Rounded.Downloading, "Update Downloading")
                }
                is PetalUpdateManager.UpdateDownloadState.Paused -> {
                    Triple(MaterialTheme.colorScheme.tertiary, Icons.Rounded.PauseCircle, "Download Paused")
                }
                is PetalUpdateManager.UpdateDownloadState.Completed -> {
                    Triple(Color(0xFF2E7D32), Icons.Rounded.CheckCircle, "Ready to Install")
                }
                is PetalUpdateManager.UpdateDownloadState.Failed -> {
                    Triple(MaterialTheme.colorScheme.error, Icons.Rounded.ErrorOutline, "Download Failed")
                }
                else -> {
                    if (updateInfo.isUpdateAvailable) {
                        Triple(MaterialTheme.colorScheme.primary, Icons.Rounded.SystemUpdate, "New Update Available")
                    } else {
                        Triple(MaterialTheme.colorScheme.secondary, Icons.Rounded.Verified, "All Caught Up")
                    }
                }
            }

            // Hero Badge with 96dp Cookie9Sided shape
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(
                        color = heroTint.copy(alpha = 0.16f),
                        shape = PetalMaterialShapes.Cookie9Sided.toShape()
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = heroIcon,
                    contentDescription = null,
                    tint = heroTint,
                    modifier = Modifier.size(46.dp)
                )
            }

            // Eyebrow, Title & Version Transition Pill
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = eyebrowText.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp
                    ),
                    color = heroTint
                )

                Text(
                    text = if (updateInfo.isUpdateAvailable) {
                        "Petal Browser ${updateInfo.versionName}"
                    } else {
                        "Petal Browser is Up to Date"
                    },
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                // Version Transition Pill (Orion-Store style)
                if (updateInfo.isUpdateAvailable) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "v$currentVersion",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.Rounded.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "v${updateInfo.versionName.replace(Regex("^[vV]"), "")}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = "v$currentVersion (Current)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }

                Text(
                    text = if (updateInfo.isUpdateAvailable) {
                        "Squashed bugs, added magic. Ready to download & install!"
                    } else {
                        "Running the latest release with all the newest features."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Live Download Card (When Downloading, Paused, or Completed)
            when (val state = liveUpdateState) {
                is PetalUpdateManager.UpdateDownloadState.Downloading -> {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Text(
                                        text = "Downloading update...",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = "${state.progress}%",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            LinearWavyProgressIndicator(
                                progress = { (state.progress / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val downloadedStr = formatBytes(state.downloadedBytes)
                                val totalStr = if (state.totalBytes > 0) formatBytes(state.totalBytes) else "..."
                                Text(
                                    text = "$downloadedStr / $totalStr",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (state.bytesPerSecond > 0) formatSpeed(state.bytesPerSecond) else "Connecting...",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                                        PetalUpdateManager.pauseUpdateDownload(context)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(Icons.Rounded.PauseCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Pause")
                                }
                                FilledTonalButton(
                                    onClick = {
                                        PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                                        onDismiss()
                                        PetalToast.show(context, "Update downloading in background. You can keep browsing.")
                                    },
                                    modifier = Modifier.weight(1.2f),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text("Keep Browsing")
                                }
                            }
                        }
                    }
                }
                is PetalUpdateManager.UpdateDownloadState.Paused -> {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.tertiary)
                                    )
                                    Text(
                                        text = "Download paused",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = "${state.progress}%",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            LinearWavyProgressIndicator(
                                progress = { (state.progress / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp),
                                color = MaterialTheme.colorScheme.tertiary,
                                trackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.6f)
                                        PetalUpdateManager.resumeUpdateDownload(context)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Resume")
                                }
                                OutlinedButton(
                                    onClick = {
                                        PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                                        PetalUpdateManager.cancelUpdateDownload(context)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }
                is PetalUpdateManager.UpdateDownloadState.Completed -> {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFF2E7D32).copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Download Complete",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Petal v${state.version} is ready to be inspected & installed.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.HEAVY_CLICK, 0.9f)
                                    PetalUpdateManager.installActiveUpdate(context)
                                },
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                Icon(Icons.Rounded.DownloadDone, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Install Update", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
                else -> {
                    if (updateInfo.isUpdateAvailable && updateInfo.downloadUrl.isNotBlank()) {
                        Button(
                            onClick = {
                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.HEAVY_CLICK, 0.9f)
                                PetalUpdateManager.startUpdateDownload(
                                    context = context,
                                    downloadUrl = updateInfo.downloadUrl,
                                    version = updateInfo.versionName
                                )
                            },
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Download & Install Update",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // Release Notes / What's New Container
            val notesToDisplay = fetchedNotes ?: updateInfo.releaseNotes
            if (isFetchingNotes) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LinearWavyProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(10.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                }
            } else if (notesToDisplay.isNotBlank()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = stringResource(R.string.ui_what_s_new),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    val notesShape = RoundedCornerShape(24.dp)
                    Surface(
                        shape = notesShape,
                        color = if (isGlass) com.petal.browser.ui.containment.petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isGlass) Modifier.liquidGlassChrome(notesShape, enabled = true) else Modifier)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            PetalMarkdownText(
                                markdown = notesToDisplay,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            if (updateInfo.releaseUrl.isNotBlank()) {
                OutlinedButton(
                    onClick = {
                        PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.6f)
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.releaseUrl))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_view_release_notes_on_github))
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun fetchMarkdownFromUrl(urlStr: String): String {
    return try {
        var targetUrl = urlStr
        val gitHubReleaseRegex = Regex("https?://github\\.com/([^/]+)/([^/]+)/releases/tag/(.+)")
        val match = gitHubReleaseRegex.matchEntire(urlStr)
        if (match != null) {
            val owner = match.groupValues[1]
            val repo = match.groupValues[2]
            val tag = match.groupValues[3]
            targetUrl = "https://api.github.com/repos/$owner/$repo/releases/tags/$tag"
        }

        val url = URL(targetUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json, text/plain, */*")
        conn.connectTimeout = 8000
        conn.readTimeout = 8000

        if (conn.responseCode == 200) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            try {
                val jsonObject = Gson().fromJson(responseText, JsonObject::class.java)
                if (jsonObject.has("body")) {
                    return jsonObject.get("body").asString
                }
            } catch (_: Exception) {}
            responseText
        } else {
            ""
        }
    } catch (e: Exception) {
        e.printStackTrace()
        ""
    }
}



@Composable
fun PetalChangelogHistorySheetContent(
    releases: List<PetalUpdateInfo>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val isGlass = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_containments")
    Surface(
        color = if (isGlass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Expressive Badge (M3 Expressive Flower Shape)
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(PetalMaterialShapes.Flower.toShape())
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.ui_changelog_history),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.ui_previous_browser_releases_release_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(18.dp))

            if (releases.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.ui_no_changelog_releases_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 540.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(releases.size) { index ->
                        val rel = releases[index]
                        val position = petalGroupPositionFor(index, releases.size)
                        val interactionSource = remember { MutableInteractionSource() }
                        val pressScale = rememberPetalGroupPressScale(interactionSource)

                        Card(
                            shape = petalGroupShape(position),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isGlass) com.petal.browser.ui.containment.petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .scale(pressScale)
                                .then(if (isGlass) Modifier.liquidGlassChrome(petalGroupShape(position), enabled = true) else Modifier)
                                .animateContentSize()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Expressive Version Pill Badge
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = if (index == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                        border = BorderStroke(
                                            1.dp,
                                            if (index == 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                                    )
                                            )
                                            Text(
                                                text = rel.versionName,
                                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                color = if (index == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }

                                    // Action to Open GitHub Release Link
                                    if (rel.releaseUrl.isNotBlank()) {
                                        FilledTonalIconButton(
                                            onClick = {
                                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rel.releaseUrl))
                                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            },
                                            modifier = Modifier.size(36.dp),
                                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        ) {
                                            Icon(
                                                Icons.Rounded.OpenInNew,
                                                contentDescription = stringResource(R.string.ui_view_on_github),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                if (rel.releaseNotes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        PetalMarkdownText(
                                            markdown = rel.releaseNotes,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
