package com.petal.browser.lens

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.petal.browser.R
import com.petal.browser.activity.MainActivity
import com.petal.browser.scanner.PetalScanActionHandler
import com.petal.browser.scanner.ScannerHost
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.unit.PetalAndroidUtils
import com.petal.browser.view.PetalToast

class PetalScannerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        PetalAndroidUtils.initTheme(this)
        setContent {
            PetalExpressiveTheme {
                ScannerHost(
                    onDismiss = { finish() },
                    wrapPredictive = false,
                    onResultDispatched = { value ->
                        if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) {
                            val intent = Intent(this, MainActivity::class.java).apply {
                                action = Intent.ACTION_VIEW
                                data = Uri.parse(value)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                            startActivity(intent)
                        } else {
                            PetalScanActionHandler.copyToClipboard(this, value)
                            PetalToast.show(this, getString(R.string.quick_tile_scanner_copied), Toast.LENGTH_SHORT)
                        }
                        finish()
                    }
                )
            }
        }
    }
}
