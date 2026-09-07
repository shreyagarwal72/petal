package com.petal.browser.browser;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.TextView;

/** Lightweight saved-tab entry. It deliberately owns no GeckoSession/WebView. */
public final class PlaceholderAlbumController implements AlbumController {
    private final String title;
    private final String url;
    private final String tabId;
    private final String tabGroupId;
    private final String tabGroupTitle;
    private final boolean incognito;
    private final Bitmap favicon;
    private final View albumView;

    public PlaceholderAlbumController(Context context, String title, String url, String tabId,
                                      String tabGroupId, String tabGroupTitle, boolean incognito,
                                      Bitmap favicon) {
        this.title = title == null ? "" : title;
        this.url = (url == null || url.trim().isEmpty()) ? "about:blank" : url;
        this.tabId = tabId;
        this.tabGroupId = tabGroupId;
        this.tabGroupTitle = tabGroupTitle;
        this.incognito = incognito;
        this.favicon = favicon;
        TextView view = new TextView(context);
        view.setText(this.title.isEmpty() ? this.url : this.title);
        view.setVisibility(View.GONE);
        this.albumView = view;
    }

    @Override public View getAlbumView() { return albumView; }
    @Override public void activate() { }
    @Override public void deactivate() { }
    @Override public String getTitle() { return title; }
    @Override public String getUrl() { return url; }

    public String getTabId() { return tabId; }
    public String getTabGroupId() { return tabGroupId; }
    public String getTabGroupTitle() { return tabGroupTitle; }
    public boolean isIncognito() { return incognito; }
    public Bitmap getFavicon() { return favicon; }
}
