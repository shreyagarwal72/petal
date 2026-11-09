package com.petal.browser.browser

import android.content.Context
import android.graphics.Bitmap

/**
 * @deprecated Legacy Lightning Browser placeholder. Use [PlaceholderTabController] instead.
 */
@Deprecated("Legacy Lightning Browser placeholder. Use PlaceholderTabController instead.")
class PlaceholderAlbumController(
    context: Context,
    title: String?,
    url: String?,
    favicon: Bitmap?,
    tabId: String?,
    tabGroupId: String?,
    tabGroupTitle: String?,
    incognito: Boolean
) : PlaceholderTabController(context, title, url, favicon, tabId, tabGroupId, tabGroupTitle, incognito), AlbumController
