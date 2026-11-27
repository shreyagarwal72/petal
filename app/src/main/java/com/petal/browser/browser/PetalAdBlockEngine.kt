/*
 * PetalAdBlockEngine.kt
 * ─────────────────────────────────────────────────────────────────────────
 * uBlock Origin & AdGuard-grade Ad & Tracker Blocker Engine for Petal Browser.
 * Features:
 * 1. Trie / Bloom filter network request interceptor for shouldInterceptRequest
 *    parsing EasyList, EasyPrivacy, uBlock (uAssets), and AdGuard mobile rules.
 * 2. High-performance empty 204 HTTP WebResourceResponse stream replacement.
 * 3. Official uBO scriptlets (set-constant, abort-on-property-read, nano-sib).
 * 4. Cosmetic CSS rules and procedural selectors (:has(), :has-text()) via MutationObserver.
 * 5. Dedicated YouTube mobile scriptlet auto-skipping, muting, and accelerating video ads.
 * 6. Per-domain whitelist support & SharedPreferences/DataStore persistence,
 *    with strict session-scoped isolation for private tabs.
 */

package com.petal.browser.browser

import android.content.Context
import android.net.Uri
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object PetalAdBlockEngine {

    private val whitelistedDomains = ConcurrentHashMap.newKeySet<String>()
    private val privateWhitelistedDomains = ConcurrentHashMap.newKeySet<String>()
    private val domainBlockedCount = ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>()
    private val privateDomainBlockedCount = ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>()
    private val totalBlockedCount = java.util.concurrent.atomic.AtomicLong(0)
    private val _trackerRevision = MutableStateFlow(0L)
    val trackerRevision: StateFlow<Long> = _trackerRevision.asStateFlow()
    private val _stateVersion = MutableStateFlow(0L)
    @get:JvmStatic
    val stateVersion: StateFlow<Long> = _stateVersion.asStateFlow()
    @Volatile private var isInitialized = false
    @Volatile private var storedAppContext: Context? = null

    private fun notifyStateChanged(context: Context? = storedAppContext) {
        _stateVersion.value = _stateVersion.value + 1L
        _trackerRevision.value = _trackerRevision.value + 1L
        context?.let {
            runCatching { com.petal.browser.extensions.PetalBuiltInExtensionManager.pushBlockerConfig(it.applicationContext) }
        }
    }

    @JvmStatic
    fun ensureInitialized(context: Context) {
        storedAppContext = context.applicationContext ?: context
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            val appContext = context.applicationContext ?: context
            val sp = PreferenceManager.getDefaultSharedPreferences(appContext)

            // Load user domain whitelist
            val whitelistSet = sp.getStringSet("sp_adblock_whitelisted_domains", emptySet()) ?: emptySet()
            whitelistedDomains.addAll(whitelistSet)

            sp.registerOnSharedPreferenceChangeListener { prefs, key ->
                if (key == "sp_adblock_whitelisted_domains") {
                    whitelistedDomains.clear()
                    whitelistedDomains.addAll(prefs.getStringSet("sp_adblock_whitelisted_domains", emptySet()) ?: emptySet())
                }
            }

            // Initialize active filter subscriptions
            // Multi-MB host lists: parse off the main thread so cold start isn't blocked.
            Thread({ PetalFilterSubscriptionManager.reloadActiveRules(appContext) }, "petal-adblock-rules")
                .apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
                .start()

            isInitialized = true
        }
    }

    @JvmStatic
    fun getAdBlockMode(context: Context): PetalAdBlockMode {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.contextOrApp())
        val isEnabled = sp.getBoolean("sp_ad_block", true)
        if (!isEnabled) return PetalAdBlockMode.DISABLED
        val modeStr = sp.getString("sp_adblock_protection_mode", PetalAdBlockMode.STANDARD.id)
        return PetalAdBlockMode.fromId(modeStr)
    }

    @JvmStatic
    fun setAdBlockMode(context: Context, mode: PetalAdBlockMode) {
        val appCtx = context.contextOrApp()
        storedAppContext = appCtx
        val sp = PreferenceManager.getDefaultSharedPreferences(appCtx)
        val isEnabled = mode != PetalAdBlockMode.DISABLED
        sp.edit()
            .putBoolean("sp_ad_block", isEnabled)
            .putBoolean("profileStandard_adBlock", isEnabled)
            .putString("sp_adblock_protection_mode", mode.id)
            .apply()
        try {
            com.petal.browser.engine.gecko.PetalGeckoRuntime.syncPreferences(sp)
        } catch (_: Throwable) {}
        notifyStateChanged(appCtx)
    }

    @JvmStatic
    fun getDomainOverride(context: Context, domain: String?): PetalAdBlockMode? {
        if (domain.isNullOrBlank()) return null
        val cleanDomain = domain.lowercase(Locale.US).removePrefix("www.")
        val sp = PreferenceManager.getDefaultSharedPreferences(context.contextOrApp())
        val modeStr = sp.getString("sp_adblock_domain_mode_$cleanDomain", null) ?: return null
        return PetalAdBlockMode.fromId(modeStr)
    }

    @JvmStatic
    fun setDomainOverride(context: Context, domain: String, mode: PetalAdBlockMode?) {
        if (domain.isBlank()) return
        val appCtx = context.contextOrApp()
        storedAppContext = appCtx
        val cleanDomain = domain.lowercase(Locale.US).removePrefix("www.")
        val sp = PreferenceManager.getDefaultSharedPreferences(appCtx)
        if (mode == null) {
            sp.edit().remove("sp_adblock_domain_mode_$cleanDomain").apply()
        } else {
            sp.edit().putString("sp_adblock_domain_mode_$cleanDomain", mode.id).apply()
        }
        notifyStateChanged(appCtx)
    }

    @JvmStatic
    @JvmOverloads
    fun getEffectiveMode(context: Context, domain: String?, isPrivate: Boolean = false): PetalAdBlockMode {
        if (domain != null && isDomainWhitelisted(domain, isPrivate)) return PetalAdBlockMode.DISABLED
        val override = if (isPrivate) null else getDomainOverride(context, domain)
        return override ?: getAdBlockMode(context)
    }

    @JvmStatic
    fun isAdBlockEnabled(context: Context): Boolean {
        return getAdBlockMode(context) != PetalAdBlockMode.DISABLED
    }

    @JvmStatic
    fun setAdBlockEnabled(context: Context, enabled: Boolean) {
        val targetMode = if (enabled) PetalAdBlockMode.STANDARD else PetalAdBlockMode.DISABLED
        setAdBlockMode(context, targetMode)
    }

    private fun domainMatchesEntry(cleanDomain: String, entry: String): Boolean {
        val cleanEntry = entry.lowercase(Locale.US).removePrefix("www.")
        return cleanEntry.isNotEmpty() && (cleanDomain == cleanEntry || cleanDomain.endsWith(".$cleanEntry"))
    }

    @JvmStatic
    @JvmOverloads
    fun isDomainWhitelisted(domain: String, isPrivate: Boolean = false): Boolean {
        if (domain.isBlank()) return false
        val cleanDomain = domain.lowercase(Locale.US).removePrefix("www.")
        return if (isPrivate) {
            privateWhitelistedDomains.any { domainMatchesEntry(cleanDomain, it) }
        } else {
            whitelistedDomains.any { domainMatchesEntry(cleanDomain, it) }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun addDomainToWhitelist(context: Context, domain: String, isPrivate: Boolean = false) {
        if (domain.isBlank()) return
        val appCtx = context.contextOrApp()
        storedAppContext = appCtx
        val cleanDomain = domain.lowercase(Locale.US).removePrefix("www.")
        if (isPrivate) {
            // Private tabs only hold session-scoped exceptions in memory; never persist to disk.
            privateWhitelistedDomains.add(cleanDomain)
            notifyStateChanged(appCtx)
            return
        }
        whitelistedDomains.add(cleanDomain)

        val sp = PreferenceManager.getDefaultSharedPreferences(appCtx)
        sp.edit().putStringSet("sp_adblock_whitelisted_domains", whitelistedDomains.toSet()).apply()
        notifyStateChanged(appCtx)
    }

    @JvmStatic
    @JvmOverloads
    fun removeDomainFromWhitelist(context: Context, domain: String, isPrivate: Boolean = false) {
        val appCtx = context.contextOrApp()
        storedAppContext = appCtx
        val cleanDomain = domain.lowercase(Locale.US).removePrefix("www.")
        if (isPrivate) {
            privateWhitelistedDomains.remove(cleanDomain)
            notifyStateChanged(appCtx)
            return
        }
        whitelistedDomains.remove(cleanDomain)

        val sp = PreferenceManager.getDefaultSharedPreferences(appCtx)
        sp.edit().putStringSet("sp_adblock_whitelisted_domains", whitelistedDomains.toSet()).apply()
        notifyStateChanged(appCtx)
    }

    @JvmStatic
    fun clearPrivateSessionData() {
        privateWhitelistedDomains.clear()
        privateDomainBlockedCount.clear()
        privateDomainCategoryCounts.clear()
        notifyStateChanged()
    }

    @JvmStatic
    fun getWhitelistedDomains(): Set<String> = whitelistedDomains.toSet()

    @JvmStatic
    fun getPrivateWhitelistedDomains(): Set<String> = privateWhitelistedDomains.toSet()

    @JvmStatic
    fun getAllDomainOverrides(context: Context): Map<String, String> {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.contextOrApp())
        val prefix = "sp_adblock_domain_mode_"
        val out = LinkedHashMap<String, String>()
        for ((k, v) in sp.all) {
            if (k.startsWith(prefix) && v is String) {
                val domain = k.removePrefix(prefix)
                if (domain.isNotBlank()) out[domain] = PetalAdBlockMode.fromId(v).id
            }
        }
        return out
    }

    data class BlockedCategoryCounts(
        val ads: Int = 0,
        val analytics: Int = 0,
        val social: Int = 0,
        val content: Int = 0,
        val cookies: Int = 0
    )

    private val domainCategoryCounts = ConcurrentHashMap<String, BlockedCategoryCounts>()
    private val privateDomainCategoryCounts = ConcurrentHashMap<String, BlockedCategoryCounts>()

    @JvmOverloads
    fun recordBlock(pageUrl: String?, isPrivate: Boolean = false) {
        if (!isPrivate) {
            totalBlockedCount.incrementAndGet()
        }
        if (!pageUrl.isNullOrBlank()) {
            val pageHost = try { Uri.parse(pageUrl).host ?: "" } catch (e: Exception) { "" }
            val cleanHost = pageHost.lowercase(Locale.US).removePrefix("www.")
            if (cleanHost.isNotEmpty()) {
                val targetMap = if (isPrivate) privateDomainBlockedCount else domainBlockedCount
                targetMap.computeIfAbsent(cleanHost) { java.util.concurrent.atomic.AtomicInteger(0) }.incrementAndGet()
            }
        }
        notifyStateChanged()
    }

    @JvmStatic
    @JvmOverloads
    fun recordBlockWithCategories(
        pageUrl: String?,
        antiTrackingCategory: Int,
        cookieCategory: Int = 0,
        isPrivate: Boolean = false
    ) {
        recordBlock(pageUrl, isPrivate = isPrivate)
        if (pageUrl.isNullOrBlank()) return
        val pageHost = try { Uri.parse(pageUrl).host ?: "" } catch (e: Exception) { "" }
        val cleanHost = pageHost.lowercase(Locale.US).removePrefix("www.")
        if (cleanHost.isEmpty()) return

        val targetMap = if (isPrivate) privateDomainCategoryCounts else domainCategoryCounts
        targetMap.compute(cleanHost) { _, current ->
            val prev = current ?: BlockedCategoryCounts()
            var ads = prev.ads
            var analytics = prev.analytics
            var social = prev.social
            var content = prev.content
            var cookies = prev.cookies

            // Check Mozilla ContentBlocking.AntiTracking flags
            if ((antiTrackingCategory and org.mozilla.geckoview.ContentBlocking.AntiTracking.AD) != 0) ads++
            if ((antiTrackingCategory and org.mozilla.geckoview.ContentBlocking.AntiTracking.ANALYTIC) != 0) analytics++
            if ((antiTrackingCategory and (org.mozilla.geckoview.ContentBlocking.AntiTracking.SOCIAL or org.mozilla.geckoview.ContentBlocking.AntiTracking.STP)) != 0) social++
            if ((antiTrackingCategory and org.mozilla.geckoview.ContentBlocking.AntiTracking.CONTENT) != 0) content++
            if (cookieCategory != 0) cookies++

            BlockedCategoryCounts(ads, analytics, social, content, cookies)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun getCategoryCountsForDomain(domain: String?, isPrivate: Boolean = false): BlockedCategoryCounts {
        if (domain.isNullOrBlank()) return BlockedCategoryCounts()
        val cleanHost = domain.lowercase(Locale.US).removePrefix("www.")
        val map = if (isPrivate) privateDomainCategoryCounts else domainCategoryCounts
        return map[cleanHost] ?: BlockedCategoryCounts()
    }

    @JvmStatic
    @JvmOverloads
    fun getBlockedCountForDomain(domain: String?, isPrivate: Boolean = false): Int {
        if (domain.isNullOrBlank()) return 0
        val cleanHost = domain.lowercase(Locale.US).removePrefix("www.")
        val map = if (isPrivate) privateDomainBlockedCount else domainBlockedCount
        return map[cleanHost]?.get() ?: 0
    }

    @JvmStatic
    fun getTotalBlockedCount(): Long = totalBlockedCount.get()

    /**
     * Deprecated: Cosmetic filtering and scriptlets are handled directly at document_start
     * by the petal_network_blocker WebExtension content script (early_cosmetics.js).
     */
    @Deprecated("Handled by petal_network_blocker WebExtension early_cosmetics.js at document_start")
    @JvmStatic
    fun getuBlockCosmeticAndScriptletPayload(url: String? = null): String = ""

    private fun Context.contextOrApp(): Context = this.applicationContext ?: this
}
