package com.petal.browser.browser

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import com.petal.browser.R
import com.petal.browser.unit.HelperUnit
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class WebAppInterface(private val mContext: Context) {

    @JavascriptInterface
    fun processBlob(base64Data: String?, ignoredMimeType: String?, fileName: String?) {
        if (base64Data == null || fileName == null) return
        try {
            var data = base64Data
            if (data.contains(",")) {
                data = data.split(",")[1]
            }
            val fileBytes = Base64.decode(data, Base64.DEFAULT)
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadDir, fileName)

            FileOutputStream(file).use { fos ->
                fos.write(fileBytes)
                fos.flush()
            }
            showSnackbar()
        } catch (e: Exception) {
            Toast.makeText(mContext, mContext.getString(R.string.app_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSnackbar() {
        if (mContext is Activity) {
            mContext.runOnUiThread {
                val rootView = mContext.findViewById<View>(android.R.id.content)
                if (rootView != null) {
                    val text = mContext.getString(R.string.app_done) + ". " + mContext.getString(R.string.menu_download) + "?"
                    val snackbar = Snackbar.make(rootView, text, Snackbar.LENGTH_SHORT)
                    HelperUnit.makeSnackbarRound(snackbar)
                    snackbar.setAction(mContext.getString(R.string.app_ok)) {
                        mContext.startActivity(Intent.createChooser(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS), null))
                    }
                    snackbar.show()
                }
            }
        }
    }
}
