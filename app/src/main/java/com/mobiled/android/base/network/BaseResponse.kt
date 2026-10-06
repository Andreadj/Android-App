package com.mobiled.android.base.network

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Keep
public open class BaseResponse : Serializable {
    @SerializedName("status")
    private var status = 0

    @SerializedName("message")
    private var message: String? = null

    constructor(status: Int, message: String?) {
        this.status = status
        this.message = message
    }

    fun isSuccessful(): Boolean {
        return status == 1
    }


    fun getStatus(): Int {
        return status
    }

    fun getMessage(): String? {
        return message
    }


}