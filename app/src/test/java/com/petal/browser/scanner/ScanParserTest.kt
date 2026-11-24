package com.petal.browser.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanParserTest {

    @Test
    fun testUrlParsing_standardHttps() {
        val payload = ScanParser.parse("   https://example.com/path?foo=bar   ")
        assertTrue(payload is ScanPayload.Url)
        val urlPayload = payload as ScanPayload.Url
        assertEquals("https://example.com/path?foo=bar", urlPayload.url)
        assertEquals("https", urlPayload.scheme)
        assertEquals("example.com", urlPayload.host)
    }

    @Test
    fun testUrlParsing_caseInsensitiveScheme() {
        val payload = ScanParser.parse("HTTPS://GITHUB.COM/shreyagarwal72/petal")
        assertTrue(payload is ScanPayload.Url)
        val urlPayload = payload as ScanPayload.Url
        assertEquals("https", urlPayload.scheme)
        assertEquals("github.com", urlPayload.host)
    }

    @Test
    fun testUrlParsing_bareDomain() {
        val payload = ScanParser.parse("example.org/search?q=petal")
        assertTrue(payload is ScanPayload.Url)
        val urlPayload = payload as ScanPayload.Url
        assertEquals("https://example.org/search?q=petal", urlPayload.url)
        assertEquals("example.org/search?q=petal", urlPayload.displayUrl)
        assertEquals("example.org", urlPayload.host)
    }

    @Test
    fun testEmailParsing_mailto() {
        val payload = ScanParser.parse("mailto:support@petal.browser?subject=Feedback&body=Hello%20Petal")
        assertTrue(payload is ScanPayload.Email)
        val email = payload as ScanPayload.Email
        assertEquals("support@petal.browser", email.address)
        assertEquals("Feedback", email.subject)
        assertEquals("Hello Petal", email.body)
    }

    @Test
    fun testEmailParsing_matmsg() {
        val payload = ScanParser.parse("MATMSG:TO:dev@petal.browser;SUB:Bug;BODY:Found a bug;;")
        assertTrue(payload is ScanPayload.Email)
        val email = payload as ScanPayload.Email
        assertEquals("dev@petal.browser", email.address)
        assertEquals("Bug", email.subject)
        assertEquals("Found a bug", email.body)
    }

    @Test
    fun testEmailParsing_bareEmail() {
        val payload = ScanParser.parse("user@domain.co.uk")
        assertTrue(payload is ScanPayload.Email)
        val email = payload as ScanPayload.Email
        assertEquals("user@domain.co.uk", email.address)
    }

    @Test
    fun testPhoneParsing_tel() {
        val payload = ScanParser.parse("TEL:+1-555-123-4567")
        assertTrue(payload is ScanPayload.Phone)
        assertEquals("+1-555-123-4567", (payload as ScanPayload.Phone).number)
    }

    @Test
    fun testPhoneParsing_bareDigits() {
        val payload = ScanParser.parse("+1 (800) 555-0199")
        assertTrue(payload is ScanPayload.Phone)
        assertEquals("+1 (800) 555-0199", (payload as ScanPayload.Phone).number)
    }

    @Test
    fun testSmsParsing() {
        val payload = ScanParser.parse("smsto:+123456789:Hello%20there")
        assertTrue(payload is ScanPayload.Sms)
        val sms = payload as ScanPayload.Sms
        assertEquals("+123456789", sms.number)
        assertEquals("Hello there", sms.message)
    }

    @Test
    fun testGeoParsing() {
        val payload = ScanParser.parse("geo:37.7749,-122.4194?q=San+Francisco")
        assertTrue(payload is ScanPayload.Geo)
        val geo = payload as ScanPayload.Geo
        assertEquals(37.7749, geo.latitude, 0.0001)
        assertEquals(-122.4194, geo.longitude, 0.0001)
        assertEquals("San Francisco", geo.query)
    }

    @Test
    fun testWifiParsing() {
        val payload = ScanParser.parse("WIFI:T:WPA;S:Home_Network;P:SuperSecret123;H:true;;")
        assertTrue(payload is ScanPayload.Wifi)
        val wifi = payload as ScanPayload.Wifi
        assertEquals("Home_Network", wifi.ssid)
        assertEquals("SuperSecret123", wifi.password)
        assertEquals("WPA", wifi.authType)
        assertTrue(wifi.hidden)
    }

    @Test
    fun testContactParsing_vCard() {
        val vcard = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Johnathan Doe
            TEL;TYPE=CELL:+1234567890
            EMAIL:jdoe@example.com
            ORG:Acme Corp
            NOTE:Senior Engineer
            END:VCARD
        """.trimIndent()
        val payload = ScanParser.parse(vcard)
        assertTrue(payload is ScanPayload.Contact)
        val contact = payload as ScanPayload.Contact
        assertEquals("Johnathan Doe", contact.name)
        assertEquals("+1234567890", contact.phone)
        assertEquals("jdoe@example.com", contact.email)
        assertEquals("Acme Corp", contact.org)
    }

    @Test
    fun testContactParsing_meCard() {
        val meCard = "MECARD:N:Doe,Jane;TEL:987654321;EMAIL:jane@example.com;ORG:Widget Inc;;"
        val payload = ScanParser.parse(meCard)
        assertTrue(payload is ScanPayload.Contact)
        val contact = payload as ScanPayload.Contact
        assertEquals("Doe Jane", contact.name)
        assertEquals("987654321", contact.phone)
        assertEquals("jane@example.com", contact.email)
    }

    @Test
    fun testCalendarParsing() {
        val vevent = """
            BEGIN:VEVENT
            SUMMARY:Product Launch
            DTSTART:20261124T100000Z
            LOCATION:Room 101
            DESCRIPTION:Big announcement
            END:VEVENT
        """.trimIndent()
        val payload = ScanParser.parse(vevent)
        assertTrue(payload is ScanPayload.Calendar)
        val cal = payload as ScanPayload.Calendar
        assertEquals("Product Launch", cal.title)
        assertNotNull(cal.startTime)
        assertEquals("Room 101", cal.location)
    }

    @Test
    fun testOtpParsing() {
        val otpUri = "otpauth://totp/GitHub:user?secret=JBSWY3DPEHPK3PXP&issuer=GitHub&digits=6&period=30"
        val payload = ScanParser.parse(otpUri)
        assertTrue(payload is ScanPayload.Otp)
        val otp = payload as ScanPayload.Otp
        assertEquals("totp", otp.type)
        assertEquals("JBSWY3DPEHPK3PXP", otp.secret)
        assertEquals("GitHub", otp.issuer)
        assertEquals("user", otp.account)
        assertEquals(6, otp.digits)
        assertEquals(30, otp.period)
    }

    @Test
    fun testProductParsing() {
        val payload = ScanParser.parse("012345678905", formatName = "UPC_A")
        assertTrue(payload is ScanPayload.Product)
        val prod = payload as ScanPayload.Product
        assertEquals("012345678905", prod.code)
        assertEquals("UPC_A", prod.format)
    }

    @Test
    fun testFallbackText() {
        val text = "Just some random notes and text 123"
        val payload = ScanParser.parse(text)
        assertTrue(payload is ScanPayload.Text)
        assertEquals(text, (payload as ScanPayload.Text).content)
    }
}
