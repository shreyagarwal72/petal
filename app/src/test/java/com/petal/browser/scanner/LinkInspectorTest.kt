package com.petal.browser.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkInspectorTest {

    @Test
    fun testSafeUrl() {
        val report = LinkInspector.inspect("https://petal.browser/download")
        assertFalse(report.isSuspicious)
        assertEquals(RiskLevel.SAFE, report.riskLevel)
        assertTrue(report.warnings.isEmpty())
        assertEquals("petal.browser", report.host)
    }

    @Test
    fun testPunycodeDomain() {
        val report = LinkInspector.inspect("https://xn--e1afmkfd.xn--p1ai/path")
        assertTrue(report.isSuspicious)
        assertEquals(RiskLevel.HIGH, report.riskLevel)
        assertTrue(report.warnings.any { it.id == "punycode" })
    }

    @Test
    fun testLookalikeCharacters() {
        // 'а' is Cyrillic \u0430 spoofing latin 'a' in 'pаypal.com'
        val spoofed = "https://p\u0430ypal.com/login"
        val report = LinkInspector.inspect(spoofed)
        assertTrue(report.isSuspicious)
        assertEquals(RiskLevel.HIGH, report.riskLevel)
        assertTrue(report.warnings.any { it.id == "lookalike" })
    }

    @Test
    fun testUrlShortener() {
        val report = LinkInspector.inspect("https://bit.ly/3xYzAbc")
        assertTrue(report.isSuspicious)
        assertTrue(report.warnings.any { it.id == "shortener" })
        assertEquals("bit.ly", report.host)
    }

    @Test
    fun testInsecurePlainHttp() {
        val report = LinkInspector.inspect("http://example.com/insecure")
        assertTrue(report.isSuspicious)
        assertTrue(report.warnings.any { it.id == "plain_http" })
    }

    @Test
    fun testIpAddressHost() {
        val report = LinkInspector.inspect("http://192.168.1.1/admin")
        assertTrue(report.isSuspicious)
        assertEquals(RiskLevel.HIGH, report.riskLevel)
        assertTrue(report.warnings.any { it.id == "ip_host" })
        assertTrue(report.warnings.any { it.id == "plain_http" })
    }

    @Test
    fun testVeryLongUrl() {
        val longPath = "a".repeat(300)
        val report = LinkInspector.inspect("https://example.com/$longPath")
        assertTrue(report.isSuspicious)
        assertTrue(report.warnings.any { it.id == "long_url" })
    }
}
