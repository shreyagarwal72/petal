package com.petal.browser.account

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

class AccountViewModel(application: Application) : AndroidViewModel(application) {

    var profileState by mutableStateOf(GoogleAccountManager.currentProfile)
        private set

    init {
        refreshState()
    }

    fun refreshState() {
        viewModelScope.launch {
            GoogleAccountManager.init(getApplication())
            profileState = GoogleAccountManager.currentProfile
        }
    }
}
