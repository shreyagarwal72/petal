package com.petal.browser.lens

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.petal.browser.unit.PetalBrowserUtils

class PetalScannerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.petal.browser.unit.PetalAndroidUtils.initTheme(this)
        setContent {
            MaterialTheme {
                PetalQrScannerScreen(
                    onResult = { value ->
                        if (value.startsWith("http://") || value.startsWith("https://")) {
                            PetalBrowserUtils.intentURL(this, Uri.parse(value))
                        } else {
                            getSystemService(CLIPBOARD_SERVICE)?.let { service ->
                                (service as android.content.ClipboardManager).setPrimaryClip(
                                    android.content.ClipData.newPlainText("Scanned barcode", value)
                                )
                            }
                        }
                        finish()
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }
}
