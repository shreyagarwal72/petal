package com.petal.browser.fragment;

import static android.content.ContentValues.TAG;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;

import java.util.Objects;

import com.petal.browser.R;
import com.petal.browser.activity.Settings_Backup;
import com.petal.browser.activity.Settings_Delete;
import com.petal.browser.activity.Settings_Filter;
import com.petal.browser.activity.Settings_Gesture;
import com.petal.browser.activity.Settings_Menu;
import com.petal.browser.activity.Settings_Profile;
import com.petal.browser.activity.Settings_ProfileList;
import com.petal.browser.browser.AdBlock;
import com.petal.browser.preferences.BasePreferenceFragment;
import com.petal.browser.view.NinjaToast;

public class Fragment_settings extends BasePreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private Preference sp_ad_block;
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        setPreferencesFromResource(R.xml.preference_setting, rootKey);
        Context context = getContext();
        assert context != null;
        initSummary(getPreferenceScreen());

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        sp_ad_block = findPreference("ab_hosts");
        assert sp_ad_block != null;
        sp_ad_block.setSummary(AdBlock.getHostsDate(getContext()));

        Preference settings_profile = findPreference("settings_profile");
        assert settings_profile != null;
        settings_profile.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(getActivity(), Settings_Profile.class);
            requireActivity().startActivity(intent);
            return false;
        });
        Preference edit_standard = findPreference("edit_standard");
        assert edit_standard != null;
        edit_standard.setOnPreferenceClickListener(preference -> {
            sp.edit().putString("listToLoad", "standard").apply();
            Intent intent = new Intent(getActivity(), Settings_ProfileList.class);
            requireActivity().startActivity(intent);
            return false;
        });

        Preference settings_menu = findPreference("settings_menu");
        assert settings_menu != null;
        settings_menu.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(getActivity(), Settings_Menu.class);
            requireActivity().startActivity(intent);
            return false;
        });

        Preference settings_filter = findPreference("settings_filter");
        assert settings_filter != null;
        settings_filter.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(getActivity(), Settings_Filter.class);
            requireActivity().startActivity(intent);
            return false;
        });

        Preference settings_data = findPreference("settings_data");
        assert settings_data != null;
        settings_data.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(getActivity(), Settings_Backup.class);
            requireActivity().startActivity(intent);
            return false;
        });

        Preference settings_gesture = findPreference("settings_gesture");
        assert settings_gesture != null;
        settings_gesture.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(getActivity(), Settings_Gesture.class);
            requireActivity().startActivity(intent);
            return false;
        });

        Preference settings_clear = findPreference("settings_clear");
        assert settings_clear != null;
        settings_clear.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(getActivity(), Settings_Delete.class);
            requireActivity().startActivity(intent);
            return false;
        });
    }

    private void initSummary(Preference p) {
        if (p instanceof PreferenceGroup) {
            PreferenceGroup pGrp = (PreferenceGroup) p;
            for (int i = 0; i < pGrp.getPreferenceCount(); i++) {
                initSummary(pGrp.getPreference(i));
            }
        } else {
            try {updatePrefSummary(p);}
            catch (Exception e) {Log.i(TAG, "Settings:" + e);}
        }
    }

    private void updatePrefSummary(Preference p) {

        if (p instanceof ListPreference) {
            ListPreference listPref = (ListPreference) p;
            if (p.getSummaryProvider() == null) p.setSummary(listPref.getEntry());
            sp_ad_block.setSummary(AdBlock.getHostsDate(requireContext()));
        }

        Context context = getContext();
        assert context != null;
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        boolean customSE = sp.getBoolean("searchEngineSwitch", false);

        boolean defaultDynamic = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S;
        boolean useDynamicColor = sp.getBoolean("useDynamicColor", defaultDynamic);
        ListPreference theme;
        theme = findPreference("sp_theme");
        assert theme != null;
        theme.setEnabled(useDynamicColor);

        ListPreference searchEngines;
        searchEngines= findPreference("sp_search_engine");
        assert searchEngines != null;
        String customSearchEngine = sp.getString("sp_search_engine_custom", "");
        String text = getString(R.string.setting_title_searchEngine) + ": " + getString(R.string.toast_input_empty);
        if(customSE) {
            searchEngines.setEnabled(false);
            if (customSearchEngine.isEmpty()) {
                NinjaToast.show(context, text);
            }
        } else {
            searchEngines.setEnabled(true);
        }
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sp, String key) {
        assert key != null;
        updatePrefSummary(findPreference(key));
        if (key.equals("sp_ad_block") || key.equals("ab_hosts")) {
            AdBlock.downloadHosts(getActivity());
        }
        if ( key.equals("sp_theme") || key.equals("useDynamicColor")) {
            sp.edit().putInt("restart_changed", 1).apply();
            updatePrefSummary(findPreference(key));
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