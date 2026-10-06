package com.mobiled.android.ui.about

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.mobiled.android.base.BaseViewModel
import com.mobiled.android.base.network.Resource
import com.mobiled.android.model.StaticPageResult
import com.mobiled.android.repository.HelpRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HelpViewModel(var repository: HelpRepository) : BaseViewModel() {


    private var _TermsPageResult: MutableLiveData<Resource<StaticPageResult>> = MutableLiveData()
    val TermsPageResult: MutableLiveData<Resource<StaticPageResult>> get() = _TermsPageResult


    fun getContentAsync() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            _TermsPageResult.postValue(repository.getContent())
        }
    }

    override fun destroyViewModel() {

    }
}