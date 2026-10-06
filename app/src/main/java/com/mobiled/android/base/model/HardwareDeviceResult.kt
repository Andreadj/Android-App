package com.mobiled.android.base.model

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import com.mobiled.android.base.network.BaseResponse

@Keep
class HardwareDeviceResult : BaseResponse {
    @SerializedName("result")
    var value: HardwareDevice = HardwareDevice()

    @SerializedName("deviceResultList")
    var valueList: List<HardwareDevice> = listOf()

    constructor() : super(1, "Default Init")
    constructor(status: Int, message: String?) : super(status, message)
}