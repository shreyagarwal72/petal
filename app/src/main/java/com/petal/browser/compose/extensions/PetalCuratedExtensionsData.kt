package com.petal.browser.compose.extensions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Camera
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ManageSearch
import androidx.compose.material.icons.rounded.MobileFriendly
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Curated icon + accent color mapping for Petal's recommended extensions catalog.
 *
 * This is a pure data file — no logic, no state, no Android dependencies beyond
 * Compose icons. The [PetalExtensionsScreen] uses this to render per-extension
 * colored icons in the AddExtensionSheet, replacing the old generic puzzle-piece icon.
 *
 * All 28 official Mozilla-recommended Firefox Android extensions are mapped here.
 */
object PetalCuratedExtensionsData {

    data class ExtensionVisual(
        val icon: ImageVector,
        val accentColor: Color
    )

    /** Returns [ExtensionVisual] for a known extension AMO slug, or null for unknown slugs. */
    fun getVisual(amoSlug: String): ExtensionVisual? = visuals[amoSlug]

    /** Returns real Mozilla AMO CDN icon URL if known */
    fun getAmoIconUrl(slugOrId: String): String? {
        val clean = slugOrId.lowercase().trim()
        return amoIconUrls[clean]
    }

    private val amoIconUrls: Map<String, String> = mapOf(
        "ublock-origin" to "https://addons.mozilla.org/user-media/addon_icons/607/607454-64.png",
        "uBlock0@raymondhill.net" to "https://addons.mozilla.org/user-media/addon_icons/607/607454-64.png",
        "adguard-adblocker" to "https://addons.mozilla.org/user-media/addon_icons/507/507454-64.png",
        "adguardadblocker@adguard.com" to "https://addons.mozilla.org/user-media/addon_icons/507/507454-64.png",
        "privacy-badger17" to "https://addons.mozilla.org/user-media/addon_icons/506/506646-64.png",
        "jid1-MnnxcxisbtPdag@jetpack" to "https://addons.mozilla.org/user-media/addon_icons/506/506646-64.png",
        "noscript" to "https://addons.mozilla.org/user-media/addon_icons/0/722-64.png",
        "{73a6fe31-595d-460b-a920-fcc0f8807332}" to "https://addons.mozilla.org/user-media/addon_icons/0/722-64.png",
        "bitwarden-password-manager" to "https://addons.mozilla.org/user-media/addon_icons/817/817072-64.png",
        "{446900e4-71c2-419f-a6a7-df9c091e268b}" to "https://addons.mozilla.org/user-media/addon_icons/817/817072-64.png",
        "proton-pass" to "https://addons.mozilla.org/user-media/addon_icons/2816/2816353-64.png",
        "78272b6fa58f4a1fa199@proton.me" to "https://addons.mozilla.org/user-media/addon_icons/2816/2816353-64.png",
        "keepassxc-browser" to "https://addons.mozilla.org/user-media/addon_icons/913/913508-64.png",
        "keepassxc-browser@keepassxc.org" to "https://addons.mozilla.org/user-media/addon_icons/913/913508-64.png",
        "clearurls" to "https://addons.mozilla.org/user-media/addon_icons/1020/1020942-64.png",
        "{74145f27-f039-47ce-a470-a662b129930a}" to "https://addons.mozilla.org/user-media/addon_icons/1020/1020942-64.png",
        "decentraleyes" to "https://addons.mozilla.org/user-media/addon_icons/655/655977-64.png",
        "jid1-BoFifL9Vbdl2zQ@jetpack" to "https://addons.mozilla.org/user-media/addon_icons/655/655977-64.png",
        "istilldontcareaboutcookies" to "https://addons.mozilla.org/user-media/addon_icons/2744/2744383-64.png",
        "idcac-pub@guus.ninja" to "https://addons.mozilla.org/user-media/addon_icons/2744/2744383-64.png",
        "cookie-editor" to "https://addons.mozilla.org/user-media/addon_icons/909/909670-64.png",
        "{5610edea-88c1-4370-b93d-86aa131971d1}" to "https://addons.mozilla.org/user-media/addon_icons/909/909670-64.png",
        "consent-o-matic" to "https://addons.mozilla.org/user-media/addon_icons/2677/2677134-64.png",
        "gdpr@cavi.au.dk" to "https://addons.mozilla.org/user-media/addon_icons/2677/2677134-64.png",
        "styl-us" to "https://addons.mozilla.org/user-media/addon_icons/814/814444-64.png",
        "{7a7a4a84-a25a-497d-ac11-345e8d477300}" to "https://addons.mozilla.org/user-media/addon_icons/814/814444-64.png",
        "sponsorblock" to "https://addons.mozilla.org/user-media/addon_icons/1085/1085187-64.png",
        "sponsorBlocker@ajay.app" to "https://addons.mozilla.org/user-media/addon_icons/1085/1085187-64.png",
        "video-background-play-fix" to "https://addons.mozilla.org/user-media/addon_icons/827/827599-64.png",
        "video-bg-play@albin.pw" to "https://addons.mozilla.org/user-media/addon_icons/827/827599-64.png",
        "youtube-high-definition" to "https://addons.mozilla.org/user-media/addon_icons/463/463870-64.png",
        "youtube-recommended-videos" to "https://addons.mozilla.org/user-media/addon_icons/2691/2691763-64.png",
        "violentmonkey" to "https://addons.mozilla.org/user-media/addon_icons/825/825838-64.png",
        "{ae438240-c110-449e-876b-9372f8832a82}" to "https://addons.mozilla.org/user-media/addon_icons/825/825838-64.png",
        "traduzir-paginas-web" to "https://addons.mozilla.org/user-media/addon_icons/1039/1039868-64.png",
        "{036a55b4-5e72-4d05-a96c-5f307b192bc0}" to "https://addons.mozilla.org/user-media/addon_icons/1039/1039868-64.png",
        "google-search-fixer" to "https://addons.mozilla.org/user-media/addon_icons/1066/1066699-64.png",
        "search_by_image" to "https://addons.mozilla.org/user-media/addon_icons/826/826359-64.png",
        "{2e22271b-0ac3-4261-bc98-317818b64b14}" to "https://addons.mozilla.org/user-media/addon_icons/826/826359-64.png",
        "view-page-archive" to "https://addons.mozilla.org/user-media/addon_icons/920/920253-64.png",
        "single-file" to "https://addons.mozilla.org/user-media/addon_icons/845/845182-64.png",
        "{531906d3-e22f-4a6c-a102-8057b88a1a63}" to "https://addons.mozilla.org/user-media/addon_icons/845/845182-64.png",
        "tomato-clock" to "https://addons.mozilla.org/user-media/addon_icons/658/658253-64.png",
        "leechblock-ng" to "https://addons.mozilla.org/user-media/addon_icons/887/887258-64.png",
        "read-aloud" to "https://addons.mozilla.org/user-media/addon_icons/829/829410-64.png",
        "chrome-mask" to "https://addons.mozilla.org/user-media/addon_icons/865/865912-64.png"
    )

