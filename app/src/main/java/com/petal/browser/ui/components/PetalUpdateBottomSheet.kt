package com.petal.browser.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.haptics.PetalHapticEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

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
        com.petal.browser.unit.UpdateUnit.checkForUpdates(activity, isLaunchCheck)
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
            "1.9.2"
        }

        return listOf(
            PetalUpdateInfo(
                versionName = "v$currentVer",
                releaseNotes = "* **Appearance Hero Animations**: Added RvSystem-Monitor inspired spring scale physics & infinite shimmer sweep to active theme mini cards in Settings.\n* **Launcher Wallpaper Preview**: Added dynamic system launcher preview during predictive back on Home screen.",
                downloadUrl = "https://github.com/shreyagarwal72/petal/releases",
                releaseUrl = "https://github.com/shreyagarwal72/petal/releases"
            ),
            PetalUpdateInfo(
                versionName = "v1.9.1",
                releaseNotes = "* **Predictive Back Launcher Preview**: Added system launcher desktop preview behind the Home screen during predictive back exit.\n* **Profile Picture Visibility & PetalSlider**: Fixed custom gallery avatar loading with dynamic Coil cache keying and added PetalSlider to profile picture cropper dialog.",
                downloadUrl = "https://github.com/shreyagarwal72/petal/releases",
                releaseUrl = "https://github.com/shreyagarwal72/petal/releases/tag/v1.9.1"
            ),
            PetalUpdateInfo(
                versionName = "v1.9.0",
                releaseNotes = "* **Fixed Files Integration**: Merged updated project core composables, Glance widgets, and predictive back junction components.",
                downloadUrl = "https://github.com/shreyagarwal72/petal/releases",
                releaseUrl = "https://github.com/shreyagarwal72/petal/releases/tag/v1.9.0"
            ),
            PetalUpdateInfo(
                versionName = "v1.8.9",
                releaseNotes = "* **Profile Picture Image Cropper**: Added interactive Profile Picture Image Cropper dialog (`ProfilePictureCropDialog`) with circular crop viewport, pinch-to-zoom (1.0x - 4.0x), pan gesture alignment, rotation (90° steps), and anti-aliased bitmap output.\n* **Account Page Cleanup**: Removed obsolete Google Passkey option.",
                downloadUrl = "https://github.com/shreyagarwal72/petal/releases",
                releaseUrl = "https://github.com/shreyagarwal72/petal/releases/tag/v1.8.9"
            ),
            PetalUpdateInfo(
                versionName = "v1.8.8",
                releaseNotes = "* **Dynamic Predictive Back Underlay Preview**: Added `PetalDynamicUnderlayPreview()` to dynamically render active web page preview cards behind settings & secondary screens when a web page is open.",
                downloadUrl = "https://github.com/shreyagarwal72/petal/releases",
                releaseUrl = "https://github.com/shreyagarwal72/petal/releases/tag/v1.8.8"
            ),
            PetalUpdateInfo(
                versionName = "v1.8.7",
                releaseNotes = "* **Website Back Navigation & Flicker Fix**: Fixed website back gestures by detecting active WebView web history (`canNinjaGoBack()`) in `PetalPredictiveJunction.kt`, bypassing activity scale-down effects.",
                downloadUrl = "https://github.com/shreyagarwal72/petal/releases",
                releaseUrl = "https://github.com/shreyagarwal72/petal/releases/tag/v1.8.7"
            ),
            PetalUpdateInfo(
                versionName = "v1.8.6",
                releaseNotes = "* **Foreground Service Crash Fix**: Made `startForeground()` the synchronous first call in `PetalDownloadService.onStartCommand()`.",
                downloadUrl = "https://github.com/shreyagarwal72/petal/releases",
                releaseUrl = "https://github.com/shreyagarwal72/petal/releases/tag/v1.8.6"
            )
        )
    }

    private fun showChangelogDialog(activity: ComponentActivity, releases: List<PetalUpdateInfo>) {
        try {
            val dialog = BottomSheetDialog(activity)
            dialog.behavior.skipCollapsed = true
            dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            val composeView = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    PetalExpressiveTheme {
                        PetalChangelogHistorySheetContent(
                            releases = releases,
                            onDismiss = {
                                try { dialog.dismiss() } catch (ignored: Exception) {}
                            }
                        )
                    }
                }
            }
            dialog.setContentView(composeView)
            dialog.setOnShowListener {
                val sheetView = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                if (sheetView != null) {
                    com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheetView).apply {
                        skipCollapsed = true
                        state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                    }
                }
            }
            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun showUpdateSheet(activity: ComponentActivity, updateInfo: PetalUpdateInfo) {
        try {
            val dialog = BottomSheetDialog(activity)
            dialog.behavior.skipCollapsed = true
            dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            val composeView = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    PetalExpressiveTheme {
                        PetalUpdateSheetContent(
                            updateInfo = updateInfo,
                            onDismiss = {
                                try { dialog.dismiss() } catch (ignored: Exception) {}
                            }
                        )
                    }
                }
            }
            dialog.setContentView(composeView)
            dialog.setOnShowListener {
                val sheetView = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                if (sheetView != null) {
                    com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheetView).apply {
                        skipCollapsed = true
                        state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                    }
                }
            }
            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
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

