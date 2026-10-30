package com.petal.browser.browser;

import android.view.View;

/**
 * Core contract for a Petal browser tab.
 * The view returned by {@link #getTabView()} is mounted in the content
 * frame once, the first time the tab is shown, and then kept attached for the tab's
 * lifetime; the host switches tabs by toggling its visibility.
 *
 * Modernized from legacy Lightning Browser AlbumController.
 */
public interface PetalTabController {
    View getAlbumView();
    default View getTabView() { return getAlbumView(); }
    void activate();
    void deactivate();
    String getTitle();
    String getUrl();
    default void destroy() {}
    default boolean isIncognito() { return false; }
}
