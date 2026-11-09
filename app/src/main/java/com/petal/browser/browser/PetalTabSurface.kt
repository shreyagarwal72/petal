package com.petal.browser.browser

import android.graphics.Bitmap
import android.view.View

/**
 * PetalTabSurface
 * ─────────────────────────────────────────────────────────────────────────────
 * Authoritative single tab-surface interface backed by BrowserStore and PetalEngineBridge.
 * Unifies active rendered tab views (PetalTabViewController) and lightweight background
 * tab placeholders (PlaceholderTabController).
 */
interface PetalTabSurface {
    val tabView: View
    val albumView: View get() = tabView
    fun getTabView(): View = tabView
    fun getAlbumView(): View = albumView

    val title: String
    val url: String
    fun getTitle(): String = title
    fun getUrl(): String = url

    val tabId: String get() = ""
    fun getTabId(): String = tabId

    val favicon: Bitmap? get() = null
    fun getFavicon(): Bitmap? = favicon

    val isIncognito: Boolean get() = false
    fun isIncognito(): Boolean = isIncognito

    val cachedPreviewBitmap: Bitmap? get() = null
    fun getCachedPreviewBitmap(): Bitmap? = cachedPreviewBitmap

    fun activate()
    fun deactivate()
    fun destroy() {}
}
