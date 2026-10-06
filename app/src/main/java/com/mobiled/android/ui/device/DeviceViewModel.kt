package com.mobiled.android.ui.device

import android.content.Context
import com.mobiled.android.LogSystem
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.mobiled.android.base.BaseViewModel
import com.mobiled.android.base.comman.UdpClient
import com.mobiled.android.base.model.HardwareDevice
import com.mobiled.android.base.model.HardwareDeviceListResult
import com.mobiled.android.base.model.HardwareDeviceResult
import com.mobiled.android.base.network.Failure
import com.mobiled.android.base.network.Resource
import com.mobiled.android.base.network.Success
import com.mobiled.android.repository.DeviceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import om.android.mobiled.comman.hasSameAPName
import om.android.mobiled.comman.hasSameAPNameAndIp
import org.json.JSONObject

class DeviceViewModel(private var context: Context? = null) : BaseViewModel(), UdpClient.Listener {
    private val TAG = DeviceViewModel::class.simpleName ?: "DeviceViewModel"
    private var deviceRepository = DeviceRepository(context!!)

    override fun destroyViewModel() {
        context = null
    }

    private val _deviceResult = MutableLiveData<Resource<HardwareDeviceResult>>()
    val deviceResult: MutableLiveData<Resource<HardwareDeviceResult>>
        get() = _deviceResult

    private var deviceList: List<HardwareDevice> = emptyList()

    fun addDeviceAsync(device: HardwareDevice) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            _deviceResult.postValue(deviceRepository.addDevice(hardwareDevice = device))
        }
    }

    private val _deviceListResult = MutableLiveData<Resource<HardwareDeviceListResult>>()
    val deviceListResult: MutableLiveData<Resource<HardwareDeviceListResult>>
        get() = _deviceListResult


    fun getDevicesAsync() = viewModelScope.launch {
        LogSystem.d(TAG, "getDevicesAsync() called")
        withContext(Dispatchers.IO) {
            var result = deviceRepository.getDeviceList()
            _deviceListResult.postValue(result)
            LogSystem.d(
                TAG,
                "getDevicesAsync() Result : ${result.status} && ${result.status == com.mobiled.android.base.network.Status.SUCCESS} Compare : ${com.mobiled.android.base.network.Status.SUCCESS}"
            )
            if (result.status == com.mobiled.android.base.network.Status.SUCCESS) {
                deviceList = (result as Success<HardwareDeviceListResult>).value.value
                deviceListFetched = true
            }
        }
    }

    fun startListening(client: UdpClient) {
        client.with(this)
        client.openUdpClient()
        client.startListen()
    }

//    private val _udpHardwareDevice = MutableLiveData<HardwareDevice>()
//    val udpHardwareDevice: MutableLiveData<HardwareDevice>
//        get() = _udpHardwareDevice

    var udpDataReceived = false
    fun resetClient() {
        udpDataReceived = false
    }

    var deviceListFetched = false
    var udpDeviceList = arrayListOf<HardwareDevice>()

    override fun onUdpMessage(bytes: ByteArray) {
        LogSystem.d(
            TAG,
            "onUdpMessage() udpDataReceived = $udpDataReceived deviceListFetched = $deviceListFetched"
        )
        if (udpDataReceived || !deviceListFetched) {
            return
        }

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val rawData = String(bytes, Charsets.UTF_8)
                    val jsonStart = rawData.indexOf('{')
                    val jsonEnd = rawData.lastIndexOf('}')
                    if (jsonStart < 0 || jsonEnd < jsonStart) {
                        LogSystem.e(TAG, "Discovery packet does not contain a valid JSON object")
                        return@withContext
                    }
                    val jsonObject = JSONObject(rawData.substring(jsonStart, jsonEnd + 1))
                    LogSystem.d(
                        TAG,
                        "Processing UDP Bytes"
                    )
                    var hardwareDevice =
                        Gson().fromJson(jsonObject.toString(), HardwareDevice::class.java)
                    LogSystem.d(
                        TAG,
                        "Processing UDP hardwareDevice ${hardwareDevice.ApName} udpDataReceived = $udpDataReceived"
                    )
                    if (hardwareDevice.ApName != null && !udpDataReceived) {
                        udpDataReceived = true

                        var processTrans = true
                        if (!deviceList.isEmpty()) {
                            if (deviceList.hasSameAPNameAndIp(hardwareDevice)) {
                                processTrans = false
                            }
                        }
                        if (processTrans) {
                            LogSystem.e(TAG, "Device Found ${hardwareDevice.ApName}")
                        }
                        if (processTrans) {
                            if (!udpDeviceList.hasSameAPNameAndIp(hardwareDevice)) {
                                udpDeviceList.add(hardwareDevice)
                            }
                        }
                        udpDataReceived = false
                    }
                } catch (ignore: Exception) {
                    ignore.printStackTrace()
                }

            }
        }
    }

    fun getUdpResult() {
        deviceListFetched = false
        udpDataReceived = true

        viewModelScope.launch {
            withContext(Dispatchers.IO)
            {
                var msg = "Scan successfully completed"
                if (udpDeviceList.isEmpty()) {
                    msg = "Ops, we didn't found any device!. Make sure it was setup correctly."
                    _deviceResult.postValue(Failure(false, 404, msg))
                } else {
                    var result = HardwareDeviceResult(1, msg)
                    var valueList: ArrayList<HardwareDevice> = arrayListOf()
                    udpDeviceList.forEach {
                        var temp = deviceRepository.addDevice(hardwareDevice = it)
                        if (temp.status == com.mobiled.android.base.network.Status.SUCCESS) {
                            valueList.add((temp as Success<HardwareDevice>).value)
                            if (deviceList.hasSameAPName(it.ApName)) {
                                msg = "$msg\n${it.ApName} Device has been updated"
                            } else {
                                msg = "$msg\n${it.ApName} Device has been added"
                            }
                        }
                    }
                    result.valueList = valueList
                    _deviceResult.postValue(Success(result))
                }
            }
        }
    }

}