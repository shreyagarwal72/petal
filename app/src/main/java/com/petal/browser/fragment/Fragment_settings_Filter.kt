package com.petal.browser.fragment

import android.os.Bundle
import com.petal.browser.R
import com.petal.browser.preferences.BasePreferenceFragment

class Fragment_settings_Filter : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preference_filter, rootKey)
    }
}
