package com.mobiled.android.base.model

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import com.mobiled.android.base.network.BaseResponse

@Keep
class HardwareDeviceListResult : BaseResponse(0, "Default Initialization") {
    @SerializedName("result")
    var value: List<HardwareDevice> = emptyList()

    var groupList: List<HardwareGroup> = emptyList()
}