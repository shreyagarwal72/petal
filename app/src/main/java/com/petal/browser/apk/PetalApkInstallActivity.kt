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
import com.petal.browser.activity.MainActivity
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

    private var isUserInstallInitiated = false

    private val shizukuPermissionListener = rikka.shizuku.Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == 1001 && grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            fastRoute = "shizuku" to "Direct install via Shizuku without prompts"
            if (isUserInstallInitiated) {
                val current = step
                val apk = when (current) {
                    is ApkStep.Ready -> current.apk
                    is ApkStep.Failed -> current.apk
                    else -> null
                }
                if (apk != null) {
                    performInstall(apk)
                }
            }
        }
    }

    private val shizukuBinderReceivedListener = rikka.shizuku.Shizuku.OnBinderReceivedListener {
        checkAndRequestShizuku()
    }

    private fun checkAndRequestShizuku() {
        if (PetalApkInstallerEngine.isShizukuInstalled(this) || PetalApkInstallerEngine.isShizukuRunning()) {
            if (PetalApkInstallerEngine.isShizukuRunning()) {
                if (PetalApkInstallerEngine.isShizukuAvailable()) {
                    fastRoute = "shizuku" to "Direct install via Shizuku without prompts"
                } else {
                    runCatching {
                        rikka.shizuku.Shizuku.requestPermission(1001)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        runCatching { rikka.shizuku.Shizuku.addRequestPermissionResultListener(shizukuPermissionListener) }
        runCatching { rikka.shizuku.Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener) }
        checkAndRequestShizuku()

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
                    onInstall = {
                        isUserInstallInitiated = true
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
        runCatching { rikka.shizuku.Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener) }
        runCatching { rikka.shizuku.Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener) }
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
        val isShizuku = fastRoute?.first == "shizuku" || PetalApkInstallerEngine.isShizukuAvailable() || PetalApkInstallerEngine.isShizukuRunning()
        val isRoot = fastRoute?.first == "root" || (!isShizuku && selectedOptions.isNotEmpty())

        val fullOptions = selectedOptions + listOfNotNull(
            customInstaller.takeIf { "installer" in selectedOptions && it.isNotBlank() }
                ?.let { "installer=$it" }
        )

        if (isShizuku) {
            if (!PetalApkInstallerEngine.isShizukuAvailable()) {
                // Shizuku is running but permission not yet granted; request it
                isUserInstallInitiated = true
                runCatching {
                    rikka.shizuku.Shizuku.requestPermission(1001)
                }.onFailure {
                    // Fallback to system package installer if request fails
                    val installIntent = PetalApkInstallerEngine.createSystemInstallIntent(this, apk.file)
                    installerLauncher.launch(installIntent)
                }
                return
            }

            step = ApkStep.Installing(apk)
            lifecycleScope.launch {
                val result = PetalApkInstallerEngine.installWithShizuku(apk.file, fullOptions, packageName)
                if (result.isSuccess) {
                    apk.file.delete()
                    step = ApkStep.Done(apk)
                } else {
                    step = ApkStep.Failed(
                        apk,
                        "Shizuku install failed",
                        result.exceptionOrNull()?.message ?: "An unknown error occurred during Shizuku installation."
                    )
                }
            }
        } else if (isRoot) {
            step = ApkStep.Installing(apk)
            lifecycleScope.launch {
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
}
