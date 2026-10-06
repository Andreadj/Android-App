package com.mobiled.android.ui

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mobiled.android.base.comman.LogSocketManager
import com.mobiled.android.databinding.ActivityTestingBinding
import om.android.mobiled.comman.toInetAddress
import om.android.mobiled.comman.toInt
import java.net.InetAddress

class TestingActivity : AppCompatActivity() {
    lateinit var viewBinding: ActivityTestingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityTestingBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        var defaultNetmask = InetAddress.getByName("255.255.255.0").toInt()
        logMessage("TAG","Netmask : ${defaultNetmask}")
        logMessage("TAG","Netmask Address : ${defaultNetmask.toInetAddress()}")
        logMessage("TAG","Netmask Inv Address: ${defaultNetmask.inv().toInetAddress()}")

        viewBinding.root.postDelayed({
            val address = getBroadcastIpAddress()
            viewBinding.viewIpInfo.text = address.toString()
        }, 1000)
    }

    private fun getBroadcastIpAddress(): InetAddress {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val dhcpInfo = wifiManager.dhcpInfo
            var ipAddress = dhcpInfo.ipAddress
            val ipString = "${ipAddress and 0xFF}.${(ipAddress shr 8) and 0xFF}.${(ipAddress shr 16) and 0xFF}.${(ipAddress shr 24) and 0xFF}"
            ipAddress = InetAddress.getByName(ipString).toInt()
            var subnetMask = dhcpInfo.netmask

            // Check if subnet mask is 0 or not a valid mask
            if (subnetMask == 0 || Integer.bitCount(subnetMask) != 32) {
                subnetMask = 0xFFFFFF00.toInt() // Assign default subnet mask if invalid
                //-256 to IP will be 255.255.255.0 and inv of -256 will be 0.0.0.255
                //InetAddress.getByName("255.255.255.0").toInt()
            }

            logMessage("TAG", "ipAddress : ${ipAddress} Represents : ${ipAddress.toInetAddress()}")
            logMessage("TAG", "subnetMask : ${subnetMask} Represents : ${subnetMask.toInetAddress()}")

            //Network address = IP address & Subnet mask
            val NetworkAddress = ipAddress and subnetMask

            //Broadcast address = Network address | (NOT Subnet mask)
            val broadcast = NetworkAddress or subnetMask.inv()

            logMessage("TAG", "NetworkAddress : ${NetworkAddress} , Represents : ${NetworkAddress.toInetAddress()}")
            logMessage("TAG", "broadcast : ${broadcast} , Represents : ${broadcast.toInetAddress()}")

            return broadcast.toInetAddress()
        } catch (e: Exception) {
            logMessage("Error", "IP Error ${e.message}")
        }
        return InetAddress.getByName("0.0.0.0")
    }



    private fun logMessage(tag: String, message: String) {
        LogSocketManager.getSocketManager().writeLog(message)
    }

}
