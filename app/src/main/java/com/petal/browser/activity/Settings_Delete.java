package com.petal.browser.activity;

import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.snackbar.Snackbar;

import java.util.Objects;

import com.petal.browser.R;
import com.petal.browser.fragment.Fragment_settings_Delete;
import com.petal.browser.unit.BrowserUnit;
import com.petal.browser.unit.HelperUnit;

public class Settings_Delete extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HelperUnit.initTheme(this);
        EdgeToEdge.enable(this);
        setContentView(com.petal.browser.compose.settings.PetalDeleteBridge.createDeleteView(this, this::finish));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_help, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) finish();
        if (menuItem.getItemId() == R.id.menu_help) {
            Uri webpage = Uri.parse("https://github.com/shreyagarwal72/petal");
            BrowserUnit.intentURL(this, webpage);
        }
        return true;
    }
}