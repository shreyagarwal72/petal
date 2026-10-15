/*
 * SamsungContentBlockerBridge.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Reverse-Engineered Samsung Internet Content Blocker Protocol Support for Petal.
 *
 * Implements compatibility with Samsung Content Blocker extensions (AdGuard, ABP,
 * Unicorn, Disconnect, Crystal, etc.):
 *
 * Protocol Specification (discovered via REA reverse-engineering of SBrowser):
 * 1. Interface Meta-data:
 *    - Key: "com.samsung.android.sbrowser.contentBlocker.interfaceVersion"
 *    - Value: "API_1.0"
 * 2. Broadcast Action:
 *    - "com.samsung.android.sbrowser.contentBlocker.ACTION_UPDATE"
 *    - Data URI: "package:<blocker_package_name>"
 * 3. Required App Permission:
 *    - "com.samsung.android.sbrowser.permission.CONTENTBLOCKER"
 * 4. ContentProvider Authority:
 *    - "<blocker_package_name>.contentBlocker.contentProvider"
 * 5. Rule Ingestion:
 *    - Calls contentResolver.openInputStream(uri) where uri is:
 *      content://<package>.contentBlocker.contentProvider/
 *    - Parses ABP rules or declarative JSON rules and feeds them into PetalAdBlockEngine
 *      and PetalFilterSubscriptionManager.
 */

package com.petal.browser.browser.samsung

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.petal.browser.browser.PetalAdBlockEngine
import com.petal.browser.browser.PetalFilterSubscriptionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

data class SamsungBlockerInfo(
    val packageName: String,
    val appName: String,
    val interfaceVersion: String,
    val isEnabled: Boolean
)

object SamsungContentBlockerBridge {

    private const val TAG = "SamsungBlockerBridge"
    const val SAMSUNG_PERMISSION = "com.samsung.android.sbrowser.permission.CONTENTBLOCKER"
    const val SAMSUNG_METADATA_VERSION = "com.samsung.android.sbrowser.contentBlocker.interfaceVersion"
    const val SAMSUNG_ACTION_UPDATE = "com.samsung.android.sbrowser.contentBlocker.ACTION_UPDATE"
    const val PROVIDER_SUFFIX = ".contentBlocker.contentProvider"
    private const val PREFS_KEY_ENABLED_BLOCKERS = "sp_samsung_enabled_blockers"

    /**
     * Query all installed Samsung Content Blocker packages on the Android device.
     */
    fun findInstalledSamsungBlockers(context: Context): List<SamsungBlockerInfo> {
        val pm = context.packageManager
        val list = mutableListOf<SamsungBlockerInfo>()
        val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA)

        for (pkg in packages) {
            val appInfo = pkg.applicationInfo ?: continue
            val hasPermission = pkg.requestedPermissions?.contains(SAMSUNG_PERMISSION) == true
            val metaDataVersion = appInfo.metaData?.getString(SAMSUNG_METADATA_VERSION)

            if (hasPermission || metaDataVersion != null) {
                val appLabel = pm.getApplicationLabel(appInfo).toString()
                val isEnabled = isBlockerEnabled(context, pkg.packageName)
                list.add(
                    SamsungBlockerInfo(
                        packageName = pkg.packageName,
                        appName = appLabel,
                        interfaceVersion = metaDataVersion ?: "API_1.0",
                        isEnabled = isEnabled
                    )
                )
            }
        }
        return list
    }

    fun isBlockerEnabled(context: Context, packageName: String): Boolean {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val enabledSet = sp.getStringSet(PREFS_KEY_ENABLED_BLOCKERS, emptySet()) ?: emptySet()
        return enabledSet.contains(packageName)
    }

    fun setBlockerEnabled(context: Context, packageName: String, enabled: Boolean) {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val enabledSet = (sp.getStringSet(PREFS_KEY_ENABLED_BLOCKERS, emptySet()) ?: emptySet()).toMutableSet()
        if (enabled) {
            enabledSet.add(packageName)
        } else {
            enabledSet.remove(packageName)
        }
        sp.edit().putStringSet(PREFS_KEY_ENABLED_BLOCKERS, enabledSet).apply()
        CoroutineScope(Dispatchers.IO).launch {
            syncBlockerRules(context, packageName)
        }
    }

    /**
     * Pull rules from the Samsung blocker ContentProvider and import them into Petal.
     */
    suspend fun syncBlockerRules(context: Context, packageName: String) {
        if (!isBlockerEnabled(context, packageName)) {
            // Remove custom rules from this blocker if disabled
            PetalFilterSubscriptionManager.reloadActiveRules(context)
            return
        }

        val authority = "$packageName$PROVIDER_SUFFIX"
        val uri = Uri.parse("content://$authority/")

        try {
            val cr = context.contentResolver
            val inputStream = cr.openInputStream(uri)
            if (inputStream == null) {
                Log.w(TAG, "ContentResolver returned null stream for $uri")
                return
            }

            val extractedRules = mutableListOf<String>()
            val bufferedReader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            val firstLine = bufferedReader.readLine() ?: ""

            if (firstLine.trim().startsWith("[") && firstLine.trim().endsWith("]")) {
                // Adblock Plus / EasyList format
                if (!firstLine.startsWith("[{")) {
                    var line: String? = bufferedReader.readLine()
                    while (line != null) {
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("!") && !trimmed.startsWith("#")) {
                            extractedRules.add(trimmed)
                        }
                        line = bufferedReader.readLine()
                    }
                }
            }

            if (extractedRules.isEmpty()) {
                // Try JSON format (WebKit / Safari / declarative content-blocking format)
                try {
                    val fullText = firstLine + "\n" + bufferedReader.readText()
                    val jsonArray = JSONArray(fullText)
                    for (i in 0 until jsonArray.length()) {
                        val ruleObj = jsonArray.optJSONObject(i) ?: continue
                        val trigger = ruleObj.optJSONObject("trigger")
                        val urlFilter = trigger?.optString("url-filter")
                        if (!urlFilter.isNullOrBlank() && urlFilter != ".*") {
                            extractedRules.add(urlFilter)
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Not a JSON rule set: ${e.message}")
                }
            }

            bufferedReader.close()
            inputStream.close()

            if (extractedRules.isNotEmpty()) {
                Log.i(TAG, "Loaded ${extractedRules.size} rules from Samsung blocker: $packageName")
                // Append or merge into custom user rules
                val existingCustom = PetalFilterSubscriptionManager.getCustomUserRules(context)
                val header = "\n! Samsung Blocker: $packageName\n"
                val newRulesText = existingCustom + header + extractedRules.joinToString("\n")
                PetalFilterSubscriptionManager.saveCustomUserRules(context, newRulesText)
                PetalFilterSubscriptionManager.reloadActiveRules(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read content blocker stream from $packageName: ${e.message}", e)
        }
    }
}

/**
 * BroadcastReceiver listening for ACTION_UPDATE from Samsung content blocker companion apps.
 */
class SamsungContentBlockerUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == SamsungContentBlockerBridge.SAMSUNG_ACTION_UPDATE) {
            val pkg = intent.data?.schemeSpecificPart ?: intent.getStringExtra("receiver_data_package_name")
            if (!pkg.isNullOrBlank()) {
                Log.i("SamsungBlockerReceiver", "Received ACTION_UPDATE for package: $pkg")
                CoroutineScope(Dispatchers.IO).launch {
                    SamsungContentBlockerBridge.syncBlockerRules(context, pkg)
                }
            }
        }
    }
}
