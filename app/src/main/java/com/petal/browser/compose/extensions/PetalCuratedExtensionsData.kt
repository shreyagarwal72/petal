package com.petal.browser.compose.extensions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
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
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Curated icon + accent color mapping for Petal's recommended extensions catalog.
 *
 * This is a pure data file — no logic, no state, no Android dependencies beyond
 * Compose icons. The [PetalExtensionsScreen] uses this to render per-extension
 * colored icons in the AddExtensionSheet, replacing the old generic puzzle-piece icon.
 */
object PetalCuratedExtensionsData {

    data class ExtensionVisual(
        val icon: ImageVector,
        val accentColor: Color
    )

    /** Returns [ExtensionVisual] for a known extension AMO slug, or null for unknown slugs. */
    fun getVisual(amoSlug: String): ExtensionVisual? = visuals[amoSlug]

    private val visuals: Map<String, ExtensionVisual> = mapOf(
        "ublock-origin" to ExtensionVisual(Icons.Rounded.Shield, Color(0xFFFF3B5C)),
        "darkreader" to ExtensionVisual(Icons.Rounded.DarkMode, Color(0xFF818CF8)),
        "privacy-badger17" to ExtensionVisual(Icons.Rounded.Security, Color(0xFF10B981)),
        "sponsorblock" to ExtensionVisual(Icons.Rounded.PlayCircle, Color(0xFFF59E0B)),
        "traduzir-paginas-web" to ExtensionVisual(Icons.Rounded.Translate, Color(0xFF3B82F6)),
        "clearurls" to ExtensionVisual(Icons.Rounded.LinkOff, Color(0xFFEC4899)),
        "violentmonkey" to ExtensionVisual(Icons.Rounded.Code, Color(0xFF14B8A6)),
        "decentraleyes" to ExtensionVisual(Icons.Rounded.Storage, Color(0xFF6366F1)),
        "bitwarden-password-manager" to ExtensionVisual(Icons.Rounded.Lock, Color(0xFF175DDC)),
        "proton-pass" to ExtensionVisual(Icons.Rounded.Key, Color(0xFF6D4AFF)),
        "keepassxc-browser" to ExtensionVisual(Icons.Rounded.VpnKey, Color(0xFF53A048)),
        "istilldontcareaboutcookies" to ExtensionVisual(Icons.Rounded.Block, Color(0xFF64748B)),
        "cookie-editor" to ExtensionVisual(Icons.Rounded.Edit, Color(0xFFEAB308)),
        "adguard-adblocker" to ExtensionVisual(Icons.Rounded.Shield, Color(0xFF67B346)),
        "video-background-play-fix" to ExtensionVisual(Icons.Rounded.OndemandVideo, Color(0xFF8B5CF6)),
        "youtube-high-definition" to ExtensionVisual(Icons.Rounded.HighQuality, Color(0xFFEF4444)),
        "view-page-archive" to ExtensionVisual(Icons.Rounded.History, Color(0xFF0EA5E9)),
        "google-search-fixer" to ExtensionVisual(Icons.Rounded.ManageSearch, Color(0xFF4285F4)),
        "tomato-clock" to ExtensionVisual(Icons.Rounded.Timer, Color(0xFFFF6B6B))
    )
}
