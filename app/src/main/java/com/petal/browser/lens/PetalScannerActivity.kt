package com.petal.browser.lens

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.petal.browser.unit.BrowserUnit

class PetalScannerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PetalQrScannerScreen(
                    onResult = { value ->
                        if (value.startsWith("http://") || value.startsWith("https://")) {
                            BrowserUnit.intentURL(this, Uri.parse(value))
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
