package com.mobiled.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mobiled.android.base.BaseRepository
import com.mobiled.android.repository.AboutRepository
import com.mobiled.android.repository.HelpRepository
import com.mobiled.android.repository.PrivacyPolicyRepository
import com.mobiled.android.ui.about.AboutViewModel
import com.mobiled.android.ui.about.HelpViewModel
import com.mobiled.android.ui.about.PrivacyViewModel

class ViewModelFactory(private val repository: BaseRepository) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AboutViewModel::class.java)) return AboutViewModel(repository as AboutRepository) as T
        else if (modelClass.isAssignableFrom(PrivacyViewModel::class.java)) return PrivacyViewModel(repository as PrivacyPolicyRepository) as T
        else if (modelClass.isAssignableFrom(HelpViewModel::class.java)) return HelpViewModel(repository as HelpRepository) as T
        else throw IllegalArgumentException("View Model Class Not Found")
    }
}