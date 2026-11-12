package com.petal.browser.unit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PetalDnsCatalogTest {
    @Test fun catalogIsValid() {
        assertTrue(PetalDnsCatalog.validateCatalog().toString(), PetalDnsCatalog.validateCatalog().isEmpty())
    }

    @Test fun legacyModesStillResolve() {
        listOf("CLOUDFLARE", "GOOGLE", "CLEANBROWSING", "OPENDNS", "NEXTDNS", "QUAD9").forEach {
            assertNotNull(it, PetalDnsCatalog.find(it))
        }
        assertEquals("https://cloudflare-dns.com/dns-query", PetalDnsCatalog.find("CLOUDFLARE")?.dohUrl)
    }

    @Test fun nextDnsProfileUrl() {
        assertEquals("https://dns.nextdns.io/abc123", PetalDnsCatalog.nextDnsUrl(" abc123 "))
        assertEquals("https://dns.nextdns.io/dns-query", PetalDnsCatalog.nextDnsUrl(""))
        assertEquals("https://dns.nextdns.io/dns-query", PetalDnsCatalog.nextDnsUrl("../evil"))
        assertFalse(PetalDnsCatalog.isValidNextDnsProfileId("ab"))
    }
}
