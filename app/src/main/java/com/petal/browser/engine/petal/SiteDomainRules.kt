package com.petal.browser.engine.petal

import com.petal.browser.engine.petal.blocking.PetalHostCanonicalizer
import com.petal.browser.engine.petal.blocking.PrivacyRequestSanitizer
import mozilla.components.lib.publicsuffixlist.PublicSuffixList
import kotlinx.coroutines.runBlocking

object SiteDomainRules {
    const val MAX_PER_PROFILE = 64

    @Volatile
    private var psl: PublicSuffixList? = null

    private fun getPsl(): PublicSuffixList? {
        if (psl == null) {
            val ctx = try { com.petal.browser.PetalApplication.instance } catch (_: Throwable) { null }
            if (ctx != null) {
                psl = PublicSuffixList(ctx)
            }
        }
        return psl
    }

    fun domainForUrl(url: String?): String? {
        val safeUrl = url ?: return null
        return PrivacyRequestSanitizer.webHost(safeUrl)?.let(::normalizedDomain)
    }

    // Bounded cache: the PSL lookup uses runBlocking, so repeating it for every stored
    // domain on every navigation would stall the calling (often main) thread.
    private val domainCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private const val DOMAIN_CACHE_MAX = 512

    fun normalizedDomain(host: String?): String? {
        val normalizedHost = PetalHostCanonicalizer.canonicalHost(host) ?: return null
        domainCache[normalizedHost]?.let { return it }
        val pslInstance = getPsl() ?: return normalizedHost
        val resolved = try {
            runBlocking { pslInstance.getPublicSuffixPlusOne(normalizedHost).await() } ?: normalizedHost
        } catch (_: Throwable) {
            return normalizedHost
        }
        if (domainCache.size >= DOMAIN_CACHE_MAX) domainCache.clear()
        domainCache[normalizedHost] = resolved
        return resolved
    }

    fun contains(url: String?, domains: Collection<String>): Boolean {
        val domain = domainForUrl(url) ?: return false
        return domains.any { normalizedDomain(it) == domain }
    }

    fun withState(
        current: Collection<String>,
        domain: String,
        enabled: Boolean,
        limit: Int = MAX_PER_PROFILE,
    ): Set<String> {
        val normalizedDomain = normalizedDomain(domain) ?: return current.toSet()
        val retained = current.asSequence()
            .mapNotNull(::normalizedDomain)
            .filterNot { it == normalizedDomain }
            .distinct()
        if (!enabled) return retained.toCollection(linkedSetOf())
        if (limit <= 0) return emptySet()
        return (retained.take(limit - 1) + sequenceOf(normalizedDomain))
            .toCollection(linkedSetOf())
    }
}
