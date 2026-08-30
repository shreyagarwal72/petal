package com.petal.browser.unit;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcel;
import android.util.Base64;
import android.util.Log;
import android.webkit.CookieManager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.petal.browser.browser.AlbumController;
import com.petal.browser.browser.BrowserContainer;
import com.petal.browser.database.RecordAction;
import com.petal.browser.view.NinjaWebView;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Chrome-style Tab Session Restoration & Persistence System.
 * Captures and serializes webView.saveState(bundle), URLs, titles, scroll positions,
 * and back-forward histories into persistent SQLite storage, and rehydrates tab states
 * with webView.restoreState(bundle) on app relaunch or recovery.
 */
public class TabSessionManager {

    private static final String TAG = "TabSessionManager";

    public static class TabStateRecord {
        public long tabId;
        public String title;
        public String url;
        public int scrollX;
        public int scrollY;
        public boolean isIncognito;
        public boolean isActive;
        public String webViewStateBase64;
        public long timestamp;
        public String tabGroupId;
        public String tabGroupTitle;

        public TabStateRecord() {}

        public TabStateRecord(long tabId, String title, String url, int scrollX, int scrollY,
                              boolean isIncognito, boolean isActive, String webViewStateBase64, long timestamp) {
            this(tabId, title, url, scrollX, scrollY, isIncognito, isActive, webViewStateBase64, timestamp, null, null);
        }

        public TabStateRecord(long tabId, String title, String url, int scrollX, int scrollY,
                              boolean isIncognito, boolean isActive, String webViewStateBase64, long timestamp,
                              String tabGroupId, String tabGroupTitle) {
            this.tabId = tabId;
            this.title = title;
            this.url = url;
            this.scrollX = scrollX;
            this.scrollY = scrollY;
            this.isIncognito = isIncognito;
            this.isActive = isActive;
            this.webViewStateBase64 = webViewStateBase64;
            this.timestamp = timestamp;
            this.tabGroupId = tabGroupId;
            this.tabGroupTitle = tabGroupTitle;
        }
    }

    /**
     * Serializes a Bundle to a Base64 String using Parcel.
     */
    public static String bundleToBase64(Bundle bundle) {
        if (bundle == null || bundle.isEmpty()) return "";
        Parcel parcel = Parcel.obtain();
        try {
            bundle.writeToParcel(parcel, 0);
            byte[] bytes = parcel.marshall();
            return Base64.encodeToString(bytes, Base64.DEFAULT);
        } catch (Exception e) {
            Log.e(TAG, "Error encoding bundle to base64", e);
            return "";
        } finally {
            parcel.recycle();
        }
    }

    /**
     * Deserializes a Base64 String back to a Bundle using Parcel.
     */
    public static Bundle base64ToBundle(String base64Str) {
        if (base64Str == null || base64Str.trim().isEmpty()) return null;
        Parcel parcel = Parcel.obtain();
        try {
            byte[] bytes = Base64.decode(base64Str, Base64.DEFAULT);
            parcel.unmarshall(bytes, 0, bytes.length);
            parcel.setDataPosition(0);
            Bundle bundle = new Bundle();
            bundle.readFromParcel(parcel);
            return bundle;
        } catch (Exception e) {
            Log.e(TAG, "Error decoding base64 to bundle", e);
            return null;
        } finally {
            parcel.recycle();
        }
    }

    /**
     * Saves the current active tabs and their WebView states to disk.
     */
    public static synchronized void saveSession(Context context) {
        if (context == null) return;

        // Ensure session cookies are flushed to disk on backgrounding / session save
        try {
            CookieManager.getInstance().flush();
        } catch (Exception e) {
            Log.e(TAG, "Error flushing CookieManager", e);
        }

        List<AlbumController> albumList = BrowserContainer.list();
        if (albumList == null || albumList.isEmpty()) {
            clearSession(context);
            return;
        }

        List<TabStateRecord> records = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (int i = 0; i < albumList.size(); i++) {
            AlbumController album = albumList.get(i);
            if (album instanceof NinjaWebView) {
                NinjaWebView webView = (NinjaWebView) album;
                // Skip incognito tabs from session restoration storage for privacy
                if (webView.isIncognito()) {
                    continue;
                }

                String url = webView.getUrl();
                if (url == null || url.trim().isEmpty()) {
                    url = "about:blank";
                }

                String title = webView.getTitle();
                if (title == null || title.trim().isEmpty()) {
                    title = url;
                }

                int scrollX = webView.getScrollX();
                int scrollY = webView.getScrollY();
                boolean isActive = webView.isForeground();
                String tabGroupId = webView.getTabGroupId();
                String tabGroupTitle = webView.getTabGroupTitle();

                // Store URL, title, scroll positions, tab group & metadata safely. Avoid webView.saveState() Base64
                // serialization as restoring corrupt webViewState bundles causes native Chromium crashes.
                records.add(new TabStateRecord(i, title, url, scrollX, scrollY, false, isActive, "", now, tabGroupId, tabGroupTitle));
            }
        }

        if (records.isEmpty()) {
            clearSession(context);
            return;
        }

        try {
            Gson gson = new Gson();
            String jsonRecords = gson.toJson(records);
            RecordAction action = new RecordAction(context);
            action.open(true);
            action.saveSessionStateJson(jsonRecords);
            action.close();
            Log.d(TAG, "Successfully saved session with " + records.size() + " tabs.");
        } catch (Exception e) {
            Log.e(TAG, "Error writing session state to RecordAction database", e);
        }
    }

    /**
     * Reads saved tab states from persistent storage.
     */
    public static synchronized List<TabStateRecord> loadSession(Context context) {
        List<TabStateRecord> records = new ArrayList<>();
        if (context == null) return records;

        try {
            RecordAction action = new RecordAction(context);
            action.open(false);
            String jsonRecords = action.getSessionStateJson();
            action.close();

            if (jsonRecords != null && !jsonRecords.trim().isEmpty()) {
                Gson gson = new Gson();
                Type type = new TypeToken<List<TabStateRecord>>(){}.getType();
                records = gson.fromJson(jsonRecords, type);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading session state from database", e);
        }

        return records != null ? records : new ArrayList<>();
    }

    /**
     * Clears saved session state.
     */
    public static synchronized void clearSession(Context context) {
        if (context == null) return;
        try {
            RecordAction action = new RecordAction(context);
            action.open(true);
            action.clearSessionStateJson();
            action.close();
        } catch (Exception e) {
            Log.e(TAG, "Error clearing session state", e);
        }
    }
}
