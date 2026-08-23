package com.petal.browser.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
        if (activity.isFinishing) return

        executor.execute {
            try {
                var currentVerName = "v1.3.8"
                try {
                    val pInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
                    currentVerName = "v${pInfo.versionName}"
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val url = URL("https://api.github.com/repos/shreyagarwal72/petal/releases/latest")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) PetalBrowserApp/$currentVerName")

                if (conn.responseCode == 200) {
                    val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = Gson().fromJson(responseText, JsonObject::class.java)

                    val latestTag = json.get("tag_name")?.asString ?: currentVerName
                    var body = json.get("body")?.asString?.trim() ?: ""
                    if (body.isBlank()) {
                        body = """
                            ### Release Keynotes & What's New
                            * **Ever-Haptics Tactile Engine**: Added high-precision haptic feedback adapted from Ever-Haptics with interactive pattern selector and preview in Accessibility settings.
                            * **Page Resizing & Floating Navbar Fix**: Pages that do not have a bottom navbar and pages with Floating Navbar mode active no longer undergo unwanted layout resizing or bottom padding.
                            * **Homepage Rendering Stability**: Resolved blank screen issues when opening new tabs or returning to Petal Home.
                        """.trimIndent()
                    }
                    val htmlUrl = json.get("html_url")?.asString ?: "https://github.com/shreyagarwal72/petal/releases"

                    var downloadUrl = htmlUrl
                    if (json.has("assets")) {
                        val assets = json.getAsJsonArray("assets")
                        for (i in 0 until assets.size()) {
                            val asset = assets.get(i).asJsonObject
                            val name = asset.get("name")?.asString ?: ""
                            if (name.endsWith(".apk")) {
                                downloadUrl = asset.get("browser_download_url")?.asString ?: downloadUrl
                                break
                            }
                        }
                    }

                    val isAvailable = isNewerVersion(latestTag, currentVerName)

                    activity.runOnUiThread {
                        if (activity.isFinishing) return@runOnUiThread
                        if (isAvailable || !isLaunchCheck) {
                            showUpdateSheet(
                                activity = activity,
                                updateInfo = PetalUpdateInfo(
                                    versionName = if (isAvailable) latestTag else currentVerName,
                                    releaseNotes = body,
                                    downloadUrl = downloadUrl,
                                    releaseUrl = htmlUrl,
                                    isUpdateAvailable = isAvailable
                                )
                            )
                        }
                    }
                } else if (!isLaunchCheck) {
                    activity.runOnUiThread {
                        showUpdateSheet(
                            activity = activity,
                            updateInfo = PetalUpdateInfo(
                                versionName = currentVerName,
                                releaseNotes = "You are currently running the latest build of Petal Browser ($currentVerName).\n\n### Release Keynotes & What's New\n* **Ever-Haptics Tactile Engine**: Full integration of high-precision haptics with pattern test preview under Accessibility settings.\n* **No Layout Resizing**: Pages without a bottom navbar and floating navbars no longer resize or shrink page content.\n* **Homepage Stability**: Solved blank screen issues on cold start and new tabs.",
                                downloadUrl = "",
                                releaseUrl = "https://github.com/shreyagarwal72/petal/releases",
                                isUpdateAvailable = false
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (!isLaunchCheck) {
                    activity.runOnUiThread {
                        val currentVerName = try { "v" + activity.packageManager.getPackageInfo(activity.packageName, 0).versionName } catch (ex: Exception) { "v1.3.5" }
                        showUpdateSheet(
                            activity = activity,
                            updateInfo = PetalUpdateInfo(
                                versionName = currentVerName,
                                releaseNotes = "Petal Browser is running build $currentVerName.\n\n### Release Keynotes & What's New\n* **Ever-Haptics Tactile Engine**: Full integration of high-precision haptics with pattern test preview under Accessibility settings.\n* **No Layout Resizing**: Pages without a bottom navbar and floating navbars no longer resize or shrink page content.\n* **Homepage Stability**: Solved blank screen issues on cold start and new tabs.",
                                downloadUrl = "",
                                releaseUrl = "https://github.com/shreyagarwal72/petal/releases",
                                isUpdateAvailable = false
                            )
                        )
                    }
                }
            }
        }
    }

    @JvmStatic
    fun showUpdateSheet(activity: ComponentActivity, updateInfo: PetalUpdateInfo) {
        try {
            val dialog = BottomSheetDialog(activity)
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
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Drag handle / bar indicator
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )

            // Header Icon
            Surface(
                shape = RoundedCornerShape(20.dp),
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

            // Title
            Text(
                text = if (updateInfo.isUpdateAvailable) "Update Available" else "Petal Browser is Up to Date",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = if (updateInfo.isUpdateAvailable) "Squashed bugs, added magic. You know what to do" else "You're running the latest build (${updateInfo.versionName})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        PetalMarkdownText(
                            markdown = notesToDisplay,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        )
                    }
                }
            }

            // Action Buttons Row / Column
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("View Release Notes on GitHub")
                }
            }

            if (updateInfo.isUpdateAvailable && updateInfo.downloadUrl.isNotBlank()) {
                if (isDownloading) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearRipplingWavyProgressIndicator(
                            progress = downloadProgress / 100f,
                            modifier = Modifier.fillMaxWidth(),
                            height = 8.dp,
                            strokeWidth = 4.dp
                        )
                        Spacer(Modifier.height(10.dp))
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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Install Update Now")
                    }
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
                Toast.makeText(context, "Please grant permission to install updates", Toast.LENGTH_LONG).show()
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
