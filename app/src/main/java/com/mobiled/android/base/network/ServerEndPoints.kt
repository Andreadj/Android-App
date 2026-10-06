package com.mobiled.android.base.network

import com.google.gson.JsonObject
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.*

interface ServerEndPoints {

    @POST("register")
    fun register(@Body jsonObject: JsonObject): Call<ResponseBody?>?

    @POST("login")
    fun userLogin(@Body jsonObject: JsonObject): Call<ResponseBody?>?

    @POST("getBoxData")
    fun getBoxList(@Body jsonObject: JsonObject): Call<ResponseBody?>?

    @POST("getChargingData")
    fun getChargingData(@Body jsonObject: JsonObject): Call<ResponseBody?>?

    @POST("addBoxSetup")
    fun addBox(@Body jsonObject: JsonObject): Call<ResponseBody?>?

    @POST("editBox")
    fun editBox(@Body jsonObject: JsonObject): Call<ResponseBody?>?

    //Edit user profile
    @POST("editProfile")
    fun updateUserData(@Body jsonObject: JsonObject): Call<ResponseBody?>?

    @POST("addChargingData")
    fun addChargingData(@Body jsonObject: JsonObject): Call<JsonObject?>?

    @POST("editChargingData")
    fun editChargingData(@Body jsonObject: JsonObject): Call<JsonObject?>?

    @POST("deleteBox")
    fun deleteBox(@Body jsonObject: JsonObject): Call<ResponseBody?>?

}