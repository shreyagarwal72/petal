package com.petal.browser.browser

import android.app.Dialog
import android.net.Uri
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.petal.browser.database.Record
import com.petal.browser.view.AdapterRecord

interface BrowserController {
    fun updateProgress(progress: Int)
    fun showAlbum(albumController: AlbumController)
    fun showAlbum(albumController: AlbumController, animate: Boolean, isNewTab: Boolean, showHome: Boolean) {}
    fun removeAlbum(albumController: AlbumController)
    fun showFileChooser(filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: WebChromeClient.FileChooserParams?)
    fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?)
    fun showOverview()
    fun hideOverview()
    fun hideOverflow()
    fun hideSearch()
    fun onHideCustomView()
    fun showDialogFastToggle(title: String?, url: String?, floatingActionButton: FloatingActionButton?)
    fun setProfileIcon(floatingActionButton: FloatingActionButton?, url: String?)
    fun showOverflow(
        dialog: Dialog?,
        view: View?,
        hideMenu: Int,
        title: String?,
        url: String?,
        adapterRecord: AdapterRecord?,
        recordList: List<Record>?,
        location: Int
    )
}
