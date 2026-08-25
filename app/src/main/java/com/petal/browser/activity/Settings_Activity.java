package com.petal.browser.activity;

import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.petal.browser.R;
import com.petal.browser.compose.settings.PetalSettingsBridge;
import com.petal.browser.unit.BrowserUnit;
import com.petal.browser.unit.HelperUnit;

public class Settings_Activity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(HelperUnit.applyLanguage(newBase));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HelperUnit.initTheme(this);
        EdgeToEdge.enable(this);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        setContentView(PetalSettingsBridge.createSettingsView(this, () -> {
            finish();
            return kotlin.Unit.INSTANCE;
        }));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_help, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) finish();
        else if (menuItem.getItemId() == R.id.menu_help) {
            Uri webpage = Uri.parse("https://github.com/shreyagarwal72/petal");
            BrowserUnit.intentURL(this, webpage);
        }
        return true;
    }
}