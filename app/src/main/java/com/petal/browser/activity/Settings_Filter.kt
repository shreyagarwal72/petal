package com.petal.browser.activity

import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.EdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.petal.browser.R
import com.petal.browser.fragment.Fragment_settings_Filter
import com.petal.browser.unit.BrowserUnit
import com.petal.browser.unit.HelperUnit

class Settings_Filter : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HelperUnit.initTheme(this)
        EdgeToEdge.enable(this)
        setContentView(R.layout.activity_settings)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.content_frame, Fragment_settings_Filter())
            .commit()
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
