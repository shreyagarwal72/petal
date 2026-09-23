package com.petal.browser.browser;

import android.view.View;

/**
 * A browser tab. The view returned by {@link #getAlbumView()} is mounted in the content
 * frame once, the first time the tab is shown, and then kept attached for the tab's
 * lifetime; the host switches tabs by toggling its visibility.
 *
 * Implementations must therefore NOT attach or detach the album view from {@link #activate()}
 * or {@link #deactivate()}. Those callbacks only suspend/resume the tab's engine work
 * (for Gecko tabs, {@code GeckoSession.setActive(false/true)}); the host hides the view
 * (View.GONE) after {@link #deactivate()} and shows it (View.VISIBLE) around {@link #activate()}.
 */
public interface AlbumController {
    View getAlbumView();
    void activate();
    void deactivate();
    String getTitle();
    String getUrl();
    default void destroy() {}
    default boolean isIncognito() { return false; }
}
