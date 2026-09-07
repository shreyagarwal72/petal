package com.petal.browser.browser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Lightweight controller used for saved background tabs. It intentionally owns
 * no GeckoSession/WebView and is replaced by a real controller when activated.
 */
public final class PlaceholderAlbumController implements AlbumController {
    private final Context context;
    private final View placeholderView;
    private String title;
    private String url;
    private Bitmap favicon;
    private String tabId;
    private String tabGroupId;
    private String tabGroupTitle;
    private final boolean incognito;

    public PlaceholderAlbumController(Context context, String title, String url,
                                      Bitmap favicon, String tabId,
                                      String tabGroupId, String tabGroupTitle,
                                      boolean incognito) {
        this.context = context;
        this.title = title == null ? "" : title;
        this.url = url == null || url.isEmpty() ? "about:blank" : url;
        this.favicon = favicon;
        this.tabId = tabId;
        this.tabGroupId = tabGroupId;
        this.tabGroupTitle = tabGroupTitle;
        this.incognito = incognito;
        FrameLayout view = new FrameLayout(context);
        view.setBackgroundColor(Color.TRANSPARENT);
        this.placeholderView = view;
    }

    @Override public View getAlbumView() { return placeholderView; }
    @Override public void activate() { }
    @Override public void deactivate() { }
    @Override public String getTitle() { return title; }
    @Override public String getUrl() { return url; }

    public Bitmap getFavicon() { return favicon; }
    public String getTabId() { return tabId; }
    public String getTabGroupId() { return tabGroupId; }
    public String getTabGroupTitle() { return tabGroupTitle; }
    public boolean isIncognito() { return incognito; }

    public void setTitle(String title) { this.title = title == null ? "" : title; }
    public void setUrl(String url) { this.url = url == null || url.isEmpty() ? "about:blank" : url; }
    public void setFavicon(Bitmap favicon) { this.favicon = favicon; }
    public void setTabId(String tabId) { this.tabId = tabId; }
    public void setTabGroupId(String tabGroupId) { this.tabGroupId = tabGroupId; }
    public void setTabGroupTitle(String tabGroupTitle) { this.tabGroupTitle = tabGroupTitle; }
}
