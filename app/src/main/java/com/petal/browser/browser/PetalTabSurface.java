package com.petal.browser.browser;

import android.graphics.Bitmap;
import android.view.View;

/**
 * PetalTabSurface
 * ─────────────────────────────────────────────────────────────────────────────
 * Authoritative single tab-surface interface backed by BrowserStore and PetalEngineBridge.
 * Unifies active rendered tab views (PetalTabViewController) and lightweight background
 * tab placeholders (PlaceholderTabController).
 */
public interface PetalTabSurface {
    View getTabView();
    default View getAlbumView() { return getTabView(); }
    void activate();
    void deactivate();
    String getTitle();
    String getUrl();
    default String getTabId() { return ""; }
    default Bitmap getFavicon() { return null; }
    default boolean isIncognito() { return false; }
    default void destroy() {}
    default Bitmap getCachedPreviewBitmap() { return null; }
}