@Composable
fun PetalUpdateSheetContent(
    updateInfo: PetalUpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }

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

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 600.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )

            // Header Icon Badge
            Surface(
                shape = CircleShape,
                color = if (updateInfo.isUpdateAvailable) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (updateInfo.isUpdateAvailable) Icons.Rounded.SystemUpdate else Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = if (updateInfo.isUpdateAvailable) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Title & Subtitle
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (updateInfo.isUpdateAvailable) "Update Available" else "Petal Browser is Up to Date",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = if (updateInfo.isUpdateAvailable) "Squashed bugs, added magic. Ready to install!" else "Running the latest release (${updateInfo.versionName})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Release Notes Container
            val notesToDisplay = fetchedNotes ?: updateInfo.releaseNotes
            if (isFetchingNotes) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LinearRipplingWavyProgressIndicator(
                        progress = null,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        height = 6.dp,
                        strokeWidth = 3.5.dp
                    )
                }
            } else if (notesToDisplay.isNotBlank()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "What's New in ${updateInfo.versionName}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState())
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

            // Action Buttons
            if (updateInfo.isUpdateAvailable && updateInfo.downloadUrl.isNotBlank()) {
                if (isDownloading) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearRipplingWavyProgressIndicator(
                            progress = downloadProgress / 100f,
                            modifier = Modifier.fillMaxWidth(),
                            height = 8.dp,
                            strokeWidth = 4.dp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Downloading update... $downloadProgress%",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.HEAVY_CLICK, 0.9f)
                            isDownloading = true
                            coroutineScope.launch(Dispatchers.IO) {
                                downloadAndInstallApk(
                                    context = context,
                                    apkUrl = updateInfo.downloadUrl,
                                    version = updateInfo.versionName,
                                    onProgress = { progress ->
                                        coroutineScope.launch(Dispatchers.Main) {
                                            downloadProgress = progress
                                            if (progress >= 100) isDownloading = false
                                        }
                                    }
                                )
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Install Update Now")
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
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("View Release Notes on GitHub")
                }
            }

            Spacer(Modifier.height(4.dp))
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

private fun downloadAndInstallApk(
    context: android.content.Context,
    apkUrl: String,
    version: String,
    onProgress: (Int) -> Unit
) {
    try {
        val downloadsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
        val apkFile = File(downloadsDir, "petal_update_${version.replace(Regex("[^a-zA-Z0-9]"), "_")}.apk")
        if (apkFile.exists()) apkFile.delete()

        var currentUrl = apkUrl
        var conn: HttpURLConnection? = null
        var redirects = 0
        while (redirects < 5) {
            val url = URL(currentUrl)
            conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "PetalBrowserApp/$version")
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            val status = conn.responseCode
            if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                val redirectUrl = conn.getHeaderField("Location")
                if (!redirectUrl.isNullOrEmpty()) {
                    currentUrl = redirectUrl
                    redirects++
                    continue
                }
            }
            break
        }

        if (conn == null || conn.responseCode != HttpURLConnection.HTTP_OK) {
            throw Exception("HTTP connection failed: ${conn?.responseCode}")
        }

        val totalSize = conn.contentLength
        val inputStream = conn.inputStream
        val outputStream = apkFile.outputStream()

        val buffer = ByteArray(8192)
        var downloaded = 0
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
            downloaded += bytesRead
            if (totalSize > 0) {
                onProgress((downloaded * 100L / totalSize).toInt())
            }
        }
        outputStream.flush()
        outputStream.close()
        inputStream.close()

        onProgress(100)
        installApk(context, apkFile)
    } catch (e: Exception) {
        e.printStackTrace()
        onProgress(0)
    }
}

private fun installApk(context: android.content.Context, apkFile: File) {
    try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                com.petal.browser.view.NinjaToast.show(context, "Please grant permission to install updates")
                return
            }
        }

        val apkUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(installIntent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
fun PetalChangelogHistorySheetContent(
    releases: List<PetalUpdateInfo>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 580.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Changelog History",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Previous browser releases & release notes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))

            if (releases.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No changelog releases found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(releases) { rel ->
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    ) {
                                        Text(
                                            text = rel.versionName,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                    if (rel.releaseUrl.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rel.releaseUrl))
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.OpenInNew,
                                                contentDescription = "View on GitHub",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                                if (rel.releaseNotes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        PetalMarkdownText(
                                            markdown = rel.releaseNotes,
                                            modifier = Modifier.padding(12.dp)
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
