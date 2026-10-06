package com.mobiled.android.base.model

import androidx.annotation.Keep

@Keep
class WifiItem : java.io.Serializable {
    var capabilities: String = ""
    var BSSID = ""
    var wifiName = ""
    var wifiPassword = ""
    var isConnected = false
    var isSecure = false
    var wifiSignal = 0


    override fun toString(): String {
        return "WifiItem(capabilities='$capabilities', BSSID='$BSSID', wifiName='$wifiName', wifiPassword='$wifiPassword', isConnected=$isConnected, isSecure=$isSecure, wifiSignal=$wifiSignal)"
    }


}