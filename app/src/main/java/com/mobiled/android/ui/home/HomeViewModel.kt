package com.mobiled.android.ui.home

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.mobiled.android.base.BaseViewModel
import com.mobiled.android.base.model.HardwareDevice
import com.mobiled.android.base.model.HardwareDeviceListResult
import com.mobiled.android.base.model.HardwareGroup
import com.mobiled.android.base.network.Resource
import com.mobiled.android.base.network.Status
import com.mobiled.android.base.network.Success
import com.mobiled.android.repository.DeviceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(var context: Context? = null) : BaseViewModel() {

    private var deviceRepository = DeviceRepository(context!!)

    private val _deviceListResult = MutableLiveData<Resource<HardwareDeviceListResult>>()
    val deviceListResult: MutableLiveData<Resource<HardwareDeviceListResult>>
        get() = _deviceListResult


    private var deviceList: List<HardwareDevice> = emptyList()

    fun getDevicesAsync() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            var result = deviceRepository.getDeviceList()
            _deviceListResult.postValue(result)
            deviceListFetched = true
        }
    }

    fun getGroupsAsync() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            var result = deviceRepository.getGroupsAsync()
            _deviceListResult.postValue(result)
            if (result.status == com.mobiled.android.base.network.Status.SUCCESS) {
                deviceList = (result as Success<HardwareDeviceListResult>).value.value
            }
            deviceListFetched = true
        }
    }

    override fun destroyViewModel() {

    }

    fun removeDevices() = viewModelScope.launch {
        if(_deviceListResult.value is Success) {
            var groupList =
                (_deviceListResult.value as Success<HardwareDeviceListResult>).value.groupList
            groupList.forEach {
                it.groupItems = listOf()
            }
            _deviceListResult.postValue(Success<HardwareDeviceListResult>(HardwareDeviceListResult().also {
                it.value = listOf()
                it.groupList = groupList
            }))
        }
        withContext(Dispatchers.IO) {
            deviceRepository?.deleteDevices()
        }
    }

    fun removeDevice(hardwareDevice: HardwareDevice) {
        var tempList = arrayListOf<HardwareDevice>()
        var groupList = (_deviceListResult.value as Success<HardwareDeviceListResult>).value.groupList
        tempList.addAll((_deviceListResult.value as Success<HardwareDeviceListResult>).value.value)
        var matchIndex = -1
        tempList.forEachIndexed { index, _hardwareDevice ->
            if (hardwareDevice.rowId == _hardwareDevice.rowId) {
                matchIndex = index
                return@forEachIndexed
            }
        }
        if (matchIndex != -1) {
            tempList.removeAt(matchIndex)
            groupList.forEachIndexed { gIndex, hardwareGroup ->
                var groupItemMatchIndex  = -1
                var items = ArrayList(hardwareGroup.groupItems?: emptyList())
                items.forEachIndexed { index, hardwareGroupItem ->
                    if (hardwareGroupItem.hardwareDevice?.rowId == hardwareDevice.rowId) {
                        groupItemMatchIndex = index
                        return@forEachIndexed
                    }
                }
                if(groupItemMatchIndex != -1) {
                    items.removeAt(groupItemMatchIndex)
                    hardwareGroup.isDevicesOn = false
                    hardwareGroup.isDevicesOn = false
                    hardwareGroup.groupItems = items
                }
            }
            _deviceListResult.postValue(Success<HardwareDeviceListResult>(HardwareDeviceListResult().also {
                it.value = tempList
                it.groupList = groupList
            }))

            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    deviceRepository.deleteDevice(hardwareDevice)
                }
            }
        }
    }

    fun removeGroup(hardwareGroup: HardwareGroup) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            deviceRepository.removeGroup(hardwareGroup)
        }
    }

    fun onDeviceFrameChanged(device: HardwareDevice) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            deviceRepository.updateDevice(device)
        }
    }

    var deviceListFetched = false
}
