package com.petal.browser.activity;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.petal.browser.compose.settings.SettingsCategory;

/**
 * Modern redirect: routes legacy gesture settings directly to the modern Jetpack Compose
 * Address Bar & Gestures settings screen.
 */
public class Settings_Gesture extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent(this, Settings_Activity.class);
        intent.putExtra(Settings_Activity.EXTRA_SETTINGS_CATEGORY, SettingsCategory.ADDRESS_BAR.name());
        startActivity(intent);
        finish();
    }
}
