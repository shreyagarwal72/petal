package com.petal.browser.activity;

import android.content.Intent;
import android.net.Uri;
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
            shortcut -> {
                if (shortcut != null && shortcut.getUrl() != null && !shortcut.getUrl().trim().isEmpty()) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(shortcut.getUrl().trim()));
                    intent.setClass(Settings_Profile.this, BrowserActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                }
                return kotlin.Unit.INSTANCE;
            }
        ));
    }
}