    private val visuals: Map<String, ExtensionVisual> = mapOf(
        // ── Ad/tracker blocking ──────────────────────────────────────────
        "ublock-origin" to ExtensionVisual(Icons.Rounded.Shield, Color(0xFFFF3B5C)),
        "adguard-adblocker" to ExtensionVisual(Icons.Rounded.Shield, Color(0xFF67B346)),
        "privacy-badger17" to ExtensionVisual(Icons.Rounded.Security, Color(0xFF10B981)),
        "noscript" to ExtensionVisual(Icons.Rounded.Block, Color(0xFFDC2626)),

        // ── Password managers ────────────────────────────────────────────
        "bitwarden-password-manager" to ExtensionVisual(Icons.Rounded.Lock, Color(0xFF175DDC)),
        "proton-pass" to ExtensionVisual(Icons.Rounded.Key, Color(0xFF6D4AFF)),
        "keepassxc-browser" to ExtensionVisual(Icons.Rounded.VpnKey, Color(0xFF53A048)),

        // ── Privacy tools ────────────────────────────────────────────────
        "clearurls" to ExtensionVisual(Icons.Rounded.LinkOff, Color(0xFFEC4899)),
        "decentraleyes" to ExtensionVisual(Icons.Rounded.Storage, Color(0xFF6366F1)),

        // ── Cookie management ────────────────────────────────────────────
        "istilldontcareaboutcookies" to ExtensionVisual(Icons.Rounded.Block, Color(0xFF64748B)),
        "cookie-editor" to ExtensionVisual(Icons.Rounded.Edit, Color(0xFFEAB308)),
        "consent-o-matic" to ExtensionVisual(Icons.Rounded.Policy, Color(0xFF0EA5E9)),

        // ── Appearance / UI ──────────────────────────────────────────────
        "styl-us" to ExtensionVisual(Icons.Rounded.Style, Color(0xFFD946EF)),

        // ── YouTube / Video ──────────────────────────────────────────────
        "sponsorblock" to ExtensionVisual(Icons.Rounded.PlayCircle, Color(0xFFF59E0B)),
        "video-background-play-fix" to ExtensionVisual(Icons.Rounded.OndemandVideo, Color(0xFF8B5CF6)),
        "youtube-high-definition" to ExtensionVisual(Icons.Rounded.HighQuality, Color(0xFFEF4444)),
        "youtube-recommended-videos" to ExtensionVisual(Icons.Rounded.Pause, Color(0xFFE11D48)),

        // ── Scripting / Automation ───────────────────────────────────────
        "violentmonkey" to ExtensionVisual(Icons.Rounded.Code, Color(0xFF14B8A6)),

        // ── Translation ──────────────────────────────────────────────────
        "traduzir-paginas-web" to ExtensionVisual(Icons.Rounded.Translate, Color(0xFF3B82F6)),

        // ── Search / Utilities ───────────────────────────────────────────
        "google-search-fixer" to ExtensionVisual(Icons.Rounded.ManageSearch, Color(0xFF4285F4)),
        "search_by_image" to ExtensionVisual(Icons.Rounded.Camera, Color(0xFFF97316)),
        "view-page-archive" to ExtensionVisual(Icons.Rounded.History, Color(0xFF0EA5E9)),
        "single-file" to ExtensionVisual(Icons.Rounded.Save, Color(0xFF059669)),

        // ── Productivity ─────────────────────────────────────────────────
        "tomato-clock" to ExtensionVisual(Icons.Rounded.Timer, Color(0xFFFF6B6B)),
        "leechblock-ng" to ExtensionVisual(Icons.Rounded.Lock, Color(0xFF7C3AED)),
        "read-aloud" to ExtensionVisual(Icons.Rounded.VolumeUp, Color(0xFF2563EB)),

        // ── Compatibility ────────────────────────────────────────────────
        "chrome-mask" to ExtensionVisual(Icons.Rounded.MobileFriendly, Color(0xFF4A90D9))
    )
}
