package com.petal.browser.lens

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.petal.browser.scanner.PetalScanActionHandler
import com.petal.browser.scanner.ScannerHost
import com.petal.browser.unit.PetalAndroidUtils
import com.petal.browser.unit.PetalBrowserUtils

class PetalScannerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PetalAndroidUtils.initTheme(this)
        setContent {
            com.petal.browser.ui.theme.PetalExpressiveTheme {
                ScannerHost(
                    onDismiss = { finish() },
                    onResultDispatched = { value ->
                        if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) {
                            PetalBrowserUtils.intentURL(this, Uri.parse(value))
                        } else {
                            PetalScanActionHandler.copyToClipboard(this, value)
                        }
                        finish()
                    }
                )
            }
        }
    }
}
