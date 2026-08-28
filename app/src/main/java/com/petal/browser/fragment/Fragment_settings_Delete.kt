package com.petal.browser.fragment

import android.os.Bundle
import com.petal.browser.R
import com.petal.browser.preferences.BasePreferenceFragment

class Fragment_settings_Delete : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preference_delete, rootKey)
    }
}
