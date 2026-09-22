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
import androidx.compose.material.icons.rounded.NoAdult
import androidx.compose.material.icons.rounded.OndemandVideo
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
        "consent-o-matic" to ExtensionVisual(Icons.Rounded.NoAdult, Color(0xFF0EA5E9)),

        // ── Appearance / UI ──────────────────────────────────────────────
        "darkreader" to ExtensionVisual(Icons.Rounded.DarkMode, Color(0xFF818CF8)),
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
