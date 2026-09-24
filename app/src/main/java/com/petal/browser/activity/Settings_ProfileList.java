package com.petal.browser.activity;

import static android.content.ContentValues.TAG;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import android.widget.ImageView;
import android.widget.ListView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
import com.petal.browser.ui.components.PetalConfirmSheetBridge;

import java.util.List;
import java.util.Objects;

import com.petal.browser.R;
import com.petal.browser.browser.List_standard;
import com.petal.browser.database.RecordAction;
import com.petal.browser.unit.BrowserUnit;
import com.petal.browser.unit.HelperUnit;
import com.petal.browser.unit.RecordUnit;
import com.petal.browser.view.PetalToast;
import com.petal.browser.view.AdapterProfileList;

public class Settings_ProfileList extends AppCompatActivity {

    private List<String> list;
    private List_standard listStandard;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HelperUnit.initTheme(this);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings_profile_list);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        listStandard = new List_standard(this);
        RecordAction action = new RecordAction(this);
        action.open(false);
        list = action.listDomains(RecordUnit.TABLE_STANDARD);
        action.close();
        ListView listView = findViewById(R.id.whitelist);
        listView.setEmptyView(findViewById(R.id.whitelist_empty));
        //noinspection NullableProblems
        AdapterProfileList adapter = new AdapterProfileList(this, list) {
            @Override
            public View getView(final int position, View convertView, @NonNull ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                ImageView deleteEntry = v.findViewById(R.id.iconMenu);
                deleteEntry.setVisibility(View.VISIBLE);

                TypedValue typedValue = new TypedValue();
                getTheme().resolveAttribute(R.attr.colorSurface, typedValue, true);
                int color = typedValue.data;
                MaterialCardView cardView = v.findViewById(R.id.menuCardView);
                cardView.setBackgroundColor(color);
                HelperUnit.applyBouncyTouchFeedback(deleteEntry, 0.88f);
                deleteEntry.setOnClickListener(v1 -> {
                    String domain = list.get(position);
                    PetalConfirmSheetBridge.showClearDatabaseConfirmation(
                        Settings_ProfileList.this,
                        getString(R.string.hint_database),
                        "Are you sure you want to remove settings for \"" + domain + "\"?",
                        () -> {
                            try {
                                listStandard.removeDomain(domain);
                                list.remove(position);
                                notifyDataSetChanged();
                                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(Settings_ProfileList.this);
                                sp.edit()
                                        .remove(domain + "_saveData")
                                        .remove(domain + "_images")
                                        .remove(domain + "_adBlock")
                                        .remove(domain + "_trackingULS")
                                        .remove(domain + "_location")
                                        .remove(domain + "_fingerPrintProtection")
                                        .remove(domain + "_cookies")
                                        .remove(domain + "_cookiesThirdParty")
                                        .remove(domain + "_deny_cookie_banners")
                                        .remove(domain + "_javascript")
                                        .remove(domain + "_javascriptPopUp")
                                        .remove(domain + "_saveHistory")
                                        .remove(domain + "_camera")
                                        .remove(domain + "_microphone")
                                        .remove(domain + "_dom")
                                        .remove(domain + "_night")
                                        .remove(domain + "_drm")
                                        .remove(domain + "_desktop").apply();
                                PetalToast.show(Settings_ProfileList.this, R.string.app_done);
                            } catch (Exception e) {
                                Log.i(TAG, "dialogCustomSearches:" + e);
                            }
                        }
                    );
                });
                return v;
            }
        };
        listView.setAdapter(adapter);
        adapter.notifyDataSetChanged();
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