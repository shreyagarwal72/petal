package com.petal.browser.browser;

import android.app.Dialog;
import android.net.Uri;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

import com.petal.browser.database.Record;
import com.petal.browser.view.AdapterRecord;

public interface BrowserController {
    void updateProgress(int progress);
    void showAlbum(AlbumController albumController);
    void removeAlbum(AlbumController albumController);
    void showFileChooser(ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams);
    void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback);
    void showOverview();
    void hideOverview();
    void hideOverflow();
    void hideSearch();
    void onHideCustomView();
    void showDialogFastToggle(String title, String url, FloatingActionButton floatingActionButton);
    void setProfileIcon (FloatingActionButton floatingActionButton, String url);
    void showOverflow(Dialog dialog, View view, int hideMenu, String title, String url, final AdapterRecord adapterRecord, List<Record> recordList, int location);
}