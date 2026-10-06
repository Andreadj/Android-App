package com.mobiled.android.base.network

import androidx.annotation.Keep
import okhttp3.ResponseBody

@Keep
class Failure<T> : Resource<T> {
    val isNetworkError: Boolean
    val errorCode: Int
    var errorBody: ResponseBody? = null
    var errorMessage = ""

    constructor(
        isNetworkError: Boolean,
        errorCode: Int,
        errorBody: ResponseBody?
    ) : super(com.mobiled.android.base.network.Status.FAIL) {
        this.isNetworkError = isNetworkError
        this.errorCode = errorCode
        this.errorBody = errorBody
    }

    constructor(
        isNetworkError: Boolean,
        errorCode: Int,
        errorMessage: String
    ) : super(com.mobiled.android.base.network.Status.FAIL) {
        this.isNetworkError = isNetworkError
        this.errorCode = errorCode
        this.errorMessage = errorMessage
    }
}