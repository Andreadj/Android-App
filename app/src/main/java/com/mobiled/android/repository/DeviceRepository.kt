package com.mobiled.android.repository

import android.content.Context
import com.mobiled.android.LogSystem
import com.mobiled.android.base.BaseRepository
import com.mobiled.android.base.database.AppDatabase
import com.mobiled.android.base.model.HardwareDevice
import com.mobiled.android.base.model.HardwareDeviceListResult
import com.mobiled.android.base.model.HardwareDeviceResult
import com.mobiled.android.base.model.HardwareGroup
import com.mobiled.android.base.network.Failure
import com.mobiled.android.base.network.Resource
import com.mobiled.android.base.network.Success

class DeviceRepository(context: Context) : BaseRepository(context) {
    private var appDatabase: AppDatabase = AppDatabase.getDatabase(context)

    fun addDevice(hardwareDevice: HardwareDevice): Resource<HardwareDeviceResult> {
        var rowId = appDatabase.getHardwareTable().insertOrUpdate(hardwareDevice)
        if (rowId == -1L) {
            return Failure<HardwareDeviceResult>(false, 0, "Something wrong with database!")
        } else {
            hardwareDevice.rowId = rowId
            return Success<HardwareDeviceResult>(HardwareDeviceResult().apply {
                value = hardwareDevice
            })
        }
    }

    fun getDeviceList(): Resource<HardwareDeviceListResult> {
        var data = HardwareDeviceListResult().also {
            it.value = appDatabase.getHardwareTable().getHardwareDevices()
        }
        return Success<HardwareDeviceListResult>(data)
    }

    fun getGroupsAsync(): Resource<HardwareDeviceListResult> {
        // Device membership is persistent. If the hardware_device table is missing
        // rows but group items still contain the saved device data, restore only
        // those missing rows. Explicit Remove Device / Remove Devices removes the
        // corresponding group items as well, so an explicitly removed device is
        // never recreated here.
        var devices = appDatabase.getHardwareTable().getHardwareDevices().toMutableList()
        val existingApNames = devices.mapNotNull { it.ApName }.toMutableSet()
        val groups = appDatabase.getGroupTable().getHardwareGroups()

        groups.flatMap { it.groupItems.orEmpty() }
            .mapNotNull { it.hardwareDevice }
            .forEach { savedDevice ->
                val apName = savedDevice.ApName ?: return@forEach
                if (!existingApNames.contains(apName)) {
                    val id = appDatabase.getHardwareTable().insert(savedDevice)
                    if (id != -1L) {
                        if (savedDevice.rowId == null || savedDevice.rowId == 0L) {
                            savedDevice.rowId = id
                        }
                        devices.add(savedDevice)
                        existingApNames.add(apName)
                    }
                }
            }

        val finalDevices = appDatabase.getHardwareTable().getHardwareDevices().sortedBy { it.rowId }
        val finalGroups = appDatabase.getGroupTable().getHardwareGroups()
        finalGroups.forEach { group ->
            group.groupItems = group.groupItems?.sortedBy { it.hardwareDevice?.rowId }
        }

        return Success<HardwareDeviceListResult>(HardwareDeviceListResult().also {
            it.value = finalDevices
            it.groupList = finalGroups
        })
    }

    fun deleteDevices() {
        appDatabase.getGroupTable().deleteHardwareGroupItems()
        appDatabase.getHardwareTable().deleteDevices()
    }

    fun deleteDevice(hardwareDevice: HardwareDevice) {
        appDatabase.getHardwareTable().deleteDevice(hardwareDevice)
        appDatabase.getGroupTable().deleteItemByHardwareId(hardwareDevice.rowId)
    }

    fun getGroupAsync(groupId: String): Resource<HardwareGroup>? {
        LogSystem.e("TAG","getGroupAsync Invoked $groupId")
        var hardwareGroup = appDatabase.getGroupTable().getGroup(groupId.toLong())
        return Success(hardwareGroup)
    }

    fun addGroup(group: HardwareGroup): Resource<HardwareGroup> {
        if (appDatabase.getGroupTable().addGroup(group)) {
            return Success(group)
        } else {
            return Failure(false, 101, "Something went wrong!")
        }
    }

    fun removeGroup(hardwareGroup: HardwareGroup) {
        appDatabase.getGroupTable().deleteGroup(hardwareGroup)
    }

    fun hasGroupName(groupTitle: String): Boolean {
        return appDatabase.getGroupTable().hasGroupName(groupTitle)
    }

    fun updateDevice(device: HardwareDevice) {
        appDatabase.getHardwareTable().update(device)
    }

    fun hasPortConflict(port: String): Boolean {
        return appDatabase.getGroupTable().hasPort(port).isNotEmpty()
    }
}
