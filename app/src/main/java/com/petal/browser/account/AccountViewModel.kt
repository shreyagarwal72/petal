package com.petal.browser.account

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class AccountViewModel(application: Application) : AndroidViewModel(application) {

    // Proxies GoogleAccountManager's own Compose state directly instead of a
    // one-time copy. GoogleAccountManager.currentProfile updates asynchronously
    // (e.g. once the real profile finishes fetching after sign-in), so reading
    // it live here - rather than snapshotting it once in init - is what lets
    // the home screen and account page pick up sign-in/avatar changes without
    // needing the activity to be recreated.
    val profileState: GoogleUserProfile
        get() = GoogleAccountManager.currentProfile

    init {
        GoogleAccountManager.init(getApplication())
    }
}
