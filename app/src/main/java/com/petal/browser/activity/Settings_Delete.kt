package com.petal.browser.activity

import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.EdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.petal.browser.R
import com.petal.browser.compose.settings.PetalDeleteBridge
import com.petal.browser.unit.BrowserUnit
import com.petal.browser.unit.HelperUnit

class Settings_Delete : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HelperUnit.initTheme(this)
        EdgeToEdge.enable(this)
        setContentView(PetalDeleteBridge.createDeleteView(this) { finish() })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_help, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == android.R.id.home) finish()
        if (menuItem.itemId == R.id.menu_help) {
            val webpage = Uri.parse("https://github.com/shreyagarwal72/petal")
            BrowserUnit.intentURL(this, webpage)
        }
        return true
    }
}
