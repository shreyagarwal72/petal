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

import java.util.List;
import java.util.Objects;

import com.petal.browser.R;
import com.petal.browser.browser.List_standard;
import com.petal.browser.database.RecordAction;
import com.petal.browser.unit.BrowserUnit;
import com.petal.browser.unit.HelperUnit;
import com.petal.browser.unit.RecordUnit;
import com.petal.browser.view.NinjaToast;
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

                    Snackbar snackbarDelete = Snackbar.make(v1, R.string.hint_database, Snackbar.LENGTH_SHORT);
                    HelperUnit.makeSnackbarRound(snackbarDelete);
                    snackbarDelete.setAction(this.getContext().getString(R.string.app_ok), (v2 -> {
                        try {
                            listStandard.removeDomain(list.get(position));
                            list.remove(position);
                            notifyDataSetChanged();
                            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this.getContext());
                            sp.edit()
                                    .remove(list.get(position) + "_saveData")
                                    .remove(list.get(position) + "_images")
                                    .remove(list.get(position) + "_adBlock")
                                    .remove(list.get(position) + "_trackingULS")
                                    .remove(list.get(position) + "_location")
                                    .remove(list.get(position) + "_fingerPrintProtection")
                                    .remove(list.get(position) + "_cookies")
                                    .remove(list.get(position) + "_cookiesThirdParty")
                                    .remove(list.get(position) + "_deny_cookie_banners")
                                    .remove(list.get(position) + "_javascript")
                                    .remove(list.get(position) + "_javascriptPopUp")
                                    .remove(list.get(position) + "_saveHistory")
                                    .remove(list.get(position) + "_camera")
                                    .remove(list.get(position) + "_microphone")
                                    .remove(list.get(position) + "_dom")
                                    .remove(list.get(position) + "_night")
                                    .remove(list.get(position) + "_drm")
                                    .remove(list.get(position) + "_desktop").apply();
                            NinjaToast.show(Settings_ProfileList.this, R.string.app_done);
                        } catch (Exception e) {
                            Log.i(TAG, "dialogCustomSearches:" + e);
                        }
                    }));
                    snackbarDelete.show();
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