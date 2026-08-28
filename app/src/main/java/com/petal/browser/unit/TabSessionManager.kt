package com.petal.browser.unit

import android.content.Context
import android.os.Bundle
import android.os.Parcel
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.petal.browser.browser.AlbumController
import com.petal.browser.browser.BrowserContainer
import com.petal.browser.database.RecordAction
import com.petal.browser.view.NinjaWebView

/**
 * Chrome-style Tab Session Restoration & Persistence System.
 * Captures and serializes webView.saveState(bundle), URLs, titles, scroll positions,
 * and back-forward histories into persistent SQLite storage, and rehydrates tab states
 * with webView.restoreState(bundle) on app relaunch or recovery.
 */
object TabSessionManager {

    private const val TAG = "TabSessionManager"

    class TabStateRecord(
        var tabId: Long = 0,
        var title: String? = null,
        var url: String? = null,
        var scrollX: Int = 0,
        var scrollY: Int = 0,
        var isIncognito: Boolean = false,
        var isActive: Boolean = false,
        var webViewStateBase64: String? = null,
        var timestamp: Long = 0
    )

    @JvmStatic
    fun bundleToBase64(bundle: Bundle?): String? {
        if (bundle == null) return null
        var parcel: Parcel? = null
        return try {
            parcel = Parcel.obtain()
            bundle.writeToParcel(parcel, 0)
            val bytes = parcel.marshall()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Error serializing bundle: ${e.message}")
            null
        } finally {
            parcel?.recycle()
        }
    }

    @JvmStatic
    fun base64ToBundle(base64Str: String?): Bundle? {
        if (base64Str.isNullOrEmpty()) return null
        var parcel: Parcel? = null
        return try {
            val bytes = Base64.decode(base64Str, Base64.NO_WRAP)
            parcel = Parcel.obtain()
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            Bundle.CREATOR.createFromParcel(parcel)
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializing bundle: ${e.message}")
            null
        } finally {
            parcel?.recycle()
        }
    }

    @JvmStatic
    fun saveSession(context: Context) {
        try {
            val tabList: MutableList<TabStateRecord> = ArrayList()
            val albumList = BrowserContainer.list()
            for (i in albumList.indices) {
                val controller = albumList[i]
                if (controller is NinjaWebView) {
                    if (controller.isIncognito) continue

                    val stateBundle = Bundle()
                    controller.saveState(stateBundle)
                    val base64State = bundleToBase64(stateBundle)

                    val record = TabStateRecord(
                        tabId = i.toLong(),
                        title = controller.title,
                        url = controller.url,
                        scrollX = controller.scrollX,
                        scrollY = controller.scrollY,
                        isIncognito = false,
                        isActive = controller.isForeground,
                        webViewStateBase64 = base64State,
                        timestamp = System.currentTimeMillis()
                    )
                    tabList.add(record)
                }
            }

            val json = Gson().toJson(tabList)
            val action = RecordAction(context)
            action.open(true)
            action.clearTable(RecordUnit.TABLE_SESSION)
            action.addSession(json)
            action.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving tab session: ${e.message}")
        }
    }

    @JvmStatic
    fun restoreSession(context: Context): List<TabStateRecord>? {
        return try {
            val action = RecordAction(context)
            action.open(false)
            val json = action.getSession()
            action.close()

            if (!json.isNullOrEmpty()) {
                val type = object : TypeToken<List<TabStateRecord>>() {}.type
                Gson().fromJson<List<TabStateRecord>>(json, type)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed restoring tab session: ${e.message}")
            null
        }
    }
}
