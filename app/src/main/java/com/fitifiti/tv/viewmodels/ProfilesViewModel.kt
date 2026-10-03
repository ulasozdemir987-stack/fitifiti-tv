package com.fitifiti.tv.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitifiti.tv.data.local.dao.ProfileDao
import com.fitifiti.tv.data.local.entity.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val profileDao: ProfileDao
) : ViewModel() {

    val profiles = profileDao.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addProfile(name: String, avatarUrl: String, pin: String?) {
        viewModelScope.launch {
            profileDao.insertProfile(Profile(name = name, avatarUrl = avatarUrl, pin = pin?.takeIf { it.isNotBlank() }))
        }
    }
    
    fun deleteProfile(profile: Profile) {
        viewModelScope.launch {
            profileDao.deleteProfile(profile)
        }
    }
}
