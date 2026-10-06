package com.mobiled.android.base.comman

import android.net.NetworkInfo.DetailedState
import android.net.wifi.WifiInfo
import com.mobiled.android.base.model.WifiItem

interface WiFiConnectorListener {
    fun onWiFiStateUpdate(wifiInfo: WifiInfo?, detailedState: DetailedState?)
    fun onWiFiRssiChanged(rssi: Int)
    fun onWiFiScanResults(wiFiList: List<WifiItem>)
}