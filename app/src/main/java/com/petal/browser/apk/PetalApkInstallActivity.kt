package com.petal.browser.apk

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.apk.PetalApkInstallerEngine.ApkStep
import com.petal.browser.ui.theme.PetalExpressiveTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Transparent Activity hosting the Petal APK Installer bottom sheet.
 * Floats directly over whatever file manager, browser, or messenger opened the APK.
 */
class PetalApkInstallActivity : ComponentActivity() {

    private var step by mutableStateOf<ApkStep>(ApkStep.Reading)
    private var insights by mutableStateOf<PetalApkInsights.Report?>(null)
    private var isInspecting by mutableStateOf(false)
    private var fastRoute by mutableStateOf<Pair<String, String>?>(null)
    private var selectedOptions by mutableStateOf(emptySet<String>())
    private var customInstaller by mutableStateOf("")

    private val installerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val currentStep = step
        if (currentStep is ApkStep.Ready) {
            when (result.resultCode) {
                Activity.RESULT_OK -> {
                    currentStep.apk.file.delete()
                    step = ApkStep.Done(currentStep.apk)
                }
                Activity.RESULT_CANCELED -> {
                    // Stay on Ready sheet so user can re-try or change options
                    step = ApkStep.Ready(currentStep.apk)
                }
                else -> {
                    step = ApkStep.Failed(
                        currentStep.apk,
                        "Installation Incomplete",
                        "The package installer did not finish successfully."
                    )
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        readIntent(intent)

        setContent {
            PetalExpressiveTheme {
                PetalApkInstallSheet(
                    step = step,
                    insights = insights,
                    isInspecting = isInspecting,
                    fastRoute = fastRoute,
                    selectedOptions = selectedOptions,
                    customInstaller = customInstaller,
                    onOptionToggle = { optId, isChecked ->
                        selectedOptions = if (isChecked) selectedOptions + optId else selectedOptions - optId
                    },
                    onCustomInstallerChange = { customInstaller = it.trim() },
                    onOpenInspect = {
                        val current = step
                        if (current is ApkStep.Ready) {
                            step = ApkStep.Inspect(current.apk)
                        }
                    },
                    onCloseInspect = {
                        val current = step
                        if (current is ApkStep.Inspect) {
                            step = ApkStep.Ready(current.apk)
                        }
                    },
                    onScanVirusTotal = {
                        val current = step
                        val apk = when (current) {
                            is ApkStep.Ready -> current.apk
                            is ApkStep.Inspect -> current.apk
                            else -> null
                        }
                        if (apk != null) {
                            scanWithVirusTotal(apk)
                        }
                    },
                    onInstall = {
                        val current = step
                        val apk = when (current) {
                            is ApkStep.Ready -> current.apk
                            is ApkStep.Failed -> current.apk
                            else -> null
                        }
                        if (apk != null) {
                            performInstall(apk)
                        }
                    },
                    onOpenInstalledApp = { pkg ->
                        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                        if (launchIntent != null) {
                            runCatching { startActivity(launchIntent) }
                        }
                        finish()
                    },
                    onDismiss = {
                        finish()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
    }

    override fun onDestroy() {
        cleanCurrentFile()
        super.onDestroy()
    }

    private fun cleanCurrentFile() {
        when (val s = step) {
            is ApkStep.Ready -> s.apk.file.delete()
            is ApkStep.Inspect -> s.apk.file.delete()
            is ApkStep.Failed -> s.apk?.file?.delete()
            else -> Unit
        }
    }

    private fun readIntent(intent: Intent?) {
        val uri: Uri? = if (intent?.action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        } else {
            intent?.data
        }

        if (uri == null) {
            finish()
            return
        }

        step = ApkStep.Reading
        insights = null
        isInspecting = false

        lifecycleScope.launch {
            val cachedFile = PetalApkInstallerEngine.copyUriToCache(this@PetalApkInstallActivity, uri)
            if (cachedFile == null) {
                step = ApkStep.Failed(
                    null,
                    "Cannot read file",
                    "The selected package could not be cached or read."
                )
                return@launch
            }

            val apkMeta = withContext(Dispatchers.IO) {
                PetalApkInstallerEngine.parseApk(this@PetalApkInstallActivity, cachedFile, uri)
            }

            if (apkMeta == null) {
                cachedFile.delete()
                step = ApkStep.Failed(
                    null,
                    "Invalid APK package",
                    "The file is not a valid Android application archive."
                )
                return@launch
            }

            step = ApkStep.Ready(apkMeta)

            // Auto-detect fast routes (Root / Shizuku)
            fastRoute = PetalApkInstallerEngine.detectFastRoute()

            // Auto-suggest needed switches
            val neededOptions = mutableSetOf<String>()
            if (apkMeta.isOlderThanInstalled) {
                neededOptions += "downgrade"
            }
            selectedOptions = selectedOptions + neededOptions

            // Run offline insights asynchronously
            isInspecting = true
            val report = withContext(Dispatchers.IO) {
                PetalApkInsights.read(applicationContext, apkMeta.file)
            }
            insights = report
            isInspecting = false

            report?.let { rep ->
                val extras = mutableSetOf<String>()
                if (rep.testOnly) extras += "test"
                if (rep.targetSdk in 1..23) extras += "lowsdk"
                if (extras.isNotEmpty()) {
                    selectedOptions = selectedOptions + extras
                }
            }
        }
    }

    private fun performInstall(apk: PetalApkInstallerEngine.ApkMeta) {
        val isRoot = fastRoute?.first == "root"
        val hasElevatedFlags = selectedOptions.isNotEmpty()

        if (isRoot && hasElevatedFlags) {
            step = ApkStep.Installing(apk)
            lifecycleScope.launch {
                val fullOptions = selectedOptions + listOfNotNull(
                    customInstaller.takeIf { "installer" in selectedOptions && it.isNotBlank() }
                        ?.let { "installer=$it" }
                )
                val result = PetalApkInstallerEngine.installWithRoot(apk.file, fullOptions, packageName)
                if (result.isSuccess) {
                    apk.file.delete()
                    step = ApkStep.Done(apk)
                } else {
                    step = ApkStep.Failed(
                        apk,
                        "Root install failed",
                        result.exceptionOrNull()?.message ?: "An unknown error occurred during installation."
                    )
                }
            }
        } else {
            // Standard system PackageInstaller route via FileProvider
            val installIntent = PetalApkInstallerEngine.createSystemInstallIntent(this, apk.file)
            installerLauncher.launch(installIntent)
        }
    }

    private fun scanWithVirusTotal(apk: PetalApkInstallerEngine.ApkMeta) {
        lifecycleScope.launch(Dispatchers.IO) {
            val hash = PetalApkInstallerEngine.sha256(apk.file)
            if (!hash.isNullOrBlank()) {
                val vtUrl = "https://www.virustotal.com/gui/file/$hash"
                withContext(Dispatchers.Main) {
                    val browserIntent = Intent(this@PetalApkInstallActivity, BrowserActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        data = Uri.parse(vtUrl)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    runCatching { startActivity(browserIntent) }
                }
            }
        }
    }
}
