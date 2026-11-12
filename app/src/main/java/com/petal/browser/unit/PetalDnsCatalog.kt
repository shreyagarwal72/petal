package com.petal.browser.unit

/**
 * Bundled DNS-over-HTTPS provider presets.
 *
 * Derived from third-party MIT-licensed code; the required notice is in THIRD_PARTY_NOTICES.md.
 *
 * [mode] is the value persisted in "sp_private_dns_mode". The legacy modes (CLOUDFLARE, GOOGLE,
 * CLEANBROWSING, OPENDNS, NEXTDNS, QUAD9) are kept so existing user settings keep working.
 */
data class PetalDnsPreset(
    val mode: String,
    val provider: String,
    val variant: String?,
    val description: String,
    val hostname: String,
    val dohUrl: String,
) {
    val title: String get() = if (variant == null) provider else "$provider · $variant"
}

object PetalDnsCatalog {

    val presets: List<PetalDnsPreset> = listOf(
        // AdGuard
        PetalDnsPreset("ADGUARD", "AdGuard DNS", "Default", "Blocks ads and trackers", "dns.adguard-dns.com", "https://dns.adguard-dns.com/dns-query"),
        PetalDnsPreset("ADGUARD_FAMILY", "AdGuard DNS", "Family", "Blocks ads, trackers and adult content, enforces safe search", "family.adguard-dns.com", "https://family.adguard-dns.com/dns-query"),
        PetalDnsPreset("ADGUARD_UNFILTERED", "AdGuard DNS", "Unfiltered", "Encrypted DNS with no filtering", "unfiltered.adguard-dns.com", "https://unfiltered.adguard-dns.com/dns-query"),
        // Cloudflare
        PetalDnsPreset("CLOUDFLARE", "Cloudflare", "Standard", "Fast and private 1.1.1.1 DNS over HTTPS", "one.one.one.one", "https://cloudflare-dns.com/dns-query"),
        PetalDnsPreset("CLOUDFLARE_SECURITY", "Cloudflare", "Security", "Blocks known malware domains", "security.cloudflare-dns.com", "https://security.cloudflare-dns.com/dns-query"),
        PetalDnsPreset("CLOUDFLARE_FAMILY", "Cloudflare", "Family", "Blocks malware and adult content", "family.cloudflare-dns.com", "https://family.cloudflare-dns.com/dns-query"),
        // Google
        PetalDnsPreset("GOOGLE", "Google Public DNS", null, "High performance resolution", "dns.google", "https://dns.google/dns-query"),
        // Quad9
        PetalDnsPreset("QUAD9", "Quad9", "Secured", "Blocks malware and phishing", "dns.quad9.net", "https://dns.quad9.net/dns-query"),
        PetalDnsPreset("QUAD9_ECS", "Quad9", "Secured + ECS", "Malware blocking with EDNS client subnet for localized routing", "dns11.quad9.net", "https://dns11.quad9.net/dns-query"),
        PetalDnsPreset("QUAD9_UNSECURED", "Quad9", "Unsecured", "Standard resolution, no blocking", "dns10.quad9.net", "https://dns10.quad9.net/dns-query"),
        // OpenDNS
        PetalDnsPreset("OPENDNS", "OpenDNS", "Standard", "Cisco OpenDNS security protection", "dns.opendns.com", "https://doh.opendns.com/dns-query"),
        PetalDnsPreset("OPENDNS_FAMILY", "OpenDNS", "FamilyShield", "Blocks adult content", "dns.familyshield.opendns.com", "https://doh.familyshield.opendns.com/dns-query"),
        // CleanBrowsing
        PetalDnsPreset("CLEANBROWSING", "CleanBrowsing", "Family filter", "Blocks adult and malicious sites", "family-filter-dns.cleanbrowsing.org", "https://doh.cleanbrowsing.org/doh/family-filter/"),
        // DNSForge
        PetalDnsPreset("DNSFORGE", "DNSForge", "Normal", "Blocks ads, trackers and malware", "dnsforge.de", "https://dnsforge.de/dns-query"),
        PetalDnsPreset("DNSFORGE_CLEAN", "DNSForge", "Clean", "Ads, trackers, malware, adult content, safe search", "clean.dnsforge.de", "https://clean.dnsforge.de/dns-query"),
        PetalDnsPreset("DNSFORGE_HARD", "DNSForge", "Hard", "Stricter ad, tracker and malware blocking", "hard.dnsforge.de", "https://hard.dnsforge.de/dns-query"),
        PetalDnsPreset("DNSFORGE_BLANK", "DNSForge", "Blank", "Encrypted DNS with no filtering", "blank.dnsforge.de", "https://blank.dnsforge.de/dns-query"),
        // HaGeZi
        PetalDnsPreset("HAGEZI_ROOT", "HaGeZi", "Root", "Ads, trackers and malware", "root.hagezi.org", "https://root.hagezi.org/dns-query"),
        PetalDnsPreset("HAGEZI_WURZN", "HaGeZi", "Wurzn", "Ads, trackers and malware", "wurzn.hagezi.org", "https://wurzn.hagezi.org/dns-query"),
        PetalDnsPreset("HAGEZI_JUURI", "HaGeZi", "Juuri", "Ads, trackers and malware", "juuri.hagezi.org", "https://juuri.hagezi.org/dns-query"),
        PetalDnsPreset("HAGEZI_CTIF", "HaGeZi", "CTIF", "Malware and threat-intelligence feeds only", "ctif.hagezi.org", "https://ctif.hagezi.org/dns-query"),
        // NextDNS (endpoint depends on the user's profile ID, see nextDnsUrl)
        PetalDnsPreset("NEXTDNS", "NextDNS", null, "Encrypted DNS with your own NextDNS profile", "dns.nextdns.io", "https://dns.nextdns.io/dns-query"),
    )

    private val byMode: Map<String, PetalDnsPreset> = presets.associateBy { it.mode }

    private val profileIdRegex = Regex("^[A-Za-z0-9]{4,12}$")

    @JvmStatic
    fun find(mode: String?): PetalDnsPreset? = mode?.let { byMode[it] }

    @JvmStatic
    fun isValidNextDnsProfileId(id: String?): Boolean = id != null && profileIdRegex.matches(id.trim())

    /** Personal NextDNS endpoint when a valid profile ID is set, otherwise the generic one. */
    @JvmStatic
    fun nextDnsUrl(profileId: String?): String {
        val id = profileId?.trim()
        return if (isValidNextDnsProfileId(id)) "https://dns.nextdns.io/$id" else "https://dns.nextdns.io/dns-query"
    }

    /** Only catalog modes are valid presets; CUSTOM and OFF are handled by the caller. */
    @JvmStatic
    fun validateCatalog(): List<String> {
        val problems = mutableListOf<String>()
        presets.groupBy { it.mode }.filterValues { it.size > 1 }.keys.forEach { problems += "duplicate mode $it" }
        presets.forEach { if (!it.dohUrl.startsWith("https://")) problems += "non-https url ${it.mode}" }
        return problems
    }
}
