package com.petal.browser.browser;

import android.view.View;

public interface AlbumController {
    View getAlbumView();
    void activate();
    void deactivate();
    String getTitle();
    String getUrl();
}
