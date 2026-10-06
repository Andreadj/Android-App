package com.mobiled.android.base

import android.content.Context
import com.mobiled.android.base.network.BaseResponse
import com.mobiled.android.base.network.Failure
import com.mobiled.android.base.network.Resource
import com.mobiled.android.base.network.Success
import retrofit2.Call
import retrofit2.HttpException
import retrofit2.Response

abstract class BaseRepository(var context: Context) {

    fun <T> safeNetworkCall(networkCall: Call<T>): Resource<T> {
        try {
            var result = networkCall.execute() as Response<T>
            if (result.code() != 200) {
                return Failure<T>(false, result.code(), result.errorBody())
            } else {
                if ((result.body() as BaseResponse).getStatus() == 0) {
                    return Failure<T>(
                        false,
                        result.code(),
                        (result.body() as BaseResponse).getMessage() ?: ""
                    )
                }
            }
            return Success(result.body() as T)
        } catch (e: HttpException) {
            e.printStackTrace()
            return Failure<T>(false, e.code(), e.response()?.errorBody())
        } catch (e: Exception) {
            e.printStackTrace()
            return Failure<T>(true, 0, "")
        }
    }

    fun readHtmlFromAssets(fileName: String): String {
        val inputStream = context.assets.open(fileName)
        val size = inputStream.available()
        val buffer = ByteArray(size)
        inputStream.read(buffer)
        inputStream.close()
        return String(buffer, Charsets.UTF_8)
    }


}