package com.petal.browser.apk

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed interface ApkStep {
    data object Reading : ApkStep
    data class Ready(val apk: PetalApkInstallerEngine.ApkMeta) : ApkStep
    data class Installing(val apk: PetalApkInstallerEngine.ApkMeta) : ApkStep
    data class Inspect(val apk: PetalApkInstallerEngine.ApkMeta) : ApkStep
    data class Done(val apk: PetalApkInstallerEngine.ApkMeta) : ApkStep
    data class Failed(val apk: PetalApkInstallerEngine.ApkMeta?, val title: String, val body: String) : ApkStep
}

/**
 * Multi-route APK installer engine for Petal Browser.
 * Supports Elevated (Root / Shizuku), Android PackageInstaller Session, and system PackageInstaller fallback.
 * Adheres strictly to minimal permissions: zero .aab dependencies, zero Google Play restricted permissions.
 */
object PetalApkInstallerEngine {

    const val APK_MIME = "application/vnd.android.package-archive"

    data class ApkMeta(
        val file: File,
        val label: String,
        val packageName: String,
        val versionName: String,
        val versionCode: Long,
        val icon: Bitmap?,
        val installedVersion: String?,
        val installedVersionCode: Long?,
        val isOlderThanInstalled: Boolean,
        val isInstalled: Boolean,
        val isSystemApp: Boolean,
        val clash: ClashType? = null
    )

    enum class ClashType {
        SYSTEM,
        SIGNATURE_MISMATCH,
        HIDDEN
    }

    data class InstallOption(
        val id: String,
        val label: String,
        val desc: String,
        val flag: String?,
        val minSdk: Int = 0,
        val excludes: String? = null
    )

    val INSTALL_OPTIONS = listOf(
        InstallOption("downgrade", "Allow downgrade", "Install an older version over a newer one, keeping its data", "-d"),
        InstallOption("grant", "Grant all permissions", "Every runtime permission the app asks for, granted on install", "-g"),
        InstallOption("restricted", "Allow restricted settings", "SMS, call log and accessibility work without Android restricted-settings block", null),
        InstallOption("test", "Allow test builds", "Debug and test-only APKs install too", "-t"),
        InstallOption("lowsdk", "Bypass target SDK block", "Install apps built for older Android versions", "--bypass-low-target-sdk-block", 34),
        InstallOption("forcesdk", "Allow preview SDK builds", "Install apps built against an unreleased Android preview", "--force-sdk"),
        InstallOption("dontkill", "Keep the app running", "Don't stop the app while it updates", "--dont-kill"),
        InstallOption("currentuser", "Only this user", "Skip other profiles like Work, Secure Folder and Dual apps", "--user current"),
        InstallOption("internal", "Internal storage", "Force the app onto internal storage", "--install-location 1", excludes = "external"),
        InstallOption("external", "Prefer SD card", "Put the app on external storage when room permits", "--install-location 2", excludes = "internal"),
        InstallOption("rollback", "Enable rollback", "Let Android roll this update back later", "--enable-rollback", 29),
        InstallOption("noverify", "Skip package verification", "Skip verifier checks while still verifying cryptographic signatures", "--skip-verification", 31),
        InstallOption("queryable", "Visible to every app", "Other apps can see this package even when visibility is restricted", "--force-queryable", 30),
        InstallOption("play", "Show as installed by Play Store", "For apps that require Google Play installer attribution", "-i com.android.vending", excludes = "installer"),
        InstallOption("installer", "Custom installer", "Report any custom package name as installer", null, excludes = "play")
    )

