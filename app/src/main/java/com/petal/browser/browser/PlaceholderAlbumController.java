package com.petal.browser.browser;

import android.content.Context;
import android.graphics.Bitmap;

/**
 * @deprecated Legacy Lightning Browser placeholder. Use {@link PlaceholderTabController} instead.
 */
@Deprecated
public final class PlaceholderAlbumController extends PlaceholderTabController implements AlbumController {

    public PlaceholderAlbumController(Context context, String title, String url,
                                      Bitmap favicon, String tabId,
                                      String tabGroupId, String tabGroupTitle,
                                      boolean incognito) {
        super(context, title, url, favicon, tabId, tabGroupId, tabGroupTitle, incognito);
    }
}
