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
    fun buildPayload(context: Context, pageUrl: String): String? {
        if (!pageUrl.startsWith("http://", ignoreCase = true) &&
            !pageUrl.startsWith("https://", ignoreCase = true)
        ) return null

        val scripts = PetalStoreCatalog.enabledScriptsFor(context.applicationContext, pageUrl)
        if (scripts.isEmpty()) return null

        val body = StringBuilder()
        body.append("var r=window.__petalStoreScripts=window.__petalStoreScripts||{};\n")
        for (script in scripts) {
            val id = script.id.replace("'", "")
            // The guard keeps a second onPageStop for the same document from stacking timers.
            body.append("if(!r['").append(id).append("']){r['").append(id).append("']=1;try{\n")
            body.append(script.scriptContent)
            body.append("\n}catch(e){}}\n")
        }
        return "void((function(){" + body + "})());"
    }

    /** Injects every enabled, matching store script into [session]. Safe to call on any page. */
    fun inject(session: GeckoSession, context: Context, pageUrl: String?) {
        if (pageUrl.isNullOrBlank()) return
        try {
            val payload = buildPayload(context, pageUrl) ?: return
            session.loadUri("javascript:" + Uri.encode(payload))
        } catch (t: Throwable) {
            Log.w(TAG, "Store script injection failed for $pageUrl: ${t.message}")
        }
    }
}
