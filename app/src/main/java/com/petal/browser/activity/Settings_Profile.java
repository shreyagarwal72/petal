package com.petal.browser.activity;

import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.petal.browser.account.PetalAccountSyncBridge;

public class Settings_Profile extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(PetalAccountSyncBridge.createAccountSyncView(
            this,
            () -> { finish(); return kotlin.Unit.INSTANCE; },
            shortcut -> kotlin.Unit.INSTANCE
        ));
    }
}