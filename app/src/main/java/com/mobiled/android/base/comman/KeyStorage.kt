package com.mobiled.android.base.comman

import android.content.Context
import android.content.SharedPreferences

class KeyStorage private constructor(context: Context) {
    var sharedPreferences: SharedPreferences? = null

    init {
        sharedPreferences = context.getSharedPreferences("sdk-data", Context.MODE_PRIVATE)
    }

    fun setStringValue(key: String?, value: String?) {
        val editor = sharedPreferences!!.edit()
        editor.putString(key, value)
        editor.apply()
        editor.commit()
    }

    fun getStringValue(key: String?): String {
        return sharedPreferences!!.getString(key, "") ?: "";
    }

    fun clear() {
        val editor = sharedPreferences!!.edit()
        editor.clear()
        editor.apply()
        editor.commit()
    }

    companion object {
        private var keyStorage: KeyStorage? = null

        @JvmStatic
        fun getKeyStorage(context: Context): KeyStorage {
            if (keyStorage == null) keyStorage = KeyStorage(context)
            return keyStorage!!
        }
    }
}