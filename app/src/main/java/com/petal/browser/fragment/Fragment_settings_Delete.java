package com.petal.browser.fragment;


import android.os.Bundle;

import com.petal.browser.R;
import com.petal.browser.preferences.BasePreferenceFragment;

public class Fragment_settings_Delete extends BasePreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preference_delete, rootKey);
    }
}