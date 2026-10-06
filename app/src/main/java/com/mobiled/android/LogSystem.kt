package com.mobiled.android

import android.util.Log

class LogSystem
{
    companion object
    {
        @JvmStatic
        fun e(tag : String?="LogSystem", message: String)
        {
            if(BuildConfig.DEBUG)
            {
                Log.e(tag, message)
            }
        }

        @JvmStatic
        fun d(tag : String, message: String)
        {
            if(BuildConfig.DEBUG)
            {
                Log.e(tag, message)
            }
        }
    }
}
