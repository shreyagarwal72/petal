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
    fun getTabView(): View
    fun getAlbumView(): View = getTabView()
    fun activate()
    fun deactivate()
    fun getTitle(): String
    fun getUrl(): String
    fun getTabId(): String = ""
    fun getFavicon(): Bitmap? = null
    fun isIncognito(): Boolean = false
    fun destroy() {}
    fun getCachedPreviewBitmap(): Bitmap? = null
}
