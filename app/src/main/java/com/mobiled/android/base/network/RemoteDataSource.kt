package com.mobiled.android.base.network

import com.mobiled.android.LogSystem
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.mobiled.android.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class RemoteDataSource {

    companion object {
        val MAIN_NET_URL = ""

        //public static final String BASE_URL = "";
        val BASE_URL = "https://keshavinfotechdemo2.com/keshav/"
        private var retrofit: Retrofit? = null

        /**
         * This method returns retrofit client instance
         *
         * @return Retrofit object
         */
        val client: Retrofit?
            get() {
                if (retrofit == null) {

                    val gsonBuilder = GsonBuilder()
                    gsonBuilder.setDateFormat("yyyy-MM-dd")
                    gsonBuilder.disableHtmlEscaping()

                    var client: OkHttpClient =
                        OkHttpClient.Builder().addNetworkInterceptor(Interceptor { chain ->
                            val request = chain.request().newBuilder()
                                .build()
                            LogSystem.e("Server", "Intercepting Request : " + request.headers)
                            chain.proceed(request)
                        }).readTimeout(60, TimeUnit.SECONDS)
                            .connectTimeout(60, TimeUnit.SECONDS)
                            .build()
                    if (BuildConfig.DEBUG) {
                        val interceptor = HttpLoggingInterceptor()
                        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
                        client = OkHttpClient.Builder().addInterceptor(interceptor)
                            .addNetworkInterceptor(Interceptor { chain ->
                                val request = chain.request().newBuilder()
                                    .build()
                                LogSystem.e("Server", "Intercepting Request : " + request.headers)
                                chain.proceed(request)
                            })
                            .readTimeout(60, TimeUnit.SECONDS)
                            .connectTimeout(60, TimeUnit.SECONDS)
                            .build()
                    }
                    retrofit = Retrofit.Builder()
                        .baseUrl(BASE_URL)
                        .client(client)
                        .addConverterFactory(GsonConverterFactory.create(gsonBuilder.create()))
                        .build()
                }
                return retrofit
            }

        fun <INTERFACE> getDefaultClient(server: Class<INTERFACE>?): INTERFACE {
            return client!!.create(server) as INTERFACE
        }

        fun getErrorBody(response: Response<JsonObject?>): JSONObject {
            try {
                if (response.errorBody() == null) {
                    return JSONObject()
                }
                val i = response.errorBody()!!.byteStream()
                val r = BufferedReader(InputStreamReader(i))
                val errorResult = StringBuilder()
                var line: String?
                try {
                    while (r.readLine().also { line = it } != null) {
                        errorResult.append(line).append('\n')
                    }
                    val _result = errorResult.toString().trim { it <= ' ' }
                    return try {
                        JSONObject(_result)
                    } catch (e: Exception) {
                        val jsonObject = JSONObject()
                        jsonObject.put("ReturnKeycode", "JavaError")
                        jsonObject.put("ReturnMessage", _result)
                        jsonObject
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return JSONObject()
        }


    }
}