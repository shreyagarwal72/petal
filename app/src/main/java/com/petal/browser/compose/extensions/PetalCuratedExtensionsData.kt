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
        // Keys are matched case-insensitively: several real extension IDs (uBlock0@..,
        // sponsorBlocker@.., jid1-..@jetpack) contain capitals and never matched a lowercased lookup.
        return amoIconUrlsByLowerKey[clean]
    }

    /**
     * Maps a Gecko extension ID (GUID / e-mail style) back to its AMO slug, using the icon table
     * where both forms point at the same icon. Returns null for IDs that are not curated.
     */
    fun slugForExtensionId(extensionId: String): String? = idToSlug[extensionId.trim().lowercase()]

    private val amoIconUrlsByLowerKey: Map<String, String> by lazy {
        amoIconUrls.mapKeys { it.key.lowercase() }
    }

    private val idToSlug: Map<String, String> by lazy {
        fun isId(key: String) = key.contains('@') || key.startsWith("{")
        val slugByIcon = amoIconUrls.entries.filter { !isId(it.key) }.associate { it.value to it.key }
        amoIconUrls.entries
            .filter { isId(it.key) }
            .mapNotNull { e -> slugByIcon[e.value]?.let { e.key.lowercase() to it } }
            .toMap()
    }

    private val amoIconUrls: Map<String, String> = mapOf(
        "adblock-plus" to "https://addons.mozilla.org/user-media/addon_icons/1/1865-64.png?modified=mcrushed",
        "{d10d0bf8-f5b5-c8b4-a8b2-2b9879e08c5d}" to "https://addons.mozilla.org/user-media/addon_icons/1/1865-64.png?modified=mcrushed",
        "adguard-adblocker" to "https://addons.mozilla.org/user-media/addon_icons/520/520576-64.png?modified=acab9fe4",
        "adguardadblocker@adguard.com" to "https://addons.mozilla.org/user-media/addon_icons/520/520576-64.png?modified=acab9fe4",
        "auto-tab-discard" to "https://addons.mozilla.org/user-media/addon_icons/909/909373-64.png?modified=mcrushed",
        "{c2c003ee-bd69-42a2-b0e9-6f34222cb046}" to "https://addons.mozilla.org/user-media/addon_icons/909/909373-64.png?modified=mcrushed",
        "bitwarden-password-manager" to "https://addons.mozilla.org/user-media/addon_icons/735/735894-64.png?modified=27707d93",
        "{446900e4-71c2-419f-a6a7-df9c091e268b}" to "https://addons.mozilla.org/user-media/addon_icons/735/735894-64.png?modified=27707d93",
        "buster-captcha-solver" to "https://addons.mozilla.org/user-media/addon_icons/1013/1013715-64.png?modified=121a7563",
        "{e58d3966-3d76-4cd9-8552-1582fbc800c1}" to "https://addons.mozilla.org/user-media/addon_icons/1013/1013715-64.png?modified=121a7563",
        "canvasblocker" to "https://addons.mozilla.org/user-media/addon_icons/534/534930-64.png?modified=1581849561",
        "CanvasBlocker@kkapsner.de" to "https://addons.mozilla.org/user-media/addon_icons/534/534930-64.png?modified=1581849561",
        "chrome-mask" to "https://addons.mozilla.org/user-media/addon_icons/2853/2853597-64.png?modified=7e22a281",
        "chrome-mask@overengineer.dev" to "https://addons.mozilla.org/user-media/addon_icons/2853/2853597-64.png?modified=7e22a281",
        "clearurls" to "https://addons.mozilla.org/user-media/addon_icons/839/839767-64.png?modified=b06fa7ed",
        "{74145f27-f039-47ce-a470-a662b129930a}" to "https://addons.mozilla.org/user-media/addon_icons/839/839767-64.png?modified=b06fa7ed",
        "consent-o-matic" to "https://addons.mozilla.org/user-media/addon_icons/2613/2613823-64.png?modified=4616e5b4",
        "gdpr@cavi.au.dk" to "https://addons.mozilla.org/user-media/addon_icons/2613/2613823-64.png?modified=4616e5b4",
        "cookie-autodelete" to "https://addons.mozilla.org/user-media/addon_icons/784/784287-64.png?modified=mcrushed",
        "CookieAutoDelete@kennydo.com" to "https://addons.mozilla.org/user-media/addon_icons/784/784287-64.png?modified=mcrushed",
        "cookie-quick-manager" to "https://addons.mozilla.org/user-media/addon_icons/905/905349-64.png?modified=mcrushed",
        "{60f82f00-9ad5-4de5-b31c-b16a47c51558}" to "https://addons.mozilla.org/user-media/addon_icons/905/905349-64.png?modified=mcrushed",
        "cookie-editor" to "https://addons.mozilla.org/user-media/addon_icons/869/869585-64.png?modified=278a433f",
        "{c3c10168-4186-445c-9c5b-63f12b8e2c87}" to "https://addons.mozilla.org/user-media/addon_icons/869/869585-64.png?modified=278a433f",
        "darkreader" to "https://addons.mozilla.org/user-media/addon_icons/855/855413-64.png?modified=3f34e276",
        "addon@darkreader.org" to "https://addons.mozilla.org/user-media/addon_icons/855/855413-64.png?modified=3f34e276",
        "decentraleyes" to "https://addons.mozilla.org/user-media/addon_icons/521/521554-64.png?modified=mcrushed",
        "jid1-BoFifL9Vbdl2zQ@jetpack" to "https://addons.mozilla.org/user-media/addon_icons/521/521554-64.png?modified=mcrushed",
        "enhancer-for-youtube" to "https://addons.mozilla.org/user-media/addon_icons/700/700308-64.png?modified=4bc8e79f",
        "enhancerforyoutube@maximerf.addons.mozilla.org" to "https://addons.mozilla.org/user-media/addon_icons/700/700308-64.png?modified=4bc8e79f",
        "fastforwardteam" to "https://addons.mozilla.org/user-media/addon_icons/2721/2721049-64.png?modified=910c0d95",
        "addon@fastforward.team" to "https://addons.mozilla.org/user-media/addon_icons/2721/2721049-64.png?modified=910c0d95",
        "multi-account-containers" to "https://addons.mozilla.org/user-media/addon_icons/782/782160-64.png?modified=mcrushed",
        "@testpilot-containers" to "https://addons.mozilla.org/user-media/addon_icons/782/782160-64.png?modified=mcrushed",
        "firemonkey" to "https://addons.mozilla.org/user-media/addon_icons/1019/1019336-64.png?modified=f7e6f88a",
        "firemonkey@eros.man" to "https://addons.mozilla.org/user-media/addon_icons/1019/1019336-64.png?modified=f7e6f88a",
        "floccus" to "https://addons.mozilla.org/user-media/addon_icons/707/707450-64.png?modified=1784362624",
        "floccus@handmadeideas.org" to "https://addons.mozilla.org/user-media/addon_icons/707/707450-64.png?modified=1784362624",
        "foxyproxy-standard" to "https://addons.mozilla.org/user-media/addon_icons/2/2464-64.png?modified=1f409a60",
        "foxyproxy@eric.h.jung" to "https://addons.mozilla.org/user-media/addon_icons/2/2464-64.png?modified=1f409a60",
        "ghostery" to "https://addons.mozilla.org/user-media/addon_icons/9/9609-64.png?modified=3e27332a",
        "firefox@ghostery.com" to "https://addons.mozilla.org/user-media/addon_icons/9/9609-64.png?modified=3e27332a",
        "greasemonkey" to "https://addons.mozilla.org/user-media/addon_icons/0/748-64.png?modified=1531822767",
        "{e4a8a97b-f2ed-450b-b12d-ee082ba24781}" to "https://addons.mozilla.org/user-media/addon_icons/0/748-64.png?modified=1531822767",
        "istilldontcareaboutcookies" to "https://addons.mozilla.org/user-media/addon_icons/2766/2766861-64.png?modified=0ce8cd02",
        "idcac-pub@guus.ninja" to "https://addons.mozilla.org/user-media/addon_icons/2766/2766861-64.png?modified=0ce8cd02",
        "keepassxc-browser" to "https://addons.mozilla.org/user-media/addon_icons/917/917354-64.png?modified=1757958965",
        "keepassxc-browser@keepassxc.org" to "https://addons.mozilla.org/user-media/addon_icons/917/917354-64.png?modified=1757958965",
        "leechblock-ng" to "https://addons.mozilla.org/user-media/addon_icons/866/866226-64.png?modified=1777236061",
        "leechblockng@proginosko.com" to "https://addons.mozilla.org/user-media/addon_icons/866/866226-64.png?modified=1777236061",
        "localcdn-fork-of-decentraleyes" to "https://addons.mozilla.org/user-media/addon_icons/2627/2627159-64.png?modified=b2f5fc36",
        "{b86e4813-687a-43e6-ab65-0bde4ab75758}" to "https://addons.mozilla.org/user-media/addon_icons/2627/2627159-64.png?modified=b2f5fc36",
        "noscript" to "https://addons.mozilla.org/user-media/addon_icons/0/722-64.png?modified=238235f7",
        "{73a6fe31-595d-460b-a920-fcc0f8843232}" to "https://addons.mozilla.org/user-media/addon_icons/0/722-64.png?modified=238235f7",
        "popupoff" to "https://addons.mozilla.org/user-media/addon_icons/2646/2646176-64.png?modified=a435ab54",
        "{154cddeb-4c8b-4627-a478-c7e5b427ffdf}" to "https://addons.mozilla.org/user-media/addon_icons/2646/2646176-64.png?modified=a435ab54",
        "privacy-badger17" to "https://addons.mozilla.org/user-media/addon_icons/506/506646-64.png?modified=mcrushed",
        "jid1-MnnxcxisBPnSXQ@jetpack" to "https://addons.mozilla.org/user-media/addon_icons/506/506646-64.png?modified=mcrushed",
        "privacy-possum" to "https://addons.mozilla.org/user-media/addon_icons/953/953945-64.png?modified=8ab422b7",
        "woop-NoopscooPsnSXQ@jetpack" to "https://addons.mozilla.org/user-media/addon_icons/953/953945-64.png?modified=8ab422b7",
        "proton-pass" to "https://addons.mozilla.org/user-media/addon_icons/2785/2785662-64.png?modified=d3ccdd4c",
        "78272b6fa58f4a1abaac99321d503a20@proton.me" to "https://addons.mozilla.org/user-media/addon_icons/2785/2785662-64.png?modified=d3ccdd4c",
        "raindropio" to "https://addons.mozilla.org/user-media/addon_icons/473/473846-64.png?modified=167461f6",
        "jid0-adyhmvsP91nUO8pRv0Mn2VKeB84@jetpack" to "https://addons.mozilla.org/user-media/addon_icons/473/473846-64.png?modified=167461f6",
        "read-aloud" to "https://addons.mozilla.org/user-media/addon_icons/952/952959-64.png?modified=cccc5f2c",
        "{ddc62400-f22d-4dd3-8b4a-05837de53c2e}" to "https://addons.mozilla.org/user-media/addon_icons/952/952959-64.png?modified=cccc5f2c",
        "return-youtube-dislikes" to "https://addons.mozilla.org/user-media/addon_icons/2729/2729178-64.png?modified=aa0eaf60",
        "{762f9885-5a13-4abd-9c77-433dcd38b8fd}" to "https://addons.mozilla.org/user-media/addon_icons/2729/2729178-64.png?modified=aa0eaf60",
        "search_by_image" to "https://addons.mozilla.org/user-media/addon_icons/824/824288-64.png?modified=39d0cb7b",
        "{2e5ff8c8-32fe-46d0-9fc8-6b8986621f3c}" to "https://addons.mozilla.org/user-media/addon_icons/824/824288-64.png?modified=39d0cb7b",
        "single-file" to "https://addons.mozilla.org/user-media/addon_icons/985/985621-64.png?modified=76aa1d23",
        "{531906d3-e22f-4a6c-a102-8057b88a1a63}" to "https://addons.mozilla.org/user-media/addon_icons/985/985621-64.png?modified=76aa1d23",
        "sponsorblock" to "https://addons.mozilla.org/user-media/addon_icons/2590/2590937-64.png?modified=d215907f",
        "sponsorBlocker@ajay.app" to "https://addons.mozilla.org/user-media/addon_icons/2590/2590937-64.png?modified=d215907f",
        "styl-us" to "https://addons.mozilla.org/user-media/addon_icons/814/814814-64.png?modified=1768574075",
        "{7a7a4a92-a2a0-41d1-9fd7-1e92480d612d}" to "https://addons.mozilla.org/user-media/addon_icons/814/814814-64.png?modified=1768574075",
        "tabliss" to "https://addons.mozilla.org/user-media/addon_icons/850/850407-64.png?modified=1650892013",
        "extension@tabliss.io" to "https://addons.mozilla.org/user-media/addon_icons/850/850407-64.png?modified=1650892013",
        "tampermonkey" to "https://addons.mozilla.org/user-media/addon_icons/683/683490-64.png?modified=1625638973",
        "firefox@tampermonkey.net" to "https://addons.mozilla.org/user-media/addon_icons/683/683490-64.png?modified=1625638973",
        "temporary-containers" to "https://addons.mozilla.org/user-media/addon_icons/888/888648-64.png?modified=mcrushed",
        "{c607c8df-14a7-4f28-894f-29e8722976af}" to "https://addons.mozilla.org/user-media/addon_icons/888/888648-64.png?modified=mcrushed",
        "tomato-clock" to "https://addons.mozilla.org/user-media/addon_icons/627/627490-64.png?modified=mcrushed",
        "jid1-Kt2kYYgi32zPuw@jetpack" to "https://addons.mozilla.org/user-media/addon_icons/627/627490-64.png?modified=mcrushed",
        "traduzir-paginas-web" to "https://addons.mozilla.org/user-media/addon_icons/2623/2623538-64.png?modified=8be60eed",
        "{036a55b4-5e72-4d05-a06c-cba2dfcc134a}" to "https://addons.mozilla.org/user-media/addon_icons/2623/2623538-64.png?modified=8be60eed",
        "ublock-origin" to "https://addons.mozilla.org/user-media/addon_icons/607/607454-64.png?modified=mcrushed",
        "uBlock0@raymondhill.net" to "https://addons.mozilla.org/user-media/addon_icons/607/607454-64.png?modified=mcrushed",
        "umatrix" to "https://addons.mozilla.org/user-media/addon_icons/613/613250-64.png?modified=1586876248",
        "uMatrix@raymondhill.net" to "https://addons.mozilla.org/user-media/addon_icons/613/613250-64.png?modified=1586876248",
        "youtube-recommended-videos" to "https://addons.mozilla.org/user-media/addon_icons/2602/2602948-64.png?modified=07136808",
        "myallychou@gmail.com" to "https://addons.mozilla.org/user-media/addon_icons/2602/2602948-64.png?modified=07136808",
        "user-agent-string-switcher" to "https://addons.mozilla.org/user-media/addon_icons/853/853731-64.png?modified=a1617cd0",
        "{a6c4a591-f1b2-4f03-b3ff-767e5bedf4e7}" to "https://addons.mozilla.org/user-media/addon_icons/853/853731-64.png?modified=a1617cd0",
        "video-background-play-fix" to "https://addons.mozilla.org/user-media/addon_icons/811/811592-64.png?modified=c75499b8",
        "video-bg-play@timdream.org" to "https://addons.mozilla.org/user-media/addon_icons/811/811592-64.png?modified=c75499b8",
        "violentmonkey" to "https://addons.mozilla.org/user-media/addon_icons/797/797378-64.png?modified=1773795053",
        "{aecec67f-0d10-4fa7-b7c7-609a2db280cf}" to "https://addons.mozilla.org/user-media/addon_icons/797/797378-64.png?modified=1773795053",
        "view-page-archive" to "https://addons.mozilla.org/user-media/addon_icons/844/844320-64.png?modified=b8e4dcca",
        "{d07ccf11-c0cd-4938-a265-2a4d6ad01189}" to "https://addons.mozilla.org/user-media/addon_icons/844/844320-64.png?modified=b8e4dcca",
        "youtube-high-definition" to "https://addons.mozilla.org/user-media/addon_icons/328/328839-64.png?modified=mcrushed",
        "{7b1bf0b6-a1b9-42b0-b75d-252036438bdc}" to "https://addons.mozilla.org/user-media/addon_icons/328/328839-64.png?modified=mcrushed",
    )

    private val visuals: Map<String, ExtensionVisual> = mapOf(
        // ── Ad/tracker blocking ──────────────────────────────────────────
        "ublock-origin" to ExtensionVisual(Icons.Rounded.Shield, Color(0xFFFF3B5C)),
        "adguard-adblocker" to ExtensionVisual(Icons.Rounded.Shield, Color(0xFF67B346)),
        "adguardadblocker@adguard.com" to ExtensionVisual(Icons.Rounded.Shield, Color(0xFF67B346)),
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