    private val PACKAGE_REGEX = Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+""")

    /** Compiles `pm install` arguments based on active options and Android SDK version. */
    fun buildInstallArgs(opts: Collection<String>, sdk: Int, ownerPackage: String): String {
        val on = INSTALL_OPTIONS.filter { it.id in opts && sdk >= it.minSdk }.map { it.id }.toSet()
        val customInstaller = opts.firstOrNull { it.startsWith("installer=") }
            ?.substringAfter('=')
            ?.takeIf { "installer" in on && it.matches(PACKAGE_REGEX) }

        return buildString {
            if ("restricted" !in on && sdk >= 34) append(" --restrict-permissions")
            if (customInstaller != null) {
                append(" -i $customInstaller")
            } else if ("play" in on) {
                append(" -i com.android.vending")
            } else if (ownerPackage.isNotBlank()) {
                append(" -i $ownerPackage")
            }
            INSTALL_OPTIONS.filter { it.id in on }.forEach { o ->
                o.flag?.let { append(" $it") }
            }
        }
    }

    /** Cleans up old installer temporary files from cache older than 1 hour. */
    fun purgeStaleCache(context: Context) {
        runCatching {
            val cacheDir = context.cacheDir ?: return
            val now = System.currentTimeMillis()
            cacheDir.listFiles { f -> f.name.startsWith("petal_install_") && (now - f.lastModified() > 3_600_000) }
                ?.forEach { it.delete() }
        }
    }

    /** Copies an incoming content or file URI to a private cache file for safe reading. */
    suspend fun copyUriToCache(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        purgeStaleCache(context)
        val tempFile = File(context.cacheDir, "petal_install_${System.currentTimeMillis()}.apk")
        try {
            val input: InputStream? = context.contentResolver.openInputStream(uri)
            if (input == null) return@withContext null
            input.use { src ->
                tempFile.outputStream().use { dst ->
                    src.copyTo(dst)
                }
            }
            if (tempFile.length() > 0) tempFile else {
                tempFile.delete()
                null
            }
        } catch (e: Exception) {
            tempFile.delete()
            null
        }
    }

    /** Reads metadata and package clash information from the cached APK file. */
    @Suppress("DEPRECATION")
    fun parseApk(context: Context, file: File, uri: Uri? = null): ApkMeta? {
        if (!file.exists() || file.length() == 0L) return null
        val pm = context.packageManager
        val pi = pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNATURES) ?: return null
        val ai = pi.applicationInfo ?: return null
        ai.sourceDir = file.absolutePath
        ai.publicSourceDir = file.absolutePath

        val displayName = uri?.let {
            runCatching {
                context.contentResolver.query(it, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull()
        }

        val label = runCatching { ai.loadLabel(pm).toString() }.getOrNull()
            ?.takeIf { it.isNotBlank() && it != pi.packageName }
            ?: displayName?.removeSuffix(".apk")
            ?: pi.packageName

        val icon = runCatching { ai.loadIcon(pm).toBitmap(144, 144) }.getOrNull()

        var installedVersion: String? = null
        var installedVersionCode: Long? = null
        var isSystem = false
        var clash: ClashType? = null

        val installedInfo = runCatching {
            pm.getPackageInfo(pi.packageName, PackageManager.GET_SIGNATURES)
        }.getOrNull()

        if (installedInfo != null) {
            installedVersion = installedInfo.versionName
            installedVersionCode = PackageInfoCompat.getLongVersionCode(installedInfo)
            val instAi = installedInfo.applicationInfo
            if (instAi != null && (instAi.flags and ApplicationInfo.FLAG_SYSTEM != 0)) {
                isSystem = true
                clash = ClashType.SYSTEM
            }

            // Check signature clash
            val apkSignatures = pi.signatures?.map { it.toCharsString() }?.toSet().orEmpty()
            val installedSignatures = installedInfo.signatures?.map { it.toCharsString() }?.toSet().orEmpty()
            if (apkSignatures.isNotEmpty() && installedSignatures.isNotEmpty() && apkSignatures != installedSignatures) {
                clash = ClashType.SIGNATURE_MISMATCH
            }
        }

        val versionCode = PackageInfoCompat.getLongVersionCode(pi)
        val isOlder = installedVersionCode != null && versionCode < installedVersionCode

        return ApkMeta(
            file = file,
            label = label,
            packageName = pi.packageName,
            versionName = pi.versionName.orEmpty(),
            versionCode = versionCode,
            icon = icon,
            installedVersion = installedVersion,
            installedVersionCode = installedVersionCode,
            isOlderThanInstalled = isOlder,
            isInstalled = installedInfo != null,
            isSystemApp = isSystem,
            clash = clash
        )
    }

    /** Checks whether root access is available. */
    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val exitCode = process.waitFor(5, TimeUnit.SECONDS)
            exitCode && process.exitValue() == 0 && "uid=0" in process.inputStream.bufferedReader().readText()
        }.getOrDefault(false)
    }

    /** Checks whether Shizuku is running and authorized via reflection without hard dependencies. */
    fun isShizukuAvailable(): Boolean = runCatching {
        val clazz = try {
            Class.forName("rikka.shizuku.Shizuku")
        } catch (e: ClassNotFoundException) {
            Class.forName("moe.shizuku.api.Shizuku")
        }
        val pingMethod = clazz.getMethod("pingBinder")
        val isPing = pingMethod.invoke(null) as? Boolean ?: false
        if (!isPing) return false
        val checkMethod = clazz.getMethod("checkSelfPermission")
        val perm = checkMethod.invoke(null) as? Int ?: -1
        perm == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /** Determines the preferred fast route available on the device. */
    suspend fun detectFastRoute(): Pair<String, String>? = withContext(Dispatchers.IO) {
        if (isRootAvailable()) {
            return@withContext "root" to "Installs with Root, no prompts and no 24-hour wait"
        }
        if (isShizukuAvailable()) {
            return@withContext "shizuku" to "Installs through Shizuku, no prompts and no 24-hour wait"
        }
        null
    }

    /** Performs installation via elevated root shell streaming directly into `pm install`. */
    suspend fun installWithRoot(
        file: File,
        options: Collection<String>,
        ownerPackage: String = "com.petal.browser"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val args = buildInstallArgs(options, Build.VERSION.SDK_INT, ownerPackage)
            val cmd = "pm install -r $args -S ${file.length()}"
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            file.inputStream().use { input ->
                process.outputStream.use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }
            val output = process.inputStream.bufferedReader().readText()
            val errorOutput = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode != 0 || "Failure" in output || "Failure" in errorOutput) {
                throw IllegalStateException("Root install failed ($exitCode): ${output.trim()} ${errorOutput.trim()}".trim())
            }
        }
    }

    /** Creates an Intent to launch Android's system package installer via FileProvider. */
    fun createSystemInstallIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val base = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }

        val pm = context.packageManager
        val handlers = pm.queryIntentActivities(base, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }

        val target = handlers.firstOrNull { it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 && "packageinstaller" in it.packageName }
            ?: handlers.firstOrNull { "packageinstaller" in it.packageName }
            ?: handlers.firstOrNull { it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 }

        return if (target != null) {
            Intent(base).setClassName(target.packageName, target.name)
        } else {
            Intent.createChooser(base, "Install with").apply {
                putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(android.content.ComponentName(context, PetalApkInstallActivity::class.java)))
            }
        }
    }

    /** Computes SHA-256 hash of a file. */
    fun sha256(file: File): String? = runCatching {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(16384)
        file.inputStream().use { src ->
            while (true) {
                val n = src.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()
}
