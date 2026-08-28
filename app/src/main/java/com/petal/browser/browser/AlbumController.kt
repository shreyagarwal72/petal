package com.petal.browser.browser

import android.view.View

interface AlbumController {
    fun getAlbumView(): View?
    fun activate()
    fun deactivate()
    fun getTitle(): String?
    fun getUrl(): String?
}
