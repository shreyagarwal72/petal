package com.petal.browser.activity;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.petal.browser.compose.settings.SettingsCategory;

/**
 * Modern redirect: routes legacy filter settings directly to the modern Jetpack Compose
 * Privacy & Security settings screen (which hosts AdBlock, Tracker Protection, and Whitelisting).
 */
public class SettingsFilterActivity extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.putExtra(SettingsActivity.EXTRA_SETTINGS_CATEGORY, SettingsCategory.PRIVACY.name());
        startActivity(intent);
        finish();
    }
}
