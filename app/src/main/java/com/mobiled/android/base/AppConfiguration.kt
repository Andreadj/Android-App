package com.mobiled.android.base
import com.mobiled.android.BuildConfig

class AppConfiguration {
    companion object {
        open val UDP_SEND_PORT: Int = 8889
        open val UDP_DEST_PORT: Int = 8232
        open val UDP_LOCAL_PORT: Int = 8232 /* must be equal to LOCAL_PORT */
        open val UDP_SIMULATE_DEVICES = false && BuildConfig.DEBUG
        open val MASTER_SLAVE_DEBUG = false && BuildConfig.DEBUG
        open val SOCKET_LOG = false && BuildConfig.DEBUG
        open val DEBUG = false && BuildConfig.DEBUG
    }
}
