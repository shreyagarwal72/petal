package com.petal.browser.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import com.petal.browser.unit.TabThumbnailCache

/**
 * Lightweight controller used for saved background tabs. It intentionally owns
 * no GeckoSession/WebView and is replaced by a real controller when activated.
 * Modernized from legacy Lightning Browser PlaceholderAlbumController.
 */
open class PlaceholderTabController(
    private val context: Context,
    private var _title: String? = "",
    private var _url: String? = "about:blank",
    private var _favicon: Bitmap? = null,
    private var _tabId: String? = null,
    private var _tabGroupId: String? = null,
    private var _tabGroupTitle: String? = null,
    private val incognito: Boolean = false
) : PetalTabSurface, PetalTabController, AlbumController {

    private val placeholderView: View = FrameLayout(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
    }

    override fun getTabView(): View = placeholderView
    override fun getAlbumView(): View = placeholderView
    override fun activate() {}
    override fun deactivate() {}

    override fun getTitle(): String = _title.orEmpty()
    override fun getUrl(): String = if (_url.isNullOrEmpty()) "about:blank" else _url!!

    override fun getFavicon(): Bitmap? = _favicon
    override fun getTabId(): String = _tabId.orEmpty()
    fun getTabGroupId(): String? = _tabGroupId
    fun getTabGroupTitle(): String? = _tabGroupTitle
    override fun isIncognito(): Boolean = incognito

    fun setTitle(title: String?) { _title = title.orEmpty() }
    fun setUrl(url: String?) { _url = if (url.isNullOrEmpty()) "about:blank" else url }
    fun setFavicon(favicon: Bitmap?) { _favicon = favicon }
    fun setTabId(tabId: String?) { _tabId = tabId }
    fun setTabGroupId(groupId: String?) { _tabGroupId = groupId }
    fun setTabGroupTitle(groupTitle: String?) { _tabGroupTitle = groupTitle }

    override fun getCachedPreviewBitmap(): Bitmap? {
        val id = _tabId
        if (id.isNullOrEmpty()) return null
        return TabThumbnailCache.getMemoryOnly(id, incognito)
    }
}
