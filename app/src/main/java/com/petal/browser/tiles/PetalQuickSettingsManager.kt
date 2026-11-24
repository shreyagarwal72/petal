package com.petal.browser.tiles

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.TileService
import android.widget.Toast
import com.petal.browser.R
import com.petal.browser.lens.PetalScannerTileService

data class PetalTileInfo(
    val id: String,
    val titleRes: Int,
    val subtitleRes: Int,
    val iconRes: Int,
    val serviceClass: Class<out TileService>,
    val isToggle: Boolean = false
)

object PetalQuickSettingsManager {

    val TILES: List<PetalTileInfo> = listOf(
        PetalTileInfo("new_tab", R.string.quick_tile_new_tab_label, R.string.quick_tile_new_tab_subtitle, R.drawable.ic_tile_new_tab, PetalNewTabTileService::class.java),
        PetalTileInfo("incognito", R.string.quick_tile_incognito_label, R.string.quick_tile_incognito_subtitle, R.drawable.ic_tile_incognito, PetalIncognitoTileService::class.java),
        PetalTileInfo("ai_search", R.string.quick_tile_ai_search_label, R.string.quick_tile_ai_search_subtitle, R.drawable.ic_tile_ai, PetalAiSearchTileService::class.java),
        PetalTileInfo("scanner", R.string.quick_tile_scanner_label, R.string.quick_tile_scanner_subtitle, R.drawable.ic_tile_scanner, PetalScannerTileService::class.java),
        PetalTileInfo("lens", R.string.quick_tile_lens_label, R.string.quick_tile_lens_subtitle, R.drawable.ic_tile_lens, PetalLensTileService::class.java),
        PetalTileInfo("snap_camera", R.string.quick_tile_snap_camera_label, R.string.quick_tile_snap_camera_subtitle, R.drawable.ic_tile_camera, PetalSnapCameraTileService::class.java),
        PetalTileInfo("voice", R.string.quick_tile_voice_search_label, R.string.quick_tile_voice_search_subtitle, R.drawable.ic_tile_voice, PetalVoiceSearchTileService::class.java),
        PetalTileInfo("search", R.string.quick_tile_search_label, R.string.quick_tile_search_subtitle, R.drawable.ic_tile_search, PetalSearchTileService::class.java),
        PetalTileInfo("downloads", R.string.quick_tile_downloads_label, R.string.quick_tile_downloads_subtitle, R.drawable.ic_tile_downloads, PetalDownloadsTileService::class.java),
        PetalTileInfo("bookmarks", R.string.quick_tile_bookmarks_label, R.string.quick_tile_bookmarks_subtitle, R.drawable.ic_tile_bookmarks, PetalBookmarksTileService::class.java),
        PetalTileInfo("adblock", R.string.quick_tile_adblock_label, R.string.quick_tile_adblock_standard, R.drawable.ic_tile_adblock, PetalAdBlockTileService::class.java, isToggle = true),
        PetalTileInfo("desktop_site", R.string.quick_tile_desktop_site_label, R.string.quick_tile_desktop_site_subtitle, R.drawable.ic_tile_desktop, PetalDesktopTileService::class.java),
        PetalTileInfo("javascript", R.string.quick_tile_javascript_label, R.string.quick_tile_javascript_on, R.drawable.ic_tile_javascript, PetalJavaScriptTileService::class.java, isToggle = true),
        PetalTileInfo("force_dark", R.string.quick_tile_force_dark_label, R.string.quick_tile_force_dark_on, R.drawable.ic_tile_dark_mode, PetalForceDarkTileService::class.java, isToggle = true),
        PetalTileInfo("https_only", R.string.quick_tile_https_only_label, R.string.quick_tile_https_only_on, R.drawable.ic_tile_https, PetalHttpsOnlyTileService::class.java, isToggle = true),
        PetalTileInfo("close_incognito", R.string.quick_tile_close_incognito_label, R.string.quick_tile_close_incognito_none, R.drawable.ic_tile_close_incognito, PetalCloseIncognitoTileService::class.java)
    )

    fun requestAddTile(
        context: Context,
        tile: PetalTileInfo,
        onShowLegacyInstructions: () -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val statusBarManager = context.getSystemService(StatusBarManager::class.java)
            if (statusBarManager == null) {
                onShowLegacyInstructions()
                return
            }
            val componentName = ComponentName(context, tile.serviceClass)
            val label = context.getString(tile.titleRes)
            val icon = Icon.createWithResource(context, tile.iconRes)
            statusBarManager.requestAddTileService(
                componentName,
                label,
                icon,
                context.mainExecutor
            ) { statusCode ->
                val text = when (statusCode) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "Added $label to Quick Settings"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "$label is already in Quick Settings"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> "Tile not added"
                    else -> context.getString(R.string.quick_settings_tile_added_success)
                }
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            }
        } else {
            onShowLegacyInstructions()
        }
    }
}
