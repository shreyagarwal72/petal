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

    val title: String
    val url: String

    val tabId: String get() = ""

    val favicon: Bitmap? get() = null

    val isIncognito: Boolean get() = false

    val cachedPreviewBitmap: Bitmap? get() = null

    fun activate()
    fun deactivate()
    fun destroy() {}
}

// Extension bridge for legacy getter syntax across Kotlin callers without JVM signature clash
fun PetalTabSurface.getTabView(): View = tabView
fun PetalTabSurface.getAlbumView(): View = albumView
fun PetalTabSurface.getTitle(): String = title
fun PetalTabSurface.getUrl(): String = url
fun PetalTabSurface.getTabId(): String = tabId
fun PetalTabSurface.getFavicon(): Bitmap? = favicon
fun PetalTabSurface.isIncognito(): Boolean = isIncognito
fun PetalTabSurface.getCachedPreviewBitmap(): Bitmap? = cachedPreviewBitmap
