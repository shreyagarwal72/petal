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
        val prefKey: String
    )

    val builtIns: List<BuiltInSpec> = listOf(
        BuiltInSpec(
            assetPath   = "web_extensions/petal_universal_copy/",
            extensionId = "petal-universal-copy@petalbrowser.app",
            label       = "Petal Universal Copy",
            prefKey     = "petal_builtin_universal_copy"
        ),
        BuiltInSpec(
            assetPath   = "web_extensions/petal_ai_blocker/",
            extensionId = "petal-ai-blocker@petalbrowser.app",
            label       = "Petal AI Blocker",
            prefKey     = "petal_builtin_ai_blocker"
        ),
        BuiltInSpec(
            assetPath   = "web_extensions/petal_translate/",
            extensionId = "petal-translate@petalbrowser.app",
            label       = "Petal Translate",
            prefKey     = "petal_builtin_translate"
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
}
