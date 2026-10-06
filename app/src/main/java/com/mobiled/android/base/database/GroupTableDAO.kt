package com.mobiled.android.base.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mobiled.android.LogSystem
import com.mobiled.android.base.model.HardwareDevice
import com.mobiled.android.base.model.HardwareGroup
import com.mobiled.android.base.model.HardwareGroupItem

@Dao
interface GroupTableDAO {
    @Query("SELECT * FROM hardwaregroup")
    fun getHardwareGroupList(): List<HardwareGroup>

    @Query("SELECT * FROM hardwaregroup WHERE rowId =:groupId LIMIT 1")
    fun getItem(groupId: Long): HardwareGroup

    @Query("SELECT COUNT(*) FROM hardwaregroup WHERE groupTitle =:groupTitle LIMIT 1")
    fun getItemByTitle(groupTitle: String): Int

    @Delete
    fun deleteItem(hardwareGroup: HardwareGroup)

    @Transaction
    fun getGroup(groupId: Long): HardwareGroup {
        var item = getItem(groupId)
        var groupTableItems = getGroupItems(groupId)
        LogSystem.e("TAG", "Group ${groupId} Table Item Size : ${groupTableItems.size}")

        if (item.allDevices && groupTableItems.isEmpty()) {

            var groupItems = arrayListOf<HardwareGroupItem>()
            for (device in getDevices()) {
                var hardwareGroupItem = HardwareGroupItem()
                hardwareGroupItem.groupId = item.rowId
                hardwareGroupItem.selected = true
                hardwareGroupItem.hardwareDevice = device
                groupItems.add(hardwareGroupItem)
            }

            insertGroupDevices(groupItems)

            groupTableItems = getGroupItems(groupId)
        }

        var groupItems = arrayListOf<HardwareGroupItem>()
        for (device in getDevices()) {
            var hardwareGroupItem = HardwareGroupItem()
            hardwareGroupItem.hardwareDevice = device
            hardwareGroupItem.selected = false
            groupItems.add(hardwareGroupItem)
        }

        for (groupItem in groupTableItems) {
            groupItems.forEach { nonItem ->
                if (nonItem.hardwareDevice?.rowId == groupItem.hardwareDevice?.rowId) {
                    LogSystem.e("TAG", "nonItem Item : ${nonItem.hardwareDevice?.rowId} groupItem = ${groupItem.hardwareDevice?.rowId} RowId : ${nonItem.gItemRowId}")
                    nonItem.gItemRowId = groupItem.gItemRowId
                    nonItem.selected = true
                    nonItem.groupId = groupItem.groupId
                    nonItem.Gport = groupItem.Gport
                    nonItem.GState = groupItem.GState
                }
            }
        }
        groupItems.forEach {
            LogSystem.e("TAG", "Item : ${it.gItemRowId}")
        }
        item.groupItems = groupItems

        return item
    }

    @Query("SELECT * FROM hardwaregroupitem WHERE groupId =:groupId")
    fun getGroupItems(groupId: Long): List<HardwareGroupItem>

    @Query("SELECT * FROM hardwaregroupitem")
    fun getGroupItems(): List<HardwareGroupItem>

    @Query("DELETE FROM hardwaregroupitem WHERE groupId =:groupId")
    fun deleteGroupDevices(groupId: Long)

    @Insert
    fun insertGroupDevices(devicesList: List<HardwareGroupItem>)

    @Query("SELECT * FROM hardware_device")
    fun getDevices(): List<HardwareDevice>

    @Query("SELECT * FROM HARDWARE_DEVICE WHERE rowId =:hardwareItem")
    fun getHardwareItem(hardwareItem: Long): HardwareDevice


    fun getHardwareGroups(): List<HardwareGroup> {
        var items = getHardwareGroupList()
        for (item in items) {
            var groupItems = getGroupItems(item.rowId ?: -1L)
            item.groupItems = groupItems

            if (item.allDevices && groupItems.isEmpty()) {
                var groupItems = arrayListOf<HardwareGroupItem>()
                for (device in getDevices()) {
                    var hardwareGroupItem = HardwareGroupItem()
                    hardwareGroupItem.groupId = item.rowId
                    hardwareGroupItem.hardwareDevice = device
                    groupItems.add(hardwareGroupItem)
                }
                insertGroupDevices(groupItems)
                item.groupItems = getGroupItems(item.rowId ?: -1L)
            }
        }

        return items
    }

    @Insert
    fun addItem(group: HardwareGroup): Long

    @Update
    fun updateItem(group: HardwareGroup): Int

    @Transaction
    fun addGroup(group: HardwareGroup): Boolean {
        if (group.rowId == null) {
            group.rowId = addItem(group)
            if (group.rowId == -1L) {
                return false
            }
        } else {
            if (updateItem(group) != 1) {
                return false;
            }
            deleteGroupDevices(group.rowId!!)
        }
        group.groupItems?.let {
            it.forEach { hgi ->
                hgi.groupId = group?.rowId
            }
            insertGroupDevices(it)
        }

        return true
    }

    @Transaction
    fun deleteGroup(hardwareGroup: HardwareGroup) {
        deleteGroupDevices(hardwareGroup.rowId ?: -1)
        deleteItem(hardwareGroup)
    }

    fun hasGroupName(groupTitle: String): Boolean {
        return getItemByTitle(groupTitle) >= 1
    }

    @Query("DELETE FROM HardwareGroupItem WHERE rowId =:hardwareDeviceRowId")
    fun deleteItemByHardwareId(hardwareDeviceRowId: Long?)

    @Query("DELETE FROM HardwareGroupItem")
    fun deleteHardwareGroupItems()
    @Query("SELECT Gport FROM HardwareGroupItem WHERE Gport =:port")
    fun hasPort(port: String): List<String>


}