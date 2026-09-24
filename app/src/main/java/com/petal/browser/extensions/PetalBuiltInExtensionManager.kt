package com.petal.browser.extensions

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.petal.browser.engine.gecko.PetalGeckoRuntime
import org.mozilla.geckoview.WebExtensionController

/**
 * PetalBuiltInExtensionManager
 *
 * Manages Petal's bundled GeckoView WebExtensions that ship inside the APK
 * (as opposed to user-installed AMO extensions managed by PetalExtensionManager).
 *
 * Each built-in extension lives at assets/web_extensions/<assetPath>/ and is
 * loaded via resource://android/assets/... — GeckoView's built-in mechanism.
 *
 * Safe to call on every startup — ensureBuiltIn() is idempotent.
 */
object PetalBuiltInExtensionManager {

    private const val TAG = "PetalBuiltInExt"

    data class BuiltInSpec(
        val assetPath: String,
        val extensionId: String,
        val label: String,
        val description: String,
        val prefKey: String
    )

    val builtIns: List<BuiltInSpec> = listOf(
        // Mozilla WebCompat — bundled in the GeckoView AAR, fixes site compatibility breakages.
        // This is the same extension Firefox for Android ships as a default built-in.
        // The resource URI points to GeckoView's own bundled copy (no asset to ship).
        BuiltInSpec(
            assetPath   = "extensions/webcompat/",
            extensionId = "webcompat@mozilla.org",
            label       = "Mozilla WebCompat",
            description = "Fixes compatibility quirks and site breakages for mobile Firefox engine.",
            prefKey     = "petal_builtin_webcompat"
        ),
        BuiltInSpec(
            assetPath   = "web_extensions/petal_dark_webpages/",
            extensionId = "petal-dark-webpages@petalbrowser.app",
            label       = "Petal Dark Webpages",
            description = "Smart night mode for all websites with automatic inversion and per-site whitelist.",
            prefKey     = "petal_builtin_dark_webpages"
        ),
        BuiltInSpec(
            assetPath   = "web_extensions/petal_clean_link/",
            extensionId = "petal-clean-link@petalbrowser.app",
            label       = "Petal Clean Link",
            description = "Automatically strips tracking tokens (UTM, fbclid, gclid) from links.",
            prefKey     = "petal_builtin_clean_link"
        ),
        BuiltInSpec(
            assetPath   = "web_extensions/petal_universal_copy/",
            extensionId = "petal-universal-copy@petalbrowser.app",
            label       = "Petal Universal Copy",
            description = "Bypasses copy restrictions and unlocks text selection on all websites.",
            prefKey     = "petal_builtin_universal_copy"
        ),
        BuiltInSpec(
            assetPath   = "web_extensions/petal_ai_blocker/",
            extensionId = "petal-ai-blocker@petalbrowser.app",
            label       = "Petal AI Blocker",
            description = "Hides AI overview cards and synthesized summaries in search engine results.",
            prefKey     = "petal_builtin_ai_blocker"
        ),
        BuiltInSpec(
            assetPath   = "web_extensions/petal_translate/",
            extensionId = "petal-translate@petalbrowser.app",
            label       = "Petal Translate",
            description = "Real-time in-page translation engine powered by Petal on-device models.",
            prefKey     = "petal_builtin_translate"
        ),
        BuiltInSpec(
            assetPath   = "web_extensions/google_search_fixer/",
            extensionId = "google-search-fixer@petalbrowser.app",
            label       = "Google Search Fixer",
            description = "Ensures modern full-featured Google Search experience on GeckoView.",
            prefKey     = "petal_builtin_google_search_fixer"
        )
    )


    /** Install and sync all built-in extensions. Called from BrowserActivity. */
    @JvmStatic
    fun installAll(context: Context) {
        val runtime = try {
            PetalGeckoRuntime.getOrCreate(context.applicationContext)
        } catch (t: Throwable) {
            Log.e(TAG, "Cannot access GeckoRuntime for built-in extensions", t)
            return
        }
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val controller = runtime.webExtensionController
        for (spec in builtIns) {
            val enabled = sp.getBoolean(spec.prefKey, true)
            installAndSync(controller, spec, enabled)
        }
    }

    private fun installAndSync(
        controller: WebExtensionController,
        spec: BuiltInSpec,
        enabled: Boolean
    ) {
        val uri = "resource://android/assets/${spec.assetPath}"
        controller.ensureBuiltIn(uri, spec.extensionId).accept(
            { ext ->
                if (ext == null) return@accept
                controller.setAllowedInPrivateBrowsing(ext, true)
                val action = if (enabled)
                    controller.enable(ext, WebExtensionController.EnableSource.APP)
                else
                    controller.disable(ext, WebExtensionController.EnableSource.APP)
                action.accept(
                    { Log.d(TAG, "${spec.label} → ${if (enabled) "enabled" else "disabled"}") },
                    { e -> Log.e(TAG, "Failed to set state for ${spec.label}", e) }
                )
                Log.i(TAG, "${spec.label} installed (${spec.extensionId})")
            },
            { e -> Log.e(TAG, "Failed to install ${spec.label}", e) }
        )
    }

    /** Toggle a built-in extension at runtime. */
    @JvmStatic
    fun setEnabled(context: Context, prefKey: String, enabled: Boolean) {
        val spec = builtIns.find { it.prefKey == prefKey } ?: return
        val runtime = try { PetalGeckoRuntime.getOrCreate(context.applicationContext) }
                      catch (_: Throwable) { return }
        runtime.webExtensionController.list().accept({ list ->
            val ext = list?.find { it.id == spec.extensionId } ?: return@accept
            val action = if (enabled)
                runtime.webExtensionController.enable(ext, WebExtensionController.EnableSource.APP)
            else
                runtime.webExtensionController.disable(ext, WebExtensionController.EnableSource.APP)
            action.accept(
                { Log.d(TAG, "${spec.label} runtime toggle → ${if (enabled) "on" else "off"}") },
                { e -> Log.e(TAG, "Toggle failed for ${spec.label}", e) }
            )
        }, { e -> Log.e(TAG, "Cannot list extensions for toggle", e) })
    }

    private const val PREF_DARK_WHITELIST = "petal_dark_webpages_whitelist"
    private const val PREF_DARK_CONTRAST = "petal_dark_webpages_contrast"

    @JvmStatic
    fun getDarkWebpagesWhitelist(context: Context): Set<String> {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        return sp.getStringSet(PREF_DARK_WHITELIST, emptySet()) ?: emptySet()
    }

    @JvmStatic
    fun addDarkWebpageWhitelist(context: Context, domain: String) {
        val clean = domain.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").split("/")[0]
        if (clean.isBlank()) return
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val current = sp.getStringSet(PREF_DARK_WHITELIST, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(clean)
        sp.edit().putStringSet(PREF_DARK_WHITELIST, current).apply()
    }

    @JvmStatic
    fun removeDarkWebpageWhitelist(context: Context, domain: String) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val current = sp.getStringSet(PREF_DARK_WHITELIST, emptySet())?.toMutableSet() ?: return
        current.remove(domain)
        sp.edit().putStringSet(PREF_DARK_WHITELIST, current).apply()
    }

    @JvmStatic
    fun getDarkWebpagesContrast(context: Context): Int {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        return sp.getInt(PREF_DARK_CONTRAST, 90)
    }

    @JvmStatic
    fun setDarkWebpagesContrast(context: Context, contrast: Int) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        sp.edit().putInt(PREF_DARK_CONTRAST, contrast).apply()
    }
}
