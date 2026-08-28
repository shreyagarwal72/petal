package com.petal.browser.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class ActivityShare : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        val browserIntent = Intent(this, BrowserActivity::class.java).apply {
            putExtra(Intent.EXTRA_TEXT, sharedText)
            action = "postLink"
        }
        startActivity(browserIntent)
        finish()
    }
}
