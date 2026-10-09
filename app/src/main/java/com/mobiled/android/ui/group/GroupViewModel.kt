package com.mobiled.android.ui.group

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mobiled.android.LogSystem
import com.mobiled.android.MobiLedApp
import com.mobiled.android.base.BaseViewModel
import com.mobiled.android.base.model.HardwareDeviceListResult
import com.mobiled.android.base.model.HardwareGroup
import com.mobiled.android.base.model.HardwareGroupItem
import com.mobiled.android.base.network.Failure
import com.mobiled.android.base.network.Resource
import com.mobiled.android.base.network.Success
import com.mobiled.android.repository.DeviceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GroupViewModel(var repository: DeviceRepository) : BaseViewModel() {
    private var _HardwareGroupResult=MutableLiveData<Resource<HardwareGroup>>()
    val HardwareGroupResult get()=_HardwareGroupResult
    private var _SaveHardwareGroupResult=MutableLiveData<Resource<HardwareGroup>>()
    val SaveHardwareGroupResult get()=_SaveHardwareGroupResult

    fun getGroupAsync()=viewModelScope.launch{withContext(Dispatchers.IO){getDeviceList()}}
    private fun getDeviceList(){
        val result=Success(HardwareGroup().also{group->
            val items=ArrayList<HardwareGroupItem>()
            (repository.getDeviceList() as Success<HardwareDeviceListResult>).value.value.forEachIndexed{index,device->items.add(HardwareGroupItem().also{it.hardwareDevice=device;it.PixelID=index})}
            group.groupItems=items
        })
        _HardwareGroupResult.postValue(result)
    }
    fun getGroupAsync(groupId:String?)=viewModelScope.launch{withContext(Dispatchers.IO){if(groupId!=null)_HardwareGroupResult.postValue(repository.getGroupAsync(groupId)) else getDeviceList()}}
    fun getGroupMasterSlaveAsync(groupId:String?)=viewModelScope.launch{withContext(Dispatchers.IO){if(groupId!=null){var group=repository.getGroupAsync(groupId);if(group?.status==com.mobiled.android.base.network.Status.SUCCESS){group as Success<HardwareGroup>;group.value.groupItems=group.value.groupItems?.filter{it.groupId==groupId.toLong()}};_HardwareGroupResult.postValue(group!!)}else _HardwareGroupResult.postValue(Failure<HardwareGroup>(false,404,"No Group Id!"))}}
    override fun destroyViewModel() {}
    fun onSaveButtonPressed(group:HardwareGroup)=viewModelScope.launch{withContext(Dispatchers.IO){
        normalizeRouting(group)
        if((group.rowId?:-1L)!=-1L)_SaveHardwareGroupResult.postValue(repository.addGroup(group)) else if(repository.hasGroupName(group.groupTitle))_SaveHardwareGroupResult.postValue(Failure(false,0,"Group name should be new, Duplicate entry not allowed!")) else _SaveHardwareGroupResult.postValue(repository.addGroup(group))
    }}

    private fun normalizeRouting(group: HardwareGroup) {
        val items = group.groupItems.orEmpty()
        if (items.isEmpty()) return

        if (group.allDevices) {
            items.forEach {
                it.Gport = "8890"
                it.GState = "X"
            }
            return
        }

        val master = items.firstOrNull { it.GState.equals("M", ignoreCase = true) }
        val existingPort = items.asSequence()
            .map { it.Gport.toIntOrNull() }
            .firstOrNull { it in 10000..65535 }
        val port = existingPort?.toString() ?: generatePortNumber()

        items.forEach { item ->
            item.Gport = port
            item.GState = when {
                master != null && item === master -> "M"
                master != null -> "S"
                else -> "X"
            }
        }
    }

    private fun generatePortNumber(): String {
        var port: Int
        do {
            port = kotlin.random.Random.nextInt(10000, 65536)
        } while (repository.hasPortConflict(port.toString()) || port == 8889 || port == 8890)
        return port.toString()
    }

    fun hasPortConflict(port:String)=repository.hasPortConflict(port)
    companion object{val Factory:ViewModelProvider.Factory=object:ViewModelProvider.Factory{@Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>,extras:CreationExtras):T{val application=checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]);return GroupViewModel(DeviceRepository((application as MobiLedApp).applicationContext)) as T}}}
}
