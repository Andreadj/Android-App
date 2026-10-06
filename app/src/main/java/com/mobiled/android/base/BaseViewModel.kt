package com.mobiled.android.base

import androidx.lifecycle.ViewModel

abstract class BaseViewModel() : ViewModel() {


    abstract fun destroyViewModel()
}
