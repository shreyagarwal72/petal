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
import com.petal.browser.compose.settings.SettingsCategory;
import com.petal.browser.unit.BrowserUnit;
import com.petal.browser.unit.HelperUnit;

public class Settings_Activity extends AppCompatActivity {

    /**
     * Optional String extra naming a {@link SettingsCategory} enum constant (e.g.
     * "API_INTEGRATIONS"). When present, Settings opens straight to that category's
     * page instead of the root category list - so a caller like the Petal AI Hub's
     * "Configure Keys" action lands the user on the actual page they asked for, not
     * a list they then have to tap through themselves.
     */
    public static final String EXTRA_SETTINGS_CATEGORY = "settings_category";

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(HelperUnit.applyLanguage(newBase));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        com.petal.browser.unit.PetalHighRefreshRateManager.applyHighRefreshRate(this);
        HelperUnit.initTheme(this);
        EdgeToEdge.enable(this);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        SettingsCategory initialCategory = SettingsCategory.OVERVIEW;
        String requestedCategory = getIntent() != null ? getIntent().getStringExtra(EXTRA_SETTINGS_CATEGORY) : null;
        if (requestedCategory != null) {
            try {
                initialCategory = SettingsCategory.valueOf(requestedCategory);
            } catch (IllegalArgumentException ignored) {
                // Unknown/typo'd category name - fall back to the overview rather than crash.
            }
        }

        setContentView(PetalSettingsBridge.createSettingsView(this, initialCategory, () -> {
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