package com.petal.browser.compose.store

import android.content.Context
import android.net.Uri
import android.util.Log
import org.mozilla.geckoview.GeckoSession

/**
 * PetalStoreScriptInjector
 * ─────────────────────────────────────────────────────────────────────────
 * Runs the Browser Store's enabled "Scripts" on a page. Before this existed the Scripts
 * tab only saved a list of IDs that nothing ever read, so enabling a script did nothing.
 *
 * Scripts are verified (SHA-256) and matched against the page URL by [PetalStoreCatalog],
 * combined into a single guarded payload, and delivered through a `javascript:` URI, the
 * same channel Petal already uses for its other page-level injections. The payload is
 * percent-encoded (the raw form broke on `%`, `#` and newlines) and wrapped in `void(...)`
 * so the load can never replace the page with the script's return value.
 */
object PetalStoreScriptInjector {
    private const val TAG = "PetalStoreScripts"

    /** Builds the injection payload for [pageUrl], or null when no enabled script applies. */
    fun buildPayload(context: Context, pageUrl: String, stage: String = "document-idle"): String? {
        if (!pageUrl.startsWith("http://", ignoreCase = true) &&
            !pageUrl.startsWith("https://", ignoreCase = true)
        ) return null

        val storeScripts = if (stage == "document-idle") {
            PetalStoreCatalog.enabledScriptsFor(context.applicationContext, pageUrl)
        } else {
            emptyList()
        }
        val customScripts = PetalUserScriptEngine.getActiveScriptsForUrl(context.applicationContext, pageUrl, stage)

        if (storeScripts.isEmpty() && customScripts.isEmpty()) return null

        val body = StringBuilder()
        body.append("var r=window.__petalStoreScripts=window.__petalStoreScripts||{};\n")
        // Standard Greasemonkey/Violentmonkey Polyfill: GM_addStyle, GM_log, unsafeWindow
        body.append("window.unsafeWindow=window.unsafeWindow||window;\n")
        body.append("if(!window.GM_addStyle){window.GM_addStyle=function(css){var s=document.createElement('style');s.textContent=css;(document.head||document.documentElement).appendChild(s);return s;};}\n")
        body.append("if(!window.GM_log){window.GM_log=console.log.bind(console);}\n")

        for (script in storeScripts) {
            val id = script.id.replace("'", "")
            body.append("if(!r['").append(id).append("']){r['").append(id).append("']=1;try{\n")
            body.append(script.scriptContent)
            body.append("\n}catch(e){console.warn('Script error', e);}}\n")
        }

        for ((idx, scriptText) in customScripts.withIndex()) {
            val id = "custom_" + stage + "_" + idx
            body.append("if(!r['").append(id).append("']){r['").append(id).append("']=1;try{\n")
            body.append(scriptText)
            body.append("\n}catch(e){console.warn('UserScript error', e);}}\n")
        }

        return "void((function(){" + body + "})());"
    }

    /** Injects every enabled, matching store and user script into [session] at [stage]. Safe to call on any page. */
    fun inject(session: GeckoSession, context: Context, pageUrl: String?, stage: String = "document-idle") {
        if (pageUrl.isNullOrBlank()) return
        try {
            val payload = buildPayload(context, pageUrl, stage) ?: return
            com.petal.browser.extensions.PetalBuiltInExtensionManager.evaluateJavascript(session, payload)
        } catch (t: Throwable) {
            Log.w(TAG, "Store script injection failed for $pageUrl: ${t.message}")
        }
    }
}
