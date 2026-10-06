package com.mobiled.android.model

import androidx.annotation.Keep
import com.mobiled.android.base.network.BaseResponse

@Keep
class StaticPageResult : BaseResponse {

    var value: String = ""

    constructor(status: Int, message: String?) : super(status, message)

    constructor(status: Int, message: String?, value: String) : super(status, message) {
        this.value = value
    }


}