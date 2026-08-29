package com.petal.browser.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;

import java.util.Objects;

import com.petal.browser.R;
import com.petal.browser.browser.BannerBlock;
import com.petal.browser.preferences.BasePreferenceFragment;

public class Fragment_settings_Profile extends BasePreferenceFragment  implements SharedPreferences.OnSharedPreferenceChangeListener  {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        assert context != null;
        getPreferenceScreen();
        setPreferencesFromResource(R.xml.preference_profile_standard, rootKey);
        PreferenceManager.setDefaultValues(context, R.xml.preference_profile_standard, false);
        initSummary(getPreferenceScreen());
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sp, String key) {
        updatePrefSummary(findPreference(key));
        if (key.equals("profileStandard_adBlock")) {
            if (sp.getBoolean("profileStandard_adBlock",false)) BannerBlock.downloadBanners(getActivity());
        }
    }

    private void initSummary(Preference p) {
        if (p instanceof PreferenceGroup) {
            PreferenceGroup pGrp = (PreferenceGroup) p;
            for (int i = 0; i < pGrp.getPreferenceCount(); i++) {
                initSummary(pGrp.getPreference(i));
            }
        } else {
            updatePrefSummary(p);
        }
    }

    private void updatePrefSummary(Preference p) {
        Context context = getContext();
        assert context != null;
        if (p instanceof ListPreference) {
            ListPreference listPref = (ListPreference) p;
            p.setSummary(listPref.getEntry());
        }
        if (p instanceof EditTextPreference) {
            EditTextPreference editTextPref = (EditTextPreference) p;
            if (Objects.requireNonNull(p.getTitle()).toString().toLowerCase().contains("password")) {
                p.setSummary("******");
            } else {
                if (p.getSummaryProvider() == null) p.setSummary(editTextPref.getText());
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Objects.requireNonNull(getPreferenceScreen().getSharedPreferences()).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        Objects.requireNonNull(getPreferenceScreen().getSharedPreferences()).unregisterOnSharedPreferenceChangeListener(this);
    }
}