package com.petal.browser.activity

import android.os.Bundle
import androidx.activity.EdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.petal.browser.account.PetalAccountSyncBridge

class Settings_Profile : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge.enable(this)
        setContentView(
            PetalAccountSyncBridge.createAccountSyncView(
                this,
                { finish() },
                { }
            )
        )
    }
}
